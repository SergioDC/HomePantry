package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Importar gastos de SNcount desde capturas de la lista de movimientos de la app del banco
 * (spec 2026-11-21-sncount-importar-capturas). Gemini lee las capturas; aquí se decide qué
 * cargos salen marcados en la revisión y cómo se convierten en gastos.
 */

/** Un movimiento leído de las capturas. [charge] = false es un ingreso o una devolución. */
data class CardMovement(val title: String, val amountCents: Long, val date: Date?, val charge: Boolean)

/** Por qué un cargo sale desmarcado en la revisión. */
enum class CardRowFlag { NONE, DUPLICATE, OUT_OF_MONTH, NO_DATE }

/** Un cargo tal como se edita en la revisión: el importe como texto, sin validar. */
data class CardImportRow(
    val title: String = "",
    val amountText: String = "",
    /** Día del cargo a medianoche local, en milisegundos; null si no se leyó. */
    val dateMillis: Long? = null,
    val selected: Boolean = true,
    val flag: CardRowFlag = CardRowFlag.NONE,
    /**
     * Id del documento del gasto que se creará. Se fija al leer y no al guardar para que reintentar
     * un guardado que falló a medias sobrescriba lo ya escrito en vez de duplicarlo.
     */
    val id: String = ""
) {
    fun isValid(): Boolean = title.isNotBlank() && parseCents(amountText) != null && dateMillis != null
}

/**
 * Cargos leídos y aún sin añadir. Se guarda en disco (Gson) para que la lectura, lenta y de pago,
 * no se pierda si Android cierra la app; por eso va en `data/` con valores por defecto (R8).
 */
data class CardImportDraft(
    val monthId: String = "",
    val payerId: String = "",
    val rows: List<CardImportRow> = emptyList(),
    /** Capturas leídas, para poder reintentar la lectura. */
    val imageUris: List<String> = emptyList()
)

private data class GeminiCardMovement(val title: String?, val amount: Double?, val date: String?, val charge: Boolean?)
private data class GeminiCardMovementsPayload(val movements: List<GeminiCardMovement>?)

private val cardGson = Gson()

/** Respuesta JSON de Gemini a movimientos. El signo del importe no cuenta: lo decide `charge`. */
fun mapCardMovementsOutput(outputText: String, today: Date = Date()): List<CardMovement> {
    val payload = try {
        cardGson.fromJson(outputText, GeminiCardMovementsPayload::class.java)
    } catch (e: Exception) {
        throw GeminiResponseException("el JSON de movimientos no es válido")
    }
    val movements = payload?.movements ?: throw GeminiResponseException("falta el campo 'movements'")
    return movements.mapNotNull { movement ->
        val title = movement.title?.trim().orEmpty()
        val cents = movement.amount?.let { (abs(it) * 100).roundToLong() } ?: 0L
        if (title.isEmpty() || cents <= 0) return@mapNotNull null
        CardMovement(
            title = title,
            amountCents = cents,
            date = plausibleReceiptDate(parseIsoReceiptDate(movement.date), today),
            charge = movement.charge ?: true
        )
    }
}

