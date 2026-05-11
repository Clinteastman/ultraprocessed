package com.ultraprocessed.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.analyzer.Nutrients
import com.ultraprocessed.core.Http
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.data.repository.FoodRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Backs [FoodDetailScreen]. Streams a single [FoodEntry] from Room
 * (so an inbound sync update redraws automatically) plus its consumption
 * history. Parses the JSON-serialised ingredient list and per-100g
 * nutrient blob lazily.
 */
class FoodDetailViewModel(
    private val foodUuid: String,
    private val foodRepository: FoodRepository,
    private val consumptionRepository: ConsumptionRepository
) : ViewModel() {

    val food: StateFlow<FoodEntry?> =
        foodRepository.observe(foodUuid)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ingredients: StateFlow<List<String>> =
        foodRepository.observe(foodUuid)
            .map { entry -> parseIngredients(entry?.ingredientsJson) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nutrients: StateFlow<Nutrients?> =
        foodRepository.observe(foodUuid)
            .map { entry -> parseNutrients(entry?.nutrientsJson) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<ConsumptionLog>> =
        consumptionRepository.observeByFood(foodUuid, limit = 200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun parseIngredients(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            Http.Json.decodeFromString(ListSerializer(String.serializer()), json)
        }.getOrDefault(emptyList())
    }

    private fun parseNutrients(json: String?): Nutrients? {
        if (json.isNullOrBlank()) return null
        return runCatching { Http.Json.decodeFromString(Nutrients.serializer(), json) }.getOrNull()
    }

    companion object {
        const val ARG_FOOD_UUID = "foodUuid"

        fun factory(foodUuid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                FoodDetailViewModel(
                    foodUuid = foodUuid,
                    foodRepository = app.container.foodRepository,
                    consumptionRepository = app.container.consumptionRepository
                )
            }
        }
    }
}
