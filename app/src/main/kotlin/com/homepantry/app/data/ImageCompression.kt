package com.homepantry.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

private const val MAX_PHOTO_SIDE = 1024
private const val JPEG_QUALITY = 80

/**
 * Calcula el tamaño destino manteniendo el ratio de aspecto, sin superar
 * `maxSide` en el lado mayor (cierre de huecos §6: compresión ligera de fotos).
 */
fun computeResizedDimensions(width: Int, height: Int, maxSide: Int = MAX_PHOTO_SIDE): Pair<Int, Int> {
    require(width > 0 && height > 0) { "width and height must be positive" }
    val largestSide = maxOf(width, height)
    if (largestSide <= maxSide) return width to height
    val scale = maxSide.toDouble() / largestSide
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)
    return newWidth to newHeight
}

/**
 * Redimensiona y recomprime una foto local antes de subirla a Storage.
 * Sin test unitario (depende de android.graphics.Bitmap, sin Robolectric en
 * este proyecto) — se verifica manualmente añadiendo una foto en la app.
 */
object ImageCompressor {
    fun compress(context: Context, sourceUri: Uri): Uri {
        val original = context.contentResolver.openInputStream(sourceUri).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return sourceUri

        val (targetWidth, targetHeight) = computeResizedDimensions(original.width, original.height)
        val resized = if (targetWidth == original.width && targetHeight == original.height) {
            original
        } else {
            Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        }

        val outFile = File.createTempFile("compressed_", ".jpg", context.cacheDir)
        FileOutputStream(outFile).use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return Uri.fromFile(outFile)
    }
}
