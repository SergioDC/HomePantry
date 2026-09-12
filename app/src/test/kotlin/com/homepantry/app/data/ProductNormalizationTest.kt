package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductNormalizationTest {
    @Test fun `lowercases and trims`() {
        assertEquals("tomate", normalizeProductName("  Tomate  "))
    }

    @Test fun `strips accents`() {
        assertEquals("platano", normalizeProductName("Plátano"))
    }

    @Test fun `collapses internal whitespace`() {
        assertEquals("aceite oliva", normalizeProductName("Aceite   Oliva"))
    }

    @Test fun `strips a simple trailing plural s when longer than 3 chars`() {
        assertEquals("tomate", normalizeProductName("Tomates"))
        assertEquals("tomate", normalizeProductName("TOMATE"))
    }

    @Test fun `does not strip trailing s from short words`() {
        assertEquals("gas", normalizeProductName("Gas"))
    }
}
