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

class PeopleLogicTest {
    private fun entry(person: String?, id: String = "e", order: Int = 0) =
        MealEntry(id = id, date = "2026-09-07", slot = MealSlot.LUNCH.name, name = "Plato $id", order = order, person = person)

    // ---- personDocId ----

    @Test fun `the document id of a person ignores case and accents, and blank or family is the family id`() {
        assertEquals("jose", personDocId("José"))
        assertEquals("jose", personDocId("  JOSE "))
        assertEquals("familia", personDocId(null))
        assertEquals("familia", personDocId(""))
        assertEquals("familia", personDocId("Familia"))
    }

    @Test fun `the document id never contains a slash, which Firestore would read as a path`() {
        assertEquals("a-b", personDocId("A/B"))
    }

    // ---- colorFor ----

    @Test fun `a person gets the color stored for them, whatever the case`() {
        val people = listOf(Person(id = "pepe", name = "Pepe", color = "CORAL"))
        assertEquals(PersonColor.CORAL, colorFor("Pepe", people))
        assertEquals(PersonColor.CORAL, colorFor("PEPE", people))
    }

    @Test fun `no person means the family and uses the family color`() {
        val people = listOf(Person(id = "familia", name = "Familia", color = "SKY"))
        assertEquals(PersonColor.SKY, colorFor(null, people))
    }

    @Test fun `there is no color for someone without a document, without a color or with an unknown one`() {
        val people = listOf(Person(id = "ana", name = "Ana"), Person(id = "juan", name = "Juan", color = "FUCSIA"))
        assertNull(colorFor("Pepe", people))
        assertNull(colorFor("Ana", people))
        assertNull(colorFor("Juan", people))
        assertNull(colorFor(null, people))
    }

    // ---- knownPeople con los documentos de personas ----

    @Test fun `known people join the stored ones with those found in entries, without repeating and without the family`() {
        val people = listOf(
            Person(id = "familia", name = "Familia", color = "SKY"),
            Person(id = "juan", name = "Juan"),
            Person(id = "pepe", name = "Pepe")
        )
        val entries = listOf(entry("pepe"), entry("Ana"), entry(null))
        assertEquals(listOf("Ana", "Juan", "Pepe"), knownPeople(entries, people))
    }

    // ---- assignableEntries ----

    @Test fun `assigning only the unassigned leaves alone what already belongs to someone`() {
        val entries = listOf(entry(null, "a"), entry("Juan", "b"), entry("  ", "c"), entry("Pepe", "d"))
        assertEquals(listOf("a", "c"), assignableEntries(entries, "Pepe", onlyUnassigned = true).map { it.id })
    }

    @Test fun `assigning everything skips the entries that already have that person`() {
        val entries = listOf(entry(null, "a"), entry("Juan", "b"), entry("pepe", "c"))
        assertEquals(listOf("a", "b"), assignableEntries(entries, "Pepe", onlyUnassigned = false).map { it.id })
    }

    @Test fun `assigning to the family clears the person of the entries that had one`() {
        val entries = listOf(entry(null, "a"), entry("Juan", "b"))
        assertEquals(listOf("b"), assignableEntries(entries, null, onlyUnassigned = false).map { it.id })
    }

    @Test fun `assigning only the unassigned to the family changes nothing`() {
        val entries = listOf(entry(null, "a"), entry("Juan", "b"))
        assertTrue(assignableEntries(entries, null, onlyUnassigned = true).isEmpty())
    }
}
