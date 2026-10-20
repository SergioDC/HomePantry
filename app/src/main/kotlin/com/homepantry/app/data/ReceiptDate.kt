package com.homepantry.app.data

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Antigüedad máxima admitida para la fecha leída de un ticket; más atrás suele ser una lectura errónea. */
private const val MAX_RECEIPT_AGE_YEARS = 2

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

/**
 * Fechas como las imprimen los súper españoles: "18/10/2026" (Mercadona), "04.10.26" (Lidl),
 * "30-09-2026" (Aldi). Los dígitos pegados a ambos lados se excluyen para no leer una fecha
 * dentro de un teléfono o de un código; un precio ("1,50") no casa porque usa coma.
 */
private val PRINTED_DATE = Regex("""(?<![\d/.-])(\d{1,2})([/.-])(\d{1,2})\2(\d{4}|\d{2})(?![\d/.-])""")

private fun strictDate(year: Int, month: Int, day: Int): Date? {
    val cal = Calendar.getInstance().apply {
        isLenient = false
        clear()
        set(year, month - 1, day)
    }
    return try {
        cal.time
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** "2026-10-18" (el formato que se pide a Gemini) a medianoche local; null si no es una fecha real. */
fun parseIsoReceiptDate(text: String?): Date? {
    val trimmed = text?.trim().orEmpty()
    if (!ISO_DATE.matches(trimmed)) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(trimmed)
    } catch (e: ParseException) {
        null
    }
}

/**
 * Devuelve [date] solo si puede ser la fecha de un ticket: ni posterior a hoy ni de hace más de
 * [MAX_RECEIPT_AGE_YEARS] años. Así una caducidad, una promoción o una lectura errónea no
 * mandan el ticket a un mes que no le toca.
 */
fun plausibleReceiptDate(date: Date?, today: Date): Date? {
    if (date == null) return null
    val endOfToday = Calendar.getInstance().apply {
        time = today
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.time
    val oldest = Calendar.getInstance().apply {
        time = today
        add(Calendar.YEAR, -MAX_RECEIPT_AGE_YEARS)
    }.time
    return date.takeIf { !it.after(endOfToday) && !it.before(oldest) }
}

/** Primera fecha plausible impresa en las líneas del OCR clásico, o null. */
fun detectReceiptDate(lines: List<String>, today: Date): Date? =
    lines.asSequence()
        .flatMap { PRINTED_DATE.findAll(it) }
        .mapNotNull { match ->
            val (day, _, month, year) = match.destructured
            val fullYear = year.toInt().let { if (year.length == 2) 2000 + it else it }
            plausibleReceiptDate(strictDate(fullYear, month.toInt(), day.toInt()), today)
        }
        .firstOrNull()

/**
 * El `DatePicker` de Material 3 trabaja con la medianoche UTC del día; la app, con fechas
 * locales. Estas dos funciones pasan de una a otra conservando año, mes y día.
 */
fun localDayToUtcMillis(day: Date): Long {
    val local = Calendar.getInstance().apply { time = day }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun utcMillisToLocalDay(millis: Long): Date {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.time
}

/**
 * El día elegido para el ticket con la hora de [now]: los tickets del mismo día mantienen el
 * orden en que se escanearon y la detección de duplicados (por día) sigue funcionando.
 */
fun ticketTimestamp(day: Date, now: Date): Date {
    val time = Calendar.getInstance().apply { this.time = now }
    return Calendar.getInstance().apply {
        this.time = day
        set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, time.get(Calendar.MINUTE))
        set(Calendar.SECOND, time.get(Calendar.SECOND))
        set(Calendar.MILLISECOND, time.get(Calendar.MILLISECOND))
    }.time
}
