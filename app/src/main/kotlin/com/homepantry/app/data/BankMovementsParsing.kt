package com.homepantry.app.data

import java.text.Normalizer
import java.util.Calendar
import java.util.Date

/**
 * Una línea del OCR con su caja en la imagen. Hace falta la posición porque ML Kit puede devolver
 * la columna de conceptos y la de importes como bloques separados: sin ella no se sabría qué
 * importe va con qué concepto.
 */
data class OcrLine(val text: String = "", val top: Int = 0, val bottom: Int = 0, val left: Int = 0)

/**
 * Filas de una captura, de arriba abajo; cada fila une de izquierda a derecha las líneas que se
 * solapan en vertical al menos la mitad de la altura de la más baja.
 */
fun groupOcrRows(lines: List<OcrLine>): List<String> {
    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.top }) {
        val row = rows.lastOrNull()
        if (row != null && overlapsEnough(row.first(), line)) row.add(line) else rows.add(mutableListOf(line))
    }
    return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
}

private fun overlapsEnough(a: OcrLine, b: OcrLine): Boolean {
    val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    val minHeight = maxOf(1, minOf(a.bottom - a.top, b.bottom - b.top))
    return overlap * 2 >= minHeight
}

/** Texto de las filas de varias capturas, para mandarlo a Gemini o copiarlo. */
fun ocrRowsText(screenshots: List<List<String>>): String =
    screenshots.withIndex().joinToString("\n") { (index, rows) ->
        (listOf("--- Captura ${index + 1} ---") + rows).joinToString("\n")
    }

// Importe español: miles con punto y dos decimales con coma; el signo y el € son opcionales.
// Cualquier guion cuenta como menos: el OCR lee a menudo el signo como guion largo.
private val AMOUNT = Regex("""(?<![\d.,])([-−–—‐‑+])?\s?(\d{1,3}(?:\.\d{3})+|\d+),(\d{2})(?!\d)\s?€?""")
private val RELATIVE_DAY = Regex("""(?i)\b(hoy|ayer)\b""")
private val NUMERIC_DATE = Regex("""(?<![\d.,/])(\d{1,2})/(\d{1,2})(?:/(\d{4}|\d{2}))?(?![\d/])""")
private val MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)
// Nombres completos antes que abreviaturas para que «noviembre» no se quede en «nov».
private val MONTH_WORDS = (MONTHS + "setiembre" + "sept" + MONTHS.map { it.take(3) }).joinToString("|")
private val NAMED_DATE = Regex("""(?i)(?<!\d)(\d{1,2})\s+(?:de\s+)?($MONTH_WORDS)\.?(?![a-zñ])(?:\s+(?:de\s+)?(\d{4}))?""")

private fun monthNumber(word: String): Int {
    val w = word.lowercase()
    return if (w.startsWith("set")) 9 else MONTHS.indexOfFirst { it.startsWith(w.take(3)) } + 1
}

