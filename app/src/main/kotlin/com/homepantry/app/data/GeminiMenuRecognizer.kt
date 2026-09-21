package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.time.YearMonth

/** SPEC 2026-09-21: importar el menú mensual desde una foto con Gemini (misma API que el ticket). */
private val menuGson = Gson()

private data class GeminiMenuDayPayload(val day: Int?, val dishes: List<String?>?)
private data class GeminiMenuPayload(val month: Int?, val year: Int?, val days: List<GeminiMenuDayPayload>?)

/** Esquema JSON que se le pide a Gemini como salida estructurada del menú. */
val MENU_JSON_SCHEMA: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "month" to mapOf("type" to "integer"),
        "year" to mapOf("type" to "integer"),
        "days" to mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "day" to mapOf("type" to "integer"),
                    "dishes" to mapOf("type" to "array", "items" to mapOf("type" to "string"))
                ),
                "required" to listOf("day", "dishes")
            )
        )
    ),
    "required" to listOf("days")
)

private val MENU_PROMPT = """
    Eres un asistente que extrae el menú de comidas de la foto de un menú
    mensual español (tipo comedor): una cuadrícula de lunes a viernes con el
    número del día dentro de cada recuadro y, debajo, los platos de ese día.
    Devuelve, para cada recuadro con platos:
    - day: el número de día que aparece marcado dentro del recuadro (1 a 31).
      Asigna los platos por ese número, NO por la posición en la cuadrícula.
    - dishes: los platos de ese día en el orden en que están impresos
      (normalmente primero, segundo, verdura y postre), cada uno como un texto
      corto tal y como se lee. NO incluyas el pan.
    Devuelve además month y year (números, por ejemplo 9 y 2026) solo si el
    mes y el año se leen con claridad en la cabecera; omítelos si no.
    Ignora los textos que no son platos ("Festivo", "No lectivo", leyendas,
    alérgenos, teléfonos) y omite los recuadros sin platos.
""".trimIndent()

private const val MIN_PLAUSIBLE_YEAR = 2000
private const val MAX_PLAUSIBLE_YEAR = 2100

/**
 * Convierte el JSON de salida de Gemini en un [ParsedMenu] ya limpio. El mes solo se acepta si
 * llegan mes (1..12) y un año plausible; si no, se ignora y decide el usuario en la revisión.
 */
internal fun mapGeminiMenuText(outputText: String): ParsedMenu {
    val payload = try {
        menuGson.fromJson(outputText, GeminiMenuPayload::class.java)
    } catch (e: JsonParseException) {
        throw GeminiResponseException("el JSON del menú no es válido")
    }
    val rawDays = payload?.days ?: throw GeminiResponseException("falta el campo 'days'")
    val days = rawDays.mapNotNull { day ->
        val number = day.day ?: return@mapNotNull null
        ParsedMenuDay(number, day.dishes.orEmpty().filterNotNull())
    }
    val month = if (payload.month in 1..12 && payload.year in MIN_PLAUSIBLE_YEAR..MAX_PLAUSIBLE_YEAR) {
        YearMonth.of(payload.year!!, payload.month!!)
    } else {
        null
    }
    return cleanParsedMenu(month, days)
}

/** Manda la foto de un menú mensual a Gemini y devuelve el menú ya estructurado y limpio. */
suspend fun recognizeMenuWithGemini(context: Context, imageUri: Uri, apiKey: String, model: String): ParsedMenu {
    val imageBytes = try {
        loadPhotoBytesForGemini(context, imageUri)
    } catch (e: GeminiReceiptException) {
        throw e
    } catch (e: Exception) {
        throw GeminiResponseException("no se pudo preparar la foto del menú: ${e.message}")
    }
    val request = GeminiInteractionRequest(
        model = model,
        input = listOf(
            GeminiInputPart(type = "text", text = MENU_PROMPT),
            GeminiInputPart(
                type = "image",
                data = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
                mimeType = "image/jpeg"
            )
        ),
        responseFormat = GeminiResponseFormat(schema = MENU_JSON_SCHEMA)
    )
    return mapGeminiMenuText(extractOutputText(callGemini(apiKey, request)))
}
