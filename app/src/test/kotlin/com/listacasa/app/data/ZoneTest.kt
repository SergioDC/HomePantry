package com.listacasa.app.data

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
}
