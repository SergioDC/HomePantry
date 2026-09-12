package com.homepantry.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Lado máximo (px) al que se reduce la imagen antes de pasarla a ML Kit.
 * Un ticket no necesita más resolución que esta para OCR fiable, y decodificar
 * a resolución nativa de cámara (48-64MP) arriesga OutOfMemoryError. Este
 * límite es independiente del usado para comprimir fotos antes de subirlas
 * (ver [ImageCompressor]), que apunta a un tamaño de almacenamiento menor.
 */
private const val MAX_OCR_SIDE = 2000

/**
 * OCR on-device con ML Kit (gratis, offline, sin backend propio -- spec
 * "Flujo de captura y parseo"). Devuelve el texto detectado, una entrada
 * por línea, en el orden en que ML Kit las reporta.
 *
 * La imagen se decodifica ya reducida (ver [MAX_OCR_SIDE]) para evitar cargar
 * un bitmap a resolución nativa de cámara en memoria.
 */
suspend fun recognizeReceiptTextLines(context: Context, imageUri: Uri): List<String> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(imageUri).use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }

    val (sourceWidth, sourceHeight) = bounds.outWidth to bounds.outHeight
    val sampleSize = if (sourceWidth > 0 && sourceHeight > 0) {
        computeInSampleSize(sourceWidth, sourceHeight, MAX_OCR_SIDE)
    } else {
        1
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = context.contentResolver.openInputStream(imageUri).use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: return emptyList()

    return try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()
        result.textBlocks.flatMap { block -> block.lines.map { line -> line.text } }
    } finally {
        bitmap.recycle()
    }
}

/**
 * `inSampleSize` (potencia de 2) mínimo que deja el lado mayor de la imagen
 * decodificada por debajo de `maxSide`, siguiendo el patrón estándar de
 * `BitmapFactory.Options.inJustDecodeBounds`.
 */
private fun computeInSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sampleSize = 1
    var largestSide = maxOf(width, height)
    while (largestSide / 2 >= maxSide) {
        sampleSize *= 2
        largestSide /= 2
    }
    return sampleSize
}
