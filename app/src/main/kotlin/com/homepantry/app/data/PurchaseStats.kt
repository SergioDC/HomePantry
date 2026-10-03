package com.homepantry.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Resumen del mes de referencia. `previousTotal` es null si el mes anterior no tiene compras. */
data class MonthSummary(
    val total: Double,
    val previousTotal: Double?,
    val ticketCount: Int,
    val averagePerTicket: Double
)

/** Gasto de un supermercado en el mes; `fraction` (0..1) sobre el total del mes. */
data class StoreSpend(val storeKey: String, val displayName: String, val total: Double, val fraction: Double)

enum class TopBy { SPEND, COUNT }

/** `count` es el número de tickets distintos que contienen el producto. */
data class TopProduct(val normalizedName: String, val displayName: String, val total: Double, val count: Int)

data class PriceRise(
    val normalizedName: String,
    val displayName: String,
    val unit: String,
    val firstUnitPrice: Double,
    val latestUnitPrice: Double,
    val changeFraction: Double
)

data class StoreComparison(
    val normalizedName: String,
    val displayName: String,
    val unit: String,
    val cheapestStore: String,
    val cheapestUnitPrice: Double,
    val dearestUnitPrice: Double,
    val savingFraction: Double
)

private fun yearMonth(date: Date): String = SimpleDateFormat("yyyy-MM", Locale.US).format(date)

/** Clave "yyyy-MM" del mes de `date` desplazado `offset` meses. */
private fun monthKey(date: Date, offset: Int = 0): String {
    val cal = Calendar.getInstance().apply {
        time = date
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offset)
    }
    return yearMonth(cal.time)
}

private fun List<Purchase>.inMonth(key: String) = filter { yearMonth(it.date) == key }

fun monthSummary(purchases: List<Purchase>, referenceDate: Date = Date()): MonthSummary {
    val current = purchases.inMonth(monthKey(referenceDate))
    val previous = purchases.inMonth(monthKey(referenceDate, -1))
    val total = current.sumOf { it.price }
    val tickets = current.map { ticketKey(it) }.distinct().size
    return MonthSummary(
        total = total,
        previousTotal = if (previous.isEmpty()) null else previous.sumOf { it.price },
        ticketCount = tickets,
        averagePerTicket = if (tickets == 0) 0.0 else total / tickets
    )
}

/** Gasto de los últimos `months` meses (el actual incluido), en orden cronológico; huecos a 0. */
fun lastMonthsSpend(purchases: List<Purchase>, months: Int = 6, referenceDate: Date = Date()): List<MonthlySpend> {
    val byMonth = monthlySpend(purchases).associate { it.yearMonth to it.total }
    return (months - 1 downTo 0).map { back ->
        val key = monthKey(referenceDate, -back)
        MonthlySpend(key, byMonth[key] ?: 0.0)
    }
}

fun spendByStore(purchases: List<Purchase>, referenceDate: Date = Date()): List<StoreSpend> {
    val current = purchases.inMonth(monthKey(referenceDate))
    val monthTotal = current.sumOf { it.price }
    return current.groupBy { storeKey(it.store) }
        .map { (key, group) ->
            val total = group.sumOf { it.price }
            StoreSpend(
                storeKey = key,
                displayName = group.maxBy { it.date }.store?.trim().orEmpty(),
                total = total,
                fraction = if (monthTotal == 0.0) 0.0 else total / monthTotal
            )
        }
        .sortedByDescending { it.total }
}

fun topProducts(purchases: List<Purchase>, by: TopBy, limit: Int = 5): List<TopProduct> =
    purchases.groupBy { it.normalizedName }
        .map { (name, group) ->
            TopProduct(
                normalizedName = name,
                displayName = group.maxBy { it.date }.rawName,
                total = group.sumOf { it.price },
                count = group.map { ticketKey(it) }.distinct().size
            )
        }
        .sortedWith(
            when (by) {
                TopBy.SPEND -> compareByDescending<TopProduct> { it.total }.thenByDescending { it.count }
                TopBy.COUNT -> compareByDescending<TopProduct> { it.count }.thenByDescending { it.total }
            }
        )
        .take(limit)

/** Subida del precio unitario entre la primera y la última compra, por producto y unidad. */
fun priceRises(purchases: List<Purchase>, limit: Int = 5): List<PriceRise> =
    purchases.groupBy { it.normalizedName to it.unit }
        .mapNotNull { (key, group) ->
            if (group.size < 2) return@mapNotNull null
            val sorted = group.sortedBy { it.date }
            val first = sorted.first().unitPrice()
            val last = sorted.last().unitPrice()
            if (first <= 0.0 || last <= first) return@mapNotNull null
            PriceRise(
                normalizedName = key.first,
                displayName = sorted.last().rawName,
                unit = key.second,
                firstUnitPrice = first,
                latestUnitPrice = last,
                changeFraction = last / first - 1.0
            )
        }
        .sortedByDescending { it.changeFraction }
        .distinctBy { it.normalizedName }
        .take(limit)

/** Productos comprados en 2+ supermercados con la misma unidad: el más barato y cuánto se ahorra. */
fun cheapestStores(purchases: List<Purchase>, limit: Int = 5): List<StoreComparison> =
    purchases.filter { storeKey(it.store).isNotEmpty() }
        .groupBy { it.normalizedName to it.unit }
        .mapNotNull { (key, group) ->
            val perStore = group.groupBy { storeKey(it.store) }
                .map { (_, lines) ->
                    lines.maxBy { it.date }.store!!.trim() to lines.map { it.unitPrice() }.average()
                }
            if (perStore.size < 2) return@mapNotNull null
            val cheapest = perStore.minBy { it.second }
            val dearest = perStore.maxBy { it.second }
            if (dearest.second <= 0.0 || dearest.second <= cheapest.second) return@mapNotNull null
            StoreComparison(
                normalizedName = key.first,
                displayName = group.maxBy { it.date }.rawName,
                unit = key.second,
                cheapestStore = cheapest.first,
                cheapestUnitPrice = cheapest.second,
                dearestUnitPrice = dearest.second,
                savingFraction = 1.0 - cheapest.second / dearest.second
            )
        }
        .sortedByDescending { it.savingFraction }
        .distinctBy { it.normalizedName }
        .take(limit)
