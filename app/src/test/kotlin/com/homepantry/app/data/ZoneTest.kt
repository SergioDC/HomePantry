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
}
