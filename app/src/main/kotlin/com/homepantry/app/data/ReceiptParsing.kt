package com.homepantry.app.data

/** Una línea de ticket ya separada en nombre de producto + precio total. */
data class ParsedReceiptLine(val name: String, val price: Double)

/**
 * Muchos tickets imprimen la categoría de IVA de cada producto justo después
 * de su precio: como letra en Lidl ("...  3,90 A") o como dígito en Aldi
 * ("...  0,95 € 3"), así que el precio no siempre es literalmente lo último
 * de la línea: se admite un carácter suelto (letra o dígito) tras el precio
 * y se descarta sin usarlo.
 */
private val PRICE_AT_END = Regex("""(-)?(\d{1,3}(?:\.\d{3})*,\d{2})\s*€?\s*[A-Z0-9]?\s*$""")

/**
 * Algunos artículos imprimen "precio_unitario x cantidad" pegado al nombre,
 * en la MISMA línea que el total (p.ej. "PALETA CEBO IBÉRICA 4,79x 2 9,58 B"
 * -> nombre "PALETA CEBO IBÉRICA", precio 9,58). Se recorta del nombre tras
 * extraer el precio final; a diferencia de [BARE_QUANTITY_NAME] esto no
 * necesita reconstrucción entre líneas porque el total ya viene en la misma.
 */
private val INLINE_UNIT_QTY_SUFFIX = Regex("""\s*\d{1,3}(?:\.\d{3})*,\d{2}\s*[xX]\.?\s*\d+\s*$""")

/**
 * Artículos por peso imprimen el nombre + precio total en su línea normal, y
 * una línea aparte con el desglose "peso UD x precio/UD" (p.ej.
 * "0,200 kg x 22,90 EUR/kg"). Esa línea de desglose no aporta un precio
 * nuevo (el total ya se capturó en la línea del producto) así que se
 * descarta en vez de reemplazar el nombre pendiente.
 */
private val WEIGHT_DETAIL_LINE =
    Regex("""^\d+(?:[.,]\d+)?\s*(KG|G|L|ML)\.?\s*[xX]\.?\s*\d{1,3}(?:\.\d{3})*,\d{2}""")

/**
 * Fila de la tabla de desglose de IVA que suele aparecer al pie del ticket,
 * identificada por letra (Lidl: "A   4%   0,71   17,84   18,55") o por
 * dígito (Aldi: "2 4,00% 0,82 0,03 0,85"): nunca es un producto aunque
 * termine en un número con forma de precio.
 */
private val TAX_BREAKDOWN_ROW = Regex("""^[A-Z0-9]\s+\d{1,2}(?:[.,]\d+)?\s*%""")

/**
 * Artículos por cantidad imprimen el nombre en una línea sin precio y, en la
 * siguiente, "N X precio_unitario", con el total en otra columna que ML Kit
 * reporta como una tercera línea. "N X" nunca es un nombre de producto real
 * (ver hallazgo "invents things"), pero el producto tampoco debe perderse:
 * [parseReceiptLines] usa esta cantidad para recomponerlo en vez de
 * descartarlo o inventar un artículo falso a partir de "N X".
 */
private val BARE_QUANTITY_NAME = Regex("""^(\d+)\s*[xX]\.?$""")

/** Líneas de resumen de ticket que nunca son un producto (spec "Flujo de captura y parseo"). */
private val SUMMARY_LINE_KEYWORDS = listOf(
    "TOTAL", "SUBTOTAL", "IVA", "CAMBIO", "TARJETA", "EFECTIVO", "BASE IMPONIBLE", "A PAGAR"
)

/**
 * Cabecera/pie de ticket y metadatos (datos fiscales de la tienda, número de
 * ticket/operación, cajero, puntos de fidelización, importe entregado...) y
 * descuentos/promociones: información real del ticket, pero nunca un
 * producto, aunque la línea termine en un número con pinta de precio.
 */
