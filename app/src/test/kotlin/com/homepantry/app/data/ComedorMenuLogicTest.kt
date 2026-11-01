package com.homepantry.app.data

import com.google.gson.Gson
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComedorMenuLogicTest {

    @Test
    fun `rango de noviembre 2026 en UTC`() = assertEquals(
        "2026-10-31T23:00:00.000Z" to "2026-11-29T23:00:00.000Z",
        comedorMonthRange(YearMonth.of(2026, 11))
    )

    @Test
    fun `rango de octubre 2026 cruza el cambio de hora`() = assertEquals(
        "2026-09-30T22:00:00.000Z" to "2026-10-30T23:00:00.000Z",
        comedorMonthRange(YearMonth.of(2026, 10))
    )

    @Test
    fun `rango de marzo 2027 cruza el cambio de hora`() = assertEquals(
        "2027-02-28T23:00:00.000Z" to "2027-03-30T22:00:00.000Z",
        comedorMonthRange(YearMonth.of(2027, 3))
    )

    @Test
    fun `cuerpo serializado como espera Menuo`() {
        val json = Gson().toJson(comedorRequestBody(YearMonth.of(2026, 11)))
        assertEquals(
            """[{"nombre":"FechaIni","valorObjeto":"2026-10-31T23:00:00.000Z"},""" +
                """{"nombre":"FechaFin","valorObjeto":"2026-11-29T23:00:00.000Z"},""" +
                """{"nombre":"PkMenu","valorObjeto":"fce8da488dcf48bab961b36a009fcfdc"},""" +
                """{"nombre":"PkCentro","valorObjeto":"4cf8682f16604ae1b1dcb2e400f2972e"},""" +
                """{"nombre":"PkCentroEnvio","valorObjeto":"cef83569f1ae40cc816cb3d300cf64d9"},""" +
                """{"nombre":"PkDieta","valorObjeto":"d12fe25a9bbd49239e3cd65eef62e7c5"},""" +
                """{"nombre":"PkServicio","valorObjeto":"[\"6f6096586ff248189d7d535b8509f411\"]"},""" +
                """{"nombre":"PkIdioma","valorObjeto":"00242C08E94B10101810432101271385"},""" +
                """{"nombre":"NombreArchivo","valorObjeto":"menu"},""" +
                """{"nombre":"TipoVista","valorObjeto":"Mensual"}]""",
            json
        )
    }

    @Test
    fun `isPdf acepta content type o cabecera PDF`() {
        assertTrue(isPdf("application/pdf", byteArrayOf()))
        assertTrue(isPdf("application/pdf; charset=binary", byteArrayOf()))
        assertTrue(isPdf(null, "%PDF-1.7".toByteArray()))
        assertFalse(isPdf("text/html", "<html>".toByteArray()))
        assertFalse(isPdf("application/json", byteArrayOf()))
    }

    @Test
    fun `huella sha256`() = assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        pdfFingerprint("abc".toByteArray())
    )

    @Test
    fun `hasComedorMenu solo cuenta comidas de Oihan`() {
        assertTrue(hasComedorMenu(listOf(MealEntry(slot = MealSlot.LUNCH.name, person = " oihan "))))
        assertFalse(hasComedorMenu(listOf(MealEntry(slot = MealSlot.LUNCH.name, person = null))))
        assertFalse(hasComedorMenu(listOf(MealEntry(slot = MealSlot.DINNER.name, person = "Oihan"))))
    }

    @Test
    fun `manualComedorMonth usa el mes leido salvo si es anterior al actual`() {
        val today = LocalDate.of(2026, 10, 30)
        assertEquals(YearMonth.of(2026, 11), manualComedorMonth(YearMonth.of(2026, 11), today))
        assertEquals(YearMonth.of(2026, 10), manualComedorMonth(YearMonth.of(2026, 10), today))
        assertNull(manualComedorMonth(YearMonth.of(2026, 9), today))
        assertEquals(YearMonth.of(2026, 11), manualComedorMonth(null, today))
    }

    @Test
    fun `isStaleComedorMonth solo si el mes leido es anterior`() {
        assertTrue(isStaleComedorMonth(YearMonth.of(2026, 10), YearMonth.of(2026, 11)))
        assertFalse(isStaleComedorMonth(YearMonth.of(2026, 11), YearMonth.of(2026, 11)))
        assertFalse(isStaleComedorMonth(YearMonth.of(2026, 12), YearMonth.of(2026, 11)))
        assertFalse(isStaleComedorMonth(null, YearMonth.of(2026, 11)))
    }
}
