package com.homepantry.app.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuCalendarTest {
    private fun entry(date: String, slot: MealSlot = MealSlot.LUNCH, order: Int = 0, id: String = "x") =
        MealEntry(id = id, date = date, slot = slot.name, name = "n", order = order)

    @Test fun `weekStart of a Sunday is the previous Monday`() {
        assertEquals(LocalDate.parse("2026-09-14"), weekStart(LocalDate.parse("2026-09-20")))
    }

    @Test fun `weekStart of a Monday is itself`() {
        assertEquals(LocalDate.parse("2026-09-21"), weekStart(LocalDate.parse("2026-09-21")))
    }

    @Test fun `weekDays returns seven consecutive days`() {
        val days = weekDays(LocalDate.parse("2026-09-21"))
        assertEquals(7, days.size)
        assertEquals(LocalDate.parse("2026-09-21"), days.first())
        assertEquals(LocalDate.parse("2026-09-27"), days.last())
    }

    @Test fun `monthGrid covers whole weeks with neighbour days`() {
        val grid = monthGrid(YearMonth.of(2026, 9))
        assertEquals(35, grid.size)
        assertEquals(LocalDate.parse("2026-08-31"), grid.first())
        assertEquals(LocalDate.parse("2026-10-04"), grid.last())
    }

    @Test fun `monthGrid of a month that fits exactly four weeks`() {
        val grid = monthGrid(YearMonth.of(2027, 2))
        assertEquals(28, grid.size)
        assertEquals(LocalDate.parse("2027-02-01"), grid.first())
        assertEquals(LocalDate.parse("2027-02-28"), grid.last())
    }

    @Test fun `pageWeekStart moves by weeks from the anchor page`() {
        val anchor = LocalDate.parse("2026-09-20")
        assertEquals(LocalDate.parse("2026-09-14"), pageWeekStart(anchor, 5000, 5000))
        assertEquals(LocalDate.parse("2026-09-21"), pageWeekStart(anchor, 5001, 5000))
        assertEquals(LocalDate.parse("2026-09-07"), pageWeekStart(anchor, 4999, 5000))
    }

    @Test fun `pageWeekStart crosses the year boundary`() {
        val anchor = LocalDate.parse("2026-12-31")
        assertEquals(LocalDate.parse("2027-01-04"), pageWeekStart(anchor, 5001, 5000))
    }

    @Test fun `pageMonth moves by months and crosses years`() {
        val anchor = YearMonth.of(2026, 12)
        assertEquals(YearMonth.of(2027, 1), pageMonth(anchor, 5001, 5000))
        assertEquals(YearMonth.of(2025, 12), pageMonth(anchor, 4988, 5000))
    }

    @Test fun `pageForWeek is the inverse of pageWeekStart`() {
        val anchor = LocalDate.parse("2026-09-20")
        assertEquals(5000, pageForWeek(anchor, LocalDate.parse("2026-09-15"), 5000))
        assertEquals(5001, pageForWeek(anchor, LocalDate.parse("2026-09-27"), 5000))
        assertEquals(4999, pageForWeek(anchor, LocalDate.parse("2026-09-08"), 5000))
    }

    @Test fun `pageForMonth is the inverse of pageMonth`() {
        assertEquals(5004, pageForMonth(YearMonth.of(2026, 9), YearMonth.of(2027, 1), 5000))
        assertEquals(4998, pageForMonth(YearMonth.of(2026, 9), YearMonth.of(2026, 7), 5000))
    }

    @Test fun `visibleRange for a week covers previous, current and next week`() {
        assertEquals(
            "2026-09-14" to "2026-10-04",
            visibleRange(CalendarMode.WEEK, LocalDate.parse("2026-09-23"))
        )
    }

    @Test fun `visibleRange for a month covers previous, current and next month grids`() {
        assertEquals(
            "2026-07-27" to "2026-11-01",
            visibleRange(CalendarMode.MONTH, LocalDate.parse("2026-09-15"))
        )
    }

    @Test fun `weekRangeLabel within one month`() {
        assertEquals("21–27 sep", weekRangeLabel(LocalDate.parse("2026-09-21")))
    }

    @Test fun `weekRangeLabel across two months`() {
        assertEquals("31 ago – 6 sep", weekRangeLabel(LocalDate.parse("2026-08-31")))
    }

    @Test fun `monthLabel and dayLabel are in Spanish`() {
        assertEquals("septiembre 2026", monthLabel(YearMonth.of(2026, 9)))
        assertEquals("Lunes 21 de septiembre", dayLabel(LocalDate.parse("2026-09-21")))
        assertEquals("Domingo 20 de septiembre", dayLabel(LocalDate.parse("2026-09-20")))
    }

    @Test fun `shiftEntries moves dates by whole weeks and clears ids`() {
        val source = listOf(
            entry("2026-09-14", MealSlot.BREAKFAST, order = 1, id = "a"),
            entry("2026-09-20", MealSlot.DINNER, order = 0, id = "b")
        )
        val shifted = shiftEntries(source, LocalDate.parse("2026-09-14"), LocalDate.parse("2026-09-28"))
        assertEquals(listOf("2026-09-28", "2026-10-04"), shifted.map { it.date })
        assertEquals(listOf(MealSlot.BREAKFAST.name, MealSlot.DINNER.name), shifted.map { it.slot })
        assertEquals(listOf(1, 0), shifted.map { it.order })
        assertEquals(listOf("", ""), shifted.map { it.id })
    }
}
