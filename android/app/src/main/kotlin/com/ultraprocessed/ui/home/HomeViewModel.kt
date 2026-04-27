package com.ultraprocessed.ui.home

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
import com.ultraprocessed.data.entities.SyncState
import com.ultraprocessed.domain.IntakeAggregation
import com.ultraprocessed.domain.aggregate
import com.ultraprocessed.ui.components.DateRangePreset
import com.ultraprocessed.ui.components.DateRangeWindow
import com.ultraprocessed.ui.components.computeRangeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class HomeViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _selected = MutableStateFlow(DateRangePreset.Today)
    val selected: StateFlow<DateRangePreset> = _selected.asStateFlow()

    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customRange: StateFlow<Pair<Long, Long>?> = _customRange.asStateFlow()

    /**
     * The recent ConsumptionLog list. We over-fetch (last 1000 entries) so
     * a single Flow can drive every preset's aggregate, with filtering
     * happening in [aggregate]. Cheap on phone scale.
     */
    private val recent = container.consumptionRepository.observeRecent(limit = 1000)

    val window: StateFlow<DateRangeWindow> =
        combine(_selected, _customRange) { preset, custom ->
            computeRangeWindow(preset, custom?.first, custom?.second)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            computeRangeWindow(DateRangePreset.Today)
        )

    val state: StateFlow<HomeState> =
        combine(_selected, _customRange, recent) { preset, custom, items ->
            Triple(preset, custom, items)
        }
            .map { (preset, custom, items) ->
                val w = computeRangeWindow(preset, custom?.first, custom?.second)
                HomeState(
                    window = w,
                    aggregation = aggregate(items, w.fromMs, w.toMs),
                    items = items.filter { it.log.eatenAt in w.fromMs..w.toMs }
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeState(
                    window = computeRangeWindow(DateRangePreset.Today),
                    aggregation = aggregate(emptyList(), 0L, Long.MAX_VALUE),
                    items = emptyList()
                )
            )

    fun selectPreset(preset: DateRangePreset) {
        _selected.value = preset
    }

    fun selectCustomRange(fromMs: Long, toMs: Long) {
        _customRange.value = fromMs to toMs
        _selected.value = DateRangePreset.Custom
    }

    fun updateGrams(item: ConsumptionWithFood, newGrams: Double) {
        viewModelScope.launch {
            val recomputedKcal = run {
                item.food.kcalPer100g?.let { return@run it * (newGrams / 100.0) }
                val perUnit = item.food.kcalPerUnit
                val gpu = item.food.gramsPerUnit
                if (perUnit != null && gpu != null && gpu > 0) perUnit * (newGrams / gpu) else null
            }
            val factor = newGrams / 100.0
            val per100 = item.food.nutrientsJson?.let {
                runCatching { Http.Json.decodeFromString(Nutrients.serializer(), it) }.getOrNull()
            }
            val consumedJson = per100?.scaledBy(factor)?.let {
                Http.Json.encodeToString(Nutrients.serializer(), it)
            }
            val pct = ((newGrams / 100.0) * 100.0).roundToInt().coerceIn(0, 100)

            val updated = item.log.copy(
                gramsEaten = newGrams,
                percentageEaten = pct,
                kcalConsumedSnapshot = recomputedKcal,
                nutrientsConsumedJson = consumedJson,
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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                HomeViewModel(app.container)
            }
        }
    }
}

data class HomeState(
    val window: DateRangeWindow,
    val aggregation: IntakeAggregation,
    val items: List<ConsumptionWithFood>
)
