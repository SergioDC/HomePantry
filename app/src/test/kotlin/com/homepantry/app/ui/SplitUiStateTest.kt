package com.homepantry.app.ui

import com.homepantry.app.data.Member
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.computeBalances
import com.homepantry.app.data.computeSettlements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitUiStateTest {
    private val month = SplitMonth(id = "m1", name = "Octubre 2026", monthKey = "2026-10", memberIds = listOf("a", "b", "c"))
    private val expenses = listOf(
        SplitExpense(id = "1", amountCents = 3000, payerId = "a", participants = listOf("a", "b", "c")),
        SplitExpense(id = "2", amountCents = 1001, payerId = "b", participants = listOf("a", "b")),
        SplitExpense(id = "3", amountCents = 500, payerId = "c", participants = listOf("a"), reimbursement = true)
    )
    private val state = SplitUiState(
        months = listOf(month),
        members = listOf(Member(id = "a", name = "Ana"), Member(id = "b", name = "Bea")),
        openMonthId = "m1",
        expenses = expenses
    )

    @Test fun `derived values come from the open month and SplitLogic`() {
        assertEquals(month, state.openMonth)
        val balances = computeBalances(expenses, month.memberIds)
        assertEquals(balances, state.balances)
        assertEquals(computeSettlements(balances), state.settlements)
        assertEquals(4001L, state.monthTotalCents)
    }

    @Test fun `member names fall back to null for unknown ids`() {
        assertEquals("Ana", state.memberName("a"))
        assertNull(state.memberName("c"))
    }

    @Test fun `without an open month there is nothing to show`() {
        val closed = state.copy(openMonthId = null, expenses = emptyList())
        assertNull(closed.openMonth)
        assertTrue(closed.balances.isEmpty())
        assertTrue(closed.settlements.isEmpty())
        assertEquals(0L, closed.monthTotalCents)
    }

    @Test fun `active and archived months keep order`() {
        val m1 = SplitMonth(id = "m1")
        val m2 = SplitMonth(id = "m2", archived = true)
        val m3 = SplitMonth(id = "m3")
        val s = SplitUiState(months = listOf(m1, m2, m3))
        assertEquals(listOf(m1, m3), s.activeMonths)
        assertEquals(listOf(m2), s.archivedMonths)
    }
}
