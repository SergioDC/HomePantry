package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class SplitLogicTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")

    private fun e(payer: String, cents: Long, vararg who: String, reimbursement: Boolean = false) =
        SplitExpense(
            title = "x", amountCents = cents, payerId = payer,
            participants = who.toList(), reimbursement = reimbursement
        )

    private fun net(balances: List<MemberBalance>, id: String) =
        balances.first { it.memberId == id }.netCents

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Date =
        Calendar.getInstance(madrid).apply { clear(); set(y, m - 1, d, h, min) }.time

    private val scenario = listOf(
        e("a", 1001, "a", "b", "c"),
        e("b", 2599, "a", "b"),
        e("c", 333, "a", "b", "c"),
        e("a", 7, "b", "c"),
        e("b", 1500, "a", reimbursement = true)
    )

    // --- splitEqually ---

    @Test fun `splitEqually gives the extra cents to the first ids in alphabetical order`() {
        val r = splitEqually(100, listOf("a", "b", "c"))
        assertEquals(mapOf("a" to 34L, "b" to 33L, "c" to 33L), r)
        assertEquals(100L, r.values.sum())
    }

    @Test fun `splitEqually does not depend on the list order`() {
        assertEquals(splitEqually(100, listOf("a", "b", "c")), splitEqually(100, listOf("c", "a", "b")))
    }

    @Test fun `splitEqually one cent between two`() {
        assertEquals(mapOf("a" to 1L, "b" to 0L), splitEqually(1, listOf("b", "a")))
    }

    @Test fun `splitEqually ignores duplicates and handles empty`() {
        assertEquals(mapOf("a" to 50L, "b" to 50L), splitEqually(100, listOf("a", "a", "b")))
        assertTrue(splitEqually(100, emptyList()).isEmpty())
    }

    // --- computeBalances ---

    @Test fun `balances of one expense among everyone`() {
        val b = computeBalances(listOf(e("a", 3000, "a", "b", "c")), listOf("a", "b", "c"))
        assertEquals(2000L, net(b, "a"))
        assertEquals(-1000L, net(b, "b"))
        assertEquals(-1000L, net(b, "c"))
    }

    @Test fun `balances always add up to zero`() {
        val b = computeBalances(scenario, listOf("a", "b", "c"))
        assertEquals(0L, b.sumOf { it.netCents })
    }

    @Test fun `partial participants leave the rest untouched`() {
        val b = computeBalances(listOf(e("a", 1000, "b")), listOf("a", "b", "c"))
        assertEquals(1000L, net(b, "a"))
        assertEquals(-1000L, net(b, "b"))
        assertEquals(0L, net(b, "c"))
    }

    @Test fun `a reimbursement reduces the debt without being a new expense`() {
        val b = computeBalances(
            listOf(e("a", 3000, "a", "b", "c"), e("b", 1000, "a", reimbursement = true)),
            listOf("a", "b", "c")
        )
        assertEquals(1000L, net(b, "a"))
        assertEquals(0L, net(b, "b"))
        assertEquals(-1000L, net(b, "c"))
    }

    @Test fun `expenses without participants or amount are ignored`() {
        val b = computeBalances(listOf(e("a", 1000), e("a", 0, "a", "b")), listOf("a", "b"))
        assertTrue(b.all { it.paidCents == 0L && it.shareCents == 0L })
    }

    @Test fun `ids that are no longer members still show up and keep the sum at zero`() {
        val b = computeBalances(listOf(e("old", 900, "old", "a", "b")), listOf("a", "b"))
        assertEquals(3, b.size)
        assertEquals(0L, b.sumOf { it.netCents })
    }

    @Test fun `no expenses gives zero for every member`() {
        val b = computeBalances(emptyList(), listOf("a", "b"))
        assertEquals(2, b.size)
        assertTrue(b.all { it.netCents == 0L })
    }

    @Test fun `balances are sorted by net descending`() {
        val b = computeBalances(listOf(e("b", 3000, "a", "b", "c")), listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), b.map { it.memberId })
    }

    // --- computeSettlements ---

    @Test fun `settlements pay the creditor`() {
        val b = computeBalances(listOf(e("a", 3000, "a", "b", "c")), listOf("a", "b", "c"))
        assertEquals(
            listOf(Settlement("b", "a", 1000), Settlement("c", "a", 1000)),
            computeSettlements(b).sortedBy { it.fromId }
        )
    }

    @Test fun `one debtor pays two creditors`() {
        val b = listOf(
            MemberBalance("a", 3000, 0), MemberBalance("b", 1000, 0), MemberBalance("c", 0, 4000)
        )
        assertEquals(
            listOf(Settlement("c", "a", 3000), Settlement("c", "b", 1000)),
            computeSettlements(b)
        )
    }

    @Test fun `no settlements when everything is even`() {
        assertTrue(computeSettlements(computeBalances(emptyList(), listOf("a", "b"))).isEmpty())
    }

    @Test fun `applying the settlements leaves everyone at zero with at most n-1 payments`() {
        val members = listOf("a", "b", "c", "d")
        val expenses = scenario + e("d", 4321, "a", "b", "c", "d")
        val balances = computeBalances(expenses, members)
        val settlements = computeSettlements(balances)
        val nets = balances.associate { it.memberId to it.netCents }.toMutableMap()
        settlements.forEach {
            nets[it.fromId] = nets.getValue(it.fromId) + it.amountCents
            nets[it.toId] = nets.getValue(it.toId) - it.amountCents
        }
        assertTrue(nets.values.all { it == 0L })
        assertTrue(settlements.size <= members.size - 1)
        assertTrue(settlements.all { it.amountCents > 0 })
    }

    // --- monthTotalCents ---

    @Test fun `month total excludes reimbursements`() {
        assertEquals(1001L + 2599 + 333 + 7, monthTotalCents(scenario))
        assertEquals(0L, monthTotalCents(emptyList()))
    }

    // --- monthKeyOf / isDateInMonth ---

    @Test fun `monthKeyOf uses the given time zone`() {
        assertEquals("2026-10", monthKeyOf(at(2026, 10, 31, 23, 30), madrid))
        // 00:30 del día 1 en Madrid son las 23:30 del día anterior en UTC.
        assertEquals("2026-11", monthKeyOf(at(2026, 11, 1, 0, 30), madrid))
    }

    @Test fun `isDateInMonth compares against the key`() {
        assertTrue(isDateInMonth(at(2026, 10, 15, 12, 0), "2026-10", madrid))
        assertFalse(isDateInMonth(at(2026, 11, 1, 0, 30), "2026-10", madrid))
    }

    // --- parseCents ---

    @Test fun `parseCents accepts comma and dot decimals`() {
        assertEquals(1250L, parseCents("12,5"))
        assertEquals(1250L, parseCents("12.50"))
        assertEquals(1200L, parseCents("12"))
        assertEquals(1L, parseCents("0,01"))
        assertEquals(123456L, parseCents("1234,56"))
        assertEquals(700L, parseCents(" 7 "))
    }

    @Test fun `parseCents rejects what is not a positive amount`() {
        assertNull(parseCents(""))
        assertNull(parseCents("abc"))
        assertNull(parseCents("-5"))
        assertNull(parseCents("0"))
        assertNull(parseCents("0,00"))
        assertNull(parseCents("1,234"))
        assertNull(parseCents("1.234,56"))
        assertNull(parseCents("12,5,3"))
        assertNull(parseCents("5,"))
    }

    // --- validateSplitExpense ---

    private val october = SplitMonth(id = "m", name = "Octubre 2026", monthKey = "2026-10", memberIds = listOf("a", "b"))

    private fun validate(
        title: String = "Cena",
        amount: String = "12,50",
        payer: String = "a",
        who: List<String> = listOf("a", "b"),
        date: Date? = at(2026, 10, 15, 12, 0),
        reimbursement: Boolean = false
    ) = validateSplitExpense(title, amount, payer, who, date, reimbursement, october, madrid)

    @Test fun `a complete expense inside the month is valid`() {
        assertTrue(validate().isEmpty())
    }

    @Test fun `each invalid field is reported`() {
        assertEquals(setOf(SplitExpenseError.TITLE), validate(title = "  "))
        assertEquals(setOf(SplitExpenseError.AMOUNT), validate(amount = "0"))
        assertEquals(setOf(SplitExpenseError.PARTICIPANTS), validate(who = emptyList()))
        assertEquals(setOf(SplitExpenseError.PAYER), validate(payer = "z"))
        assertEquals(setOf(SplitExpenseError.DATE), validate(date = at(2026, 11, 1, 0, 30)))
        assertEquals(setOf(SplitExpenseError.DATE), validate(date = null))
    }

    @Test fun `several errors are reported together`() {
        assertEquals(
            setOf(SplitExpenseError.TITLE, SplitExpenseError.AMOUNT),
            validate(title = "", amount = "")
        )
    }

    @Test fun `a reimbursement may fall outside the month`() {
        assertTrue(validate(date = at(2026, 11, 3, 10, 0), reimbursement = true).isEmpty())
    }

    @Test fun `an activity accepts dates from any month`() {
        val trip = october.copy(activity = true)
        val errors = validateSplitExpense("Hotel", "80", "a", listOf("a", "b"), at(2026, 12, 2, 10, 0), false, trip, madrid)
        assertTrue(errors.isEmpty())
        val noDate = validateSplitExpense("Hotel", "80", "a", listOf("a", "b"), null, false, trip, madrid)
        assertEquals(setOf(SplitExpenseError.DATE), noDate)
    }

    // --- myShareCents ---

    @Test fun `my expenses are my shares without reimbursements`() {
        // a: 1001/3 -> 334, 2599/2 -> 1300 (sobrante a "a"), 333/3 -> 111; el reembolso no cuenta.
        assertEquals(334L + 1300 + 111, myShareCents(scenario, "a"))
        assertEquals(0L, myShareCents(scenario, "z"))
        assertEquals(0L, myShareCents(emptyList(), "a"))
    }

    // --- expensesByDay ---

    @Test fun `expenses are grouped by local day keeping their order`() {
        val late = e("a", 100, "a").copy(id = "1", date = at(2026, 10, 16, 23, 30))
        val early = e("a", 200, "a").copy(id = "2", date = at(2026, 10, 16, 0, 15))
        val before = e("a", 300, "a").copy(id = "3", date = at(2026, 10, 15, 12, 0))
        val noDate = e("a", 400, "a").copy(id = "4", date = null)
        val groups = expensesByDay(listOf(late, early, before, noDate), madrid)
        assertEquals(listOf("2026-10-16", "2026-10-15", ""), groups.map { it.first })
        assertEquals(listOf("1", "2"), groups[0].second.map { it.id })
        assertEquals(listOf("4"), groups[2].second.map { it.id })
    }

    @Test fun `parseCents has an upper bound`() {
        assertEquals(99_999_999L, parseCents("999999,99"))
        assertNull(parseCents("1000000"))
        assertNull(parseCents("99999999999999999999"))
    }

    // --- reparto por importes ---

    @Test fun `expenseShares falls back to equal split`() {
        assertEquals(splitEqually(1001, listOf("a", "b", "c")), expenseShares(e("a", 1001, "a", "b", "c")))
    }

    @Test fun `expenseShares uses explicit shares when present`() {
        val x = e("a", 6000, "a", "b").copy(shares = mapOf("a" to 4000L, "b" to 2000L))
        assertEquals(mapOf("a" to 4000L, "b" to 2000L), expenseShares(x))
    }

    @Test fun `balances are unchanged for expenses without shares`() {
        // Sin `shares` debe dar lo mismo que con `shares` igual a splitEqually: el reparto de siempre.
        val explicit = scenario.map { it.copy(shares = splitEqually(it.amountCents, it.participants)) }
        val members = listOf("a", "b", "c")
        assertEquals(computeBalances(explicit, members), computeBalances(scenario, members))
        assertEquals(0L, computeBalances(scenario, members).sumOf { it.netCents })
    }

    @Test fun `balances with mixed equal and explicit shares sum to zero`() {
        val members = listOf("a", "b", "c")
        val mixed = scenario + e("c", 6000, "a", "b", "c").copy(shares = mapOf("a" to 1000L, "b" to 2000L, "c" to 3000L))
        val balances = computeBalances(mixed, members)
        assertEquals(0L, balances.sumOf { it.netCents })
        val before = computeBalances(scenario, members)
        assertEquals(net(before, "a") - 1000, net(balances, "a"))
        assertEquals(net(before, "c") + 3000, net(balances, "c"))
    }

    @Test fun `myShareCents uses explicit shares`() {
        val x = e("a", 6000, "a", "b").copy(shares = mapOf("a" to 4500L, "b" to 1500L))
        assertEquals(1500L, myShareCents(listOf(x), "b"))
    }

    @Test fun `blank or zero share means not participating`() {
        assertEquals(mapOf("a" to 2500L), parseShares(mapOf("a" to "25", "b" to "", "c" to "0")))
        assertEquals(0L, parseShareCents("  "))
        assertEquals(0L, parseShareCents("0,00"))
        assertNull(parseShareCents("-3"))
        assertNull(parseShares(mapOf("a" to "abc")))
    }

    @Test fun `sharesRemainderCents reports missing and extra`() {
        assertEquals(320L, sharesRemainderCents(6000, mapOf("a" to 2500L, "b" to 1800L, "c" to 1380L)))
        assertEquals(-100L, sharesRemainderCents(6000, mapOf("a" to 6100L)))
    }

    @Test fun `shares that no longer match the total are rejected`() {
        val day = at(2026, 10, 5, 12, 0)
        val ok = validateSplitExpense(
            "Cena", "60", "a", emptyList(), day, false, october, madrid,
            shareTexts = mapOf("a" to "40", "b" to "20")
        )
        assertTrue(ok.isEmpty())
        val changedTotal = validateSplitExpense(
            "Cena", "65", "a", emptyList(), day, false, october, madrid,
            shareTexts = mapOf("a" to "40", "b" to "20")
        )
        assertEquals(setOf(SplitExpenseError.SHARES), changedTotal)
    }

    @Test fun `manual mode needs at least one positive share`() {
        val errors = validateSplitExpense(
            "Cena", "60", "a", emptyList(), at(2026, 10, 5, 12, 0), false, october, madrid,
            shareTexts = mapOf("a" to "", "b" to "0")
        )
        assertTrue(SplitExpenseError.PARTICIPANTS in errors)
    }
}
