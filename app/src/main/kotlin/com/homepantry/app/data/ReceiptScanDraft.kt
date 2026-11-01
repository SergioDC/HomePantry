package com.homepantry.app.data

import com.google.gson.Gson
import java.io.File
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una línea del ticket tal como se edita en la revisión: los números como texto, sin validar. */
data class ReceiptDraftLine(
    val name: String = "",
    val qtyText: String = "",
    val unit: String = Unit.UD.name,
    val priceText: String = ""
) {
    /** Las líneas sin nombre se descartan al guardar; las que tienen nombre necesitan un precio válido. */
    fun hasInvalidPrice(): Boolean = name.isNotBlank() && parseUserPrice(priceText) == null
}

/**
 * Ticket leído (con Gemini o con el OCR clásico) y aún sin guardar, con lo que el usuario lleva
 * corregido. Como el borrador del menú, vive en un ViewModel y se guarda en disco para que la
 * lectura (lenta y de pago) no se pierda al girar el móvil, salir de la pantalla o si Android
 * cierra la app. Va en `data/` y con valores por defecto porque Gson lo serializa (R8).
 */
data class ReceiptScanDraft(
    val store: String = "",
    /** Fecha del ticket en milisegundos; null si no se leyó y hay que elegirla. */
    val dateMillis: Long? = null,
    /** Fecha que se leyó del ticket, para decir en la revisión si la actual sale de ahí. */
    val detectedDateMillis: Long? = null,
    val lines: List<ReceiptDraftLine> = emptyList(),
    /** Foto del ticket (Uri en texto), que se sube al guardar. */
    val photoUri: String? = null,
    /** Aviso de cómo fue la lectura (Gemini falló, OCR vacío…), que se muestra en la revisión. */
    val notice: String? = null
) {
    /** Lo que se guarda: sin líneas sin nombre ni precios ilegibles, y una cantidad ilegible cuenta como 1. */
    fun toParsedReceipt(): ParsedReceipt = ParsedReceipt(
        store = store.trim().ifBlank { null },
        lines = lines.mapNotNull { line ->
            val price = parseUserPrice(line.priceText)
            if (line.name.isBlank() || price == null) return@mapNotNull null
            val quantity = line.qtyText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
            ParsedReceiptLine(name = line.name.trim(), price = price, quantity = quantity, unit = line.unit)
        },
        date = dateMillis?.let(::Date)
    )
}

private fun formatPrice(price: Double): String = String.format(Locale.forLanguageTag("es-ES"), "%.2f", price)

/** Borrador recién leído, antes de que el usuario toque nada. */
fun receiptScanDraft(parsed: ParsedReceipt, photoUri: String?, notice: String?): ReceiptScanDraft = ReceiptScanDraft(
    store = parsed.store.orEmpty(),
    dateMillis = parsed.date?.time,
    detectedDateMillis = parsed.date?.time,
    lines = parsed.lines.map { ReceiptDraftLine(it.name, formatQuantity(it.quantity), it.unit, formatPrice(it.price)) },
    photoUri = photoUri,
    notice = notice
)

private val gson = Gson()

fun receiptScanDraftToJson(draft: ReceiptScanDraft): String = gson.toJson(draft)

/** Null si el texto no es un borrador válido (fichero a medio escribir). Gson puede dejar nulos: se rellenan. */
fun receiptScanDraftFromJson(json: String): ReceiptScanDraft? = try {
    val stored: ReceiptScanDraft? = gson.fromJson(json, ReceiptScanDraft::class.java)
    stored?.let { draft ->
        @Suppress("USELESS_ELVIS")
        draft.copy(
            store = draft.store ?: "",
            lines = (draft.lines ?: emptyList()).map { line ->
                ReceiptDraftLine(
                    name = line.name ?: "",
                    qtyText = line.qtyText ?: "",
                    unit = line.unit ?: Unit.UD.name,
                    priceText = line.priceText ?: ""
                )
            }
        )
    }
} catch (e: Exception) {
    null
}

/** Fichero del borrador del ticket de una casa. */
fun receiptScanDraftFile(filesDir: File, householdCode: String): File =
    File(filesDir, "receipt_scan_draft_$householdCode.json")

/** Guarda el borrador en [file]; guardar null lo borra. Las lecturas nunca lanzan. */
class ReceiptScanDraftStore(private val file: File) {

    suspend fun load(): ReceiptScanDraft? = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) receiptScanDraftFromJson(file.readText()) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun save(draft: ReceiptScanDraft?) = withContext(Dispatchers.IO) {
        if (draft == null) {
            file.delete()
        } else {
            // Se escribe aparte y se renombra para no dejar un fichero a medias si la app muere.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(receiptScanDraftToJson(draft))
            tmp.renameTo(file)
        }
    }
}
