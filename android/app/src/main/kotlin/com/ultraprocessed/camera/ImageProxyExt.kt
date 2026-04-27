package com.ultraprocessed.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Encodes an [ImageProxy] frame as a rotated JPEG byte array for
 * sending to LLM vision endpoints. Keeps quality at 85 to stay under
 * provider request limits while preserving readable label text.
 */
internal fun ImageProxy.toJpeg(quality: Int = 85): ByteArray {
    val rotation = imageInfo.rotationDegrees
    val bitmap = when (format) {
        ImageFormat.JPEG -> {
            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        ImageFormat.YUV_420_888 -> yuvToJpegBitmap(quality)
        else -> error("unsupported ImageProxy format $format")
    }

    val rotated = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } else bitmap

    return ByteArrayOutputStream().use {
        rotated.compress(Bitmap.CompressFormat.JPEG, quality, it)
        it.toByteArray()
    }.also { rotated.recycle() }
}

private fun ImageProxy.yuvToJpegBitmap(quality: Int): Bitmap {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val ySize = yPlane.buffer.remaining()
    val uSize = uPlane.buffer.remaining()
    val vSize = vPlane.buffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)

    yPlane.buffer.get(nv21, 0, ySize)
    vPlane.buffer.get(nv21, ySize, vSize)
    uPlane.buffer.get(nv21, ySize + vSize, uSize)

    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
