package com.ultraprocessed.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.analyzer.Nutrients
import com.ultraprocessed.core.AppContainer
import com.ultraprocessed.core.Http
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class HistoryViewModel(
    private val container: AppContainer
) : ViewModel() {

    val recent: Flow<List<ConsumptionWithFood>> =
        container.consumptionRepository.observeRecent(limit = 200)

    /** Update the grams eaten for an existing log; recomputes kcal and nutrient snapshots. */
    fun updateGrams(item: ConsumptionWithFood, newGrams: Double) {
        viewModelScope.launch {
            val recomputedKcal = kcalForGrams(item.food, newGrams)
            val factor = newGrams / 100.0
            val per100g: Nutrients? = item.food.nutrientsJson?.let {
                runCatching { Http.Json.decodeFromString(Nutrients.serializer(), it) }.getOrNull()
            }
            val consumedNutrients = per100g?.scaledBy(factor)
            val consumedNutrientsJson = consumedNutrients?.let {
                Http.Json.encodeToString(Nutrients.serializer(), it)
            }
            // legacy percentage kept as a 0-100 clamp for back-compat
            val pct = ((newGrams / 100.0) * 100.0).roundToInt().coerceIn(0, 100)

            val updated = item.log.copy(
                gramsEaten = newGrams,
                percentageEaten = pct,
                kcalConsumedSnapshot = recomputedKcal,
                nutrientsConsumedJson = consumedNutrientsJson,
                syncState = SyncState.PENDING
            )
            container.consumptionRepository.log(updated)
            container.syncCoordinator.trigger()
        }
    }

    fun delete(item: ConsumptionWithFood) {
        viewModelScope.launch {
            container.consumptionRepository.delete(item.log.clientUuid)
            container.syncCoordinator.trigger()
        }
    }

    private fun kcalForGrams(food: FoodEntry, grams: Double): Double? {
        food.kcalPer100g?.let { return it * (grams / 100.0) }
        val perUnit = food.kcalPerUnit
        val gramsPerUnit = food.gramsPerUnit
        if (perUnit != null && gramsPerUnit != null && gramsPerUnit > 0) {
            return perUnit * (grams / gramsPerUnit)
        }
        return null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                HistoryViewModel(app.container)
            }
        }
    }
}
