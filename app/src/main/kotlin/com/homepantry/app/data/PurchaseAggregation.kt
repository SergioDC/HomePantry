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
    /** Precio unitario y unidad de la compra más reciente del grupo. */
    val latestUnitPrice: Double,
    val latestUnit: String,
    val purchases: List<Purchase>
)

/**
 * Productos comprados en un supermercado. `storeKey` (y `displayName`) vacíos
 * corresponden a las compras sin supermercado.
 */
data class StoreSection(
    val storeKey: String,
    val displayName: String,
    val products: List<ProductSummary>
)

private fun yearMonthOf(date: Date): String = SimpleDateFormat("yyyy-MM", Locale.US).format(date)

/** Precio por unidad (ud, kg o l): total de la línea entre la cantidad; una cantidad no positiva cuenta como 1. */
fun Purchase.unitPrice(): Double = price / (if (quantity > 0) quantity else 1.0)

/** Clave para agrupar supermercados: "MERCADONA " y "Mercadona" son el mismo. Vacía = sin supermercado. */
fun storeKey(store: String?): String = store?.trim()?.lowercase().orEmpty()

/** Sufijo de unidad para mostrar junto a un precio o cantidad ("€/kg"). */
fun unitSuffix(unit: String): String = when (unit) {
    "KG" -> "kg"
    "L" -> "l"
    else -> "ud"
}

/** Cantidad sin decimales si es entera, y con coma y sin ceros finales si no ("2", "0,85"). */
fun formatQuantity(quantity: Double): String =
    if (quantity == quantity.toLong().toDouble()) {
        quantity.toLong().toString()
    } else {
        String.format(Locale("es", "ES"), "%.3f", quantity).trimEnd('0').trimEnd(',')
    }

/** Supermercados ya usados, sin repetir (por clave), con la grafía más reciente y el más reciente primero. */
fun knownStores(purchases: List<Purchase>): List<String> =
    purchases.filter { storeKey(it.store).isNotEmpty() }
        .sortedByDescending { it.date }
        .distinctBy { storeKey(it.store) }
        .map { it.store!!.trim() }

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
            val latest = sortedByDateDesc.first()
            ProductSummary(
                normalizedName = normalizedName,
                displayName = latest.rawName,
                currentMonthTotal = group.filter { yearMonthOf(it.date) == currentYearMonth }.sumOf { it.price },
                allTimeTotal = group.sumOf { it.price },
                latestUnitPrice = latest.unitPrice(),
                latestUnit = latest.unit,
                purchases = sortedByDateDesc
            )
        }
        .sortedByDescending { it.allTimeTotal }
}

/**
 * Agrupa las compras por supermercado (clave normalizada) para el historial.
 * Los supermercados se ordenan por su compra más reciente, con "sin
 * supermercado" siempre al final; dentro de cada uno, el producto comprado más
 * recientemente primero. Un producto comprado en dos supermercados aparece en
 * ambos, cada vez con su propio último precio unitario.
 */
fun storeSections(purchases: List<Purchase>, referenceDate: Date = Date()): List<StoreSection> =
    purchases.groupBy { storeKey(it.store) }
        .map { (key, group) ->
            val newest = group.maxBy { it.date }
            val section = StoreSection(
                storeKey = key,
                displayName = newest.store?.trim().orEmpty(),
                products = productSummaries(group, referenceDate)
                    .sortedByDescending { it.purchases.first().date }
            )
            section to newest.date
        }
        .sortedWith(
            compareBy<Pair<StoreSection, Date>>({ it.first.storeKey.isEmpty() }, { -it.second.time })
        )
        .map { it.first }
