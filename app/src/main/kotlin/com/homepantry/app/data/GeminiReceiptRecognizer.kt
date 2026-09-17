package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException

sealed class GeminiReceiptException(message: String) : Exception(message)
class GeminiAuthException : GeminiReceiptException("La clave de Gemini no es válida o no tiene permisos.")
class GeminiQuotaException : GeminiReceiptException("Se ha agotado la cuota gratuita de Gemini por hoy.")
class GeminiNetworkException(cause: Throwable) : GeminiReceiptException("Sin conexión con Gemini: ${cause.message}")
class GeminiResponseException(reason: String) : GeminiReceiptException("Respuesta inesperada de Gemini: $reason")

private val geminiGson = Gson()

private data class GeminiProduct(val name: String?, val price: Double?)
private data class GeminiProductsPayload(val products: List<GeminiProduct>?)

internal fun classifyHttpErrorCode(code: Int): GeminiReceiptException? = when (code) {
    401, 403 -> GeminiAuthException()
    429 -> GeminiQuotaException()
    else -> null
}

internal fun extractOutputText(response: GeminiInteractionResponse): String {
    return response.steps
        ?.firstOrNull { it.type == "model_output" }
        ?.content
        ?.firstOrNull { it.type == "text" }
        ?.text
        ?: throw GeminiResponseException("no se encontró texto de salida en la respuesta")
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
        if (name.isNullOrEmpty() || price == null) null else ParsedReceiptLine(name, price)
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
    } catch (e: Exception) {
        throw GeminiResponseException("respuesta ilegible: ${e.message}")
    }
    classifyHttpErrorCode(response.code())?.let { throw it }
    if (!response.isSuccessful) {
        throw GeminiResponseException("HTTP ${response.code()}")
    }
    return response.body() ?: throw GeminiResponseException("cuerpo de respuesta vacío")
}

/** Manda una foto de ticket a Gemini y devuelve las líneas ya estructuradas (sustituye a ReceiptTextRecognizer + parseReceiptLines para este escaneo). */
suspend fun recognizeReceiptWithGemini(context: Context, imageUri: Uri, apiKey: String): List<ParsedReceiptLine> {
    val compressedUri = ImageCompressor.compress(context, imageUri)
    val imageBytes = try {
        context.contentResolver.openInputStream(compressedUri)?.use { it.readBytes() }
            ?: throw GeminiResponseException("no se pudo leer la foto comprimida del ticket")
    } catch (e: IOException) {
        throw GeminiResponseException("no se pudo leer la foto comprimida del ticket: ${e.message}")
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
