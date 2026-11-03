package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductAliasLogicTest {
    @Test fun `alias name wins over gemini`() = assertEquals(
        ProductProposal("Arándanos", PackSize(0.3, "KG")),
        applyAlias(ProductProposal("Arándano cultivado", PackSize(0.3, "KG")), ProductAlias(productName = "Arándanos"))
    )

    @Test fun `no alias keeps the proposal`() {
        val proposal = ProductProposal("Pan", null)
        assertEquals(proposal, applyAlias(proposal, null))
    }

    @Test fun `alias pack only when known`() {
        val explicitNone = ProductAlias(productName = "Leche entera", packKnown = true, packSize = null)
        assertEquals(ProductProposal("Leche entera", null), applyAlias(ProductProposal("Leche", PackSize(1.0, "L")), explicitNone))
        val known = ProductAlias(productName = "Leche entera", packKnown = true, packSize = 1.0, packUnit = "L")
        assertEquals(ProductProposal("Leche entera", PackSize(1.0, "L")), applyAlias(ProductProposal("Leche", null), known))
    }

    @Test fun `no change no alias`() = assertNull(
        aliasForCorrection(
            "ARANDANO 300GR",
            ProductProposal("Arándanos", PackSize(0.3, "KG")),
            ProductProposal("Arándanos", PackSize(0.3, "KG"))
        )
    )

    @Test fun `name change creates alias without pack when printed name has size`() {
        val alias = aliasForCorrection(
            "ARANDANO 300GR",
            ProductProposal("Arándano", PackSize(0.3, "KG")),
            ProductProposal("Arándanos", PackSize(0.25, "KG"))
        )!!
        assertEquals(aliasKey("ARANDANO 300GR"), alias.id)
        assertEquals("Arándanos", alias.productName)
        assertEquals("ARANDANO 300GR", alias.rawName)
        assertFalse(alias.packKnown)
    }

    @Test fun `removing pack on sizeless name is remembered`() {
        val alias = aliasForCorrection(
            "LECHE ENT. HACENDADO",
            ProductProposal("Leche entera", PackSize(1.0, "L")),
            ProductProposal("Leche entera", null)
        )!!
        assertTrue(alias.packKnown)
        assertNull(alias.packSize)
    }

    @Test fun `no alias for a name that is only a size`() =
        assertNull(aliasForCorrection("500G", ProductProposal("", null), ProductProposal("Harina", null)))

    @Test fun `a name correction keeps the remembered pack`() {
        val existing = ProductAlias(id = "k", productName = "Arándano", packKnown = true, packSize = 0.3, packUnit = "KG")
        val merged = keepRememberedPack(ProductAlias(id = "k", productName = "Arándanos"), existing)
        assertEquals("Arándanos", merged.productName)
        assertTrue(merged.packKnown)
        assertEquals(0.3, merged.packSize!!, 0.0)
    }

    @Test fun `an explicit pack correction replaces the remembered one`() {
        val existing = ProductAlias(id = "k", productName = "Leche", packKnown = true, packSize = 1.0, packUnit = "L")
        val merged = keepRememberedPack(ProductAlias(id = "k", productName = "Leche", packKnown = true), existing)
        assertNull(merged.packSize)
    }

    @Test fun `editing an old line without touching the name keeps it unnormalized`() {
        val old = Purchase(rawName = "ARANDANO 300GR", price = 2.5)
        assertEquals("", editedProductName(old, "ARANDANO 300GR"))
        assertEquals("Arándanos", editedProductName(old, "Arándanos"))
        assertNull(aliasForEdit(old, old.copy(price = 3.0, packSize = 0.3, packUnit = "KG")))
    }

    @Test fun `editing a normalized line remembers the correction`() {
        val line = Purchase(rawName = "ARAND. CULTIVADO", productName = "Arándano")
        assertEquals("Arándanos", aliasForEdit(line, line.copy(productName = "Arándanos"))!!.productName)
    }
}
