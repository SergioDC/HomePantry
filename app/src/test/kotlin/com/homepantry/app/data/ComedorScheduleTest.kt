package com.homepantry.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComedorScheduleTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, madrid)

    @Test
    fun `ventana de los 3 ultimos dias al dia 5`() {
        assertNull(comedorTargetMonth(LocalDate.of(2026, 10, 28)))
        assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 10, 29)))
        assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 10, 31)))
        assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 11, 1)))
        assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 11, 5)))
        assertNull(comedorTargetMonth(LocalDate.of(2026, 11, 6)))
        assertNull(comedorTargetMonth(LocalDate.of(2027, 2, 25)))
        assertEquals(YearMonth.of(2027, 3), comedorTargetMonth(LocalDate.of(2027, 2, 26)))
        assertEquals(YearMonth.of(2027, 1), comedorTargetMonth(LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun `sin menu se repite manana dentro de la ventana`() {
        assertEquals(at(2026, 10, 31, 10), nextComedorCheck(at(2026, 10, 30), false))
        assertEquals(at(2026, 11, 1, 10), nextComedorCheck(at(2026, 10, 31), false))
    }

    @Test
    fun `sin menu el dia 5 pasa a la siguiente ventana`() =
        assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 11, 5), false))

    @Test
    fun `con menu salta a la siguiente ventana aunque queden dias`() {
        assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 10, 30), true))
        assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 11, 2), true))
    }

    @Test
    fun `fuera de la ventana espera a la siguiente`() =
        assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 11, 15), false))

    @Test
    fun `primera programacion`() {
        assertEquals(at(2026, 10, 30), initialComedorCheck(at(2026, 10, 30)))
        assertEquals(at(2026, 11, 28, 10), initialComedorCheck(at(2026, 11, 15)))
    }
}
