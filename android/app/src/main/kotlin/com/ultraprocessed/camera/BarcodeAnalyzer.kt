package com.ultraprocessed.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.Closeable

/**
 * CameraX [ImageAnalysis.Analyzer] that streams detected barcodes off
 * each preview frame. Configured for the formats that show up on food
 * packaging worldwide.
 *
 * Emits a deduped sequence of barcode values via [barcodes]; consumers
 * can debounce/filter further if needed.
 */
@OptIn(ExperimentalGetImage::class)
class BarcodeAnalyzer : ImageAnalysis.Analyzer, Closeable {

    private val scanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    private val _barcodes = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val barcodes: SharedFlow<String> = _barcodes.asSharedFlow()

    private var lastEmitted: String? = null

    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { results ->
                results
                    .mapNotNull { it.rawValue }
                    .firstOrNull { it.length in MIN_LENGTH..MAX_LENGTH && it.matches(DIGITS) }
                    ?.let { code ->
                        if (code != lastEmitted) {
                            lastEmitted = code
                            _barcodes.tryEmit(code)
                        }
                    }
            }
            .addOnCompleteListener { image.close() }
    }

    override fun close() {
        scanner.close()
    }

    private companion object {
        const val MIN_LENGTH = 6
        const val MAX_LENGTH = 14
        val DIGITS = Regex("\\d+")
    }
}
