package com.ultraprocessed.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.analyzer.AnalyzerError
import com.ultraprocessed.analyzer.AnalyzerFactory
import com.ultraprocessed.analyzer.FoodAnalysis
import com.ultraprocessed.camera.IngredientDetection
import com.ultraprocessed.camera.ScanPipeline
import com.ultraprocessed.data.entities.FoodSource
import com.ultraprocessed.openfoodfacts.OpenFoodFactsClient
import com.ultraprocessed.openfoodfacts.OpenFoodFactsResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the scan-pipeline lifecycle and the state machine driving the
 * scan UI. Outcomes (success / error / abandoned) are pushed to
 * [scanState]; the screen observes and routes to the result screen via
 * the activity-scoped [com.ultraprocessed.ui.MainViewModel].
 */
class ScanViewModel(
    val pipeline: ScanPipeline,
    private val analyzerFactory: AnalyzerFactory,
    private val off: OpenFoodFactsClient
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var inFlight: Job? = null

    fun onBarcode(barcode: String) {
        if (_scanState.value !is ScanState.Idle) return
        inFlight?.cancel()
        inFlight = viewModelScope.launch {
            _scanState.value = ScanState.Looking("Found $barcode, looking up...")
            val result = runCatching { off.lookup(barcode) }.getOrElse {
                _scanState.value = ScanState.Error(it.message ?: "Open Food Facts lookup failed")
                return@launch
            }
            when (result) {
                is OpenFoodFactsResult.Found -> _scanState.value = ScanState.Done(
                    analysis = result.analysis,
                    source = FoodSource.BARCODE,
                    barcode = result.barcode,
                    imageBytes = null,
                    imageUrl = result.imageUrl
                )
                OpenFoodFactsResult.NotFound -> _scanState.value =
                    ScanState.Error("Barcode not in Open Food Facts. Try the shutter for OCR or photo classification.")
                is OpenFoodFactsResult.NetworkError -> _scanState.value =
                    ScanState.Error(result.message)
            }
        }
    }

    fun onShutter() {
        if (_scanState.value !is ScanState.Idle) return
        inFlight?.cancel()
        inFlight = viewModelScope.launch {
            _scanState.value = ScanState.Looking("Capturing...")
            val capture = runCatching { pipeline.capture() }.getOrElse {
                _scanState.value = ScanState.Error("Capture failed: ${it.message}")
                return@launch
            }
            val analyzer = runCatching { analyzerFactory.current() }.getOrElse {
                _scanState.value = ScanState.Error("Analyzer not configured: ${it.message}")
                return@launch
            }

            val ingredients = capture.recognizedText
            val routedToText = ingredients.length >= 24 &&
                IngredientDetection.looksLikeIngredients(ingredients)

            _scanState.value = ScanState.Looking(
                if (routedToText) "Reading ingredients..." else "Analysing photo..."
            )

            val analysisResult = if (routedToText) analyzer.analyzeText(ingredients)
            else analyzer.analyzeImage(capture.jpegBytes)

            analysisResult.fold(
                onSuccess = { analysis ->
                    _scanState.value = ScanState.Done(
                        analysis = analysis,
                        source = if (routedToText) FoodSource.OCR else FoodSource.LLM,
                        barcode = null,
                        imageBytes = capture.jpegBytes,
                        imageUrl = null
                    )
                },
                onFailure = { err ->
                    _scanState.value = ScanState.Error(formatAnalyzerError(err))
                }
            )
        }
    }

    fun reset() {
        inFlight?.cancel()
        _scanState.value = ScanState.Idle
    }

    override fun onCleared() {
        inFlight?.cancel()
        pipeline.shutdown()
        super.onCleared()
    }

    private fun formatAnalyzerError(t: Throwable): String = when (t) {
        is AnalyzerError.MissingApiKey -> "Add an API key in Settings to enable analysis."
        is AnalyzerError.HttpError -> "Provider error ${t.status}: ${t.message?.take(160)}"
        is AnalyzerError.MalformedResponse -> "Couldn't parse provider response."
        is AnalyzerError.Network -> "Network error. Try again."
        else -> t.message ?: "Analysis failed."
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                val container = app.container
                ScanViewModel(
                    pipeline = ScanPipeline(app.applicationContext),
                    analyzerFactory = container.analyzerFactory,
                    off = container.openFoodFactsClient
                )
            }
        }
    }
}

sealed class ScanState {
    data object Idle : ScanState()
    data class Looking(val message: String) : ScanState()
    data class Done(
        val analysis: FoodAnalysis,
        val source: FoodSource,
        val barcode: String?,
        val imageBytes: ByteArray?,
        val imageUrl: String?
    ) : ScanState()
    data class Error(val message: String) : ScanState()
}
