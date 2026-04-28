package com.ultraprocessed.sync

import android.content.Context
import android.util.Log
import com.ultraprocessed.data.AppDatabase
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.ScheduleType
import com.ultraprocessed.data.entities.SyncState
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.data.repository.FastingRepository
import com.ultraprocessed.data.repository.FoodRepository
import com.ultraprocessed.widget.UltraprocessedWidgetUpdater
import com.ultraprocessed.data.settings.SecretStore
import com.ultraprocessed.data.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val TAG = "Ultraprocessed.Sync"

/**
 * Two-way sync coordinator. Pending FoodEntry + ConsumptionLog rows
 * are uploaded to the configured backend, marked SYNCED on success;
 * then the latest server-side rows are pulled back so entries created
 * on other clients (dashboard, Home Assistant, another phone) appear
 * on this device.
 *
 * Trigger points:
 *  - After "I ate it" in the result screen.
 *  - On app startup (in case anything was queued offline).
 *  - On pull-to-refresh from the home screen.
 */
class SyncCoordinator(
    private val appContext: Context,
    private val settings: Settings,
    private val secrets: SecretStore,
    private val httpClient: HttpClient,
    private val database: AppDatabase,
    private val foodRepository: FoodRepository,
    private val consumptionRepository: ConsumptionRepository,
    private val fastingRepository: FastingRepository,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()

    private val _lastResult = MutableStateFlow<SyncResult>(SyncResult.NotConfigured)
    val lastResult: StateFlow<SyncResult> = _lastResult.asStateFlow()

    fun trigger() {
        scope.launch(Dispatchers.IO) {
            runCatching { syncOnce() }
                .onFailure { Log.e(TAG, "syncOnce threw", it) }
        }
    }

    suspend fun syncOnce(): SyncResult = mutex.withLock {
        val result = runSync()
        _lastResult.value = result
        Log.i(TAG, "sync result: $result")
        // Refresh any home-screen widget instances so today's totals stay
        // current. Cheap when there are no widgets installed.
        runCatching { UltraprocessedWidgetUpdater.update(appContext) }
            .onFailure { Log.w(TAG, "widget update failed: ${it.message}") }
        result
    }

    private suspend fun runSync(): SyncResult {
        val baseUrl = settings.backendBaseUrl.first().orEmpty()
        val token = secrets.backendToken.orEmpty()
        if (baseUrl.isBlank() || token.isBlank()) {
            Log.i(TAG, "skipping sync: backend not configured (url='$baseUrl', tokenSet=${token.isNotBlank()})")
            return SyncResult.NotConfigured
        }

        Log.i(TAG, "syncing to $baseUrl")
        val client = BackendClient(baseUrl = baseUrl, token = token, client = httpClient)
        if (!client.health()) {
            Log.w(TAG, "backend health check failed at $baseUrl")
            return SyncResult.BackendUnreachable
        }

        val pendingFoods = foodRepository.pending()
        val pendingLogs = consumptionRepository.pending()
        Log.i(TAG, "pending: ${pendingFoods.size} foods, ${pendingLogs.size} logs")

        if (pendingFoods.isNotEmpty()) {
            val foodsResult = client.pushFoods(pendingFoods.map { it.toDto() })
            if (foodsResult.isFailure) {
                val msg = foodsResult.exceptionOrNull()?.message ?: "food push failed"
                Log.w(TAG, "food push failed: $msg")
                return SyncResult.Failed(msg)
            }
            markFoodsSynced(pendingFoods)
        }

        if (pendingLogs.isNotEmpty()) {
            val logsResult = client.pushConsumption(pendingLogs.map { it.toDto() })
            if (logsResult.isFailure) {
                val msg = logsResult.exceptionOrNull()?.message ?: "consumption push failed"
                Log.w(TAG, "consumption push failed: $msg")
                return SyncResult.Failed(msg)
            }
            markLogsSynced(pendingLogs)
        }

        // Best-effort image upload for any synced food whose JPEG hasn't
        // been pushed yet. Failures here don't fail the sync overall;
        // the row stays image_synced=false so the next attempt retries.
        uploadPendingImages(client)

        // Pull pass: brings down rows created on other clients.
        val pulled = pullRecent(client)

        return SyncResult.Synced(
            foodsPushed = pendingFoods.size,
            logsPushed = pendingLogs.size,
            foodsPulled = pulled.foods,
            logsPulled = pulled.logs
        )
    }

    /**
     * Fetches the most recent foods and consumption logs from the backend
     * and merges them into Room. New rows are inserted as SYNCED; existing
     * rows are updated only when the server has a newer [FoodEntry.updatedAt]
     * (foods) or are left alone (logs - immutable post-creation in practice,
     * and a pending local edit must not be clobbered).
     */
    private suspend fun pullRecent(client: BackendClient): PullCounts {
        var newFoods = 0
        var newLogs = 0

        client.listFoods(limit = 200).onSuccess { foods ->
            for (dto in foods) {
                runCatching {
                    val existing = foodRepository.get(dto.clientUuid)
                    val updatedMs = dto.updatedAt.toEpochMs()
                    if (existing == null) {
                        foodRepository.upsert(dto.toEntity(updatedMs))
                        newFoods++
                    } else if (updatedMs > existing.updatedAt && existing.syncState == SyncState.SYNCED) {
                        foodRepository.upsert(
                            dto.toEntity(updatedMs).copy(
                                imagePath = existing.imagePath,
                                imageSynced = existing.imageSynced
                            )
                        )
                    }
                }.onFailure { Log.w(TAG, "skipping food ${dto.clientUuid}: ${it.message}") }
            }
        }.onFailure { Log.w(TAG, "food pull failed: ${it.message}") }

        client.listConsumption(limit = 500).onSuccess { logs ->
            for (dto in logs) {
                runCatching {
                    val existing = database.consumptionLogDao().getByClientUuid(dto.clientUuid)
                    if (existing == null) {
                        consumptionRepository.log(dto.toEntity())
                        newLogs++
                    }
                }.onFailure { Log.w(TAG, "skipping log ${dto.clientUuid}: ${it.message}") }
            }
        }.onFailure { Log.w(TAG, "consumption pull failed: ${it.message}") }

        // Pull active fasting profile. The backend stores at most one active
        // profile per user; we mirror that locally by reusing the active
        // row's primary key (so we update in place rather than accumulating
        // a new row on every sync).
        client.getActiveFastingProfile().onSuccess { dto ->
            if (dto != null) {
                runCatching {
                    val existing = fastingRepository.getActive()
                    val merged = dto.toEntity().copy(
                        id = existing?.id ?: 0L,
                        active = true
                    )
                    val savedId = fastingRepository.save(merged)
                    fastingRepository.setActive(savedId)
                }.onFailure { Log.w(TAG, "fasting profile merge failed: ${it.message}") }
            }
        }.onFailure { Log.w(TAG, "fasting pull failed: ${it.message}") }

        Log.i(TAG, "pulled: $newFoods new foods, $newLogs new logs")
        return PullCounts(foods = newFoods, logs = newLogs)
    }

    private data class PullCounts(val foods: Int, val logs: Int)

    private suspend fun uploadPendingImages(client: BackendClient) {
        val pending = foodRepository.pendingImages()
        if (pending.isEmpty()) return
        Log.i(TAG, "uploading ${pending.size} food images")
        for (food in pending) {
            val path = food.imagePath ?: continue
            val bytes = runCatching { File(path).readBytes() }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                Log.w(TAG, "missing/empty image at $path; marking synced to skip")
                foodRepository.upsert(food.copy(imageSynced = true))
                continue
            }
            val result = client.uploadFoodImage(food.clientUuid, bytes)
            if (result.isSuccess) {
                foodRepository.upsert(food.copy(imageSynced = true))
            } else {
                Log.w(TAG, "image upload failed for ${food.clientUuid}: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private suspend fun markFoodsSynced(rows: List<FoodEntry>) {
        rows.forEach {
            foodRepository.upsert(it.copy(syncState = SyncState.SYNCED))
        }
    }

    private suspend fun markLogsSynced(rows: List<ConsumptionLog>) {
        rows.forEach {
            database.consumptionLogDao().upsert(it.copy(syncState = SyncState.SYNCED))
        }
    }
}

sealed class SyncResult {
    data object NotConfigured : SyncResult()
    data object BackendUnreachable : SyncResult()
    data object UpToDate : SyncResult()
    data class Synced(
        val foodsPushed: Int,
        val logsPushed: Int,
        val foodsPulled: Int,
        val logsPulled: Int
    ) : SyncResult()
    data class Failed(val message: String) : SyncResult()
}

private val isoFormatter: DateTimeFormatter =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME

private fun Long.toIso(): String =
    Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).format(isoFormatter)

private fun FoodEntry.toDto(): FoodEntryDto = FoodEntryDto(
    clientUuid = clientUuid,
    name = name,
    brand = brand,
    barcode = barcode,
    novaClass = novaClass,
    novaRationale = novaRationale,
    kcalPer100g = kcalPer100g,
    kcalPerUnit = kcalPerUnit,
    gramsPerUnit = gramsPerUnit,
    packageGrams = packageGrams,
    servingDescription = servingDescription,
    imageUrl = imageUrl,
    ingredientsJson = ingredientsJson,
    nutrientsJson = nutrientsJson,
    source = source,
    confidence = confidence,
    createdAt = createdAt.toIso(),
    updatedAt = updatedAt.toIso()
)

private fun ConsumptionLog.toDto(): ConsumptionLogDto = ConsumptionLogDto(
    clientUuid = clientUuid,
    foodClientUuid = foodClientUuid,
    percentageEaten = percentageEaten,
    gramsEaten = gramsEaten,
    eatenAt = eatenAt.toIso(),
    lat = lat,
    lng = lng,
    locationLabel = locationLabel,
    kcalConsumedSnapshot = kcalConsumedSnapshot,
    nutrientsConsumedJson = nutrientsConsumedJson,
    createdAt = createdAt.toIso()
)

/**
 * Parse the backend's ISO-8601 timestamp into epoch millis. The backend
 * round-trips datetimes through SQLite which strips timezone info, so the
 * wire format may have an explicit offset (`+00:00`), a `Z`, or no offset
 * at all. Treat the offset-less form as UTC since the backend stores
 * everything in UTC.
 */
internal fun String.toEpochMs(): Long {
    return runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(this).toEpochMilli() }
        .recoverCatching {
            LocalDateTime.parse(this).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        .getOrThrow()
}

internal fun FoodEntryDto.toEntity(updatedAtMs: Long): FoodEntry = FoodEntry(
    clientUuid = clientUuid,
    name = name,
    brand = brand,
    barcode = barcode,
    novaClass = novaClass,
    novaRationale = novaRationale,
    kcalPer100g = kcalPer100g,
    kcalPerUnit = kcalPerUnit,
    gramsPerUnit = gramsPerUnit,
    packageGrams = packageGrams,
    servingDescription = servingDescription,
    imagePath = null,
    imageUrl = imageUrl,
    ingredientsJson = ingredientsJson,
    nutrientsJson = nutrientsJson,
    source = source,
    confidence = confidence,
    createdAt = createdAt.toEpochMs(),
    updatedAt = updatedAtMs,
    syncState = SyncState.SYNCED,
    // Server already has whatever image is referenced; nothing to upload.
    imageSynced = true
)

internal fun ConsumptionLogDto.toEntity(): ConsumptionLog = ConsumptionLog(
    clientUuid = clientUuid,
    foodClientUuid = foodClientUuid,
    percentageEaten = percentageEaten,
    gramsEaten = gramsEaten,
    eatenAt = eatenAt.toEpochMs(),
    lat = lat,
    lng = lng,
    locationLabel = locationLabel,
    kcalConsumedSnapshot = kcalConsumedSnapshot,
    nutrientsConsumedJson = nutrientsConsumedJson,
    createdAt = createdAt.toEpochMs(),
    syncState = SyncState.SYNCED
)

internal fun FastingProfileDto.toEntity(): FastingProfile = FastingProfile(
    name = name,
    scheduleType = ScheduleType.valueOf(scheduleType),
    eatingWindowStartMinutes = eatingWindowStartMinutes,
    eatingWindowEndMinutes = eatingWindowEndMinutes,
    restrictedDaysMask = restrictedDaysMask,
    restrictedKcalTarget = restrictedKcalTarget,
    active = active
)
