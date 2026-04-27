package com.ultraprocessed.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.analyzer.FoodAnalysis
import com.ultraprocessed.core.AppContainer
import com.ultraprocessed.data.entities.FoodSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped ViewModel that holds transient cross-screen state, like
 * the most recent scan result that's about to be shown on the result
 * screen. Avoids encoding [FoodAnalysis] as a navigation arg.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val container: AppContainer = (application as UltraprocessedApplication).container

    private val _pending = MutableStateFlow<PendingResult?>(null)
    val pending: StateFlow<PendingResult?> = _pending.asStateFlow()

    fun publish(result: PendingResult) {
        _pending.value = result
    }

    fun clearPending() {
        _pending.value = null
    }
}

data class PendingResult(
    val analysis: FoodAnalysis,
    val source: FoodSource,
    val barcode: String? = null,
    val imageBytes: ByteArray? = null,
    val imageUrl: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingResult) return false
        val bytesEqual = imageBytes?.contentEquals(other.imageBytes) ?: (other.imageBytes == null)
        return analysis == other.analysis &&
            source == other.source &&
            barcode == other.barcode &&
            bytesEqual &&
            imageUrl == other.imageUrl
    }

    override fun hashCode(): Int {
        var result = analysis.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (barcode?.hashCode() ?: 0)
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        return result
    }
}
