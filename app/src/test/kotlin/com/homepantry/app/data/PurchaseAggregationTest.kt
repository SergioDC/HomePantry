package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PurchaseAggregationTest {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun date(s: String) = format.parse(s)!!

    @Test fun `monthlySpend groups purchases by year-month regardless of product`() {
        val purchases = listOf(
            Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.5, date = date("2026-03-05")),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-20")),
            Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.6, date = date("2026-04-01"))
        )
        assertEquals(
            listOf(MonthlySpend("2026-04", 1.6), MonthlySpend("2026-03", 2.4)),
            monthlySpend(purchases)
        )
    }

    @Test fun `productSummaries groups by normalizedName and sums current-month and all-time totals`() {
        val purchases = listOf(
            Purchase(rawName = "Tomates", normalizedName = "tomate", price = 1.5, date = date("2026-03-05")),
            Purchase(rawName = "TOMATE RAMA", normalizedName = "tomate", price = 1.7, date = date("2026-03-20")),
            Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.2, date = date("2026-02-01")),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-10"))
        )
        val summaries = productSummaries(purchases, referenceDate = date("2026-03-25"))

        val tomate = summaries.first { it.normalizedName == "tomate" }
        assertEquals("TOMATE RAMA", tomate.displayName)
        assertEquals(3.2, tomate.currentMonthTotal, 0.001)
        assertEquals(4.4, tomate.allTimeTotal, 0.001)
        assertEquals(3, tomate.purchases.size)

        val leche = summaries.first { it.normalizedName == "leche" }
        assertEquals(0.9, leche.currentMonthTotal, 0.001)
        assertEquals(0.9, leche.allTimeTotal, 0.001)
    }

    @Test fun `productSummaries is sorted by all-time total descending`() {
        val purchases = listOf(
            Purchase(rawName = "Barato", normalizedName = "barato", price = 1.0, date = date("2026-03-01")),
            Purchase(rawName = "Caro", normalizedName = "caro", price = 50.0, date = date("2026-03-01"))
        )
        val summaries = productSummaries(purchases, referenceDate = date("2026-03-25"))
        assertEquals(listOf("caro", "barato"), summaries.map { it.normalizedName })
    }
}
