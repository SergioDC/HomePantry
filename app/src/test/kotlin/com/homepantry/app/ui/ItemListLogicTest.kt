package com.homepantry.app.ui

import com.homepantry.app.data.Item
import com.homepantry.app.data.Zone
import com.homepantry.app.data.zoneColorFor
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

    @Test fun `sort mode newest first orders by addedAt descending within a group`() {
        val older = Item(id = "1", name = "Leche", zone = "z1", addedAt = java.util.Date(1000))
        val newer = Item(id = "2", name = "Pan", zone = "z1", addedAt = java.util.Date(2000))
        val sections = groupAndSort(
            listOf(older, newer), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.NEWEST_FIRST
        )
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `sort mode oldest first orders by addedAt ascending within a group`() {
        val older = Item(id = "1", name = "Leche", zone = "z1", addedAt = java.util.Date(1000))
        val newer = Item(id = "2", name = "Pan", zone = "z1", addedAt = java.util.Date(2000))
        val sections = groupAndSort(
            listOf(older, newer), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.OLDEST_FIRST
        )
        assertEquals(listOf("1", "2"), sections.first().items.map { it.id })
    }

    @Test fun `sort mode alphabetical orders by name case-insensitively`() {
        val zebra = Item(id = "1", name = "zanahoria", zone = "z1")
        val apple = Item(id = "2", name = "Arroz", zone = "z1")
        val sections = groupAndSort(
            listOf(zebra, apple), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.ALPHABETICAL
        )
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `pending items always precede done items regardless of sort mode`() {
        val doneOld = Item(id = "1", name = "A", zone = "z1", done = true, addedAt = java.util.Date(5000))
        val pendingNew = Item(id = "2", name = "B", zone = "z1", done = false, addedAt = java.util.Date(1000))
        val sections = groupAndSort(
            listOf(doneOld, pendingNew), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.OLDEST_FIRST
        )
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `filterItemsByQuery matches case-insensitive partial names`() {
        val items = listOf(
            Item(id = "1", name = "Leche entera"),
            Item(id = "2", name = "Pan de molde"),
            Item(id = "3", name = "Leche de avena")
        )
        val result = filterItemsByQuery(items, "leche")
        assertEquals(listOf("1", "3"), result.map { it.id })
    }

    @Test fun `filterItemsByQuery with blank query returns all items unchanged`() {
        val items = listOf(Item(id = "1", name = "Leche"), Item(id = "2", name = "Pan"))
        assertEquals(items, filterItemsByQuery(items, "  "))
    }

    @Test fun `findPendingDuplicateByBarcode matches only pending items with same barcode`() {
        val pendingMatch = Item(id = "1", name = "Leche", barcode = "123", done = false)
        val doneMatch = Item(id = "2", name = "Leche vieja", barcode = "123", done = true)
        val items = listOf(pendingMatch, doneMatch)
        assertEquals(pendingMatch, findPendingDuplicateByBarcode(items, "123"))
        assertEquals(null, findPendingDuplicateByBarcode(listOf(doneMatch), "123"))
        assertEquals(null, findPendingDuplicateByBarcode(items, "999"))
    }

    @Test fun `flattenAndSort orders pending before done, ungrouped across zones`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = true),
            Item(id = "2", name = "Pan", zone = "z2", done = false)
        )
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals(listOf("2", "1"), rows.map { it.item.id })
    }

    @Test fun `flattenAndSort attaches the zone name to each row`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "z1"))
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals("Nevera", rows.single().zoneName)
    }

    @Test fun `flattenAndSort leaves the zone name blank when the zone is unknown`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "missing"))
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals("", rows.single().zoneName)
    }

    @Test fun `flattenAndSort attaches the zone's resolved color to each row`() {
        val coloredZone = Zone(id = "z1", name = "Nevera", order = 0, color = "#ABCDEF")
        val items = listOf(Item(id = "1", name = "Leche", zone = "z1"))
        val rows = flattenAndSort(items, listOf(coloredZone, despensa), SortMode.NEWEST_FIRST)
        assertEquals("#ABCDEF", rows.single().zoneColor)
    }

    @Test fun `flattenAndSort falls back to the index-derived color when the zone has none`() {
        val items = listOf(Item(id = "1", name = "Arroz", zone = "z2"))
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals(zoneColorFor(1), rows.single().zoneColor)
    }

    @Test fun `flattenAndSort leaves the zone color null when the zone is unknown`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "missing"))
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals(null, rows.single().zoneColor)
    }

    @Test fun `flattenAndSort respects sort mode within the pending and done groups`() {
        val zebra = Item(id = "1", name = "zanahoria", zone = "z1")
        val apple = Item(id = "2", name = "Arroz", zone = "z2")
        val rows = flattenAndSort(listOf(zebra, apple), listOf(nevera, despensa), SortMode.ALPHABETICAL)
        assertEquals(listOf("2", "1"), rows.map { it.item.id })
    }

    @Test fun `zoneSummaries counts total and pending items per zone, in zone order`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = false),
            Item(id = "2", name = "Pan", zone = "z1", done = true),
            Item(id = "3", name = "Arroz", zone = "z2", done = false)
        )
        val summaries = zoneSummaries(items, listOf(despensa, nevera))
        assertEquals(listOf("z1", "z2"), summaries.map { it.zone.id })
        assertEquals(1, summaries[0].itemCount)
        assertEquals(1, summaries[0].pendingCount)
        assertEquals(0, summaries[1].itemCount)
        assertEquals(1, summaries[1].pendingCount)
    }

    @Test fun `zoneSummaries reports zero pending for a zone with only done items`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "z1", done = true))
        val summaries = zoneSummaries(items, listOf(nevera))
        assertEquals(0, summaries.single().pendingCount)
    }

    @Test fun `zoneSummaries includes zones with no items at all`() {
        val summaries = zoneSummaries(emptyList(), listOf(nevera, despensa))
        assertEquals(listOf(0, 0), summaries.map { it.itemCount })
    }

    @Test fun `zoneSummaries has no card for a subzone`() {
        val puerta = Zone(id = "z3", name = "Puerta", parentZoneId = "z1")
        val summaries = zoneSummaries(emptyList(), listOf(nevera, puerta))
        assertEquals(listOf("z1"), summaries.map { it.zone.id })
    }

    @Test fun `zoneSummaries folds a subzone's items into its parent's card`() {
        val puerta = Zone(id = "z3", name = "Puerta", parentZoneId = "z1")
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = true),
            Item(id = "2", name = "Mantequilla", zone = "z3", done = true),
            Item(id = "3", name = "Huevos", zone = "z3", done = false)
        )
        val summary = zoneSummaries(items, listOf(nevera, puerta)).single()
        assertEquals(2, summary.itemCount)
        assertEquals(1, summary.pendingCount)
    }

    @Test fun `flattenAndSort labels a subzone item with its parent's name`() {
        val puerta = Zone(id = "z3", name = "Puerta", order = 0, parentZoneId = "z1")
        val items = listOf(Item(id = "1", name = "Mantequilla", zone = "z3"))
        val rows = flattenAndSort(items, listOf(nevera, puerta), SortMode.NEWEST_FIRST)
        assertEquals("Nevera > Puerta", rows.single().zoneName)
    }
}
