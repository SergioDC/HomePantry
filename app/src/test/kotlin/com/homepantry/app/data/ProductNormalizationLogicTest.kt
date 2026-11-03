package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ProductNormalizationLogicTest {
    private fun p(id: String, raw: String, product: String = "", time: Long = 0, unit: String = "UD") =
        Purchase(id = id, rawName = raw, productName = product, date = Date(time), unit = unit, price = 1.0)

    @Test fun `same printed name in different sizes is one row`() {
        val (rows, names) = pendingRawNames(listOf(p("1", "ARANDANO 300GR", time = 1), p("2", "ARANDANO 150GR", time = 2)), emptyMap())
        assertTrue(rows.isEmpty())
        assertEquals(listOf("ARANDANO 150GR"), names)
    }

    @Test fun `aliased names do not go to gemini`() {
        val key = aliasKey("ARAND. CULTIVADO")!!
        val (rows, names) = pendingRawNames(
            listOf(p("1", "ARAND. CULTIVADO")),
            mapOf(key to ProductAlias(id = key, productName = "Arándanos"))
        )
        assertTrue(names.isEmpty())
        val row = rows.single()
        assertTrue(row.fromAlias)
        assertEquals("Arándanos", row.final.productName)
    }

    @Test fun `already normalized purchases are skipped`() {
        val (rows, names) = pendingRawNames(listOf(p("1", "ARANDANO 300GR", product = "Arándanos")), emptyMap())
        assertTrue(rows.isEmpty())
        assertTrue(names.isEmpty())
    }

    @Test fun `missing proposal falls back to rules and unknown proposals are ignored`() {
        val purchases = listOf(p("1", "ARANDANO 300GR"))
        val rows = rowsFromProposals(
            purchases,
            listOf("ARANDANO 300GR"),
            mapOf("OTRO" to ProductProposal("Otro", null))
        )
        assertEquals(ProductProposal("ARANDANO", PackSize(0.3, "KG")), rows.single().final)
    }

    @Test fun `unselected rows are not applied`() {
        val rows = rowsFromProposals(
            listOf(p("1", "PAN"), p("2", "LECHE")),
            listOf("PAN", "LECHE"),
            mapOf("PAN" to ProductProposal("Pan", null), "LECHE" to ProductProposal("Leche entera", null))
        ).map { if (it.rawName == "LECHE") it.copy(selected = false) else it }
        val (updates, aliases) = applyPlan(rows)
        assertEquals(listOf("1"), updates.map { it.purchaseId })
        assertEquals(listOf("Pan"), aliases.map { it.productName })
    }

    @Test fun `plan keeps pack per purchase line`() {
        val rows = rowsFromProposals(
            listOf(p("1", "ARANDANO 300GR", time = 1), p("2", "ARANDANO 150GR", time = 2), p("3", "ARANDANO 1KG", time = 3, unit = "KG")),
            listOf("ARANDANO 1KG"),
            mapOf("ARANDANO 1KG" to ProductProposal("Arándanos", PackSize(1.0, "KG")))
        )
        val updates = applyPlan(rows).first.associateBy { it.purchaseId }
        assertEquals(PackSize(0.3, "KG"), updates.getValue("1").pack)
        assertEquals(PackSize(0.15, "KG"), updates.getValue("2").pack)
        assertEquals(null, updates.getValue("3").pack)
        assertTrue(updates.values.all { it.productName == "Arándanos" && it.normalizedName == "arandano" && it.setPack })
    }

    @Test fun `review order groups by generic name`() {
        val rows = rowsFromProposals(
            listOf(p("1", "ARAND. CULTIVADO"), p("2", "PAN"), p("3", "ARANDANO 300GR")),
            listOf("ARAND. CULTIVADO", "PAN", "ARANDANO 300GR"),
            mapOf(
                "ARAND. CULTIVADO" to ProductProposal("Arándanos", null),
                "PAN" to ProductProposal("Pan", null),
                "ARANDANO 300GR" to ProductProposal("arandanos", PackSize(0.3, "KG"))
            )
        )
        assertEquals(listOf("ARAND. CULTIVADO", "ARANDANO 300GR", "PAN"), sortedForReview(rows).map { it.rawName })
    }

    private fun bought(id: String, raw: String, key: String, product: String = "", time: Long = 0, pack: Double? = null) =
        Purchase(
            id = id, rawName = raw, productName = product, normalizedName = key, date = Date(time),
            price = 1.0, packSize = pack, packUnit = pack?.let { "KG" }
        )

    @Test fun `merge renames every purchase and keeps packs`() {
        val purchases = listOf(
            bought("1", "ARANDANO 300GR", "arandano 300gr", pack = 0.3),
            bought("2", "ARANDANO 300GR", "arandano 300gr", pack = 0.3),
            bought("3", "ARAND. CULTIVADO", "arand. cultivado"),
            bought("4", "PAN", "pan")
        )
        val (updates, aliases) = mergePlan(purchases, setOf("arandano 300gr", "arand. cultivado"), "Arándanos", emptyMap())
        assertEquals(listOf("1", "2", "3"), updates.map { it.purchaseId })
        assertTrue(updates.all { !it.setPack && it.productName == "Arándanos" && it.normalizedName == "arandano" })
        assertEquals(setOf(aliasKey("ARANDANO 300GR"), aliasKey("ARAND. CULTIVADO")), aliases.map { it.id }.toSet())
    }

    @Test fun `merge keeps existing alias pack`() {
        val key = aliasKey("LECHE ENT. HACENDADO")!!
        val existing = ProductAlias(id = key, productName = "Leche", packKnown = true, packSize = 1.0, packUnit = "L")
        val (_, aliases) = mergePlan(
            listOf(bought("1", "LECHE ENT. HACENDADO", "leche")),
            setOf("leche"),
            "Leche entera",
            mapOf(key to existing)
        )
        val alias = aliases.single()
        assertEquals("Leche entera", alias.productName)
        assertTrue(alias.packKnown)
        assertEquals(1.0, alias.packSize!!, 0.0)
        assertEquals("L", alias.packUnit)
    }

    @Test fun `default name is the most bought product`() {
        val purchases = listOf(
            bought("1", "ARANDANO", "a", product = "Arándanos", time = 1),
            bought("2", "ARANDANO", "a", product = "Arándanos", time = 2),
            bought("3", "ARANDANO", "a", product = "Arándanos", time = 3),
            bought("4", "ARAND", "b", product = "Arándano", time = 9)
        )
        assertEquals("Arándanos", defaultMergeName(purchases, setOf("a", "b")))
    }

    @Test fun `namer output ignores bad entries`() {
        val proposals = mapNamerOutput(
            """{"products":[{"rawName":"A","productName":"Aa","packSize":1,"packUnit":"UD"},""" +
                """{"productName":"Sin nombre"},{"rawName":"B","productName":"Bb","packSize":0.5,"packUnit":"L"}]}"""
        )
        assertEquals(ProductProposal("Aa", null), proposals["A"])
        assertEquals(ProductProposal("Bb", PackSize(0.5, "L")), proposals["B"])
        assertEquals(2, proposals.size)
    }

    @Test fun `namer broken json throws`() {
        assertThrows(GeminiResponseException::class.java) { mapNamerOutput("{\"products\":[") }
    }

    @Test fun `rows are refreshed against current purchases before applying`() {
        val rows = rowsFromProposals(
            listOf(p("1", "PAN"), p("2", "PAN"), p("3", "LECHE")),
            listOf("PAN", "LECHE"),
            mapOf("PAN" to ProductProposal("Pan", null), "LECHE" to ProductProposal("Leche entera", null))
        )
        // La compra 2 se borró y la 3 ya se normalizó por otro lado.
        val current = listOf(p("1", "PAN"), p("3", "LECHE", product = "Leche"))
        val refreshed = refreshRows(rows, current)
        assertEquals(listOf("PAN"), refreshed.map { it.rawName })
        assertEquals(listOf("1"), refreshed.single().purchases.map { it.id })
    }
}
