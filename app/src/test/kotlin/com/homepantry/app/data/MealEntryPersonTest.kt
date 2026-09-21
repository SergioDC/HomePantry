package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealEntryPersonTest {
    private fun entry(name: String, person: String? = null, order: Int = 0) =
        MealEntry(date = "2026-09-07", slot = MealSlot.LUNCH.name, name = name, order = order, person = person)

    // ---- MealEntry ----

    @Test fun `an entry has no person by default, which means the whole family`() {
        assertNull(MealEntry().person)
    }

    // ---- groupByPerson ----

    @Test fun `entries without a person form a single family group in their order`() {
        val groups = groupByPerson(listOf(entry("A"), entry("B"), entry("C")))
        assertEquals(1, groups.size)
        assertNull(groups.single().person)
        assertEquals(listOf("A", "B", "C"), groups.single().entries.map { it.name })
    }

    @Test fun `the family group comes first and people follow in order of appearance`() {
        val groups = groupByPerson(listOf(
            entry("Sopa", person = "Pepe"),
            entry("Fruta"),
            entry("Pasta", person = "Ana"),
            entry("Pescado", person = "Pepe")
        ))
        assertEquals(listOf(null, "Pepe", "Ana"), groups.map { it.person })
        assertEquals(listOf("Fruta"), groups[0].entries.map { it.name })
        assertEquals(listOf("Sopa", "Pescado"), groups[1].entries.map { it.name })
        assertEquals(listOf("Pasta"), groups[2].entries.map { it.name })
    }

    @Test fun `the same person written with different case or accents is one group under the first spelling`() {
        val groups = groupByPerson(listOf(entry("A", person = "José"), entry("B", person = "jose"), entry("C", person = "JOSÉ")))
        assertEquals(1, groups.size)
        assertEquals("José", groups.single().person)
        assertEquals(listOf("A", "B", "C"), groups.single().entries.map { it.name })
    }

    @Test fun `a blank person counts as family`() {
        val groups = groupByPerson(listOf(entry("A", person = "  "), entry("B")))
        assertEquals(1, groups.size)
        assertNull(groups.single().person)
    }

    @Test fun `no entries give no groups`() {
        assertTrue(groupByPerson(emptyList()).isEmpty())
    }

    // ---- normalizePerson ----

    @Test fun `a blank name or the word family means the whole family`() {
        assertNull(normalizePerson("", emptyList()))
        assertNull(normalizePerson("   ", emptyList()))
        assertNull(normalizePerson("familia", emptyList()))
        assertNull(normalizePerson("  FAMILIA ", emptyList()))
    }

    @Test fun `a name that matches a known one reuses its spelling ignoring case and accents`() {
        assertEquals("Pepe", normalizePerson("pepe", listOf("Pepe")))
        assertEquals("José", normalizePerson("jose", listOf("Ana", "José")))
    }

    @Test fun `a new name is trimmed, single-spaced and starts with a capital`() {
        assertEquals("Ana maría", normalizePerson("  ana   maría ", emptyList()))
    }

    @Test fun `a very long name is cut so it cannot break the layout`() {
        assertEquals(30, normalizePerson("A".repeat(80), emptyList())!!.length)
    }

    @Test fun `similar names are not confused, so Marco and Marcos stay different people`() {
        assertEquals("Marcos", normalizePerson("marcos", listOf("Marco")))
    }

    // ---- knownPeople ----

    @Test fun `known people are the distinct names used, sorted, without blanks or duplicates`() {
        val entries = listOf(
            entry("A", person = "Pepe"), entry("B"), entry("C", person = "ana"),
            entry("D", person = "PEPE"), entry("E", person = " ")
        )
        assertEquals(listOf("ana", "Pepe"), knownPeople(entries))
    }
}
