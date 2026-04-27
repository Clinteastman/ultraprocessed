package com.ultraprocessed.ui.search

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID

/**
 * Backs [SearchScreen]. Lets the user find a previously-seen food by name
 * (or classify a new one via the LLM) and log it with a current-time
 * timestamp - covers the case where a meal was eaten earlier without a
 * scan or photo.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _classifying = MutableStateFlow(false)
    val classifying: StateFlow<Boolean> = _classifying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val results: StateFlow<List<FoodEntry>> = _query
        .debounce(150)
        .flatMapLatest { q ->
            val trimmed = q.trim()
            if (trimmed.isBlank()) container.foodRepository.observeRecent(20)
            else container.foodRepository.observeSearch(trimmed, limit = 30)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _error.value = null
        _query.value = q
    }

    /** Log an existing FoodEntry as eaten now with [grams] grams. */
    fun logExisting(food: FoodEntry, grams: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val kcalSnapshot = kcalForGrams(food, grams)
            val factor = grams / 100.0
            val per100 = food.nutrientsJson?.let {
                runCatching { Http.Json.decodeFromString(Nutrients.serializer(), it) }.getOrNull()
            }
            val consumedNutrientsJson = per100?.scaledBy(factor)?.let {
                Http.Json.encodeToString(Nutrients.serializer(), it)
            }
            val legacyPct = ((grams / 100.0) * 100.0).toInt().coerceIn(0, 100)

            val log = ConsumptionLog(
                clientUuid = UUID.randomUUID().toString(),
                foodClientUuid = food.clientUuid,
                percentageEaten = legacyPct,
                gramsEaten = grams,
                eatenAt = now,
                kcalConsumedSnapshot = kcalSnapshot,
                nutrientsConsumedJson = consumedNutrientsJson,
                createdAt = now
            )
            container.consumptionRepository.log(log)
            container.syncCoordinator.trigger()
            onDone()
        }
    }

    /**
     * Classify a free-text food name via the configured LLM and log it as
     * a freshly-created FoodEntry. Uses [grams] grams. Returns failure
     * through [error] if the analyzer is misconfigured or the request fails.
     */
    fun classifyAndLog(text: String, grams: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            _classifying.value = true
            _error.value = null
            try {
                val analyzer = runCatching { container.analyzerFactory.current() }.getOrElse {
                    _error.value = "Set a provider in Settings first."
                    return@launch
                }
                val analysis = analyzer.analyzeText(text).getOrElse {
                    _error.value = it.message ?: "Classification failed."
                    return@launch
                }

                val now = System.currentTimeMillis()
                val foodUuid = UUID.randomUUID().toString()
                val nutrientsJson = analysis.nutrientsPer100g?.let {
                    Http.Json.encodeToString(Nutrients.serializer(), it)
                }
                val food = FoodEntry(
                    clientUuid = foodUuid,
                    name = analysis.name.ifBlank { text },
                    brand = analysis.brand,
                    barcode = null,
                    novaClass = analysis.novaClass,
                    novaRationale = analysis.novaRationale,
                    kcalPer100g = analysis.kcalPer100g,
                    kcalPerUnit = analysis.kcalPerUnit,
                    gramsPerUnit = analysis.gramsPerUnit,
                    packageGrams = analysis.packageGrams,
                    servingDescription = analysis.servingDescription,
                    imagePath = null,
                    imageUrl = null,
                    ingredientsJson = Http.Json.encodeToString(
                        ListSerializer(String.serializer()),
                        analysis.ingredients
                    ),
                    nutrientsJson = nutrientsJson,
                    source = FoodSource.MANUAL,
                    confidence = analysis.confidence,
                    createdAt = now,
                    updatedAt = now,
                    syncState = SyncState.PENDING
                )
                container.foodRepository.upsert(food)

                val kcalSnapshot = kcalFromAnalysis(analysis, grams)
                val factor = grams / 100.0
                val consumedNutrientsJson = analysis.nutrientsPer100g?.scaledBy(factor)?.let {
                    Http.Json.encodeToString(Nutrients.serializer(), it)
                }
                val legacyPct = ((grams / 100.0) * 100.0).toInt().coerceIn(0, 100)
                val log = ConsumptionLog(
                    clientUuid = UUID.randomUUID().toString(),
                    foodClientUuid = foodUuid,
                    percentageEaten = legacyPct,
                    gramsEaten = grams,
                    eatenAt = now,
                    kcalConsumedSnapshot = kcalSnapshot,
                    nutrientsConsumedJson = consumedNutrientsJson,
                    createdAt = now
                )
                container.consumptionRepository.log(log)
                container.syncCoordinator.trigger()
                onDone()
            } finally {
                _classifying.value = false
            }
        }
    }

    private fun kcalForGrams(food: FoodEntry, grams: Double): Double? {
        food.kcalPer100g?.let { return it * (grams / 100.0) }
        val pu = food.kcalPerUnit
        val gpu = food.gramsPerUnit
        if (pu != null && gpu != null && gpu > 0) return pu * (grams / gpu)
        return null
    }

    private fun kcalFromAnalysis(a: FoodAnalysis, grams: Double): Double? {
        a.kcalPer100g?.let { return it * (grams / 100.0) }
        val pu = a.kcalPerUnit
        val gpu = a.gramsPerUnit
        if (pu != null && gpu != null && gpu > 0) return pu * (grams / gpu)
        return null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                SearchViewModel(app.container)
            }
        }
    }
}
