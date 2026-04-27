package com.ultraprocessed.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.analyzer.FoodAnalysis
import com.ultraprocessed.analyzer.Nutrients
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
    private val syncCoordinator = container.syncCoordinator

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
        gramsEaten: Double,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val foodUuid = UUID.randomUUID().toString()

            val imagePath = pending.imageBytes?.let { writeImage(foodUuid, it) }

            val nutrientsJson = pending.analysis.nutrientsPer100g?.let {
                Http.Json.encodeToString(Nutrients.serializer(), it)
            }

            val foodEntry = FoodEntry(
                clientUuid = foodUuid,
                name = pending.analysis.name,
                brand = pending.analysis.brand,
                barcode = pending.barcode,
                novaClass = pending.analysis.novaClass,
                novaRationale = pending.analysis.novaRationale,
                kcalPer100g = pending.analysis.kcalPer100g,
                kcalPerUnit = pending.analysis.kcalPerUnit,
                gramsPerUnit = pending.analysis.gramsPerUnit,
                packageGrams = pending.analysis.packageGrams,
                servingDescription = pending.analysis.servingDescription,
                imagePath = imagePath,
                imageUrl = pending.imageUrl,
                ingredientsJson = Http.Json.encodeToString(
                    ListSerializer(String.serializer()),
                    pending.analysis.ingredients
                ),
                nutrientsJson = nutrientsJson,
                source = pending.source,
                confidence = pending.analysis.confidence,
                createdAt = now,
                updatedAt = now,
                syncState = SyncState.PENDING
            )
            foodRepo.upsert(foodEntry)

            val kcalSnapshot = kcalForGrams(pending.analysis, gramsEaten)
            val factor = gramsEaten / 100.0
            val consumedNutrients = pending.analysis.nutrientsPer100g?.scaledBy(factor)
            val consumedNutrientsJson = consumedNutrients?.let {
                Http.Json.encodeToString(Nutrients.serializer(), it)
            }

            // Legacy percentage_eaten: how many "100g basis units" did they eat,
            // expressed as a 0-100 int just so older sync paths don't choke. New
            // consumers read gramsEaten.
            val legacyPct = ((gramsEaten / 100.0) * 100.0).toInt().coerceIn(0, 999)

            val log = ConsumptionLog(
                clientUuid = UUID.randomUUID().toString(),
                foodClientUuid = foodUuid,
                percentageEaten = legacyPct.coerceAtMost(100),
                gramsEaten = gramsEaten,
                eatenAt = now,
                kcalConsumedSnapshot = kcalSnapshot,
                nutrientsConsumedJson = consumedNutrientsJson,
                createdAt = now
            )
            consumptionRepo.log(log)
            syncCoordinator.trigger()
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

    /**
     * kcal for a given gram amount. Prefers per-100g (most precise); falls
     * back to per-unit scaled by grams_per_unit. Returns null when neither
     * is known.
     */
    private fun kcalForGrams(analysis: FoodAnalysis, grams: Double): Double? {
        analysis.kcalPer100g?.let { return it * (grams / 100.0) }
        val perUnit = analysis.kcalPerUnit
        val gramsPerUnit = analysis.gramsPerUnit
        if (perUnit != null && gramsPerUnit != null && gramsPerUnit > 0) {
            return perUnit * (grams / gramsPerUnit)
        }
        return null
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
