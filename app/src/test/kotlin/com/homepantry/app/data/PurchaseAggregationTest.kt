package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PurchaseAggregationTest {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun date(s: String) = format.parse(s)!!

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private fun at(s: String) = timeFormat.parse(s)!!

    private fun ticketLine(id: String, store: String, total: Double, time: String) =
        Purchase(rawName = "X", price = total, date = at(time), store = store, ticketId = id)

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

    @Test fun `unitPrice divides the line total by the quantity`() {
        assertEquals(1.25, Purchase(price = 2.5, quantity = 2.0).unitPrice(), 0.001)
        // 0,850 kg por 3,39 EUR -> 3,99 EUR/kg (redondeo del ticket)
        assertEquals(3.99, Purchase(price = 3.39, quantity = 0.85, unit = "KG").unitPrice(), 0.01)
    }

    @Test fun `unitPrice treats a non-positive quantity as one`() {
        assertEquals(2.0, Purchase(price = 2.0, quantity = 0.0).unitPrice(), 0.001)
        assertEquals(2.0, Purchase(price = 2.0, quantity = -3.0).unitPrice(), 0.001)
    }

    @Test fun `an old purchase without the new fields has unit price equal to its total`() {
        val old = Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.5)
        assertEquals(1.0, old.quantity, 0.001)
        assertEquals("UD", old.unit)
        assertEquals(null, old.store)
        assertEquals(1.5, old.unitPrice(), 0.001)
    }

    @Test fun `productSummaries exposes the unit price and unit of the latest purchase`() {
        val purchases = listOf(
            Purchase(rawName = "Leche", normalizedName = "leche", price = 2.0, quantity = 2.0, date = date("2026-03-01")),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 1.1, quantity = 1.0, date = date("2026-03-20"))
        )
        val leche = productSummaries(purchases, referenceDate = date("2026-03-25")).single()
        assertEquals(1.1, leche.latestUnitPrice, 0.001)
        assertEquals("UD", leche.latestUnit)
    }

    @Test fun `storeKey ignores case and surrounding spaces and maps null to empty`() {
        assertEquals("mercadona", storeKey("  MERCADONA "))
        assertEquals("", storeKey(null))
        assertEquals("", storeKey("   "))
    }

    @Test fun `storeSections groups by normalized store, newest store first, and puts no-store last`() {
        val purchases = listOf(
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-01"), store = "Lidl"),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 1.0, date = date("2026-03-10"), store = "MERCADONA"),
            Purchase(rawName = "Pan", normalizedName = "pan", price = 1.2, date = date("2026-03-12"), store = " Mercadona "),
            Purchase(rawName = "Viejo", normalizedName = "viejo", price = 2.0, date = date("2026-04-01"))
        )
        val sections = storeSections(purchases, referenceDate = date("2026-04-02"))

        assertEquals(listOf("mercadona", "lidl", ""), sections.map { it.storeKey })
        // La grafía mostrada es la de la compra más reciente del grupo, recortada.
        assertEquals("Mercadona", sections[0].displayName)
        assertEquals("", sections[2].displayName)
        // Dentro del súper, el producto comprado más recientemente primero.
        assertEquals(listOf("pan", "leche"), sections[0].products.map { it.normalizedName })
    }

    @Test fun `a product bought in two stores appears in both with its own unit price`() {
        val purchases = listOf(
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-01"), store = "Lidl"),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 1.0, date = date("2026-03-10"), store = "Mercadona")
        )
        val sections = storeSections(purchases, referenceDate = date("2026-03-25"))

        assertEquals(1.0, sections.first { it.storeKey == "mercadona" }.products.single().latestUnitPrice, 0.001)
        assertEquals(0.9, sections.first { it.storeKey == "lidl" }.products.single().latestUnitPrice, 0.001)
    }

    @Test fun `storeSections is empty when there are no purchases`() {
        assertEquals(emptyList<StoreSection>(), storeSections(emptyList()))
    }

    @Test fun `knownStores lists each store once, most recent first, ignoring blanks`() {
        val purchases = listOf(
            Purchase(price = 1.0, date = date("2026-03-01"), store = "Lidl"),
            Purchase(price = 1.0, date = date("2026-03-10"), store = "MERCADONA"),
            Purchase(price = 1.0, date = date("2026-03-12"), store = "Mercadona"),
            Purchase(price = 1.0, date = date("2026-03-13"), store = "  "),
            Purchase(price = 1.0, date = date("2026-03-14"))
        )
        assertEquals(listOf("Mercadona", "Lidl"), knownStores(purchases))
    }

    @Test fun `unitSuffix maps the receipt units and falls back to ud`() {
        assertEquals("kg", unitSuffix("KG"))
        assertEquals("l", unitSuffix("L"))
        assertEquals("ud", unitSuffix("UD"))
        assertEquals("ud", unitSuffix("G"))
    }

    @Test fun `formatQuantity drops the decimals of whole numbers and trims trailing zeros`() {
        assertEquals("2", formatQuantity(2.0))
        assertEquals("0,85", formatQuantity(0.85))
        assertEquals("1,5", formatQuantity(1.5))
    }

    @Test fun `ticketKey uses the ticketId, or store plus exact scan time for old purchases`() {
        val scanned = at("2026-03-12 10:00")
        assertEquals("t1", ticketKey(Purchase(ticketId = "t1", store = "Lidl", date = scanned)))
        assertEquals("legacy:mercadona:${scanned.time}", ticketKey(Purchase(store = " Mercadona ", date = scanned)))
        assertEquals("legacy::${scanned.time}", ticketKey(Purchase(date = scanned)))
    }

    @Test fun `tickets groups the lines of a scan, sorted newest first with lines by name`() {
        val purchases = listOf(
            Purchase(rawName = "Pan", price = 1.2, date = at("2026-03-10 18:00"), store = "Lidl", ticketId = "a"),
            Purchase(rawName = "leche", price = 0.9, date = at("2026-03-10 18:00"), store = "Lidl", ticketId = "a"),
            Purchase(rawName = "Atún", price = 2.0, date = at("2026-03-12 09:00"), store = "Mercadona", ticketId = "b")
        )
        val result = tickets(purchases)

        assertEquals(listOf("b", "a"), result.map { it.key })
        val a = result[1]
        assertEquals("Lidl", a.store)
        assertEquals("lidl", a.storeKey)
        assertEquals(2.1, a.total, 0.001)
        assertEquals(listOf("leche", "Pan"), a.purchases.map { it.rawName })
    }

    @Test fun `tickets groups old purchases by store and exact scan time`() {
        val first = at("2026-03-10 18:00")
        val second = at("2026-03-11 12:00")
        val purchases = listOf(
            Purchase(rawName = "A", price = 1.0, date = first),
            Purchase(rawName = "B", price = 2.0, date = first),
            Purchase(rawName = "C", price = 4.0, date = second)
        )
        val result = tickets(purchases)

        assertEquals(listOf(4.0, 3.0), result.map { it.total })
        assertEquals(listOf("", ""), result.map { it.store })
    }

    @Test fun `tickets is empty when there are no purchases`() {
        assertEquals(emptyList<Ticket>(), tickets(emptyList()))
    }

    @Test fun `tickets flags every copy but the oldest when store, total and day match`() {
        val result = tickets(
            listOf(
                ticketLine("a", "Mercadona", 47.32, "2026-03-12 10:00"),
                ticketLine("b", "MERCADONA", 47.32, "2026-03-12 10:05"),
                ticketLine("c", "mercadona ", 47.32, "2026-03-12 18:30")
            )
        )
        assertEquals(
            mapOf("a" to false, "b" to true, "c" to true),
            result.associate { it.key to it.possibleDuplicate }
        )
    }

    @Test fun `tickets does not flag a different total, day or store`() {
        val result = tickets(
            listOf(
                ticketLine("a", "Mercadona", 47.32, "2026-03-12 10:00"),
                ticketLine("b", "Mercadona", 47.33, "2026-03-12 10:05"),
                ticketLine("c", "Mercadona", 47.32, "2026-03-13 10:05"),
                ticketLine("d", "Lidl", 47.32, "2026-03-12 10:05")
            )
        )
        assertTrue(result.none { it.possibleDuplicate })
    }
}
