package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ComparablePriceTest {
    private fun p(
        price: Double,
        qty: Double = 1.0,
        unit: String = "UD",
        pack: Double? = null,
        packUnit: String? = null,
        raw: String = "ARANDANO 300GR",
        product: String = ""
    ) = Purchase(
        rawName = raw, productName = product, price = price, quantity = qty, unit = unit,
        packSize = pack, packUnit = packUnit
    )

    @Test fun `pack gives price per kilo`() {
        assertEquals(Measure(0.3, "KG"), p(2.5, pack = 0.3, packUnit = "KG").measure())
        assertEquals(8.333, p(2.5, pack = 0.3, packUnit = "KG").comparablePrice(), 0.001)
    }

    @Test fun `two packs`() =
        assertEquals(5.0 / 1.2, p(5.0, qty = 2.0, pack = 0.6, packUnit = "KG").comparablePrice(), 0.001)

    @Test fun `weighed line ignores pack`() =
        assertEquals(Measure(0.85, "KG"), p(3.39, qty = 0.85, unit = "KG", pack = 0.3, packUnit = "KG").measure())

    @Test fun `no pack is per unit`() = assertEquals(Measure(1.0, "UD"), p(1.2).measure())

    @Test fun `old purchase displays raw name`() {
        assertEquals("ARANDANO 300GR", p(1.0).displayName())
        assertEquals("Arándanos", p(1.0, product = "Arándanos").displayName())
    }

    @Test fun `product key falls back to raw name`() {
        assertEquals("arandano", productKey("Arándanos", "ARAND. CULTIVADO"))
        assertEquals("arand. cultivado", productKey("", "ARAND. CULTIVADO"))
    }
}
