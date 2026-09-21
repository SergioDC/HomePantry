package com.homepantry.app.data

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuImportTest {
    private val september = YearMonth.of(2026, 9)

    private fun menu(vararg days: Pair<Int, List<String>>) =
        ParsedMenu(null, days.map { (day, dishes) -> ParsedMenuDay(day, dishes) })

    private fun ids(): () -> String {
        var n = 0
        return { "new${++n}" }
    }

    private fun plan(
        menu: ParsedMenu,
        dishes: List<Dish> = emptyList(),
        existing: List<MealEntry> = emptyList(),
        newId: () -> String = ids()
    ) = planMenuImport(menu, september, dishes, existing, newId, addedBy = "Ana")

    // ---- cleanParsedMenu ----

    @Test fun `clean trims names and drops blank dishes and days without dishes`() {
        val cleaned = cleanParsedMenu(null, listOf(
            ParsedMenuDay(1, listOf("  Lentejas ", "", "   ")),
            ParsedMenuDay(2, listOf("", " "))
        ))
        assertEquals(listOf(ParsedMenuDay(1, listOf("Lentejas"))), cleaned.days)
    }

    @Test fun `clean drops days outside 1 to 31`() {
        val cleaned = cleanParsedMenu(null, listOf(
            ParsedMenuDay(0, listOf("A")),
            ParsedMenuDay(32, listOf("B")),
            ParsedMenuDay(-3, listOf("C")),
            ParsedMenuDay(31, listOf("D"))
        ))
        assertEquals(listOf(ParsedMenuDay(31, listOf("D"))), cleaned.days)
    }

    @Test fun `clean keeps one of the dishes repeated within a day ignoring case and accents`() {
        val cleaned = cleanParsedMenu(null, listOf(ParsedMenuDay(3, listOf("Sopa de Picadillo", "SOPA DE PICADILLO", "Merluza", "sopa de picadillo"))))
        assertEquals(listOf("Sopa de Picadillo", "Merluza"), cleaned.days.single().dishes)
    }

    @Test fun `clean merges a day that appears twice and sorts days`() {
        val cleaned = cleanParsedMenu(null, listOf(
            ParsedMenuDay(8, listOf("Paella")),
            ParsedMenuDay(2, listOf("Lentejas")),
            ParsedMenuDay(8, listOf("Paella", "Fruta"))
        ))
        assertEquals(listOf(ParsedMenuDay(2, listOf("Lentejas")), ParsedMenuDay(8, listOf("Paella", "Fruta"))), cleaned.days)
    }

    @Test fun `clean drops bread as a safety net`() {
        val cleaned = cleanParsedMenu(null, listOf(ParsedMenuDay(4, listOf("Lentejas", "Pan", "PAN", "pan", "Fruta"))))
        assertEquals(listOf("Lentejas", "Fruta"), cleaned.days.single().dishes)
    }

    @Test fun `clean does not drop dishes that merely contain the word pan`() {
        val cleaned = cleanParsedMenu(null, listOf(ParsedMenuDay(4, listOf("Pan de calabacín", "Sándwich de pan"))))
        assertEquals(listOf("Pan de calabacín", "Sándwich de pan"), cleaned.days.single().dishes)
    }

    @Test fun `clean turns ALL CAPS names into sentence case and leaves mixed case alone`() {
        val cleaned = cleanParsedMenu(null, listOf(ParsedMenuDay(5, listOf("LENTEJAS CON VERDURAS", "Merluza al Horno", "FRUTA"))))
        assertEquals(listOf("Lentejas con verduras", "Merluza al Horno", "Fruta"), cleaned.days.single().dishes)
    }

    @Test fun `clean keeps the detected month`() {
        assertEquals(september, cleanParsedMenu(september, emptyList()).month)
    }

    // ---- planMenuImport ----

    @Test fun `plan links a dish that already exists and creates nothing`() {
        val lentils = Dish(id = "d1", name = "Lentejas")
        val result = plan(menu(1 to listOf("LENTEJAS")), dishes = listOf(lentils))
        assertTrue(result.newDishes.isEmpty())
        val entry = result.entries.single()
        assertEquals("d1", entry.dishId)
        assertEquals("Lentejas", entry.name)
    }

    @Test fun `plan creates a missing dish once even if it appears on several days`() {
        val result = plan(menu(1 to listOf("Fruta"), 2 to listOf("Fruta"), 3 to listOf("fruta")))
        val dish = result.newDishes.single()
        assertEquals("Fruta", dish.name)
        assertEquals("new1", dish.id)
        assertEquals(listOf("new1", "new1", "new1"), result.entries.map { it.dishId })
    }

    @Test fun `plan creates new dishes without ingredients and with the author`() {
        val dish = plan(menu(1 to listOf("Fruta"))).newDishes.single()
        assertEquals(emptyList<Ingredient>(), dish.ingredients)
        assertNull(dish.note)
        assertEquals("Ana", dish.addedBy)
    }

    @Test fun `plan writes lunch entries with an ISO date, author and sequential order`() {
        val result = plan(menu(7 to listOf("Lentejas", "Merluza", "Ensalada", "Fruta")))
        assertEquals(listOf("2026-09-07"), result.entries.map { it.date }.distinct())
        assertEquals(listOf(MealSlot.LUNCH.name), result.entries.map { it.slot }.distinct())
        assertEquals(listOf(0, 1, 2, 3), result.entries.map { it.order })
        assertEquals(listOf("Lentejas", "Merluza", "Ensalada", "Fruta"), result.entries.map { it.name })
        assertEquals(listOf("Ana"), result.entries.map { it.addedBy }.distinct())
        assertEquals(listOf(""), result.entries.map { it.id }.distinct())
    }

    @Test fun `plan puts entries after what the lunch of that day already has`() {
        val existing = listOf(
            MealEntry(date = "2026-09-07", slot = MealSlot.LUNCH.name, name = "Sopa", order = 0),
            MealEntry(date = "2026-09-07", slot = MealSlot.LUNCH.name, name = "Pollo", order = 4)
        )
        val result = plan(menu(7 to listOf("Lentejas", "Fruta")), existing = existing)
        assertEquals(listOf(5, 6), result.entries.map { it.order })
    }

    @Test fun `plan ignores entries of other slots and other days when ordering and skipping`() {
        val existing = listOf(
            MealEntry(date = "2026-09-07", slot = MealSlot.DINNER.name, name = "Lentejas", order = 9),
            MealEntry(date = "2026-09-08", slot = MealSlot.LUNCH.name, name = "Lentejas", order = 9)
        )
        val result = plan(menu(7 to listOf("Lentejas")), existing = existing)
        assertEquals(listOf(0), result.entries.map { it.order })
        assertEquals(0, result.alreadyPresent)
    }

    @Test fun `plan skips dishes already in that lunch and counts them`() {
        val existing = listOf(MealEntry(date = "2026-09-07", slot = MealSlot.LUNCH.name, name = "lentejas", order = 0))
        val result = plan(menu(7 to listOf("Lentejas", "Fruta")), existing = existing)
        assertEquals(listOf("Fruta"), result.entries.map { it.name })
        assertEquals(1, result.alreadyPresent)
    }

    @Test fun `plan does not create a dish that is skipped as already present`() {
        val existing = listOf(MealEntry(date = "2026-09-07", slot = MealSlot.LUNCH.name, name = "Lentejas", order = 0))
        val result = plan(menu(7 to listOf("Lentejas")), existing = existing)
        assertTrue(result.newDishes.isEmpty())
        assertTrue(result.entries.isEmpty())
    }

    @Test fun `importing the same photo twice adds nothing the second time`() {
        val photo = menu(7 to listOf("Lentejas", "Fruta"), 8 to listOf("Paella", "Fruta"))
        val first = plan(photo)
        val second = plan(photo, dishes = first.newDishes, existing = first.entries)
        assertTrue(second.entries.isEmpty())
        assertTrue(second.newDishes.isEmpty())
        assertEquals(4, second.alreadyPresent)
    }

    @Test fun `plan skips a dish repeated in a day after the user edited names`() {
        val result = plan(menu(7 to listOf("Fruta", "fruta")))
        assertEquals(1, result.entries.size)
        assertEquals(1, result.alreadyPresent)
    }

    @Test fun `plan drops days that do not exist in the chosen month and counts them`() {
        val result = plan(menu(30 to listOf("A"), 31 to listOf("B"), 1 to listOf("C")))
        assertEquals(listOf("2026-09-01", "2026-09-30"), result.entries.map { it.date }.sorted())
        assertEquals(1, result.droppedDays)
        assertEquals(listOf("A", "C"), result.newDishes.map { it.name }.sorted())
    }

    // ---- weekendDayCount ----

    @Test fun `weekend count is the number of read days that fall on saturday or sunday`() {
        // Septiembre de 2026: el 4 es viernes, el 5 sábado, el 6 domingo y el 7 lunes.
        val parsed = menu(4 to listOf("A"), 5 to listOf("B"), 6 to listOf("C"), 7 to listOf("D"))
        assertEquals(2, weekendDayCount(parsed, september))
    }

    @Test fun `weekend count is zero when every day is a weekday`() {
        assertEquals(0, weekendDayCount(menu(1 to listOf("A"), 2 to listOf("B"), 7 to listOf("C")), september))
    }

    @Test fun `weekend count ignores days that do not exist in the month`() {
        assertEquals(0, weekendDayCount(menu(31 to listOf("A")), september))
    }
}
