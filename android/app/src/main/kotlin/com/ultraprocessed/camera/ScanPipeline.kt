package com.ultraprocessed.camera

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the CameraX use cases and exposes a small surface to the UI:
 *
 *  - [bind] / [unbind] tie the camera to a [LifecycleOwner] and a
 *    [Preview.SurfaceProvider] (the Compose surface).
 *  - [barcodes] streams live barcode hits from the analyzer pipeline.
 *  - [capture] takes a still frame and runs OCR against it; the call site
 *    decides whether to route to [com.ultraprocessed.openfoodfacts.OpenFoodFactsClient],
 *    [com.ultraprocessed.analyzer.FoodAnalyzer.analyzeText], or
 *    [com.ultraprocessed.analyzer.FoodAnalyzer.analyzeImage].
 *
 * Designed to be hosted in a ViewModel; one instance per session.
 */
class ScanPipeline(private val context: Context) {

    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val barcodeAnalyzer = BarcodeAnalyzer()
    private val ocr = TextOcr()

    private val previewUseCase = Preview.Builder().build()
    private val captureUseCase = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
    private val analysisUseCase = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { it.setAnalyzer(analyzerExecutor, barcodeAnalyzer) }

    /** Live barcode events (deduped). */
    val barcodes: SharedFlow<String> = barcodeAnalyzer.barcodes

    /**
     * Wires the camera preview to a Compose [Preview.SurfaceProvider].
     */
    fun setSurfaceProvider(provider: Preview.SurfaceProvider) {
        previewUseCase.surfaceProvider = provider
    }

    suspend fun bind(lifecycleOwner: LifecycleOwner) {
        val provider = awaitCameraProvider()
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase,
            captureUseCase,
            analysisUseCase
        )
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    /**
     * Captures one frame, encodes it as JPEG, and runs OCR against it.
     */
    @OptIn(ExperimentalGetImage::class)
    suspend fun capture(): ScanCapture = withContext(Dispatchers.IO) {
        val proxy: ImageProxy = takeOnce()
        try {
            val mediaImage = proxy.image
                ?: throw IllegalStateException("no media image on capture")
            val rotation = proxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            val text = runCatching { ocr.recognize(inputImage) }.getOrDefault("")
            val jpegBytes = proxy.toJpeg()
            ScanCapture(jpegBytes, text)
        } finally {
            proxy.close()
        }
    }

    private suspend fun takeOnce(): ImageProxy = suspendCancellableCoroutine { cont ->
        captureUseCase.takePicture(
            analyzerExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    cont.resume(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

    fun shutdown() {
        analysisUseCase.clearAnalyzer()
        barcodeAnalyzer.close()
        analyzerExecutor.shutdown()
    }
}

data class ScanCapture(
    val jpegBytes: ByteArray,
    val recognizedText: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanCapture) return false
        if (!jpegBytes.contentEquals(other.jpegBytes)) return false
        if (recognizedText != other.recognizedText) return false
        return true
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + recognizedText.hashCode()
        return result
    }
}
