package com.ultraprocessed.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    /** Live QR-code text events (deduped). Used by the pairing-scan screen. */
    val qrCodes: SharedFlow<String> = barcodeAnalyzer.qrCodes

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
     * Captures one frame, runs OCR against the bitmap, and returns a
     * rotated JPEG byte array suitable for vision LLMs.
     *
     * ImageCapture defaults to JPEG output, so we decode the buffer to a
     * Bitmap (ML Kit's fromMediaImage only accepts YUV) and use
     * fromBitmap() with the rotation degrees instead.
     */
    suspend fun capture(): ScanCapture = withContext(Dispatchers.IO) {
        val proxy: ImageProxy = takeOnce()
        try {
            val rotation = proxy.imageInfo.rotationDegrees

            val rawJpeg = ByteArray(proxy.planes[0].buffer.remaining()).also {
                proxy.planes[0].buffer.get(it)
            }

            val bitmap = BitmapFactory.decodeByteArray(rawJpeg, 0, rawJpeg.size)
                ?: throw IllegalStateException("could not decode captured JPEG")

            val inputImage = InputImage.fromBitmap(bitmap, rotation)
            val text = runCatching { ocr.recognize(inputImage) }.getOrDefault("")

            val rotatedJpeg = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                val bytes = rotated.encodeToJpeg()
                if (rotated !== bitmap) rotated.recycle()
                bytes
            } else {
                rawJpeg
            }

            bitmap.recycle()
            ScanCapture(rotatedJpeg, text)
        } finally {
            proxy.close()
        }
    }

    private fun Bitmap.encodeToJpeg(quality: Int = 85): ByteArray =
        ByteArrayOutputStream().use {
            compress(Bitmap.CompressFormat.JPEG, quality, it)
            it.toByteArray()
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
