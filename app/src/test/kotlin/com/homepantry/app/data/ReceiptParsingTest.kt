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

    @Test fun `reassembles a quantity item split across name, unit-price and total lines`() {
        // El ticket imprime "ACEITUNA NEGRA" en una línea sin precio, y en la
        // siguiente "2 X 0,81" (precio unitario) separado del total "1,62" en
        // otra columna. El OCR reporta cada fragmento como línea propia. "2 X"
        // no es nunca un nombre de producto real (ver hallazgo "invents things"),
        // pero el producto real tampoco debe perderse: se reconstruye usando el
        // total de la línea siguiente. La "3X 1,99" final es una cantidad huérfana
        // (sin nombre pendiente al que asociarse) y se descarta.
        assertEquals(
            listOf(ParsedReceiptLine("ACEITUNA NEGRA", 1.62)),
            parseReceiptLines(listOf("ACEITUNA NEGRA", "2 X 0,81", "1,62", "3X 1,99"))
        )
    }

    @Test fun `falls back to quantity times unit price when no separate total line follows`() {
        // Si el OCR no reporta una línea de total aparte (p.ej. porque el
        // siguiente producto empieza justo después), se usa cantidad x precio
        // unitario en vez de perder el producto.
        assertEquals(
            listOf(ParsedReceiptLine("ACEITUNA NEGRA", 1.62), ParsedReceiptLine("TOMATE RAMA", 1.50)),
            parseReceiptLines(listOf("ACEITUNA NEGRA", "2 X 0,81", "TOMATE RAMA 1,50"))
        )
    }

    @Test fun `discards a negative-amount discount line instead of inventing a fake product`() {
        // El precio con signo "-0,20" nunca es el total de un producto real
        // (es un descuento/ajuste aplicado a otra línea), así que se descarta
        // en vez de crear un "producto" con el importe en positivo.
        assertEquals(
            listOf(ParsedReceiptLine("TOMATE RAMA", 1.50), ParsedReceiptLine("LECHE ENTERA 1L", 0.89)),
            parseReceiptLines(listOf("TOMATE RAMA 1,50", "DESCUENTO -0,20", "LECHE ENTERA 1L 0,89"))
        )
    }

    @Test fun `discards a bare negative-amount line even without a discount keyword`() {
        // Este caso aísla la rama del signo "-" (líneas 81-87 de
        // ReceiptParsing.kt): sin la keyword "DESCUENTO" de por medio, la
        // única razón por la que "-0,20" no se convierte en un producto es
        // el chequeo explícito del signo negativo.
        assertEquals(
            listOf(ParsedReceiptLine("TOMATE RAMA", 1.50), ParsedReceiptLine("LECHE ENTERA 1L", 0.89)),
            parseReceiptLines(listOf("TOMATE RAMA 1,50", "-0,20", "LECHE ENTERA 1L 0,89"))
        )
    }

    @Test fun `discards discount and promotion keywords even without a parseable minus sign`() {
        assertEquals(
            emptyList<ParsedReceiptLine>(),
            parseReceiptLines(listOf("DESCUENTO 0,20", "DTO 2X1 1,00", "PROMOCION 10% 0,50"))
        )
    }

    @Test fun `discards footer lines like loyalty points and cash tendered even with a price-shaped number`() {
        assertEquals(
            listOf(ParsedReceiptLine("TOMATE RAMA", 1.50)),
            parseReceiptLines(
                listOf(
                    "TOMATE RAMA 1,50",
                    "PUNTOS ACUMULADOS 45,00",
                    "IMPORTE ENTREGADO 20,00",
                    "OPERACION 000123 12,40",
                    "GRACIAS POR SU VISITA"
                )
            )
        )
    }

    @Test fun `discards a savings summary line regardless of verb conjugation`() {
        assertEquals(
            emptyList<ParsedReceiptLine>(),
            parseReceiptLines(listOf("Ha ahorrado hoy 3,50", "AHORRO TOTAL 12,00"))
        )
    }

    @Test fun `parses a real Lidl receipt with IVA-letter suffixes, inline unit prices, weight items and a tax table`() {
        // Reproduce (parafraseado) un ticket real de Lidl que reveló varios
        // huecos: cada línea de producto termina con la letra de la
        // categoría de IVA, algunos artículos llevan "precio_unitario x
        // cantidad" pegado al nombre en la misma línea, los productos por
        // peso reparten el detalle en una línea aparte, y el pie del ticket
        // trae una tabla de desglose de IVA (letra + % + importes) que no
        // tenía ninguna keyword que la filtrase.
        val lines = listOf(
            "LIDL SUPERMERCADOS S.A.U.",
            "Azganeta,16",
            "48970Basauri",
            "NIF A60195278",
            "www.lidl.es",
            "EUR",
            "MOZZARELLA RALLADO 1,95x 2 3,90 A",
            "MASA PARA PIZZA 1,69 A",
            "QUESO PARA UNTAR 1,35 A",
            "QUESO GRAN RESERVA 4,58 A",
            "0,200 kg x 22,90 EUR/kg",
            "CUÑA TIERNO 2,59 A",
            "MOLINILLO ESPECIAS 1,25 B",
            "PALETA CEBO IBÉRICA 4,79x 2 9,58 B",
            "PIÑA EN RODAJAS 1,99x 6 11,94 B",
            "PROMO LIDL PLUS -1,20",
            "LIMPIAMUEBLES 1,59 C",
            "SANDÍA NEGRA S/N SEM 4,44 A",
            "6,440 kg x 0,69 EUR/kg",
            "TOTAL 41,71",
            "ENTREGA 41,71",
            "IVA%    IVA    +   P N   =   PVP",
            "A   4%   0,71   17,84   18,55",
            "B  10%   1,96   19,61   21,57",
            "C  21%   0,28   1,31   1,59",
            "Suma    2,95   38,76   41,71",
            "GRACIAS POR SU VISITA"
        )
        assertEquals(
            listOf(
                ParsedReceiptLine("MOZZARELLA RALLADO", 3.90),
                ParsedReceiptLine("MASA PARA PIZZA", 1.69),
                ParsedReceiptLine("QUESO PARA UNTAR", 1.35),
                ParsedReceiptLine("QUESO GRAN RESERVA", 4.58),
                ParsedReceiptLine("CUÑA TIERNO", 2.59),
                ParsedReceiptLine("MOLINILLO ESPECIAS", 1.25),
                ParsedReceiptLine("PALETA CEBO IBÉRICA", 9.58),
                ParsedReceiptLine("PIÑA EN RODAJAS", 11.94),
                ParsedReceiptLine("LIMPIAMUEBLES", 1.59),
                ParsedReceiptLine("SANDÍA NEGRA S/N SEM", 4.44)
            ),
            parseReceiptLines(lines)
        )
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

    @Test fun `parses a real Aldi receipt with numeric IVA-class suffixes and an A PAGAR total`() {
        // Reproduce (parafraseado) un ticket real de Aldi: a diferencia de
        // Lidl, la categoría de IVA se marca con un DÍGITO suelto tras el
        // precio ("0,95 € 3") en vez de una letra, tanto en cada línea de
        // producto como en la tabla de desglose del pie ("2 4,00% ..."), y
        // el total a pagar se imprime como "A PAGAR" en vez de "TOTAL".
        val lines = listOf(
            "A L D I",
            "C/ Artunduaga 16, 48970 Basauri",
            "Horario de apertura:",
            "Lu-Sa: 09:30 - 21:30 /",
            "Do: Cerrado",
            "LIMON CONCENTRADO 0,95 € 3",
            "RODAJAS PINA EN SU JUG 2,70 € 3",
            "2 x 1,35 €",
            "HOJALDRE QUESO FRESCO 1,98 € 3",
            "2 x 0,99 €",
            "BREZEL 0,85 € 2",
            "A PAGAR 6,48 €",
            "Efectivo 20,00 €",
            "Cambio 13,52 €",
            "IVA NETO TOTAL IVA BRUTO",
            "2 4,00% 0,82 0,03 0,85",
            "3 10,00% 5,12 0,51 5,63",
            "GRACIAS POR TU COMPRA"
        )
        assertEquals(
            listOf(
                ParsedReceiptLine("LIMON CONCENTRADO", 0.95),
                ParsedReceiptLine("RODAJAS PINA EN SU JUG", 2.70),
                ParsedReceiptLine("HOJALDRE QUESO FRESCO", 1.98),
                ParsedReceiptLine("BREZEL", 0.85)
            ),
            parseReceiptLines(lines)
        )
    }
}
