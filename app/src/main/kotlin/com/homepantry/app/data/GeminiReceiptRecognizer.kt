package com.homepantry.app.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

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
