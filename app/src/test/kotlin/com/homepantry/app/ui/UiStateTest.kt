package com.homepantry.app.ui

import com.homepantry.app.data.Item
import com.homepantry.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {
    private val nevera = Zone(id = "z1", name = "Nevera", order = 0)
    private val despensa = Zone(id = "z2", name = "Despensa", order = 1)

    @Test fun `sections no longer filters by search query -- that is the Buscar tab's job`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Pan", zone = "z1")
            ),
            zones = listOf(nevera),
            selectedZoneId = "ALL",
            searchQuery = "leche"
        )
        assertEquals(listOf("1", "2"), state.sections.single().items.map { it.id })
    }

    @Test fun `sections applies the selected sort mode`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "zanahoria", zone = "z1"),
                Item(id = "2", name = "arroz", zone = "z1")
            ),
            zones = listOf(nevera),
            selectedZoneId = "ALL",
            sortMode = SortMode.ALPHABETICAL
        )
        assertEquals(listOf("2", "1"), state.sections.single().items.map { it.id })
    }

    @Test fun `flatRows filters by the selected zone before flattening`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Arroz", zone = "z2")
            ),
            zones = listOf(nevera, despensa),
            selectedZoneId = "z2"
        )
        assertEquals(listOf("2"), state.flatRows.map { it.item.id })
    }

    @Test fun `flatRows includes every zone when ALL is selected`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Arroz", zone = "z2")
            ),
            zones = listOf(nevera, despensa),
            selectedZoneId = "ALL"
        )
        assertEquals(setOf("1", "2"), state.flatRows.map { it.item.id }.toSet())
    }

    @Test fun `zoneCards summarizes every zone regardless of the selected zone filter`() {
        val state = UiState(
            items = listOf(Item(id = "1", name = "Leche", zone = "z1")),
            zones = listOf(nevera, despensa),
            selectedZoneId = "z1"
        )
        assertEquals(listOf("z1", "z2"), state.zoneCards.map { it.zone.id })
    }

    @Test fun `listViewMode defaults to GROUPED`() {
        assertEquals(ListViewMode.GROUPED, UiState().listViewMode)
    }

    @Test fun `purchaseSummaries exposes productSummaries computed from purchases`() {
        val state = UiState(
            purchases = listOf(
                com.homepantry.app.data.Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.5),
                com.homepantry.app.data.Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.2)
            )
        )
        assertEquals(1, state.purchaseSummaries.size)
        assertEquals(2.7, state.purchaseSummaries.single().allTimeTotal, 0.001)
    }
}
