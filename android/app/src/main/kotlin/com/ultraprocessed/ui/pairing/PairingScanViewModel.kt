package com.ultraprocessed.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.camera.ScanPipeline
import com.ultraprocessed.data.settings.SecretStore
import com.ultraprocessed.data.settings.Settings
import com.ultraprocessed.sync.parsePairPayload
import com.ultraprocessed.sync.SyncCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PairingScanViewModel(
    val pipeline: ScanPipeline,
    private val settings: Settings,
    private val secrets: SecretStore,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow<PairingScanState>(PairingScanState.Scanning)
    val state: StateFlow<PairingScanState> = _state.asStateFlow()

    private var consumed = false
    private var inFlight: Job? = null

    fun onQrCode(text: String) {
        if (consumed) return
        val payload = parsePairPayload(text) ?: return  // ignore non-pairing QR
        consumed = true
        inFlight?.cancel()
        inFlight = viewModelScope.launch {
            settings.setBackendBaseUrl(payload.url)
            secrets.backendToken = payload.token
            // Kick off a sync now so anything queued goes up immediately.
            syncCoordinator.trigger()
            _state.value = PairingScanState.Done(payload.url)
        }
    }

    override fun onCleared() {
        inFlight?.cancel()
        pipeline.shutdown()
        super.onCleared()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                val container = app.container
                PairingScanViewModel(
                    pipeline = ScanPipeline(app.applicationContext),
                    settings = container.settings,
                    secrets = container.secrets,
                    syncCoordinator = container.syncCoordinator
                )
            }
        }
    }
}

sealed class PairingScanState {
    data object Scanning : PairingScanState()
    data class Done(val backendUrl: String) : PairingScanState()
}
