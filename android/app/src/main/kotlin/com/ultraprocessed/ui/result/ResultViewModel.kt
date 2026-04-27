package com.ultraprocessed.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.analyzer.FoodAnalysis
import com.ultraprocessed.core.AppContainer
import com.ultraprocessed.core.Http
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.FoodSource
import com.ultraprocessed.data.entities.SyncState
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.data.repository.FoodRepository
import com.ultraprocessed.ui.PendingResult
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.UUID

/**
 * Persists a [PendingResult] as a FoodEntry plus a ConsumptionLog when
 * the user confirms "I ate it" with a percentage. The ViewModel is
 * intentionally thin; the UI owns its slider state and confirmation flow.
 */
class ResultViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val foodRepo: FoodRepository = container.foodRepository
    private val consumptionRepo: ConsumptionRepository = container.consumptionRepository

    /**
     * Classify a free-text food description (e.g. "mango", "tesco hummus")
     * via the configured analyzer. Used by the manual-entry chip on the
     * result screen when the model's primary + alternatives are all wrong.
     */
    suspend fun classify(text: String): Result<com.ultraprocessed.analyzer.FoodAnalysis> {
        val analyzer = runCatching { container.analyzerFactory.current() }.getOrElse {
            return Result.failure(it)
        }
        return analyzer.analyzeText(text)
    }

    fun logConsumption(
        pending: PendingResult,
        percentageEaten: Int,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val foodUuid = UUID.randomUUID().toString()

            val imagePath = pending.imageBytes?.let { writeImage(foodUuid, it) }

            val foodEntry = FoodEntry(
                clientUuid = foodUuid,
                name = pending.analysis.name,
                brand = pending.analysis.brand,
                barcode = pending.barcode,
                novaClass = pending.analysis.novaClass,
                novaRationale = pending.analysis.novaRationale,
                kcalPer100g = pending.analysis.kcalPer100g,
                kcalPerUnit = pending.analysis.kcalPerUnit,
                servingDescription = pending.analysis.servingDescription,
                imagePath = imagePath,
                imageUrl = pending.imageUrl,
                ingredientsJson = Http.Json.encodeToString(
                    ListSerializer(String.serializer()),
                    pending.analysis.ingredients
                ),
                source = pending.source,
                confidence = pending.analysis.confidence,
                createdAt = now,
                updatedAt = now,
                syncState = SyncState.PENDING
            )
            foodRepo.upsert(foodEntry)

            val kcalSnapshot = estimateKcal(pending.analysis, percentageEaten)

            val log = ConsumptionLog(
                clientUuid = UUID.randomUUID().toString(),
                foodClientUuid = foodUuid,
                percentageEaten = percentageEaten.coerceIn(0, 100),
                eatenAt = now,
                kcalConsumedSnapshot = kcalSnapshot,
                createdAt = now
            )
            consumptionRepo.log(log)
            onComplete()
        }
    }

    private fun writeImage(uuid: String, bytes: ByteArray): String? {
        return runCatching {
            val dir = File(container.let { (it as? AppContainerWithContext)?.appContext?.filesDir ?: defaultDir() }, "scans").apply { mkdirs() }
            val file = File(dir, "$uuid.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    private fun defaultDir(): File = File(System.getProperty("java.io.tmpdir") ?: "/tmp")

    private fun estimateKcal(analysis: FoodAnalysis, pct: Int): Double? {
        val kcal100 = analysis.kcalPer100g
        val kcalUnit = analysis.kcalPerUnit
        val factor = pct / 100.0
        return when {
            kcalUnit != null -> kcalUnit * factor
            kcal100 != null -> kcal100 * factor
            else -> null
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                ResultViewModel(app.container)
            }
        }
    }
}

/**
 * Compatibility shim - keeps the [writeImage] helper readable while we
 * have AppContainer not exposing context directly.
 */
private interface AppContainerWithContext {
    val appContext: android.content.Context
}
