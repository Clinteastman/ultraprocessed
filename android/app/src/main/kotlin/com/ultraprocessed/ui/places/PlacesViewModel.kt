package com.ultraprocessed.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.ui.components.DateRangePreset
import com.ultraprocessed.ui.components.DateRangeWindow
import com.ultraprocessed.ui.components.computeRangeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class PlacesViewModel(
    consumptionRepo: ConsumptionRepository
) : ViewModel() {

    private val _selected = MutableStateFlow(DateRangePreset.Last7)
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)

    val state: StateFlow<PlacesState> =
        combine(
            consumptionRepo.observeLocated(limit = 2000),
            _selected,
            _customRange
        ) { items, preset, custom ->
            val window = computeRangeWindow(preset, custom?.first, custom?.second)
            buildState(items, preset, window)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlacesState(
                points = emptyList(),
                groups = emptyList(),
                selected = DateRangePreset.Last7,
                window = computeRangeWindow(DateRangePreset.Last7)
            )
        )

    fun selectPreset(preset: DateRangePreset) {
        _selected.value = preset
    }

    fun selectCustomRange(fromMs: Long, toMs: Long) {
        _customRange.value = fromMs to toMs
        _selected.value = DateRangePreset.Custom
    }

    private fun buildState(
        all: List<ConsumptionWithFood>,
        preset: DateRangePreset,
        window: DateRangeWindow
    ): PlacesState {
        val timeFmt = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
        val items = all.filter { it.log.eatenAt in window.fromMs..window.toMs }

        val points = items.mapNotNull { i ->
            val lat = i.log.lat ?: return@mapNotNull null
            val lng = i.log.lng ?: return@mapNotNull null
            MapPoint(
                clientUuid = i.log.clientUuid,
                lat = lat,
                lng = lng,
                title = i.food.name,
                subtitle = "${i.log.locationLabel ?: "Unknown"} · ${timeFmt.format(Date(i.log.eatenAt))}",
                novaClass = i.food.novaClass
            )
        }

        // Group by label first; fall back to coarse-rounded coords (3dp ~ 100m
        // in lat) so unlabelled clusters still aggregate into something useful.
        val grouped = items.groupBy { item ->
            item.log.locationLabel?.takeIf { it.isNotBlank() }
                ?: "%.3f, %.3f".format(item.log.lat ?: 0.0, item.log.lng ?: 0.0)
        }
        val groups = grouped.map { (label, list) ->
            val totalKcal = list.sumOf { it.log.kcalConsumedSnapshot ?: 0.0 }
            val nova4Cal = list.filter { it.food.novaClass == 4 }
                .sumOf { it.log.kcalConsumedSnapshot ?: 0.0 }
            val weighted = list.sumOf { (it.log.kcalConsumedSnapshot ?: 0.0) * it.food.novaClass }
            val novaAvg = if (totalKcal > 0) weighted / totalKcal else
                list.sumOf { it.food.novaClass.toDouble() } / list.size
            val upfShare = if (totalKcal > 0) (nova4Cal / totalKcal * 100.0).roundToInt() else 0
            PlaceGroup(
                label = label,
                itemCount = list.size,
                totalKcal = totalKcal,
                novaAverage = novaAvg,
                upfShare = upfShare
            )
        }.sortedByDescending { it.itemCount }

        return PlacesState(points = points, groups = groups, selected = preset, window = window)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                PlacesViewModel(consumptionRepo = app.container.consumptionRepository)
            }
        }
    }
}

data class PlacesState(
    val points: List<MapPoint>,
    val groups: List<PlaceGroup>,
    val selected: DateRangePreset,
    val window: DateRangeWindow
)

data class MapPoint(
    val clientUuid: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val subtitle: String,
    val novaClass: Int
)

data class PlaceGroup(
    val label: String,
    val itemCount: Int,
    val totalKcal: Double,
    val novaAverage: Double,
    val upfShare: Int
)
