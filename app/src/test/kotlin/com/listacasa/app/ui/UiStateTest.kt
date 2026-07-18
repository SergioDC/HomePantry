package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {
    private val nevera = Zone(id = "z1", name = "Nevera", order = 0)

    @Test fun `sections applies the search query before grouping and sorting`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Pan", zone = "z1")
            ),
            zones = listOf(nevera),
            selectedZoneId = "ALL",
            searchQuery = "leche"
        )
        assertEquals(listOf("1"), state.sections.single().items.map { it.id })
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
}
