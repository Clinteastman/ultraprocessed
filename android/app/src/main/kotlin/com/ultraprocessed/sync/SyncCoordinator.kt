package com.ultraprocessed.sync

import android.util.Log
import com.ultraprocessed.data.AppDatabase
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.SyncState
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.data.repository.FoodRepository
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val TAG = "Ultraprocessed.Sync"

/**
 * Push-only sync coordinator. Pending FoodEntry + ConsumptionLog rows
 * are uploaded to the configured backend, marked SYNCED on success.
 *
 * Trigger points:
 *  - After "I ate it" in the result screen.
 *  - On app startup (in case anything was queued offline).
 *
 * Pulls (e.g. dashboard aggregates) live elsewhere; the today widget
 * reads directly from Room so it works without a backend at all.
 */
class SyncCoordinator(
    private val settings: Settings,
    private val secrets: SecretStore,
    private val httpClient: HttpClient,
    private val database: AppDatabase,
    private val foodRepository: FoodRepository,
    private val consumptionRepository: ConsumptionRepository,
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

        if (pendingFoods.isEmpty() && pendingLogs.isEmpty()) {
            return SyncResult.UpToDate
        }

        val foodsResult = client.pushFoods(pendingFoods.map { it.toDto() })
        if (foodsResult.isFailure) {
            val msg = foodsResult.exceptionOrNull()?.message ?: "food push failed"
            Log.w(TAG, "food push failed: $msg")
            return SyncResult.Failed(msg)
        }
        markFoodsSynced(pendingFoods)

        val logsResult = client.pushConsumption(pendingLogs.map { it.toDto() })
        if (logsResult.isFailure) {
            val msg = logsResult.exceptionOrNull()?.message ?: "consumption push failed"
            Log.w(TAG, "consumption push failed: $msg")
            return SyncResult.Failed(msg)
        }
        markLogsSynced(pendingLogs)

        // Best-effort image upload for any synced food whose JPEG hasn't
        // been pushed yet. Failures here don't fail the sync overall;
        // the row stays image_synced=false so the next attempt retries.
        uploadPendingImages(client)

        return SyncResult.Pushed(pendingFoods.size, pendingLogs.size)
    }

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
    data class Pushed(val foodCount: Int, val logCount: Int) : SyncResult()
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
