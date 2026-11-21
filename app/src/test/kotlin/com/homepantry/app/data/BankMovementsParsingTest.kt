package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class BankMovementsParsingTest {

    private fun day(y: Int, m: Int, d: Int): Date =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.time

    private val today = day(2026, 11, 21)

    private fun parse(vararg rows: String, now: Date = today) = parseBankMovements(listOf(rows.toList()), now)

    @Test
    fun `agrupa en filas por solape vertical`() {
        val rows = groupOcrRows(
            listOf(OcrLine("-12,34 €", 102, 120, 800), OcrLine("MERCADONA", 100, 118, 50), OcrLine("Hoy", 40, 58, 50))
        )
        assertEquals(listOf("Hoy", "MERCADONA  -12,34 €"), rows)
    }

    @Test
    fun `cargo con signo menos y cabecera Hoy`() {
        assertEquals(listOf(CardMovement("MERCADONA", 1234, day(2026, 11, 21), charge = true)), parse("Hoy", "MERCADONA  -12,34 €"))
    }

    @Test
    fun `ingreso con signo mas`() {
        val movements = parse("20/11", "Bizum de Ana  +15,00 €", "LIDL  -8,10 €")
        assertEquals(
            listOf(
                CardMovement("Bizum de Ana", 1500, day(2026, 11, 20), charge = false),
                CardMovement("LIDL", 810, day(2026, 11, 20), charge = true)
            ),
            movements
        )
    }

    @Test
    fun `sin ningun menos todo son cargos`() {
        val movements = parse("Ayer", "LIDL  8,10 €", "EROSKI  3,00")
        assertEquals(listOf("LIDL", "EROSKI"), movements.map { it.title })
        assertTrue(movements.all { it.charge })
        assertTrue(movements.all { it.date == day(2026, 11, 20) })
    }

    @Test
    fun `miles con punto`() {
        assertEquals(123456L, parse("Hoy", "Alquiler  -1.234,56 €").single().amountCents)
    }

    @Test
    fun `formatos de fecha`() {
        for (header in listOf("20 nov", "20 de noviembre", "20/11/2026", "20 NOV. 2026")) {
            assertEquals(header, day(2026, 11, 20), parse(header, "LIDL  -1,00 €").single().date)
        }
        val inRow = parse("Hoy", "19/11  CEPSA  -40,00 €").single()
        assertEquals(day(2026, 11, 19), inRow.date)
        assertEquals("CEPSA", inRow.title)
    }

    @Test
    fun `fecha sin anio en enero`() {
        assertEquals(day(2026, 12, 28), parse("28 dic", "IKEA  -50,00 €", now = day(2027, 1, 5)).single().date)
    }

    @Test
    fun `concepto en la fila anterior`() {
        val movements = parse("Hoy", "AMAZON EU SARL", "-23,99 €", "DIA  -2,00 €")
        assertEquals(listOf("AMAZON EU SARL" to 2399L, "DIA" to 200L), movements.map { it.title to it.amountCents })
    }

    @Test
    fun `dos importes toma el primero`() {
        assertEquals(1234L, parse("Hoy", "MERCADONA  -12,34 €  1.200,00 €").single().amountCents)
    }

    @Test
    fun `ignora saldo y filas sin concepto`() {
        assertEquals(emptyList<CardMovement>(), parse("Saldo disponible  1.500,00 €", "Hoy", "-5,00 €"))
    }

    @Test
    fun `sin importes devuelve vacio`() {
        assertEquals(emptyList<CardMovement>(), parseBankMovements(listOf(emptyList()), today))
        assertEquals(emptyList<CardMovement>(), parse("Movimientos", "Hoy"))
    }

    @Test
    fun `sin fecha queda null`() {
        assertNull(parse("LIDL  -8,10 €").single().date)
    }

    @Test
    fun `varias capturas no heredan la fecha`() {
        val movements = parseBankMovements(listOf(listOf("Hoy", "LIDL  -1,00 €"), listOf("DIA  -2,00 €")), today)
        assertEquals(day(2026, 11, 21), movements[0].date)
        assertNull(movements[1].date)
    }

    @Test
    fun `texto de las capturas`() {
        assertEquals(
            "--- Captura 1 ---\nHoy\nLIDL  -1,00 €\n--- Captura 2 ---\nDIA  -2,00 €",
            ocrRowsText(listOf(listOf("Hoy", "LIDL  -1,00 €"), listOf("DIA  -2,00 €")))
        )
    }

    @Test
    fun `cabecera con dia de la semana fija la fecha`() {
        assertEquals(day(2026, 11, 20), parse("Viernes, 20 de noviembre", "LIDL  -1,00 €").single().date)
        val movement = parse("Hoy, 21 nov", "DIA  -2,00 €").single()
        assertEquals(day(2026, 11, 21), movement.date)
        assertEquals("DIA", movement.title)
    }

    @Test
    fun `un guion largo cuenta como menos`() {
        val movements = parse("Hoy", "LIDL  -1,00 €", "CEPSA  –40,00 €", "DIA  —2,00 €")
        assertEquals(listOf(true, true, true), movements.map { it.charge })
    }

    @Test
    fun `sin signo con otros en menos sigue siendo cargo`() {
        val movements = parse("Hoy", "LIDL  -1,00 €", "CEPSA  40,00 €", "Bizum  +5,00 €")
        assertEquals(listOf(true, true, false), movements.map { it.charge })
    }

    @Test
    fun `una fila de ruido no titula un importe suelto`() {
        assertEquals(emptyList<CardMovement>(), parse("Hoy", "14:32", "-5,00 €"))
        assertEquals(emptyList<CardMovement>(), parse("Hoy", "A1", "-5,00 €"))
    }

    @Test
    fun `capturas sin texto`() {
        assertTrue(!ocrRowsHaveText(listOf(emptyList(), emptyList())))
        assertTrue(ocrRowsHaveText(listOf(emptyList(), listOf("LIDL  -1,00 €"))))
    }
}
