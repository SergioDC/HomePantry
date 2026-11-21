package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PanelLogicTest {

    private fun entry(date: String, slot: MealSlot, name: String, person: String? = null, order: Int = 0) =
        MealEntry(id = name, date = date, slot = slot.name, name = name, person = person, order = order)

    @Test
    fun `solo las entradas de hoy`() {
        val entries = listOf(
            entry("2026-11-21", MealSlot.LUNCH, "Lentejas"),
            entry("2026-11-22", MealSlot.LUNCH, "Paella")
        )
        assertEquals(
            listOf(MenuSlotSection(MealSlot.LUNCH, listOf(MenuLine(null, "Paella")))),
            todayMenuSections(entries, LocalDate.of(2026, 11, 22))
        )
        assertEquals(
            listOf(MenuSlotSection(MealSlot.LUNCH, listOf(MenuLine(null, "Lentejas")))),
            todayMenuSections(entries, LocalDate.of(2026, 11, 21))
        )
    }

    @Test
    fun `franjas en orden y sin vacias`() {
        val day = "2026-11-22"
        val entries = listOf(entry(day, MealSlot.DINNER, "Tortilla"), entry(day, MealSlot.LUNCH, "Lentejas"))
        val today = LocalDate.of(2026, 11, 22)
        assertEquals(listOf(MealSlot.LUNCH, MealSlot.DINNER), todayMenuSections(entries, today).map { it.slot })
        val withBreakfast = entries + entry(day, MealSlot.BREAKFAST, "Tostadas")
        assertEquals(MealSlot.BREAKFAST, todayMenuSections(withBreakfast, today).first().slot)
    }

    @Test
    fun `familia primero y luego personas`() {
        val day = "2026-11-22"
        val entries = listOf(
            entry(day, MealSlot.LUNCH, "Arroz a la cubana", person = "Oihan", order = 0),
            entry(day, MealSlot.LUNCH, "Lentejas", order = 1),
            entry(day, MealSlot.LUNCH, "Ensalada", person = "Familia", order = 2)
        )
        assertEquals(
            listOf(MenuLine(null, "Lentejas"), MenuLine(null, "Ensalada"), MenuLine("Oihan", "Arroz a la cubana")),
            todayMenuSections(entries, LocalDate.of(2026, 11, 22)).single().lines
        )
    }

    @Test
    fun `sin entradas`() {
        assertEquals(emptyList<MenuSlotSection>(), todayMenuSections(emptyList(), LocalDate.of(2026, 11, 22)))
    }

    private val nevera = Zone(id = "n", name = "Nevera", order = 0)
    private val despensa = Zone(id = "d", name = "Despensa", order = 1)
    private val cajon = Zone(id = "c", name = "Cajón", order = 0, parentZoneId = "n")
    private val zones = listOf(despensa, nevera, cajon)

    private fun item(name: String, zone: String, done: Boolean = true) = Item(id = name, name = name, zone = zone, done = done)

    private fun sections(items: List<Item>, query: String = "") = restockSections(items, zones, query, "Sin zona")

    @Test
    fun `solo lo que hay en casa`() {
        val result = sections(listOf(item("Leche", "n"), item("Huevos", "n", done = false)))
        assertEquals(listOf("Leche"), result.single().items.map { it.name })
    }

    @Test
    fun `zonas raiz en orden`() {
        val result = sections(listOf(item("Arroz", "d"), item("Leche", "n")))
        assertEquals(listOf("Nevera", "Despensa"), result.map { it.zoneName })
    }

    @Test
    fun `subzona dentro de su raiz`() {
        val result = sections(listOf(item("Zanahorias", "c")))
        assertEquals(RestockSection("n", "Nevera", listOf(item("Zanahorias", "c"))), result.single())
    }

    @Test
    fun `zona borrada al final`() {
        val result = sections(listOf(item("Pilas", "zz"), item("Leche", "n")))
        assertEquals(RestockSection(null, "Sin zona", listOf(item("Pilas", "zz"))), result.last())
        assertEquals(2, result.size)
    }

    @Test
    fun `orden por nombre sin tildes`() {
        val result = sections(listOf(item("Átun", "d"), item("aceite", "d"), item("Café", "d")))
        assertEquals(listOf("aceite", "Átun", "Café"), result.single().items.map { it.name })
    }

    @Test
    fun `buscador por palabras sin tildes`() {
        val items = listOf(item("Café molido", "d"), item("Café en grano", "d"))
        assertEquals(listOf("Café molido"), sections(items, "cafe mol").single().items.map { it.name })
        assertEquals(2, sections(items, "   ").single().items.size)
    }
}
