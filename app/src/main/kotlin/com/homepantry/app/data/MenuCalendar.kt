package com.homepantry.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class CalendarMode { WEEK, MONTH }

/** El pager es "infinito": la página de "hoy" es la del medio. */
const val PAGER_ANCHOR_PAGE = 5000
const val PAGER_PAGE_COUNT = 10000

val DAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val MONTH_NAMES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)
private val MONTH_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Lunes de la semana de [date]. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun weekDays(start: LocalDate): List<LocalDate> = (0L until 7L).map { start.plusDays(it) }

/** Semanas completas (lunes a domingo) que cubren el mes, con días de relleno de los meses vecinos. */
fun monthGrid(month: YearMonth): List<LocalDate> {
    val first = weekStart(month.atDay(1))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val count = ChronoUnit.DAYS.between(first, last).toInt() + 1
    return (0 until count).map { first.plusDays(it.toLong()) }
}

fun pageWeekStart(anchor: LocalDate, page: Int, anchorPage: Int): LocalDate =
    weekStart(anchor).plusWeeks((page - anchorPage).toLong())

fun pageMonth(anchor: YearMonth, page: Int, anchorPage: Int): YearMonth =
    anchor.plusMonths((page - anchorPage).toLong())

fun pageForWeek(anchor: LocalDate, date: LocalDate, anchorPage: Int): Int =
    anchorPage + ChronoUnit.WEEKS.between(weekStart(anchor), weekStart(date)).toInt()

fun pageForMonth(anchor: YearMonth, month: YearMonth, anchorPage: Int): Int =
    anchorPage + ChronoUnit.MONTHS.between(anchor, month).toInt()

/**
 * Rango de fechas ("yyyy-MM-dd", ambos incluidos) que hay que tener cargado para la página visible
 * de [date]: la anterior, la actual y la siguiente, para que al deslizar la vecina ya esté cargada.
 */
fun visibleRange(mode: CalendarMode, date: LocalDate): Pair<String, String> = when (mode) {
    CalendarMode.WEEK -> {
        val start = weekStart(date)
        start.minusWeeks(1).toString() to start.plusWeeks(1).plusDays(6).toString()
    }
    CalendarMode.MONTH -> {
        val month = YearMonth.from(date)
        monthGrid(month.minusMonths(1)).first().toString() to monthGrid(month.plusMonths(1)).last().toString()
    }
}

/** "21–27 sep", o "31 ago – 6 sep" si la semana cruza de mes. */
fun weekRangeLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    val endMonth = MONTH_SHORT[end.monthValue - 1]
    return if (start.month == end.month) {
        "${start.dayOfMonth}–${end.dayOfMonth} $endMonth"
    } else {
        "${start.dayOfMonth} ${MONTH_SHORT[start.monthValue - 1]} – ${end.dayOfMonth} $endMonth"
    }
}

fun monthLabel(month: YearMonth): String = "${MONTH_NAMES[month.monthValue - 1]} ${month.year}"

/** "Lunes 21 de septiembre". */
fun dayLabel(date: LocalDate): String {
    val dayName = DAY_NAMES[date.dayOfWeek.value - 1].replaceFirstChar { it.uppercase() }
    return "$dayName ${date.dayOfMonth} de ${MONTH_NAMES[date.monthValue - 1]}"
}

/**
 * Copias de [entries] (sin id) desplazadas de la semana [fromWeekStart] a la [toWeekStart],
 * conservando día de la semana, franja y orden.
 */
fun shiftEntries(entries: List<MealEntry>, fromWeekStart: LocalDate, toWeekStart: LocalDate): List<MealEntry> {
    val days = ChronoUnit.DAYS.between(fromWeekStart, toWeekStart)
    return entries.map { entry ->
        entry.copy(id = "", date = LocalDate.parse(entry.date).plusDays(days).toString())
    }
}
