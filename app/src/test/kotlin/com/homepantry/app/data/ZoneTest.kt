package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneTest {
    @Test fun `Otros is protected regardless of case`() {
        assertTrue(isProtectedZone(Zone(id = "z1", name = "Otros")))
        assertTrue(isProtectedZone(Zone(id = "z2", name = "otros")))
    }

    @Test fun `any other zone name is not protected`() {
        assertFalse(isProtectedZone(Zone(id = "z3", name = "Nevera")))
    }

    @Test fun `zoneIconLetter returns the uppercased first letter of the zone name`() {
        assertEquals("N", zoneIconLetter(Zone(id = "z1", name = "Nevera")))
    }

    @Test fun `zoneIconLetter uppercases even when the name starts lowercase`() {
        assertEquals("C", zoneIconLetter(Zone(id = "z2", name = "congelador")))
    }

    @Test fun `zoneIconLetter falls back to a question mark for a blank name`() {
        assertEquals("?", zoneIconLetter(Zone(id = "z3", name = "")))
    }
}
