package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemListLogicTest {
    private val nevera = Zone(id = "z1", name = "Nevera", order = 0)
    private val despensa = Zone(id = "z2", name = "Despensa", order = 1)

    @Test fun `progress text counts done vs total`() {
        val items = listOf(
            Item(id = "1", name = "Leche", done = true),
            Item(id = "2", name = "Pan", done = false),
            Item(id = "3", name = "Huevos", done = true)
        )
        assertEquals("2 de 3 listos", progressText(items))
    }

    @Test fun `progress text with empty list`() {
        assertEquals("0 de 0 listos", progressText(emptyList()))
    }

    @Test fun `groups by zone order, pending before done within a zone`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = true),
            Item(id = "2", name = "Pan", zone = "z1", done = false),
            Item(id = "3", name = "Arroz", zone = "z2", done = false)
        )
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = "ALL")
        assertEquals(listOf("z1", "z2"), sections.map { it.zone.id })
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `filters to a single zone when requested`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1"),
            Item(id = "2", name = "Arroz", zone = "z2")
        )
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = "z2")
        assertEquals(listOf("z2"), sections.map { it.zone.id })
    }

    @Test fun `zones with no items are omitted from the unfiltered view`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "z1"))
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = "ALL")
        assertEquals(listOf("z1"), sections.map { it.zone.id })
    }
}
