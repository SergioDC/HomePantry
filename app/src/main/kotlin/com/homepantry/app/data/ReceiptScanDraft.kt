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
    val priceText: String = "",
    /** Nombre genérico; `name` es el impreso. */
    val productName: String = "",
    /** Envase de una unidad como lo escribe el usuario, en [packUnit] (G, KG, ML o L). Vacío = sin envase. */
    val packText: String = "",
    val packUnit: String = "G",
    /** Lo que se propuso al leer (Gemini, reglas y alias), para saber qué corrigió el usuario. */
    val proposedProductName: String = "",
    val proposedPackText: String = "",
    val proposedPackUnit: String = "G"
) {
    /** Las líneas sin nombre se descartan al guardar; las que tienen nombre necesitan un precio válido. */
    fun hasInvalidPrice(): Boolean = name.isNotBlank() && parseUserPrice(priceText) == null

    /** Envase elegido; solo cuenta en líneas por unidades (lo pesado ya va en kg o l). */
    fun finalPack(): PackSize? = if (unit == Unit.UD.name) toPackSize(packText, packUnit) else null

    fun finalProposal(): ProductProposal =
        ProductProposal(productName.trim().ifBlank { splitPackSize(name).name }, finalPack())

    fun proposedProposal(): ProductProposal =
        ProductProposal(
            proposedProductName.trim(),
            if (unit == Unit.UD.name) toPackSize(proposedPackText, proposedPackUnit) else null
        )
}

/** Envase en el formato de edición: texto y unidad de pantalla ("" y "G" si no hay). */
private fun packFields(pack: PackSize?): Pair<String, String> = pack?.let(::packDisplay) ?: ("" to "G")

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
            val product = line.finalProposal()
            ParsedReceiptLine(
                name = line.name.trim(),
                price = price,
                quantity = quantity,
                unit = line.unit,
                productName = product.productName,
                pack = product.pack
            )
        },
        date = dateMillis?.let(::Date)
    )

    /** Correcciones de nombre o envase hechas en la revisión, una por nombre impreso (gana la última). */
    fun aliasesToSave(): List<ProductAlias> =
        lines.filter { it.name.isNotBlank() && !it.hasInvalidPrice() }
            .mapNotNull { aliasForCorrection(it.name.trim(), it.proposedProposal(), it.finalProposal()) }
            .associateBy { it.id }
            .values
            .toList()
}

private fun formatPrice(price: Double): String = String.format(Locale.forLanguageTag("es-ES"), "%.2f", price)

/** Borrador recién leído, antes de que el usuario toque nada. */
fun receiptScanDraft(
    parsed: ParsedReceipt,
    photoUri: String?,
    notice: String?,
    aliases: Map<String, ProductAlias> = emptyMap()
): ReceiptScanDraft = ReceiptScanDraft(
    store = parsed.store.orEmpty(),
    dateMillis = parsed.date?.time,
    detectedDateMillis = parsed.date?.time,
    lines = parsed.lines.map { line ->
        // Sin propuesta de Gemini (OCR clásico) se separa el tamaño por reglas; después mandan los alias.
        val fromRules = splitPackSize(line.name)
        val proposal = applyAlias(
            ProductProposal(line.productName.ifBlank { fromRules.name }, line.pack ?: fromRules.pack),
            aliasKey(line.name)?.let { aliases[it] }
        )
        val (packText, packUnit) = packFields(proposal.pack)
        ReceiptDraftLine(
            name = line.name,
            qtyText = formatQuantity(line.quantity),
            unit = line.unit,
            priceText = formatPrice(line.price),
            productName = proposal.productName,
            packText = packText,
            packUnit = packUnit,
            proposedProductName = proposal.productName,
            proposedPackText = packText,
            proposedPackUnit = packUnit
        )
    },
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
                    priceText = line.priceText ?: "",
                    productName = line.productName ?: "",
                    packText = line.packText ?: "",
                    packUnit = line.packUnit ?: "G",
                    proposedProductName = line.proposedProductName ?: "",
                    proposedPackText = line.proposedPackText ?: "",
                    proposedPackUnit = line.proposedPackUnit ?: "G"
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
