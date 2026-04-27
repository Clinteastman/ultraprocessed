package com.ultraprocessed.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.data.dao.ConsumptionWithFood
import kotlinx.coroutines.flow.Flow

class HistoryViewModel(
    private val container: com.ultraprocessed.core.AppContainer
) : ViewModel() {

    val recent: Flow<List<ConsumptionWithFood>> =
        container.consumptionRepository.observeRecent(limit = 200)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                HistoryViewModel(app.container)
            }
        }
    }
}
