package com.ultraprocessed.ui.gut

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.data.entities.BowelEvent
import com.ultraprocessed.data.entities.DailyCheckin
import com.ultraprocessed.data.entities.SymptomEvent
import com.ultraprocessed.data.entities.SymptomKind
import com.ultraprocessed.data.entities.SyncState
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.data.repository.IbsRepository
import com.ultraprocessed.domain.TriggerEngine
import com.ultraprocessed.sync.SyncCoordinator
import com.ultraprocessed.sync.TriggerReportDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Backs [GutScreen]. Streams today's bowel + symptom + check-in state
 * and exposes one-shot suspending logging methods that mark the rows
 * PENDING and trigger a sync.
 */
class GutViewModel(
    private val repo: IbsRepository,
    private val consumption: ConsumptionRepository,
    private val sync: SyncCoordinator
) : ViewModel() {

    /** Today's local-day window in epoch ms — recomputed on each cold load. */
    private val window: Pair<Long, Long> = todayWindow()

    val todayBowels: StateFlow<List<BowelEvent>> =
        repo.observeBowelRange(window.first, window.second)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaySymptoms: StateFlow<List<SymptomEvent>> =
        repo.observeSymptomRange(window.first, window.second)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayCheckin: StateFlow<DailyCheckin?> =
        repo.observeCheckin(LocalDate.now().toString())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentBowels: StateFlow<List<BowelEvent>> =
        repo.observeBowelRecent(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSymptoms: StateFlow<List<SymptomEvent>> =
        repo.observeSymptomRecent(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _triggers = MutableStateFlow<TriggersUi>(TriggersUi.Loading)
    val triggers: StateFlow<TriggersUi> = _triggers.asStateFlow()

    init {
        refreshTriggers()
    }

    fun consumeToast() { _toast.value = null }

    fun refreshTriggers() = viewModelScope.launch {
        _triggers.value = TriggersUi.Loading
        // Server is authoritative when reachable (sees data from other
        // devices). On failure or when not configured, fall back to the
        // on-device algorithm using local Room state only.
        val serverResult = runCatching { sync.fetchTriggerReport(days = 14, limit = 10) }
        val report = serverResult.getOrNull()
        _triggers.value = when {
            report != null -> TriggersUi.Loaded(report, source = TriggerSource.Server)
            else -> {
                val local = runCatching { computeLocalTriggers() }
                local.fold(
                    onSuccess = { TriggersUi.Loaded(it, source = TriggerSource.Local) },
                    onFailure = { TriggersUi.Error(it.message ?: "couldn't load triggers") }
                )
            }
        }
    }

    private suspend fun computeLocalTriggers(): TriggerReportDto {
        val to = System.currentTimeMillis()
        val from = to - 14L * 24 * 60 * 60 * 1000
        val lookbackMs = (TriggerEngine.DEFAULT_LOOKBACK_HOURS * 3600 * 1000).toLong()
        // Pull consumption from `from - lookback` so foods just before the
        // window's first flare are still in scope.
        val consumption = consumption.consumptionInRange(from - lookbackMs, to)
        val bowels = repo.bowelInRange(from, to)
        val symptoms = repo.symptomsInRange(from, to)
        return TriggerEngine.compute(
            bowels = bowels,
            symptoms = symptoms,
            consumption = consumption,
            fromMs = from,
            toMs = to,
            limit = 10
        )
    }

    fun logBowel(
        bristol: Int,
        urgency: Int,
        pain: Int,
        completeness: Int,
        blood: Boolean,
        mucus: Boolean,
        notes: String?
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        repo.logBowel(
            BowelEvent(
                clientUuid = UUID.randomUUID().toString(),
                occurredAt = now,
                bristol = bristol,
                urgency = urgency,
                pain = pain,
                completeness = completeness,
                blood = blood,
                mucus = mucus,
                notes = notes?.takeIf { it.isNotBlank() },
                syncState = SyncState.PENDING,
                createdAt = now
            )
        )
        _toast.value = "Bowel event logged"
        sync.trigger()
    }

    fun logSymptom(
        kind: SymptomKind,
        severity: Int,
        location: String?,
        notes: String?
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        repo.logSymptom(
            SymptomEvent(
                clientUuid = UUID.randomUUID().toString(),
                occurredAt = now,
                kind = kind,
                severity = severity,
                location = location?.takeIf { it.isNotBlank() },
                notes = notes?.takeIf { it.isNotBlank() },
                syncState = SyncState.PENDING,
                createdAt = now
            )
        )
        _toast.value = "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} logged"
        sync.trigger()
    }

    fun saveCheckin(
        bloating: Int,
        heartburn: Int,
        gutScore: Int?,
        stress: Int?,
        period: Boolean,
        notes: String?
    ) = viewModelScope.launch {
        val day = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val existing = repo.getCheckin(day)
        repo.saveCheckin(
            DailyCheckin(
                clientUuid = existing?.clientUuid ?: UUID.randomUUID().toString(),
                day = day,
                bloatingOverall = bloating,
                heartburnOverall = heartburn,
                gutScore = gutScore,
                stress = stress,
                period = period,
                notes = notes?.takeIf { it.isNotBlank() },
                syncState = SyncState.PENDING,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        _toast.value = "Daily check-in saved"
        sync.trigger()
    }

    fun deleteBowel(uuid: String) = viewModelScope.launch {
        repo.deleteBowel(uuid)
        sync.trigger()
    }

    fun deleteSymptom(uuid: String) = viewModelScope.launch {
        repo.deleteSymptom(uuid)
        sync.trigger()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                GutViewModel(
                    repo = app.container.ibsRepository,
                    consumption = app.container.consumptionRepository,
                    sync = app.container.syncCoordinator
                )
            }
        }

        private fun todayWindow(): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1).toEpochMilli()
            return start to end
        }
    }
}

sealed class TriggersUi {
    data object Loading : TriggersUi()
    data object NotConfigured : TriggersUi()
    data class Loaded(val report: TriggerReportDto, val source: TriggerSource) : TriggersUi()
    data class Error(val message: String) : TriggersUi()
}

enum class TriggerSource { Server, Local }
