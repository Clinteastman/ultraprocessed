package com.ultraprocessed.camera

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One-shot ML Kit text recognition. Uses the latin script recognizer,
 * which covers all the languages we care about for ingredient labels in
 * the markets the project initially targets.
 */
class TextOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(image: InputImage): String = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }

        cont.invokeOnCancellation { /* ML Kit task has no cancellation; just let it finish. */ }
    }
}
