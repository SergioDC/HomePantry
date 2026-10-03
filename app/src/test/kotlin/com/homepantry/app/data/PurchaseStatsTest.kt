package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PurchaseStatsTest {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun date(s: String) = format.parse(s)!!
    private val ref = date("2026-03-25")

    private fun p(
        name: String,
        price: Double,
        day: String,
        store: String? = null,
        ticket: String? = null,
        qty: Double = 1.0,
        unit: String = "UD"
    ) = Purchase(
        rawName = name, normalizedName = name.lowercase(), price = price, date = date(day),
        store = store, ticketId = ticket, quantity = qty, unit = unit
    )

    @Test fun `empty purchases give zeros and empty lists`() {
        val none = emptyList<Purchase>()
        assertEquals(MonthSummary(0.0, null, 0, 0.0), monthSummary(none, ref))
        assertEquals(6, lastMonthsSpend(none, 6, ref).size)
        assertTrue(lastMonthsSpend(none, 6, ref).all { it.total == 0.0 })
        assertTrue(spendByStore(none, ref).isEmpty())
        assertTrue(topProducts(none, TopBy.SPEND).isEmpty())
        assertTrue(priceRises(none).isEmpty())
        assertTrue(cheapestStores(none).isEmpty())
    }

    @Test fun `monthSummary totals the month, counts tickets and averages`() {
        val purchases = listOf(
            p("a", 10.0, "2026-03-02", ticket = "t1"),
            p("b", 5.0, "2026-03-02", ticket = "t1"),
            p("c", 20.0, "2026-03-10", ticket = "t2"),
            p("d", 30.0, "2026-02-15", ticket = "t3")
        )
        val s = monthSummary(purchases, ref)
        assertEquals(35.0, s.total, 0.001)
        assertEquals(30.0, s.previousTotal!!, 0.001)
        assertEquals(2, s.ticketCount)
        assertEquals(17.5, s.averagePerTicket, 0.001)
    }

    @Test fun `monthSummary previousTotal is null without previous month data`() {
        val s = monthSummary(listOf(p("a", 10.0, "2026-03-02", ticket = "t1")), ref)
        assertNull(s.previousTotal)
    }

    @Test fun `monthSummary handles the year boundary`() {
        val s = monthSummary(listOf(p("a", 8.0, "2025-12-20", ticket = "t1")), date("2026-01-05"))
        assertEquals(8.0, s.previousTotal!!, 0.001)
    }

    @Test fun `lastMonthsSpend is chronological and fills gaps with zero`() {
        val purchases = listOf(
            p("a", 10.0, "2026-03-02"),
            p("b", 4.0, "2026-01-10")
        )
        val result = lastMonthsSpend(purchases, 3, ref)
        assertEquals(
            listOf(MonthlySpend("2026-01", 4.0), MonthlySpend("2026-02", 0.0), MonthlySpend("2026-03", 10.0)),
            result
        )
    }

    @Test fun `spendByStore groups current month, sorts by spend and keeps no-store apart`() {
        val purchases = listOf(
            p("a", 30.0, "2026-03-02", store = "Mercadona"),
            p("b", 10.0, "2026-03-03", store = " mercadona "),
            p("c", 10.0, "2026-03-04"),
            p("d", 99.0, "2026-02-04", store = "Lidl")
        )
        val result = spendByStore(purchases, ref)
        assertEquals(2, result.size)
        assertEquals("mercadona", result[0].storeKey)
        assertEquals(40.0, result[0].total, 0.001)
        assertEquals(0.8, result[0].fraction, 0.001)
        assertEquals("", result[1].storeKey)
        assertEquals("", result[1].displayName)
    }

    @Test fun `topProducts by spend and by count`() {
        val purchases = listOf(
            p("Leche", 1.0, "2026-03-01", ticket = "t1"),
            p("Leche", 1.0, "2026-03-08", ticket = "t2"),
            p("Leche", 1.0, "2026-03-15", ticket = "t3"),
            p("Jamon", 12.0, "2026-03-01", ticket = "t1")
        )
        assertEquals("jamon", topProducts(purchases, TopBy.SPEND).first().normalizedName)
        val byCount = topProducts(purchases, TopBy.COUNT).first()
        assertEquals("leche", byCount.normalizedName)
        assertEquals(3, byCount.count)
    }

    @Test fun `topProducts counts a product once per ticket`() {
        val purchases = listOf(
            p("Leche", 1.0, "2026-03-01", ticket = "t1"),
            p("Leche", 1.0, "2026-03-01", ticket = "t1")
        )
        assertEquals(1, topProducts(purchases, TopBy.COUNT).first().count)
    }

    @Test fun `topProducts respects the limit`() {
        val purchases = (1..8).map { p("prod$it", it.toDouble(), "2026-03-01", ticket = "t$it") }
        assertEquals(5, topProducts(purchases, TopBy.SPEND).size)
    }

    @Test fun `priceRises lists products whose unit price went up`() {
        val purchases = listOf(
            p("Aceite", 4.0, "2026-01-01", unit = "L"),
            p("Aceite", 5.0, "2026-03-01", unit = "L"),
            p("Leche", 1.0, "2026-01-01", unit = "L"),
            p("Leche", 0.9, "2026-03-01", unit = "L")
        )
        val rises = priceRises(purchases)
        assertEquals(1, rises.size)
        assertEquals("aceite", rises[0].normalizedName)
        assertEquals(0.25, rises[0].changeFraction, 0.001)
    }

    @Test fun `priceRises ignores products bought once and mixed units`() {
        val purchases = listOf(
            p("Queso", 3.0, "2026-01-01", unit = "UD"),
            p("Queso", 9.0, "2026-03-01", unit = "KG"),
            p("Pan", 1.0, "2026-03-01")
        )
        assertTrue(priceRises(purchases).isEmpty())
    }

    @Test fun `cheapestStores compares average unit price between stores`() {
        val purchases = listOf(
            p("Arroz", 1.0, "2026-03-01", store = "Lidl", unit = "KG"),
            p("Arroz", 1.5, "2026-03-02", store = "Mercadona", unit = "KG")
        )
        val c = cheapestStores(purchases).single()
        assertEquals("Lidl", c.cheapestStore)
        assertEquals(1.0, c.cheapestUnitPrice, 0.001)
        assertEquals(1.5, c.dearestUnitPrice, 0.001)
        assertEquals(1.0 / 3.0, c.savingFraction, 0.001)
    }

    @Test fun `cheapestStores needs two stores with the same unit`() {
        val oneStore = listOf(
            p("Arroz", 1.0, "2026-03-01", store = "Lidl", unit = "KG"),
            p("Arroz", 1.2, "2026-03-09", store = "lidl", unit = "KG")
        )
        val mixedUnits = listOf(
            p("Queso", 1.0, "2026-03-01", store = "Lidl", unit = "KG"),
            p("Queso", 2.0, "2026-03-01", store = "Dia", unit = "UD")
        )
        val noStore = listOf(p("Pan", 1.0, "2026-03-01"), p("Pan", 2.0, "2026-03-02", store = "Dia"))
        assertTrue(cheapestStores(oneStore).isEmpty())
        assertTrue(cheapestStores(mixedUnits).isEmpty())
        assertTrue(cheapestStores(noStore).isEmpty())
    }
}
