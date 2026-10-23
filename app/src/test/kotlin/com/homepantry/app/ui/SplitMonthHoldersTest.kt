package com.homepantry.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitMonthHoldersTest {

    @Test fun `month stays open while a screen above holds it`() {
        val h = SplitMonthHolders()
        assertEquals("m1", h.retain("monthEntry", "m1"))
        assertEquals("m1", h.retain("expenseEntry", "m1"))
        assertEquals("m1", h.release("expenseEntry"))
        assertEquals("m1", h.retain("monthEntry", "m1")) // volver a componer no cuenta doble
        assertNull(h.release("monthEntry"))
        assertNull(h.openMonthId)
    }

    @Test fun `releasing unknown or twice is harmless`() {
        val h = SplitMonthHolders()
        h.retain("a", "m1")
        assertEquals("m1", h.release("zzz"))
        assertNull(h.release("a"))
        assertNull(h.release("a"))
    }

    @Test fun `latest live holder decides the open month`() {
        val h = SplitMonthHolders()
        h.retain("list-edit", "m1")
        assertEquals("m2", h.retain("other", "m2"))
        assertEquals("m1", h.release("other"))
    }

    @Test fun `holds tells whether a screen is already registered`() {
        val h = SplitMonthHolders()
        assertFalse(h.holds("a"))
        h.retain("a", "m1")
        assertTrue(h.holds("a"))
        h.release("a")
        assertFalse(h.holds("a"))
    }
}
