package com.homepantry.app.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/** Nombres impresos que se mandan a Gemini en cada petición al normalizar el histórico. */
const val NAMER_BATCH_SIZE = 100

private data class NamerEntry(
    val rawName: String? = null,
    val productName: String? = null,
    val packSize: Double? = null,
    val packUnit: String? = null
)

private data class NamerPayload(val products: List<NamerEntry>? = null)

private val NAMER_JSON_SCHEMA: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "products" to mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "rawName" to mapOf("type" to "string"),
                    "productName" to mapOf("type" to "string"),
                    "packSize" to mapOf("type" to "number"),
                    "packUnit" to mapOf("type" to "string", "enum" to listOf(Unit.KG.name, Unit.L.name))
                ),
                "required" to listOf("rawName", "productName")
            )
        )
    ),
    "required" to listOf("products")
)

private val NAMER_PROMPT = """
    Eres un asistente que normaliza nombres de productos de tickets de
    supermercado españoles. Para cada nombre impreso de la lista (uno por
    línea) devuelve:
    - rawName: el nombre impreso exactamente como aparece en la lista.
    - productName: el nombre genérico del producto en español, tipo y
      variedad, sin marca ni tamaño ni formato, con la primera letra en
      mayúscula ("ARANDANO 300GR" → "Arándanos", "LECHE ENT. HACENDADO 1L"
      → "Leche entera", "LECHE SEMI PASCUAL" → "Leche semidesnatada").
      Usa exactamente el mismo productName para nombres que sean el mismo
      producto.
    - packSize y packUnit: el peso o volumen de UNA unidad cuando el nombre
      impreso lo indica, en KG o L ("300GR" → 0.3 KG, "1,5L" → 1.5 L,
      "6X1L" → 6 L); omítelos si no aparece.
    Lista de nombres impresos:
""".trimIndent()

private val namerGson = Gson()

/** Propuestas por nombre impreso. Las entradas sin nombre se descartan; un JSON roto lanza. */
internal fun mapNamerOutput(text: String): Map<String, ProductProposal> {
    val payload = try {
        namerGson.fromJson(text, NamerPayload::class.java)
    } catch (e: JsonSyntaxException) {
        throw GeminiResponseException("el JSON de nombres no es válido")
    }
    return payload?.products.orEmpty().mapNotNull { entry ->
        val rawName = entry.rawName?.trim()
        val productName = entry.productName?.trim()
        if (rawName.isNullOrEmpty() || productName.isNullOrEmpty()) {
            null
        } else {
            rawName to ProductProposal(productName, geminiPack(entry.packSize, entry.packUnit))
        }
    }.toMap()
}

/**
 * Pide a Gemini, solo con texto (sin fotos), el nombre genérico y el envase de una tanda de
 * nombres impresos ya guardados. Lanza [GeminiReceiptException] como la lectura de tickets.
 */
suspend fun nameProductsWithGemini(rawNames: List<String>, apiKey: String, model: String): Map<String, ProductProposal> {
    if (rawNames.isEmpty()) return emptyMap()
    val request = GeminiInteractionRequest(
        model = model,
        input = listOf(GeminiInputPart(type = "text", text = NAMER_PROMPT + "\n" + rawNames.joinToString("\n"))),
        responseFormat = GeminiResponseFormat(schema = NAMER_JSON_SCHEMA)
    )
    return mapNamerOutput(extractOutputText(callGemini(apiKey, request)))
}
