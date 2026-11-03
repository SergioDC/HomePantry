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
}
