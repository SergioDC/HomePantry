package com.homepantry.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.Response

sealed class GeminiReceiptException(message: String, cause: Throwable? = null) : Exception(message, cause)
class GeminiAuthException : GeminiReceiptException("La clave de Gemini no es válida o no tiene permisos.")
class GeminiQuotaException : GeminiReceiptException("Se ha agotado la cuota gratuita de Gemini por hoy.")
class GeminiModelUnavailableException : GeminiReceiptException(
    "El modelo de Gemini configurado en la app ya no está disponible. Hace falta actualizar la app."
)
class GeminiNetworkException(cause: Throwable) : GeminiReceiptException("Sin conexión con Gemini: ${cause.message}", cause)
class GeminiResponseException(reason: String) : GeminiReceiptException("Respuesta inesperada de Gemini: $reason")

/** Fallo temporal del lado de Google (típicamente 503 por saturación del modelo): reintentar puede bastar. */
class GeminiServerException(code: Int, detail: String?) : GeminiReceiptException(
    "Gemini no está disponible ahora mismo (HTTP $code${detail?.let { ": $it" }.orEmpty()})"
)

private val geminiGson = Gson()

private data class GeminiProduct(val name: String?, val price: Double?, val quantity: Double?, val unit: String?)
private data class GeminiProductsPayload(val store: String?, val products: List<GeminiProduct>?)

/**
 * La API de `generativelanguage` devuelve 400 con `API_KEY_INVALID` (no 401/403)
 * cuando la key está mal formada, así que 400 también se trata como error de
 * autenticación para que el usuario vea un mensaje útil.
 */
internal fun classifyHttpErrorCode(code: Int): GeminiReceiptException? = when (code) {
    400, 401, 403 -> GeminiAuthException()
    404 -> GeminiModelUnavailableException()
    429 -> GeminiQuotaException()
    else -> null
}

/** Errores de servidor que suelen desaparecer al reintentar (el 503 "model is overloaded" es el habitual). */
internal fun isTransientHttpCode(code: Int): Boolean = code == 500 || code == 502 || code == 503 || code == 504

