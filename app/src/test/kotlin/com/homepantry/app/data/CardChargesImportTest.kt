package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class CardChargesImportTest {

    private fun day(y: Int, m: Int, d: Int, h: Int = 0): Date =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.time

    private val today = day(2026, 11, 21, 12)
    private val month = SplitMonth(id = "m1", monthKey = "2026-11", memberIds = listOf("a", "b"))

    private fun charge(title: String, cents: Long, date: Date?) = CardMovement(title, cents, date, charge = true)

    @Test
    fun `mapea cargos e ingresos`() {
        val json = """{"movements":[
            {"title":"MERCADONA","amount":-23.45,"date":"2026-11-10","charge":true},
            {"title":"Bizum Ana","amount":10,"date":"2026-11-11","charge":false}]}"""
        val movements = mapCardMovementsOutput(json, today)
        assertEquals(2, movements.size)
        assertEquals("MERCADONA", movements[0].title)
        assertEquals(2345L, movements[0].amountCents)
        assertTrue(movements[0].charge)
        assertEquals(day(2026, 11, 10), movements[0].date)
        assertFalse(movements[1].charge)
    }

    @Test
    fun `descarta importes cero o sin titulo`() {
        val json = """{"movements":[
            {"title":"A","amount":0,"charge":true},
            {"title":"  ","amount":5,"charge":true},
            {"title":"B","amount":5,"charge":true}]}"""
        assertEquals(listOf("B"), mapCardMovementsOutput(json, today).map { it.title })
    }

    @Test
    fun `fecha futura o ilegible queda null`() {
        val json = """{"movements":[
            {"title":"A","amount":1,"date":"2099-01-01","charge":true},
            {"title":"B","amount":1,"date":"10/11","charge":true}]}"""
        assertTrue(mapCardMovementsOutput(json, today).all { it.date == null })
    }

    @Test
    fun `JSON invalido lanza GeminiResponseException`() {
        assertThrows(GeminiResponseException::class.java) { mapCardMovementsOutput("no es json", today) }
        assertThrows(GeminiResponseException::class.java) { mapCardMovementsOutput("{}", today) }
    }

    @Test
    fun `el borrador quita los ingresos`() {
        val draft = cardImportDraft(
            listOf(charge("A", 100, day(2026, 11, 2)), CardMovement("B", 200, day(2026, 11, 3), charge = false)),
            month, emptyList(), "a", emptyList()
        )
        assertEquals(listOf("A"), draft.rows.map { it.title })
    }

    @Test
    fun `duplicado de un gasto existente`() {
        val existing = listOf(SplitExpense(id = "e1", amountCents = 2345, date = day(2026, 11, 10, 15)))
        val draft = cardImportDraft(
            listOf(charge("MERCADONA", 2345, day(2026, 11, 10)), charge("MERCADONA", 2345, day(2026, 11, 11))),
            month, existing, "a", emptyList()
        )
        assertEquals(CardRowFlag.DUPLICATE, draft.rows[0].flag)
        assertFalse(draft.rows[0].selected)
        assertEquals(CardRowFlag.NONE, draft.rows[1].flag)
        assertTrue(draft.rows[1].selected)
    }

    @Test
    fun `duplicado entre capturas`() {
        val draft = cardImportDraft(
            listOf(charge("Bar Pepe", 450, day(2026, 11, 5)), charge("bar pepe", 450, day(2026, 11, 5))),
            month, emptyList(), "a", emptyList()
        )
        assertEquals(CardRowFlag.NONE, draft.rows[0].flag)
        assertTrue(draft.rows[0].selected)
        assertEquals(CardRowFlag.DUPLICATE, draft.rows[1].flag)
        assertFalse(draft.rows[1].selected)
    }

    @Test
    fun `fuera del mes solo en grupos de tipo mes`() {
        val movements = listOf(charge("A", 100, day(2026, 10, 31)))
        val inMonth = cardImportDraft(movements, month, emptyList(), "a", emptyList()).rows.single()
        assertEquals(CardRowFlag.OUT_OF_MONTH, inMonth.flag)
        assertFalse(inMonth.selected)
        val inActivity = cardImportDraft(movements, month.copy(activity = true), emptyList(), "a", emptyList()).rows.single()
        assertEquals(CardRowFlag.NONE, inActivity.flag)
        assertTrue(inActivity.selected)
    }

    @Test
    fun `sin fecha`() {
        val row = cardImportDraft(listOf(charge("A", 100, null)), month, emptyList(), "a", emptyList()).rows.single()
        assertEquals(CardRowFlag.NO_DATE, row.flag)
        assertFalse(row.selected)
        assertFalse(row.isValid())
    }

    @Test
    fun `pagador por defecto`() {
        assertEquals("b", cardImportDraft(emptyList(), month, emptyList(), "b", emptyList()).payerId)
        assertEquals("a", cardImportDraft(emptyList(), month, emptyList(), "z", emptyList()).payerId)
    }

    @Test
    fun `amountText con coma`() {
        val row = cardImportDraft(listOf(charge("A", 2345, day(2026, 11, 2))), month, emptyList(), "a", emptyList()).rows.single()
        assertEquals("23,45", row.amountText)
    }

    @Test
    fun `toExpenses solo marcadas y validas`() {
        val date = day(2026, 11, 4).time
        val draft = CardImportDraft(
            monthId = "m1",
            payerId = "b",
            rows = listOf(
                CardImportRow(title = "  Gasolinera ", amountText = "40,10", dateMillis = date, selected = true),
                CardImportRow(title = "Personal", amountText = "5", dateMillis = date, selected = false),
                CardImportRow(title = "Sin fecha", amountText = "5", dateMillis = null, selected = true)
            )
        )
        val expense = draft.toExpenses(month).single()
        assertEquals("Gasolinera", expense.title)
        assertEquals(4010L, expense.amountCents)
        assertEquals("b", expense.payerId)
        assertEquals(listOf("a", "b"), expense.participants)
        assertEquals("", expense.emoji)
        assertFalse(expense.reimbursement)
        assertEquals(Date(date), expense.date)
    }

    @Test
    fun `json ida y vuelta`() {
        val draft = CardImportDraft(
            monthId = "m1", payerId = "a",
            rows = listOf(
                CardImportRow("A", "1,00", 1000L, true, CardRowFlag.NONE, id = "r1"),
                CardImportRow("B", "2,00", null, false, CardRowFlag.NO_DATE, id = "r2")
            ),
            imageUris = listOf("content://x/1")
        )
        assertEquals(draft, cardImportDraftFromJson(cardImportDraftToJson(draft)))
        assertNull(cardImportDraftFromJson("{"))
        val empty = cardImportDraftFromJson("{}")
        assertNotNull(empty)
        assertEquals(emptyList<CardImportRow>(), empty!!.rows)
        assertEquals(emptyList<String>(), empty.imageUris)
        assertEquals("", empty.monthId)
    }

    @Test
    fun `cada fila del borrador tiene un id propio`() {
        val rows = cardImportDraft(
            listOf(charge("A", 100, day(2026, 11, 2)), charge("B", 200, day(2026, 11, 3))),
            month, emptyList(), "a", emptyList()
        ).rows
        assertTrue(rows.all { it.id.isNotBlank() })
        assertEquals(2, rows.map { it.id }.toSet().size)
    }

    @Test
    fun `toExpenses usa el id de la fila para que reintentar no duplique`() {
        val draft = CardImportDraft(
            monthId = "m1", payerId = "a",
            rows = listOf(CardImportRow("A", "1,00", day(2026, 11, 4).time, true, id = "fila-1"))
        )
        assertEquals("fila-1", draft.toExpenses(month).single().id)
        assertEquals(draft.toExpenses(month), draft.toExpenses(month))
    }

    @Test
    fun `un borrador sin ids recibe uno estable al leerlo`() {
        val json = """{"monthId":"m1","payerId":"a","rows":[{"title":"A","amountText":"1,00","dateMillis":1000,"selected":true,"flag":"NONE"}]}"""
        val row = cardImportDraftFromJson(json)!!.rows.single()
        assertTrue(row.id.isNotBlank())
    }
}
