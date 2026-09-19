package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneTest {
    @Test fun `Otros is protected regardless of case`() {
        assertTrue(isProtectedZone(Zone(id = "z1", name = "Otros")))
        assertTrue(isProtectedZone(Zone(id = "z2", name = "otros")))
    }

    @Test fun `any other zone name is not protected`() {
        assertFalse(isProtectedZone(Zone(id = "z3", name = "Nevera")))
    }

    @Test fun `zoneIconLetter returns the uppercased first letter of the zone name`() {
        assertEquals("N", zoneIconLetter(Zone(id = "z1", name = "Nevera")))
    }

    @Test fun `zoneIconLetter uppercases even when the name starts lowercase`() {
        assertEquals("C", zoneIconLetter(Zone(id = "z2", name = "congelador")))
    }

    @Test fun `zoneIconLetter falls back to a question mark for a blank name`() {
        assertEquals("?", zoneIconLetter(Zone(id = "z3", name = "")))
    }

    @Test fun `resolvedZoneColor uses the zone's own color when set`() {
        val zone = Zone(id = "z1", name = "Nevera", color = "#123456")
        assertEquals("#123456", resolvedZoneColor(zone, index = 3))
    }

    @Test fun `resolvedZoneColor falls back to the index-derived color when unset`() {
        val zone = Zone(id = "z1", name = "Nevera")
        assertEquals(zoneColorFor(0), resolvedZoneColor(zone, index = 0))
    }

    @Test fun `resolvedZoneColor falls back to the index-derived color when blank`() {
        val zone = Zone(id = "z1", name = "Nevera", color = "  ")
        assertEquals(zoneColorFor(2), resolvedZoneColor(zone, index = 2))
    }

    @Test fun `subzonesOf returns only direct children of the given zone, ordered`() {
        val nevera = Zone(id = "z1", name = "Nevera")
        val puerta = Zone(id = "z2", name = "Puerta", order = 1, parentZoneId = "z1")
        val cajon = Zone(id = "z3", name = "Cajón", order = 0, parentZoneId = "z1")
        val despensa = Zone(id = "z4", name = "Despensa")
        val result = subzonesOf("z1", listOf(nevera, puerta, cajon, despensa))
        assertEquals(listOf("z3", "z2"), result.map { it.id })
    }

    @Test fun `subzonesOf returns empty when the zone has no children`() {
        val nevera = Zone(id = "z1", name = "Nevera")
        assertTrue(subzonesOf("z1", listOf(nevera)).isEmpty())
    }

    @Test fun `zoneDisplayLabel returns just the name for a root zone`() {
        val nevera = Zone(id = "z1", name = "Nevera")
        assertEquals("Nevera", zoneDisplayLabel(nevera, listOf(nevera)))
    }

    @Test fun `zoneDisplayLabel prefixes the parent name for a subzone`() {
        val nevera = Zone(id = "z1", name = "Nevera")
        val puerta = Zone(id = "z2", name = "Puerta", parentZoneId = "z1")
        assertEquals("Nevera > Puerta", zoneDisplayLabel(puerta, listOf(nevera, puerta)))
    }

    @Test fun `zoneDisplayLabel falls back to just the name if the parent is missing`() {
        val orphan = Zone(id = "z2", name = "Puerta", parentZoneId = "missing")
        assertEquals("Puerta", zoneDisplayLabel(orphan, listOf(orphan)))
    }

    @Test fun `nextZoneColor picks the first palette color when no zone has one`() {
        assertEquals(ZONE_COLORS[0], nextZoneColor(emptyList()))
        assertEquals(ZONE_COLORS[0], nextZoneColor(listOf(Zone(id = "z1", name = "Nevera"))))
    }

    @Test fun `nextZoneColor skips colors already in use`() {
        val zones = listOf(
            Zone(id = "z1", name = "A", color = ZONE_COLORS[0]),
            Zone(id = "z2", name = "B", color = ZONE_COLORS[1])
        )
        assertEquals(ZONE_COLORS[2], nextZoneColor(zones))
    }

    @Test fun `nextZoneColor ignores the case of the hex`() {
        val zones = listOf(Zone(id = "z1", name = "A", color = ZONE_COLORS[0].lowercase()))
        assertEquals(ZONE_COLORS[1], nextZoneColor(zones))
    }

    @Test fun `nextZoneColor picks the least used color once the palette is exhausted`() {
        val zones = ZONE_COLORS.mapIndexed { index, hex -> Zone(id = "z$index", name = "Z$index", color = hex) } +
            Zone(id = "extra", name = "Extra", color = ZONE_COLORS[0])
        // El color 0 se usa dos veces y los demás una: el primero menos usado es el 1.
        assertEquals(ZONE_COLORS[1], nextZoneColor(zones))
    }

    @Test fun `zoneColorBackfill colors uncolored roots by their position among roots`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0),
            Zone(id = "z2", name = "Despensa", order = 1),
            Zone(id = "z3", name = "Otros", order = 2)
        )
        assertEquals(
            mapOf("z1" to zoneColorFor(0), "z2" to zoneColorFor(1), "z3" to zoneColorFor(2)),
            zoneColorBackfill(zones)
        )
    }

    @Test fun `zoneColorBackfill leaves colored zones alone but still counts their position`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0, color = "#123456"),
            Zone(id = "z2", name = "Despensa", order = 1)
        )
        assertEquals(mapOf("z2" to zoneColorFor(1)), zoneColorBackfill(zones))
    }

    @Test fun `zoneColorBackfill is empty when every zone already has a color`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", color = "#123456"),
            Zone(id = "z2", name = "Cajón", parentZoneId = "z1", color = "#654321")
        )
        assertEquals(emptyMap<String, String>(), zoneColorBackfill(zones))
    }

    @Test fun `zoneColorBackfill gives each uncolored subzone its own color in a stable order`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0),
            Zone(id = "z2", name = "Despensa", order = 1),
            Zone(id = "s2", name = "Estante", order = 6, parentZoneId = "z1"),
            Zone(id = "s1", name = "Cajón", order = 5, parentZoneId = "z1"),
            Zone(id = "s3", name = "Balda", order = 4, parentZoneId = "z2")
        )
        val result = zoneColorBackfill(zones)

        assertEquals(zoneColorFor(0), result["z1"])
        assertEquals(zoneColorFor(1), result["z2"])
        // Primero las subzonas de Nevera (por order) y luego las de Despensa; con los colores 0 y 1
        // ya en uso, la paleta sigue por el 2, el 3 y el 4.
        assertEquals(ZONE_COLORS[2], result["s1"])
        assertEquals(ZONE_COLORS[3], result["s2"])
        assertEquals(ZONE_COLORS[4], result["s3"])
    }

    @Test fun `zoneColorBackfill puts a subzone whose parent is missing last`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0),
            Zone(id = "orphan", name = "Huérfana", order = 0, parentZoneId = "missing"),
            Zone(id = "s1", name = "Cajón", order = 9, parentZoneId = "z1")
        )
        val result = zoneColorBackfill(zones)

        assertEquals(ZONE_COLORS[1], result["s1"])
        assertEquals(ZONE_COLORS[2], result["orphan"])
    }
}