private val HEADER_FOOTER_KEYWORDS = listOf(
    "GRACIAS", "CIF", "NIF", "TICKET", "OPERACION", "CAJERO", "CAJERA",
    "PUNTOS", "ENTREGADO", "ENTREGA", "RECIBIDO", "DEVUELTO", "FACTURA SIMPLIFICADA",
    "DESCUENTO", "DTO", "PROMOCION", "PROMO", "OFERTA", "BONIFICACION", "SUMA"
)

private val NON_PRODUCT_KEYWORDS = SUMMARY_LINE_KEYWORDS + HEADER_FOOTER_KEYWORDS

/** Coincide con "ahorro", "ahorrado", "ahorrar"... (mensajes de ahorro acumulado en el pie del ticket). */
private val SAVINGS_PREFIX = Regex("""\bAHORR""")

/**
 * Parseo por reglas (sin motor de IA): busca un precio en formato español al
 * final de cada línea y usa el resto como nombre de producto. Descarta
 * líneas de resumen (TOTAL/IVA/...) y líneas sin precio reconocible.
 *
 * Los artículos por cantidad reparten un mismo producto en varias líneas de
 * OCR (nombre / "N X precio_unitario" / total); se recorren con un pequeño
 * estado ([pendingName]/[pendingQuantityPrice]) para reconstruir una única
 * entrada en vez de perder el producto o convertir "N X" en uno falso.
 */
fun parseReceiptLines(rawLines: List<String>): List<ParsedReceiptLine> {
    val result = mutableListOf<ParsedReceiptLine>()
    var pendingName: String? = null
    var pendingQuantityPrice: Double? = null

    fun flushPending() {
        val name = pendingName
        val price = pendingQuantityPrice
        if (name != null && price != null) {
            result.add(ParsedReceiptLine(name, price))
        }
        pendingName = null
        pendingQuantityPrice = null
    }

    for (rawLine in rawLines) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) continue

        if (isNonProductLine(trimmed)) {
            flushPending()
            continue
        }

        val match = PRICE_AT_END.find(trimmed)
        if (match == null) {
            flushPending()
            pendingName = trimmed
            continue
        }

        if (match.groupValues[1] == "-") {
            // Importe negativo (descuento/ajuste): nunca es el total de un
            // producto real, así que se descarta en vez de convertirlo en
            // positivo.
            flushPending()
            continue
        }

        val price = parseSpanishDecimal(match.groupValues[2])
        if (price == null) {
            flushPending()
            continue
        }

        val name = trimmed.substring(0, match.range.first).trim()
            .replace(INLINE_UNIT_QTY_SUFFIX, "")
            .trim()

        if (name.isEmpty()) {
            // Línea de solo precio: el total de un artículo por cantidad pendiente.
            val pending = pendingName
            if (pending != null) {
                result.add(ParsedReceiptLine(pending, price))
                pendingName = null
                pendingQuantityPrice = null
            }
            continue
        }

        val quantityMatch = BARE_QUANTITY_NAME.matchEntire(name)
        if (quantityMatch != null) {
            if (pendingName != null) {
                val quantity = quantityMatch.groupValues[1].toIntOrNull() ?: 1
                pendingQuantityPrice = price * quantity
            }
            continue
        }

        flushPending()
        result.add(ParsedReceiptLine(name, price))
    }

    flushPending()
    return result
}

private fun isNonProductLine(trimmed: String): Boolean {
    val upper = trimmed.uppercase()
    val hasKeyword = NON_PRODUCT_KEYWORDS.any { keyword ->
        Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(upper)
    }
    return hasKeyword ||
        SAVINGS_PREFIX.containsMatchIn(upper) ||
        WEIGHT_DETAIL_LINE.containsMatchIn(upper) ||
        TAX_BREAKDOWN_ROW.containsMatchIn(upper)
}

private fun parseSpanishDecimal(text: String): Double? {
    // Remove all characters except digits and decimal marker (comma)
    val cleaned = text.replace(Regex("[^0-9,]"), "").replace(",", ".")
    return cleaned.toDoubleOrNull()
}
