package com.homepantry.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
 * un bitmap a resolución nativa de cámara en memoria. Como `BitmapFactory`
 * decodifica los píxeles "en crudo" (no aplica la orientación EXIF, al
 * contrario que `InputImage.fromFilePath`), leemos el tag EXIF por separado
 * y se lo pasamos a ML Kit como `rotationDegrees`.
 */
suspend fun recognizeReceiptTextLines(context: Context, imageUri: Uri): List<String> {
    val rotationDegrees = readExifRotationDegrees(context, imageUri)

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
    } ?: error("No se pudo decodificar la imagen del ticket: $imageUri")

    return try {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
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

/**
 * Lee el tag EXIF de orientación de la imagen original (independiente del
 * `inSampleSize` usado al decodificar el bitmap, ya que EXIF vive en la
 * cabecera del fichero) y lo traduce a grados de rotación en sentido horario,
 * el formato que espera `InputImage.fromBitmap`. Las cámaras de móvil
 * habitualmente guardan fotos en modo retrato como un buffer de sensor en
 * modo paisaje más este tag, en vez de rotar los píxeles.
 */
private fun readExifRotationDegrees(context: Context, imageUri: Uri): Int {
    val orientation = context.contentResolver.openInputStream(imageUri).use { stream ->
        stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } ?: ExifInterface.ORIENTATION_NORMAL

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
