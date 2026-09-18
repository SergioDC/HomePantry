package com.homepantry.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException

sealed class GeminiReceiptException(message: String, cause: Throwable? = null) : Exception(message, cause)
class GeminiAuthException : GeminiReceiptException("La clave de Gemini no es válida o no tiene permisos.")
class GeminiQuotaException : GeminiReceiptException("Se ha agotado la cuota gratuita de Gemini por hoy.")
class GeminiNetworkException(cause: Throwable) : GeminiReceiptException("Sin conexión con Gemini: ${cause.message}", cause)
class GeminiResponseException(reason: String) : GeminiReceiptException("Respuesta inesperada de Gemini: $reason")

private val geminiGson = Gson()

private data class GeminiProduct(val name: String?, val price: Double?)
private data class GeminiProductsPayload(val products: List<GeminiProduct>?)

/**
 * La API de `generativelanguage` devuelve 400 con `API_KEY_INVALID` (no 401/403)
 * cuando la key está mal formada, así que 400 también se trata como error de
 * autenticación para que el usuario vea un mensaje útil.
 */
internal fun classifyHttpErrorCode(code: Int): GeminiReceiptException? = when (code) {
    400, 401, 403 -> GeminiAuthException()
    429 -> GeminiQuotaException()
    else -> null
}

internal fun extractOutputText(response: GeminiInteractionResponse): String {
    val texts = response.steps
        ?.firstOrNull { it.type == "model_output" }
        ?.content
        ?.filter { it.type == "text" }
        ?.mapNotNull { it.text }
        ?: emptyList()
    if (texts.isEmpty()) throw GeminiResponseException("no se encontró texto de salida en la respuesta")
    return texts.joinToString("")
}

internal fun mapGeminiOutputTextToLines(outputText: String): List<ParsedReceiptLine> {
    val payload = try {
        geminiGson.fromJson(outputText, GeminiProductsPayload::class.java)
    } catch (e: JsonSyntaxException) {
        throw GeminiResponseException("el JSON de productos no es válido")
    }
    val products = payload?.products ?: throw GeminiResponseException("falta el campo 'products'")
    return products.mapNotNull { product ->
        val name = product.name?.trim()
        val price = product.price
        if (name.isNullOrEmpty() || price == null || price <= 0) null else ParsedReceiptLine(name, price)
    }
}

private const val GEMINI_MODEL = "gemini-3.8-flash"

private val RECEIPT_PROMPT = """
    Eres un asistente que extrae la lista de la compra de la foto de un
    ticket de supermercado español. Devuelve TODOS los productos comprados
    con el precio final de esa línea (el que aparece impreso, ya con
    descuentos de esa línea aplicados si los hay). Ignora totales,
    subtotales, líneas de IVA, cambio, tarjeta/efectivo, datos de la
    tienda, promociones sueltas y cualquier línea que no sea un producto
    comprado.
""".trimIndent()

private suspend fun callGemini(apiKey: String, request: GeminiInteractionRequest): GeminiInteractionResponse {
    val response = try {
        GeminiReceiptClient.api().createInteraction(apiKey, request)
    } catch (e: IOException) {
        throw GeminiNetworkException(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw GeminiResponseException("respuesta ilegible: ${e.message}")
    }
    classifyHttpErrorCode(response.code())?.let { throw it }
    if (!response.isSuccessful) {
        throw GeminiResponseException("HTTP ${response.code()}")
    }
    return response.body() ?: throw GeminiResponseException("cuerpo de respuesta vacío")
}

/**
 * Prepara la foto del ticket para Gemini con la misma calidad que el OCR
 * clásico ([MAX_OCR_SIDE] + orientación EXIF), no con la compresión pensada
 * para subir fotos a almacenamiento ([ImageCompressor], 1024px y sin EXIF):
 * esta función existe justamente para evitar los emparejamientos
 * nombre↔precio erróneos que motivaron la spec, y una imagen pequeña o girada
 * los reproduciría. A diferencia de `InputImage.fromBitmap`, un JPEG enviado a
 * la API no admite un "hint" de rotación aparte, así que los píxeles se rotan
 * físicamente antes de codificar.
 */
private fun loadReceiptImageBytes(context: Context, imageUri: Uri): ByteArray {
    val rotationDegrees = readExifRotationDegrees(context, imageUri)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(imageUri).use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val sampleSize = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_OCR_SIDE)
    } else {
        1
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = context.contentResolver.openInputStream(imageUri).use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: throw GeminiResponseException("no se pudo decodificar la foto del ticket")

    val rotated = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } else {
        bitmap
    }

    val output = ByteArrayOutputStream()
    try {
        rotated.compress(Bitmap.CompressFormat.JPEG, 85, output)
    } finally {
        rotated.recycle()
    }
    return output.toByteArray()
}

/** Manda una foto de ticket a Gemini y devuelve las líneas ya estructuradas (sustituye a ReceiptTextRecognizer + parseReceiptLines para este escaneo). */
suspend fun recognizeReceiptWithGemini(context: Context, imageUri: Uri, apiKey: String): List<ParsedReceiptLine> {
    val imageBytes = try {
        loadReceiptImageBytes(context, imageUri)
    } catch (e: GeminiReceiptException) {
        throw e
    } catch (e: Exception) {
        throw GeminiResponseException("no se pudo preparar la foto del ticket: ${e.message}")
    }
    val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

    val request = GeminiInteractionRequest(
        model = GEMINI_MODEL,
        input = listOf(
            GeminiInputPart(type = "text", text = RECEIPT_PROMPT),
            GeminiInputPart(type = "image", data = base64Image, mimeType = "image/jpeg")
        ),
        responseFormat = GeminiResponseFormat(schema = PRODUCTS_JSON_SCHEMA)
    )

    val body = callGemini(apiKey, request)
    val outputText = extractOutputText(body)
    return mapGeminiOutputTextToLines(outputText)
}

/** Llamada mínima para confirmar que una API key es válida antes de guardarla. Lanza GeminiReceiptException si no lo es. */
suspend fun validateGeminiApiKey(apiKey: String) {
    val request = GeminiInteractionRequest(model = GEMINI_MODEL, input = listOf(GeminiInputPart(type = "text", text = "OK")))
    callGemini(apiKey, request)
}
