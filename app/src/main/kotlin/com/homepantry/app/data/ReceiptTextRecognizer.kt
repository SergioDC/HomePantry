package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * OCR on-device con ML Kit (gratis, offline, sin backend propio -- spec
 * "Flujo de captura y parseo"). Devuelve el texto detectado, una entrada
 * por línea, en el orden en que ML Kit las reporta.
 */
suspend fun recognizeReceiptTextLines(context: Context, imageUri: Uri): List<String> {
    val image = InputImage.fromFilePath(context, imageUri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val result = recognizer.process(image).await()
    return result.textBlocks.flatMap { block -> block.lines.map { line -> line.text } }
}
