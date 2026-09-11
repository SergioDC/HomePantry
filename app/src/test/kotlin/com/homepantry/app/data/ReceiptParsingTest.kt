package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptParsingTest {
    @Test fun `extracts name and price from a simple line`() {
        assertEquals(
            listOf(ParsedReceiptLine("TOMATE RAMA", 1.50)),
            parseReceiptLines(listOf("TOMATE RAMA 1,50"))
        )
    }

    @Test fun `handles a trailing euro sign`() {
        assertEquals(
            listOf(ParsedReceiptLine("LECHE ENTERA 1L", 0.89)),
            parseReceiptLines(listOf("LECHE ENTERA 1L 0,89 €"))
        )
    }

    @Test fun `parses thousand separators`() {
        assertEquals(
            listOf(ParsedReceiptLine("ACEITE OLIVA PACK", 1234.56)),
            parseReceiptLines(listOf("ACEITE OLIVA PACK 1.234,56"))
        )
    }

    @Test fun `discards summary lines even if they end in a price-shaped number`() {
        assertEquals(
            emptyList<ParsedReceiptLine>(),
            parseReceiptLines(listOf("TOTAL 12,40", "IVA 21% 2,10", "SUBTOTAL 10,30", "CAMBIO 0,00"))
        )
    }

    @Test fun `discards lines without a recognizable price`() {
        assertEquals(emptyList<ParsedReceiptLine>(), parseReceiptLines(listOf("PAN", "GRACIAS POR SU COMPRA")))
    }

    @Test fun `discards blank lines`() {
        assertEquals(emptyList<ParsedReceiptLine>(), parseReceiptLines(listOf("", "   ")))
    }

    @Test fun `keeps line order and skips invalid lines in a mixed ticket`() {
        val lines = listOf(
            "SUPERMERCADO EJEMPLO",
            "TOMATE RAMA 1,50",
            "LECHE ENTERA 1L 0,89",
            "SUBTOTAL 2,39",
            "TOTAL 2,39"
        )
        assertEquals(
            listOf(
                ParsedReceiptLine("TOMATE RAMA", 1.50),
                ParsedReceiptLine("LECHE ENTERA 1L", 0.89)
            ),
            parseReceiptLines(lines)
        )
    }
}
