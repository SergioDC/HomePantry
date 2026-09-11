package com.homepantry.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gasto total agregado para un mes concreto (formato "yyyy-MM"). */
data class MonthlySpend(val yearMonth: String, val total: Double)

/** Resumen de un producto del histórico: agrupado por `normalizedName`. */
data class ProductSummary(
    val normalizedName: String,
    val displayName: String,
    val currentMonthTotal: Double,
    val allTimeTotal: Double,
    val purchases: List<Purchase>
)

private val YEAR_MONTH_FORMAT = SimpleDateFormat("yyyy-MM", Locale.US)

private fun yearMonthOf(date: Date): String = YEAR_MONTH_FORMAT.format(date)

/** Gasto total por mes, sin distinguir producto, más reciente primero. */
fun monthlySpend(purchases: List<Purchase>): List<MonthlySpend> =
    purchases.groupBy { yearMonthOf(it.date) }
        .map { (yearMonth, group) -> MonthlySpend(yearMonth, group.sumOf { it.price }) }
        .sortedByDescending { it.yearMonth }

/**
 * Agrupa las compras por `normalizedName` para el histórico. `displayName`
 * usa el `rawName` de la compra más reciente del grupo (los nombres pueden
 * variar ligeramente entre tickets).
 */
fun productSummaries(purchases: List<Purchase>, referenceDate: Date = Date()): List<ProductSummary> {
    val currentYearMonth = yearMonthOf(referenceDate)
    return purchases.groupBy { it.normalizedName }
        .map { (normalizedName, group) ->
            val sortedByDateDesc = group.sortedByDescending { it.date }
            ProductSummary(
                normalizedName = normalizedName,
                displayName = sortedByDateDesc.first().rawName,
                currentMonthTotal = group.filter { yearMonthOf(it.date) == currentYearMonth }.sumOf { it.price },
                allTimeTotal = group.sumOf { it.price },
                purchases = sortedByDateDesc
            )
        }
        .sortedByDescending { it.allTimeTotal }
}
