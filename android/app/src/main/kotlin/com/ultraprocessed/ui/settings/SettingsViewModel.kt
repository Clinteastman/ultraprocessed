package com.ultraprocessed.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.ScheduleType
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.data.repository.FastingRepository
import com.ultraprocessed.data.settings.ProviderType
import com.ultraprocessed.data.settings.SecretStore
import com.ultraprocessed.data.settings.Settings
import com.ultraprocessed.sync.BackendClient
import com.ultraprocessed.sync.FastingProfileDto
import com.ultraprocessed.sync.SyncCoordinator
import com.ultraprocessed.sync.SyncResult
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val DEFAULT_PROFILE = FastingProfile(
    name = "16:8",
    scheduleType = ScheduleType.SIXTEEN_EIGHT,
    eatingWindowStartMinutes = 12 * 60,
    eatingWindowEndMinutes = 20 * 60,
    restrictedDaysMask = 0,
    restrictedKcalTarget = null,
    active = false
)

class SettingsViewModel(
    private val settings: Settings,
    private val secrets: SecretStore,
    private val httpClient: HttpClient,
    private val syncCoordinator: SyncCoordinator,
    private val fastingRepo: FastingRepository,
    private val consumptionRepo: ConsumptionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val fasting = fastingRepo.getActive() ?: DEFAULT_PROFILE
            val values = SettingsValues(
                provider = settings.provider.first(),
                baseUrl = settings.providerBaseUrl.first(),
                model = settings.providerModel.first(),
                apiKey = secrets.providerApiKey.orEmpty(),
                backendUrl = settings.backendBaseUrl.first().orEmpty(),
                backendToken = secrets.backendToken.orEmpty(),
                relayThroughBackend = settings.relayThroughBackend.first(),
                fasting = fasting,
                homeLabel = settings.homeLabel.first().orEmpty(),
                homeLat = settings.homeLat.first()?.toString().orEmpty(),
                homeLng = settings.homeLng.first()?.toString().orEmpty()
            )
            _state.value = _state.value.copy(
                saved = values,
                draft = values,
                saveStatus = SaveStatus.Idle,
                pairStatus = PairStatus.Idle,
                backfillStatus = BackfillStatus.Idle
            )
        }
    }

    fun setProvider(p: ProviderType) {
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(
                provider = p,
                baseUrl = p.defaultBaseUrl,
                model = p.defaultModel
            ),
            saveStatus = SaveStatus.Idle
        )
    }

    fun updateBaseUrl(url: String) = patchDraft { it.copy(baseUrl = url) }
    fun updateModel(model: String) = patchDraft { it.copy(model = model) }
    fun updateApiKey(key: String) = patchDraft { it.copy(apiKey = key) }
    fun updateBackendUrl(url: String) = patchDraft { it.copy(backendUrl = url) }
    fun updateBackendToken(token: String) = patchDraft { it.copy(backendToken = token) }
    fun setRelayThroughBackend(value: Boolean) = patchDraft { it.copy(relayThroughBackend = value) }
    fun setFasting(profile: FastingProfile) = patchDraft { it.copy(fasting = profile) }
    fun updateHomeLabel(v: String) = patchDraft { it.copy(homeLabel = v) }
    fun updateHomeLat(v: String) = patchDraft { it.copy(homeLat = v) }
    fun updateHomeLng(v: String) = patchDraft { it.copy(homeLng = v) }

    /**
     * Tags every consumption log that has no location yet with the saved
     * Home coordinates + label, and triggers a sync so the backend gets
     * updated. Uses the *saved* Home (not the in-flight draft) so the
     * user has to Save first and is sure of what they're applying.
     */
    fun backfillUnlocatedAsHome() {
        val saved = _state.value.saved
        val lat = saved.homeLat.toDoubleOrNull()
        val lng = saved.homeLng.toDoubleOrNull()
        val label = saved.homeLabel.ifBlank { "Home" }
        if (lat == null || lng == null) {
            _state.value = _state.value.copy(
                backfillStatus = BackfillStatus.Failed("Save a Home lat + lng first.")
            )
            return
        }
        _state.value = _state.value.copy(backfillStatus = BackfillStatus.InFlight)
        viewModelScope.launch {
            val updated = consumptionRepo.backfillMissingLocation(lat, lng, label)
            syncCoordinator.trigger()
            _state.value = _state.value.copy(
                backfillStatus = BackfillStatus.Done(updated)
            )
        }
    }

    /**
     * Re-applies the saved Home coords to every existing log already tagged
     * with the Home label. Used when the saved coords were initially wrong
     * (e.g. wrong sign on longitude) and the past entries are now showing
     * up in the wrong place on the map.
     */
    fun retagHomeItems() {
        val saved = _state.value.saved
        val lat = saved.homeLat.toDoubleOrNull()
        val lng = saved.homeLng.toDoubleOrNull()
        val label = saved.homeLabel.ifBlank { "Home" }
        if (lat == null || lng == null) {
            _state.value = _state.value.copy(
                retagStatus = BackfillStatus.Failed("Save a Home lat + lng first.")
            )
            return
        }
        _state.value = _state.value.copy(retagStatus = BackfillStatus.InFlight)
        viewModelScope.launch {
            val updated = consumptionRepo.retagLocation(lat, lng, label)
            syncCoordinator.trigger()
            _state.value = _state.value.copy(
                retagStatus = BackfillStatus.Done(updated)
            )
        }
    }

    private inline fun patchDraft(transform: (SettingsValues) -> SettingsValues) {
        _state.value = _state.value.copy(
            draft = transform(_state.value.draft),
            saveStatus = SaveStatus.Idle
        )
    }

    /** Persists the current draft to Settings + SecretStore + Room (fasting). */
    fun save() {
        val draft = _state.value.draft
        _state.value = _state.value.copy(saveStatus = SaveStatus.InFlight)
        viewModelScope.launch {
            settings.setProvider(draft.provider)
            settings.setProviderBaseUrl(draft.baseUrl)
            settings.setProviderModel(draft.model)
            settings.setBackendBaseUrl(draft.backendUrl.takeIf { it.isNotBlank() })
            settings.setRelayThroughBackend(draft.relayThroughBackend)
            settings.setHome(
                label = draft.homeLabel.ifBlank { null },
                lat = draft.homeLat.toDoubleOrNull(),
                lng = draft.homeLng.toDoubleOrNull()
            )
            secrets.providerApiKey = draft.apiKey
            secrets.backendToken = draft.backendToken

            // Fasting: keep one active profile per user. Make this one the
            // (only) active row and deactivate others.
            val incoming = if (draft.fasting.active) draft.fasting else draft.fasting.copy(active = false)
            val savedId = fastingRepo.save(incoming)
            if (incoming.active) fastingRepo.setActive(savedId)

            // Push fasting to backend if configured. Best-effort.
            if (draft.backendUrl.isNotBlank() && draft.backendToken.isNotBlank()) {
                val client = BackendClient(draft.backendUrl, draft.backendToken, httpClient)
                client.putFastingProfile(
                    FastingProfileDto(
                        name = incoming.name,
                        scheduleType = incoming.scheduleType.name,
                        eatingWindowStartMinutes = incoming.eatingWindowStartMinutes,
                        eatingWindowEndMinutes = incoming.eatingWindowEndMinutes,
                        restrictedDaysMask = incoming.restrictedDaysMask,
                        restrictedKcalTarget = incoming.restrictedKcalTarget,
                        active = incoming.active
                    )
                )
            }

            _state.value = _state.value.copy(
                saved = draft,
                saveStatus = SaveStatus.Saved
            )
        }
    }

    fun syncNow() {
        _state.value = _state.value.copy(syncStatus = SyncStatus.InFlight)
        viewModelScope.launch {
            val result = syncCoordinator.syncOnce()
            _state.value = _state.value.copy(syncStatus = SyncStatus.fromResult(result))
        }
    }

    fun discard() {
        _state.value = _state.value.copy(
            draft = _state.value.saved,
            saveStatus = SaveStatus.Idle
        )
    }

    fun pair() {
        val url = _state.value.draft.backendUrl
        if (url.isBlank()) {
            _state.value = _state.value.copy(
                pairStatus = PairStatus.Failed("Set a Backend URL first.")
            )
            return
        }
        _state.value = _state.value.copy(pairStatus = PairStatus.InFlight)
        viewModelScope.launch {
            val client = BackendClient(baseUrl = url, token = "", client = httpClient)
            val result = client.pair(deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            result.fold(
                onSuccess = { tokenResp ->
                    val newDraft = _state.value.draft.copy(backendToken = tokenResp.token)
                    _state.value = _state.value.copy(
                        draft = newDraft,
                        pairStatus = PairStatus.Success("Paired as device #${tokenResp.deviceId}")
                    )
                    save()
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        pairStatus = PairStatus.Failed(err.message ?: "Pairing failed.")
                    )
                }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                val container = app.container
                SettingsViewModel(
                    settings = container.settings,
                    secrets = container.secrets,
                    httpClient = container.httpClient,
                    syncCoordinator = container.syncCoordinator,
                    fastingRepo = container.fastingRepository,
                    consumptionRepo = container.consumptionRepository
                )
            }
        }
    }
}

