package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    @Test fun `blank input defaults to 1`() {
        assertEquals(1.0, parseQtyOrDefault("")!!, 0.0001)
        assertEquals(1.0, parseQtyOrDefault("   ")!!, 0.0001)
    }

    @Test fun `valid positive number is parsed`() {
        assertEquals(2.5, parseQtyOrDefault("2.5")!!, 0.0001)
        assertEquals(3.0, parseQtyOrDefault("3")!!, 0.0001)
    }

    @Test fun `zero, negative or non-numeric input is invalid`() {
        assertNull(parseQtyOrDefault("0"))
        assertNull(parseQtyOrDefault("-1"))
        assertNull(parseQtyOrDefault("abc"))
    }
}
