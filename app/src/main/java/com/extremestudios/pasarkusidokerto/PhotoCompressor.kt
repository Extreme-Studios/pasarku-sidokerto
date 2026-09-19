package com.extremestudios.pasarkusidokerto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ProcessedPhoto(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val base64: String,
    val mimeType: String = "image/jpeg"
)

object PhotoCompressor {
    private const val MAX_BYTES = 700 * 1024
    private const val START_QUALITY = 88
    private const val MIN_QUALITY = 58
    private const val MAX_DIMENSION = 1280

    fun process(context: Context, uri: Uri): ProcessedPhoto {
        val decoded = decodeBitmap(context, uri)
        val resized = resizeIfNeeded(decoded, MAX_DIMENSION)
        val jpegBytes = compressToTarget(resized)
        val previewBitmap = decodePreview(jpegBytes)
        return ProcessedPhoto(
            bitmap = previewBitmap,
            jpegBytes = jpegBytes,
            base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        )
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private fun resizeIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val longestSide = max(source.width, source.height)
        if (longestSide <= maxDimension) return source

        val scale = maxDimension.toFloat() / longestSide.toFloat()
        val targetWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun compressToTarget(source: Bitmap): ByteArray {
        var working = source
        var quality = START_QUALITY

        while (true) {
            val bytes = compress(working, quality)
            if (bytes.size <= MAX_BYTES) return bytes

            if (quality > MIN_QUALITY) {
                quality -= 8
            } else {
                val ratio = sqrt(MAX_BYTES.toDouble() / bytes.size.toDouble()).coerceAtMost(0.9)
                val nextWidth = (working.width * ratio).roundToInt().coerceAtLeast(1)
                val nextHeight = (working.height * ratio).roundToInt().coerceAtLeast(1)
                if (nextWidth == working.width || nextHeight == working.height) return bytes
                working = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true)
                quality = START_QUALITY
            }
        }
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }

    private fun decodePreview(bytes: ByteArray): Bitmap {
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