/** Google devuelve los errores como `{"error":{"code":..,"message":"..","status":".."}}`. */
internal fun extractGoogleErrorMessage(errorBody: String?): String? {
    if (errorBody.isNullOrBlank()) return null
    return try {
        JsonParser.parseString(errorBody).asJsonObject
            .getAsJsonObject("error")
            ?.get("message")
            ?.asString
            ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

/**
 * Convierte un HTTP no exitoso que [classifyHttpErrorCode] no reconoce en una
 * excepción que incluye el código y el mensaje real de Google; un simple
 * "HTTP 503" no dice si el modelo está saturado o la petición es errónea.
 */
internal fun httpFailure(code: Int, errorBody: String?): GeminiReceiptException {
    val detail = extractGoogleErrorMessage(errorBody)
    return if (isTransientHttpCode(code)) {
        GeminiServerException(code, detail)
    } else {
        GeminiResponseException("HTTP $code${detail?.let { ": $it" }.orEmpty()}")
    }
}

private const val GEMINI_MAX_ATTEMPTS = 3
private const val GEMINI_RETRY_INITIAL_DELAY_MS = 2_000L

/**
 * Reintenta [block] cuando falla con [GeminiServerException], esperando el
 * doble entre cada intento; cualquier otro error (clave, cuota, modelo, red)
 * se propaga en el acto porque repetir la llamada no lo arregla.
 */
internal suspend fun <T> retryOnTransientGeminiError(
    maxAttempts: Int = GEMINI_MAX_ATTEMPTS,
    initialDelayMs: Long = GEMINI_RETRY_INITIAL_DELAY_MS,
    sleep: suspend (Long) -> kotlin.Unit = { delay(it) },
    block: suspend () -> T
): T {
    var wait = initialDelayMs
    repeat(maxAttempts - 1) {
        try {
            return block()
        } catch (e: GeminiServerException) {
            sleep(wait)
            wait *= 2
        }
    }
    return block()
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

internal fun mapGeminiOutputText(outputText: String): ParsedReceipt {
    val payload = try {
        geminiGson.fromJson(outputText, GeminiProductsPayload::class.java)
    } catch (e: JsonSyntaxException) {
        throw GeminiResponseException("el JSON de productos no es válido")
    }
    val products = payload?.products ?: throw GeminiResponseException("falta el campo 'products'")
    val lines = products.mapNotNull { product ->
        val name = product.name?.trim()
        val price = product.price
        if (name.isNullOrEmpty() || price == null || price <= 0) {
            null
        } else {
            ParsedReceiptLine(
                name = name,
                price = price,
                quantity = product.quantity?.takeIf { it > 0 } ?: 1.0,
                unit = product.unit?.trim()?.uppercase()?.takeIf { it in RECEIPT_UNITS } ?: Unit.UD.name
            )
        }
    }
    return ParsedReceipt(store = payload.store?.trim()?.takeIf { it.isNotEmpty() }, lines = lines)
}

/** Modelo usado si el usuario nunca ha elegido uno explícitamente en Ajustes. */
internal const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"

/**
 * Modelos con soporte de visión ofrecidos en el selector de Ajustes (lista
 * seleccionada de https://ai.google.dev/gemini-api/docs/models a fecha de la
 * spec). El campo admite texto libre para no bloquear al usuario si Google
 * publica un modelo nuevo o descataloga uno de estos.
 */
val GEMINI_MODEL_OPTIONS: List<String> = listOf(
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
    "gemini-2.5-pro"
)

private val RECEIPT_PROMPT = """
    Eres un asistente que extrae la lista de la compra de la foto de un
    ticket de supermercado español. Devuelve TODOS los productos comprados
    con estos campos:
    - name: el nombre del producto tal y como aparece impreso.
    - price: el importe final de esa línea (el total impreso, ya con los
      descuentos de esa línea aplicados; NO el precio por unidad).
    - quantity y unit: cuánto se compró cuando el ticket lo indica
      ("2 x 1,25" es quantity 2 y unit UD; "0,850 kg x 3,99 €/kg" es
      quantity 0.85 y unit KG; los líquidos por litros, unit L). Si el
      ticket no lo indica, quantity 1 y unit UD.
    Devuelve además store: el nombre del supermercado o cadena que aparece en
    la cabecera del ticket (por ejemplo "Mercadona" o "Lidl"), sin dirección
    ni CIF; omítelo si no se lee con claridad.
    Ignora totales, subtotales, líneas de IVA, cambio, tarjeta/efectivo,
    promociones sueltas y cualquier línea que no sea un producto comprado.
""".trimIndent()

/**
 * Envuelve cualquier llamada Retrofit a Gemini con el mismo mapeo de errores
 * (red -> [GeminiNetworkException], cancelación cooperativa intacta, cualquier
 * otro fallo -> [GeminiResponseException]), para no repetirlo en cada endpoint.
 */
private suspend fun <T> callGeminiApi(call: suspend () -> Response<T>): Response<T> = try {
    call()
} catch (e: IOException) {
    throw GeminiNetworkException(e)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw GeminiResponseException("respuesta ilegible: ${e.message}")
}

private fun ensureSuccessful(response: Response<*>) {
    classifyHttpErrorCode(response.code())?.let { throw it }
    if (!response.isSuccessful) {
        val errorBody = try {
            response.errorBody()?.string()
        } catch (e: IOException) {
            null
        }
        throw httpFailure(response.code(), errorBody)
    }
}

/** [callGeminiApi] + comprobación del código HTTP, reintentando los fallos temporales de Google. */
private suspend fun <T> callGeminiChecked(call: suspend () -> Response<T>): Response<T> =
    retryOnTransientGeminiError {
        callGeminiApi(call).also { ensureSuccessful(it) }
    }

private suspend fun callGemini(apiKey: String, request: GeminiInteractionRequest): GeminiInteractionResponse {
    val response = callGeminiChecked { GeminiReceiptClient.api().createInteraction(apiKey, request) }
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

/** Manda una foto de ticket a Gemini y devuelve el ticket ya estructurado (supermercado y líneas; sustituye a ReceiptTextRecognizer + parseReceiptLines para este escaneo). */
suspend fun recognizeReceiptWithGemini(context: Context, imageUri: Uri, apiKey: String, model: String): ParsedReceipt {
    val imageBytes = try {
        loadReceiptImageBytes(context, imageUri)
    } catch (e: GeminiReceiptException) {
        throw e
    } catch (e: Exception) {
        throw GeminiResponseException("no se pudo preparar la foto del ticket: ${e.message}")
    }
    val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

    val request = GeminiInteractionRequest(
        model = model,
        input = listOf(
            GeminiInputPart(type = "text", text = RECEIPT_PROMPT),
            GeminiInputPart(type = "image", data = base64Image, mimeType = "image/jpeg")
        ),
        responseFormat = GeminiResponseFormat(schema = PRODUCTS_JSON_SCHEMA)
    )

    val body = callGemini(apiKey, request)
    val outputText = extractOutputText(body)
    return mapGeminiOutputText(outputText)
}

/**
 * Confirma que una API key es válida y el modelo elegido existe, antes de
 * guardarlos -- usa la consulta de metadatos (sin generar contenido) en vez de
 * una interacción completa, que en modelos con razonamiento puede tardar
 * mucho más que el timeout razonable para un simple guardado. Lanza
 * GeminiReceiptException si la key o el modelo no son válidos.
 */
suspend fun validateGeminiApiKey(apiKey: String, model: String) {
    callGeminiChecked { GeminiReceiptClient.api().getModel(model, apiKey) }
}
