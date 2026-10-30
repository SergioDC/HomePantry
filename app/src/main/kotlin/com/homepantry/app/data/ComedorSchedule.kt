package com.homepantry.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

/** Hora a la que se comprueba cada día de la ventana si hay menú nuevo del comedor. */
const val COMEDOR_CHECK_HOUR = 10

/** Días al final del mes en los que ya se busca el menú del mes siguiente. */
private const val WINDOW_DAYS_BEFORE = 3

/** Último día del mes en el que aún se busca el menú de ese mes (por si lo publican tarde). */
private const val WINDOW_LAST_DAY = 5

/** Tope de la búsqueda del próximo día de comprobación: más de un mes entero. */
private const val MAX_SEARCH_DAYS = 62L

/**
 * Mes cuyo menú se busca en [today]: el siguiente en los 3 últimos días del mes y el actual del día
 * 1 al 5. Fuera de esa ventana, null (no se comprueba, para no gastar datos ni batería).
 */
fun comedorTargetMonth(today: LocalDate): YearMonth? = when {
    today.dayOfMonth > today.lengthOfMonth() - WINDOW_DAYS_BEFORE -> YearMonth.from(today).plusMonths(1)
    today.dayOfMonth <= WINDOW_LAST_DAY -> YearMonth.from(today)
    else -> null
}

/**
 * Próxima comprobación después de [now]: el primer día siguiente dentro de la ventana, a las
 * [COMEDOR_CHECK_HOUR]. Si [gotTarget] (el menú del mes buscado ya está), se salta el resto de esta
 * ventana y se espera a la del mes siguiente.
 */
fun nextComedorCheck(now: ZonedDateTime, gotTarget: Boolean): ZonedDateTime {
    val today = now.toLocalDate()
    val currentTarget = comedorTargetMonth(today)
    var day = today.plusDays(1)
    repeat(MAX_SEARCH_DAYS.toInt()) {
        val target = comedorTargetMonth(day)
        if (target != null && !(gotTarget && target == currentTarget)) {
            return day.atTime(COMEDOR_CHECK_HOUR, 0).atZone(now.zone)
        }
        day = day.plusDays(1)
    }
    // No pasa con la ventana actual (cada mes tiene una); por si acaso, mañana.
    return today.plusDays(1).atTime(COMEDOR_CHECK_HOUR, 0).atZone(now.zone)
}

/** Primera programación al abrir la app: ya mismo si hoy toca comprobar; si no, la próxima ventana. */
fun initialComedorCheck(now: ZonedDateTime): ZonedDateTime =
    if (comedorTargetMonth(now.toLocalDate()) != null) now else nextComedorCheck(now, gotTarget = false)