data class SettingsValues(
    val provider: ProviderType = ProviderType.Anthropic,
    val baseUrl: String = ProviderType.Anthropic.defaultBaseUrl,
    val model: String = ProviderType.Anthropic.defaultModel,
    val apiKey: String = "",
    val backendUrl: String = "",
    val backendToken: String = "",
    val relayThroughBackend: Boolean = false,
    val fasting: FastingProfile = DEFAULT_PROFILE,
    val homeLabel: String = "",
    val homeLat: String = "",
    val homeLng: String = ""
)

data class SettingsState(
    val saved: SettingsValues = SettingsValues(),
    val draft: SettingsValues = SettingsValues(),
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val pairStatus: PairStatus = PairStatus.Idle,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val backfillStatus: BackfillStatus = BackfillStatus.Idle,
    val retagStatus: BackfillStatus = BackfillStatus.Idle
) {
    val dirty: Boolean get() = saved != draft
}

sealed class BackfillStatus {
    data object Idle : BackfillStatus()
    data object InFlight : BackfillStatus()
    data class Done(val rowsUpdated: Int) : BackfillStatus()
    data class Failed(val message: String) : BackfillStatus()
}

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object InFlight : SaveStatus()
    data object Saved : SaveStatus()
}

sealed class PairStatus {
    data object Idle : PairStatus()
    data object InFlight : PairStatus()
    data class Success(val message: String) : PairStatus()
    data class Failed(val message: String) : PairStatus()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object InFlight : SyncStatus()
    data class Reported(val message: String, val isError: Boolean) : SyncStatus()

    companion object {
        fun fromResult(result: SyncResult): SyncStatus = when (result) {
            SyncResult.NotConfigured -> Reported("Backend URL or token not set.", true)
            SyncResult.BackendUnreachable -> Reported("Backend unreachable. Check the URL.", true)
            SyncResult.UpToDate -> Reported("Already up to date.", false)
            is SyncResult.Synced -> Reported(
                "Pushed ${result.foodsPushed} foods, ${result.logsPushed} logs; pulled ${result.foodsPulled} + ${result.logsPulled}.",
                false
            )
            is SyncResult.Failed -> Reported("Sync failed: ${result.message}", true)
        }
    }
}