/** Día local de [date] como "yyyy-MM-dd", para comparar cargos y gastos sin la hora. */
private fun dayKey(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

private fun centsToText(cents: Long): String = String.format(Locale.forLanguageTag("es-ES"), "%.2f", cents / 100.0)

/**
 * Borrador recién leído: solo los cargos, y desmarcados los que probablemente no hay que añadir
 * (sin fecha, ya metidos en el grupo o repetidos en dos capturas, o de otro mes). El pagador es
 * quien importa, porque son los cargos de su tarjeta, salvo que no sea miembro del grupo.
 */
fun cardImportDraft(
    movements: List<CardMovement>,
    month: SplitMonth,
    existing: List<SplitExpense>,
    currentUid: String,
    imageUris: List<String>
): CardImportDraft {
    // Un gasto ya existente con el mismo importe el mismo día cuenta como duplicado aunque el
    // concepto difiera: lo metido a mano no suele llevar el texto del banco.
    val existingKeys = existing.mapNotNull { e -> e.date?.let { e.amountCents to dayKey(it) } }.toSet()
    val seen = mutableSetOf<Triple<String, Long, String>>()
    val rows = movements.filter { it.charge }.map { movement ->
        val date = movement.date
        val flag = when {
            date == null -> CardRowFlag.NO_DATE
            (movement.amountCents to dayKey(date)) in existingKeys -> CardRowFlag.DUPLICATE
            !seen.add(Triple(movement.title.lowercase(), movement.amountCents, dayKey(date))) -> CardRowFlag.DUPLICATE
            !month.activity && !isDateInMonth(date, month.monthKey) -> CardRowFlag.OUT_OF_MONTH
            else -> CardRowFlag.NONE
        }
        CardImportRow(
            title = movement.title,
            amountText = centsToText(movement.amountCents),
            dateMillis = date?.let { startOfDay(it).time },
            selected = flag == CardRowFlag.NONE,
            flag = flag,
            id = UUID.randomUUID().toString()
        )
    }
    return CardImportDraft(
        monthId = month.id,
        payerId = currentUid.takeIf { it in month.memberIds } ?: month.memberIds.firstOrNull().orEmpty(),
        rows = rows,
        imageUris = imageUris
    )
}

/** Gastos a crear: las filas marcadas y válidas, a partes iguales entre todo el grupo. */
fun CardImportDraft.toExpenses(month: SplitMonth): List<SplitExpense> =
    rows.filter { it.selected && it.isValid() }.map { row ->
        SplitExpense(
            id = row.id,
            title = row.title.trim(),
            amountCents = parseCents(row.amountText)!!,
            payerId = payerId,
            participants = month.memberIds,
            date = Date(row.dateMillis!!)
        )
    }

fun cardImportDraftToJson(draft: CardImportDraft): String = cardGson.toJson(draft)

/** Null si el texto no es un borrador válido (fichero a medio escribir). Gson puede dejar nulos: se rellenan. */
fun cardImportDraftFromJson(json: String): CardImportDraft? = try {
    val stored: CardImportDraft? = cardGson.fromJson(json, CardImportDraft::class.java)
    stored?.let { draft ->
        @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
        draft.copy(
            monthId = draft.monthId ?: "",
            payerId = draft.payerId ?: "",
            imageUris = (draft.imageUris ?: emptyList()).filterNotNull(),
            rows = (draft.rows ?: emptyList()).filterNotNull().map { row ->
                CardImportRow(
                    title = row.title ?: "",
                    amountText = row.amountText ?: "",
                    dateMillis = row.dateMillis,
                    selected = row.selected,
                    flag = row.flag ?: CardRowFlag.NONE,
                    id = row.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                )
            }
        )
    }
} catch (e: Exception) {
    null
}

/** Esquema JSON que se le pide a Gemini como salida estructurada de las capturas. */
val CARD_MOVEMENTS_JSON_SCHEMA: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "movements" to mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "title" to mapOf("type" to "string"),
                    "amount" to mapOf("type" to "number"),
                    "date" to mapOf("type" to "string"),
                    "charge" to mapOf("type" to "boolean")
                ),
                "required" to listOf("title", "amount", "charge")
            )
        )
    ),
    "required" to listOf("movements")
)

private fun cardChargesPrompt(month: SplitMonth, today: Date): String = """
    Eres un asistente que lee capturas de pantalla de la lista de movimientos de la app de un
    banco español (cargos de tarjeta). Puede haber varias capturas de la misma lista: devuelve
    una entrada por movimiento entre todas ellas y, si una misma fila aparece en dos capturas,
    devuélvela una sola vez. Para cada movimiento:
    - title: el comercio o concepto tal como aparece.
    - amount: el importe en euros.
    - charge: true si es un cargo o una compra; false si es un ingreso, una devolución, un Bizum
      recibido o una transferencia recibida.
    - date: la fecha del movimiento en formato YYYY-MM-DD. Si la captura no muestra el año, usa
      el del mes ${month.monthKey} o, si no puede ser, el de hoy. Omítela si no se ve.
    Hoy es ${dayKey(today)}.
    Ignora saldos, totales, cabeceras y cualquier cosa que no sea un movimiento.
""".trimIndent()

/** Manda las capturas a Gemini en una sola petición y devuelve los movimientos leídos. */
suspend fun recognizeCardChargesWithGemini(
    context: Context,
    imageUris: List<Uri>,
    apiKey: String,
    model: String,
    month: SplitMonth,
    today: Date = Date()
): List<CardMovement> {
    val images = imageUris.map { uri ->
        GeminiInputPart(type = "image", data = encodePhotoForGemini(context, uri, "de la captura"), mimeType = "image/jpeg")
    }
    val request = GeminiInteractionRequest(
        model = model,
        input = listOf(GeminiInputPart(type = "text", text = cardChargesPrompt(month, today))) + images,
        responseFormat = GeminiResponseFormat(schema = CARD_MOVEMENTS_JSON_SCHEMA)
    )
    return mapCardMovementsOutput(extractOutputText(callGemini(apiKey, request)), today)
}
