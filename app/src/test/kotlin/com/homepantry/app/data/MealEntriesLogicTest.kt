package com.homepantry.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealEntriesLogicTest {
    private fun entry(date: String, slot: MealSlot, order: Int = 0, id: String = "x", dishId: String? = null) =
        MealEntry(id = id, date = date, slot = slot.name, dishId = dishId, name = "n", order = order)

    private val from = LocalDate.parse("2026-09-14")
    private val to = LocalDate.parse("2026-09-28")

    private val source = listOf(
        entry("2026-09-14", MealSlot.LUNCH, 0, id = "a"),
        entry("2026-09-15", MealSlot.DINNER, 0, id = "b")
    )
    private val target = listOf(
        entry("2026-09-28", MealSlot.LUNCH, 0, id = "t1"),
        entry("2026-09-28", MealSlot.LUNCH, 1, id = "t2")
    )

    @Test fun `merge into an empty week copies everything unchanged`() {
        val plan = planDuplicateWeek(source, emptyList(), from, to, DuplicateMode.MERGE)
        assertEquals(listOf("2026-09-28", "2026-09-29"), plan.toWrite.map { it.date })
        assertEquals(listOf(0, 0), plan.toWrite.map { it.order })
        assertEquals(emptyList<String>(), plan.toDelete)
    }

    @Test fun `merge puts copies after what the target slot already has`() {
        val plan = planDuplicateWeek(source, target, from, to, DuplicateMode.MERGE)
        assertEquals(listOf(2, 0), plan.toWrite.map { it.order })
        assertEquals(emptyList<String>(), plan.toDelete)
    }

    @Test fun `replace deletes the target entries and writes the copies`() {
        val plan = planDuplicateWeek(source, target, from, to, DuplicateMode.REPLACE)
        assertEquals(listOf("2026-09-28", "2026-09-29"), plan.toWrite.map { it.date })
        assertEquals(listOf(0, 0), plan.toWrite.map { it.order })
        assertEquals(listOf("t1", "t2"), plan.toDelete)
    }

    @Test fun `nextEntryOrder is one past the highest order in that slot`() {
        val entries = listOf(
            entry("2026-09-14", MealSlot.LUNCH, 0),
            entry("2026-09-14", MealSlot.LUNCH, 1),
            entry("2026-09-14", MealSlot.DINNER, 5)
        )
        assertEquals(2, nextEntryOrder(entries, "2026-09-14", MealSlot.LUNCH))
        assertEquals(0, nextEntryOrder(entries, "2026-09-14", MealSlot.BREAKFAST))
        assertEquals(0, nextEntryOrder(entries, "2026-09-15", MealSlot.LUNCH))
    }

    @Test fun `entriesFor filters by day and slot and sorts by order`() {
        val entries = listOf(
            entry("2026-09-14", MealSlot.LUNCH, 1, id = "second"),
            entry("2026-09-14", MealSlot.LUNCH, 0, id = "first"),
            entry("2026-09-14", MealSlot.DINNER, 0, id = "other")
        )
        assertEquals(listOf("first", "second"), entriesFor(entries, "2026-09-14", MealSlot.LUNCH).map { it.id })
    }

    private val dishes = listOf(
        Dish(id = "1", name = "Lentejas"),
        Dish(id = "2", name = "Tortilla de patatas"),
        Dish(id = "3", name = "Tortellini")
    )

    @Test fun `matchDish matches by normalized name`() {
        assertEquals("1", matchDish("  lentejas ", dishes)?.id)
        assertEquals("1", matchDish("Lenteja", dishes)?.id)
    }

    @Test fun `matchDish does not match a partial name`() {
        assertNull(matchDish("tortilla", dishes))
    }

    @Test fun `suggestDishes returns dishes containing the text sorted by name`() {
        assertEquals(listOf("3", "2"), suggestDishes("tort", dishes).map { it.id })
    }

    @Test fun `suggestDishes respects the limit and a blank text lists all`() {
        assertEquals(2, suggestDishes("", dishes, limit = 2).size)
        assertEquals(3, suggestDishes("  ", dishes).size)
    }

    @Test fun `resolvedDishId is null when the dish no longer exists`() {
        assertEquals("1", resolvedDishId(entry("2026-09-14", MealSlot.LUNCH, dishId = "1"), dishes))
        assertNull(resolvedDishId(entry("2026-09-14", MealSlot.LUNCH, dishId = "9"), dishes))
        assertNull(resolvedDishId(entry("2026-09-14", MealSlot.LUNCH, dishId = null), dishes))
    }
}
