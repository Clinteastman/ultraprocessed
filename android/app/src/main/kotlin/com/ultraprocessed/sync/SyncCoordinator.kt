package com.ultraprocessed.sync

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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

    fun trigger() {
        scope.launch(Dispatchers.IO) {
            runCatching { syncOnce() }
        }
    }

    suspend fun syncOnce(): SyncResult = mutex.withLock {
        val baseUrl = settings.backendBaseUrl.first().orEmpty()
        val token = secrets.backendToken.orEmpty()
        if (baseUrl.isBlank() || token.isBlank()) return SyncResult.NotConfigured

        val client = BackendClient(baseUrl = baseUrl, token = token, client = httpClient)
        if (!client.health()) return SyncResult.BackendUnreachable

        val pendingFoods = foodRepository.pending()
        val pendingLogs = consumptionRepository.pending()

        if (pendingFoods.isEmpty() && pendingLogs.isEmpty()) {
            return SyncResult.UpToDate
        }

        val foodsResult = client.pushFoods(pendingFoods.map { it.toDto() })
        if (foodsResult.isFailure) {
            return SyncResult.Failed(foodsResult.exceptionOrNull()?.message ?: "food push failed")
        }
        markFoodsSynced(pendingFoods)

        val logsResult = client.pushConsumption(pendingLogs.map { it.toDto() })
        if (logsResult.isFailure) {
            return SyncResult.Failed(logsResult.exceptionOrNull()?.message ?: "consumption push failed")
        }
        markLogsSynced(pendingLogs)

        return SyncResult.Pushed(pendingFoods.size, pendingLogs.size)
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
    eatenAt = eatenAt.toIso(),
    lat = lat,
    lng = lng,
    locationLabel = locationLabel,
    kcalConsumedSnapshot = kcalConsumedSnapshot,
    nutrientsConsumedJson = nutrientsConsumedJson,
    createdAt = createdAt.toIso()
)
