package com.homepantry.app.data

/** Una línea de ticket ya separada en nombre de producto + precio total. */
data class ParsedReceiptLine(val name: String, val price: Double)

private val PRICE_AT_END = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})\s*€?\s*$""")

/** Líneas de resumen de ticket que nunca son un producto (spec "Flujo de captura y parseo"). */
private val SUMMARY_LINE_KEYWORDS = listOf(
    "TOTAL", "SUBTOTAL", "IVA", "CAMBIO", "TARJETA", "EFECTIVO", "BASE IMPONIBLE"
)

/**
 * Parseo por reglas (sin motor de IA): busca un precio en formato español al
 * final de cada línea y usa el resto como nombre de producto. Descarta
 * líneas de resumen (TOTAL/IVA/...) y líneas sin precio reconocible.
 */
fun parseReceiptLines(rawLines: List<String>): List<ParsedReceiptLine> =
    rawLines.mapNotNull(::parseReceiptLine)

private fun parseReceiptLine(line: String): ParsedReceiptLine? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null

    val upper = trimmed.uppercase()
    if (SUMMARY_LINE_KEYWORDS.any { keyword -> upper.contains(keyword) }) return null

    val match = PRICE_AT_END.find(trimmed) ?: return null
    val price = parseSpanishDecimal(match.groupValues[1]) ?: return null

    val name = trimmed.substring(0, match.range.first).trim()
    if (name.isEmpty()) return null

    return ParsedReceiptLine(name = name, price = price)
}

private fun parseSpanishDecimal(text: String): Double? {
    // Remove all characters except digits and decimal marker (comma)
    val cleaned = text.replace(Regex("[^0-9,]"), "").replace(",", ".")
    return cleaned.toDoubleOrNull()
}