private fun plain(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

private fun dayOf(year: Int, month: Int, day: Int): Date? = Calendar.getInstance().run {
    clear()
    isLenient = false
    set(year, month - 1, day)
    try {
        time
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Fecha sin año: la de este año salvo que quede en el futuro, y entonces la del anterior. */
private fun dateWithYear(day: Int, month: Int, year: String?, today: Date): Date? {
    val explicit = year?.toInt()?.let { if (year.length == 2) 2000 + it else it }
    val date = if (explicit != null) {
        dayOf(explicit, month, day)
    } else {
        val thisYear = Calendar.getInstance().apply { time = today }.get(Calendar.YEAR)
        dayOf(thisYear, month, day)?.let { candidate ->
            if (plausibleReceiptDate(candidate, today) == null) dayOf(thisYear - 1, month, day) else candidate
        }
    }
    return plausibleReceiptDate(date, today)
}

private fun relativeDay(word: String, today: Date): Date {
    val cal = Calendar.getInstance().apply { time = today }
    val base = dayOf(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))!!
    return if (word.equals("ayer", ignoreCase = true)) {
        Calendar.getInstance().apply { time = base; add(Calendar.DAY_OF_MONTH, -1) }.time
    } else {
        base
    }
}

/** Primera fecha de [text] y el texto sin ella, o null si no hay ninguna válida. */
private fun findDate(text: String, today: Date): Pair<Date, String>? {
    RELATIVE_DAY.find(text)?.let { return relativeDay(it.value, today) to text.removeRange(it.range) }
    NUMERIC_DATE.find(text)?.let { m ->
        val (d, mo, y) = m.destructured
        dateWithYear(d.toInt(), mo.toInt(), y.ifEmpty { null }, today)?.let { return it to text.removeRange(m.range) }
    }
    NAMED_DATE.find(text)?.let { m ->
        val (d, mo, y) = m.destructured
        dateWithYear(d.toInt(), monthNumber(mo), y.ifEmpty { null }, today)?.let { return it to text.removeRange(m.range) }
    }
    return null
}

private val WEEKDAY = Regex(
    """(?i)\b(lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo|lun|mar|mie|mié|jue|vie|sab|sáb|dom)\b\.?"""
)

/** Lo que queda de una fila con fecha sin el día de la semana ni la puntuación suelta («Viernes, 20 nov»). */
private fun isOnlyDateLeftover(rest: String): Boolean =
    listOf(WEEKDAY, RELATIVE_DAY, NUMERIC_DATE, NAMED_DATE).fold(rest) { text, regex -> regex.replace(text, " ") }
        .replace(Regex("""[\s,.;:·-]+"""), "").isEmpty()

/** Una fila sin importe vale como concepto del importe siguiente si tiene al menos 3 letras (no una hora o un código). */
private fun looksLikeTitle(text: String): Boolean = text.count { it.isLetter() } >= 3

private fun cleanTitle(text: String): String = text.replace("€", " ").replace(Regex("\\s+"), " ").trim()

private class RawMovement(val title: String, val cents: Long, val plus: Boolean, val date: Date?)

/**
 * Movimientos de las filas del OCR de capturas de la app de Kutxabank, sin IA. Una fila con
 * importe es un movimiento (si tiene dos, el primero: el segundo suele ser el saldo); una fila que
 * solo es una fecha (con o sin día de la semana) fija la de los movimientos que la siguen dentro
 * de la misma captura. Solo un «+» explícito marca un ingreso; el resto son cargos.
 */
fun parseBankMovements(screenshots: List<List<String>>, today: Date): List<CardMovement> {
    val raw = mutableListOf<RawMovement>()
    for (rows in screenshots) {
        var currentDate: Date? = null
        var pendingTitle: String? = null
        for (row in rows) {
            if (plain(row).let { "saldo" in it || "disponible" in it }) {
                pendingTitle = null
                continue
            }
            val amount = AMOUNT.find(row)
            val withoutAmounts = AMOUNT.replace(row, " ")
            val found = findDate(withoutAmounts, today)
            val rest = cleanTitle(found?.second ?: withoutAmounts)
            if (amount == null) {
                if (found != null && isOnlyDateLeftover(rest)) {
                    currentDate = found.first
                    pendingTitle = null
                } else {
                    pendingTitle = rest.takeIf { looksLikeTitle(it) }
                }
                continue
            }
            val title = rest.ifEmpty { pendingTitle.orEmpty() }
            pendingTitle = null
            if (title.isEmpty()) continue
            val (sign, whole, decimals) = amount.destructured
            val cents = whole.replace(".", "").toLong() * 100 + decimals.toLong()
            if (cents <= 0) continue
            raw.add(RawMovement(title, cents, sign == "+", found?.first ?: currentDate))
        }
    }
    // Solo un «+» explícito marca un ingreso. Un importe sin signo sale como cargo aunque otros
    // lleven «-»: si el OCR se come el signo, es mejor verlo en la revisión y desmarcarlo que
    // perder el cargo sin enterarse.
    return raw.map { CardMovement(it.title, it.cents, it.date, charge = !it.plus) }
}

/** Si el OCR ha leído algo en alguna captura: sin texto no tiene sentido pagar una llamada a Gemini. */
fun ocrRowsHaveText(screenshots: List<List<String>>): Boolean = screenshots.any { rows -> rows.any { it.isNotBlank() } }
