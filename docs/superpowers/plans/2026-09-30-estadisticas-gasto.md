# Estadísticas de gasto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pantalla de estadísticas de gasto (mes actual, últimos 6 meses, por supermercado, top productos, subidas de precio y supermercado más barato) calculada a partir de las `Purchase` existentes.

**Architecture:** Funciones puras en `data/PurchaseStats.kt` (sin Android ni Firestore) con tests JUnit; una pantalla Compose `PurchaseStatsScreen` que solo pinta sus resultados, con barras hechas con `Box`. Se abre desde un botón en la cabecera del historial.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit 4. Sin librería de gráficos.

**Spec:** `docs/superpowers/specs/2026-09-30-estadisticas-gasto-design.md`

## Global Constraints

- Sin librería de gráficos nueva ni dependencias nuevas (no engordar el APK).
- No se cambia el modelo `Purchase` ni Firestore.
- Precios unitarios solo se comparan con la misma `unit`.
- Importes con formato es-ES (`Locale("es", "ES")`); textos en `res/values/strings.xml`.
- Cada función de `PurchaseStats.kt` recibe `referenceDate: Date = Date()` cuando depende del mes actual.
- No hacer `git commit` sin que el usuario lo pida (regla de su CLAUDE.md global).

## Review Focus

- Sin ninguna compra: todas las funciones devuelven listas vacías o ceros, sin excepción (Task 1, test `empty purchases`).
- Mes anterior sin datos: `previousTotal` es null y la UI no muestra variación (Task 1).
- Compras sin supermercado: agrupadas con `displayName` vacío, la UI las llama "Sin supermercado" (Task 1 y 2).
- Un producto con la misma `unit` comprada solo una vez: no aparece en subidas de precio (Task 1).
- Un producto con unidades mezcladas (UD y KG): no se comparan entre sí (Task 1).

---

### Task 1: Cálculo de estadísticas

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/PurchaseStats.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/PurchaseStatsTest.kt`

**Interfaces:**
- Consumes: `Purchase`, `Purchase.unitPrice()`, `ticketKey(Purchase)`, `storeKey(String?)`, `MonthlySpend`, `monthlySpend(List<Purchase>)` (todos de `PurchaseAggregation.kt`/`Purchase.kt`, paquete `com.homepantry.app.data`).
- Produces:
  - `data class MonthSummary(val total: Double, val previousTotal: Double?, val ticketCount: Int, val averagePerTicket: Double)`
  - `data class StoreSpend(val storeKey: String, val displayName: String, val total: Double, val fraction: Double)`
  - `enum class TopBy { SPEND, COUNT }`
  - `data class TopProduct(val normalizedName: String, val displayName: String, val total: Double, val count: Int)`
  - `data class PriceRise(val normalizedName: String, val displayName: String, val unit: String, val firstUnitPrice: Double, val latestUnitPrice: Double, val changeFraction: Double)`
  - `data class StoreComparison(val normalizedName: String, val displayName: String, val unit: String, val cheapestStore: String, val cheapestUnitPrice: Double, val dearestUnitPrice: Double, val savingFraction: Double)`
  - `fun monthSummary(purchases: List<Purchase>, referenceDate: Date = Date()): MonthSummary`
  - `fun lastMonthsSpend(purchases: List<Purchase>, months: Int = 6, referenceDate: Date = Date()): List<MonthlySpend>`
  - `fun spendByStore(purchases: List<Purchase>, referenceDate: Date = Date()): List<StoreSpend>`
  - `fun topProducts(purchases: List<Purchase>, by: TopBy, limit: Int = 5): List<TopProduct>`
  - `fun priceRises(purchases: List<Purchase>, limit: Int = 5): List<PriceRise>`
  - `fun cheapestStores(purchases: List<Purchase>, limit: Int = 5): List<StoreComparison>`

- [ ] **Step 1: Escribir los tests (fallan porque no existe el código)**

Crear `app/src/test/kotlin/com/homepantry/app/data/PurchaseStatsTest.kt`:

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PurchaseStatsTest {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun date(s: String) = format.parse(s)!!
    private val ref = date("2026-03-25")

    private fun p(
        name: String,
        price: Double,
        day: String,
        store: String? = null,
        ticket: String? = null,
        qty: Double = 1.0,
        unit: String = "UD"
    ) = Purchase(
        rawName = name, normalizedName = name.lowercase(), price = price, date = date(day),
        store = store, ticketId = ticket, quantity = qty, unit = unit
    )

    @Test fun `empty purchases give zeros and empty lists`() {
        val none = emptyList<Purchase>()
        assertEquals(MonthSummary(0.0, null, 0, 0.0), monthSummary(none, ref))
        assertEquals(6, lastMonthsSpend(none, 6, ref).size)
        assertTrue(lastMonthsSpend(none, 6, ref).all { it.total == 0.0 })
        assertTrue(spendByStore(none, ref).isEmpty())
        assertTrue(topProducts(none, TopBy.SPEND).isEmpty())
        assertTrue(priceRises(none).isEmpty())
        assertTrue(cheapestStores(none).isEmpty())
    }

    @Test fun `monthSummary totals the month, counts tickets and averages`() {
        val purchases = listOf(
            p("a", 10.0, "2026-03-02", ticket = "t1"),
            p("b", 5.0, "2026-03-02", ticket = "t1"),
            p("c", 20.0, "2026-03-10", ticket = "t2"),
            p("d", 30.0, "2026-02-15", ticket = "t3")
        )
        val s = monthSummary(purchases, ref)
        assertEquals(35.0, s.total, 0.001)
        assertEquals(30.0, s.previousTotal!!, 0.001)
        assertEquals(2, s.ticketCount)
        assertEquals(17.5, s.averagePerTicket, 0.001)
    }

    @Test fun `monthSummary previousTotal is null without previous month data`() {
        val s = monthSummary(listOf(p("a", 10.0, "2026-03-02", ticket = "t1")), ref)
        assertNull(s.previousTotal)
    }

    @Test fun `monthSummary handles the year boundary`() {
        val s = monthSummary(listOf(p("a", 8.0, "2025-12-20", ticket = "t1")), date("2026-01-05"))
        assertEquals(8.0, s.previousTotal!!, 0.001)
    }

    @Test fun `lastMonthsSpend is chronological and fills gaps with zero`() {
        val purchases = listOf(
            p("a", 10.0, "2026-03-02"),
            p("b", 4.0, "2026-01-10")
        )
        val result = lastMonthsSpend(purchases, 3, ref)
        assertEquals(
            listOf(MonthlySpend("2026-01", 4.0), MonthlySpend("2026-02", 0.0), MonthlySpend("2026-03", 10.0)),
            result
        )
    }

    @Test fun `spendByStore groups current month, sorts by spend and keeps no-store apart`() {
        val purchases = listOf(
            p("a", 30.0, "2026-03-02", store = "Mercadona"),
            p("b", 10.0, "2026-03-03", store = " mercadona "),
            p("c", 10.0, "2026-03-04"),
            p("d", 99.0, "2026-02-04", store = "Lidl")
        )
        val result = spendByStore(purchases, ref)
        assertEquals(2, result.size)
        assertEquals("mercadona", result[0].storeKey)
        assertEquals(40.0, result[0].total, 0.001)
        assertEquals(0.8, result[0].fraction, 0.001)
        assertEquals("", result[1].storeKey)
        assertEquals("", result[1].displayName)
    }

    @Test fun `topProducts by spend and by count`() {
        val purchases = listOf(
            p("Leche", 1.0, "2026-03-01", ticket = "t1"),
            p("Leche", 1.0, "2026-03-08", ticket = "t2"),
            p("Leche", 1.0, "2026-03-15", ticket = "t3"),
            p("Jamon", 12.0, "2026-03-01", ticket = "t1")
        )
        assertEquals("jamon", topProducts(purchases, TopBy.SPEND).first().normalizedName)
        val byCount = topProducts(purchases, TopBy.COUNT).first()
        assertEquals("leche", byCount.normalizedName)
        assertEquals(3, byCount.count)
    }

    @Test fun `topProducts counts a product once per ticket`() {
        val purchases = listOf(
            p("Leche", 1.0, "2026-03-01", ticket = "t1"),
            p("Leche", 1.0, "2026-03-01", ticket = "t1")
        )
        assertEquals(1, topProducts(purchases, TopBy.COUNT).first().count)
    }

    @Test fun `topProducts respects the limit`() {
        val purchases = (1..8).map { p("prod$it", it.toDouble(), "2026-03-01", ticket = "t$it") }
        assertEquals(5, topProducts(purchases, TopBy.SPEND).size)
    }

    @Test fun `priceRises lists products whose unit price went up`() {
        val purchases = listOf(
            p("Aceite", 4.0, "2026-01-01", unit = "L"),
            p("Aceite", 5.0, "2026-03-01", unit = "L"),
            p("Leche", 1.0, "2026-01-01", unit = "L"),
            p("Leche", 0.9, "2026-03-01", unit = "L")
        )
        val rises = priceRises(purchases)
        assertEquals(1, rises.size)
        assertEquals("aceite", rises[0].normalizedName)
        assertEquals(0.25, rises[0].changeFraction, 0.001)
    }

    @Test fun `priceRises ignores products bought once and mixed units`() {
        val purchases = listOf(
            p("Queso", 3.0, "2026-01-01", unit = "UD"),
            p("Queso", 9.0, "2026-03-01", unit = "KG"),
            p("Pan", 1.0, "2026-03-01")
        )
        assertTrue(priceRises(purchases).isEmpty())
    }

    @Test fun `cheapestStores compares average unit price between stores`() {
        val purchases = listOf(
            p("Arroz", 1.0, "2026-03-01", store = "Lidl", unit = "KG"),
            p("Arroz", 1.5, "2026-03-02", store = "Mercadona", unit = "KG")
        )
        val c = cheapestStores(purchases).single()
        assertEquals("Lidl", c.cheapestStore)
        assertEquals(1.0, c.cheapestUnitPrice, 0.001)
        assertEquals(1.5, c.dearestUnitPrice, 0.001)
        assertEquals(1.0 / 3.0, c.savingFraction, 0.001)
    }

    @Test fun `cheapestStores needs two stores with the same unit`() {
        val oneStore = listOf(
            p("Arroz", 1.0, "2026-03-01", store = "Lidl", unit = "KG"),
            p("Arroz", 1.2, "2026-03-09", store = "lidl", unit = "KG")
        )
        val mixedUnits = listOf(
            p("Queso", 1.0, "2026-03-01", store = "Lidl", unit = "KG"),
            p("Queso", 2.0, "2026-03-01", store = "Dia", unit = "UD")
        )
        val noStore = listOf(p("Pan", 1.0, "2026-03-01"), p("Pan", 2.0, "2026-03-02", store = "Dia"))
        assertTrue(cheapestStores(oneStore).isEmpty())
        assertTrue(cheapestStores(mixedUnits).isEmpty())
        assertTrue(cheapestStores(noStore).isEmpty())
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PurchaseStatsTest"`
Expected: FAIL de compilación (`Unresolved reference: monthSummary`, etc.).

- [ ] **Step 3: Implementar**

Crear `app/src/main/kotlin/com/homepantry/app/data/PurchaseStats.kt`:

```kotlin
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

private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

/** Clave "yyyy-MM" del mes de `date` desplazado `offset` meses. */
private fun monthKey(date: Date, offset: Int = 0): String {
    val cal = Calendar.getInstance().apply {
        time = date
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offset)
    }
    return monthFormat.format(cal.time)
}

private fun List<Purchase>.inMonth(key: String) = filter { monthFormat.format(it.date) == key }

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
```

- [ ] **Step 4: Comprobar que pasa**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PurchaseStatsTest"`
Expected: PASS (12 tests). Si falla alguno por redondeo, ajustar el `delta` del test, no la lógica.

---

### Task 2: Pantalla y navegación

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseStatsScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt` (firma en la línea 73 y `TopAppBar` en la línea 170)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (ruta `purchaseHistory`, ~línea 294)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: todas las funciones y data classes de Task 1; `AppViewModel.state` con `purchases: List<Purchase>`.
- Produces: `fun PurchaseStatsScreen(viewModel: AppViewModel, onBack: () -> Unit, onOpenProduct: (String) -> Unit)`; nuevo parámetro `onOpenStats: () -> Unit` en `PurchaseHistoryScreen`.

- [ ] **Step 1: Strings**

Añadir dentro de `<resources>` en `app/src/main/res/values/strings.xml`:

```xml
    <string name="stats_title">Estadísticas</string>
    <string name="stats_open_cd">Ver estadísticas de gasto</string>
    <string name="stats_empty">Aún no hay compras. Escanea un ticket para ver estadísticas.</string>
    <string name="stats_this_month">Este mes</string>
    <string name="stats_vs_previous">%1$s frente al mes anterior</string>
    <string name="stats_tickets_avg">%1$d tickets · media %2$s</string>
    <string name="stats_last_months">Últimos 6 meses</string>
    <string name="stats_by_store">Por supermercado (este mes)</string>
    <string name="stats_no_store">Sin supermercado</string>
    <string name="stats_top_products">Productos que más compras</string>
    <string name="stats_by_spend">Por gasto</string>
    <string name="stats_by_count">Por veces</string>
    <string name="stats_times">%1$d veces</string>
    <string name="stats_price_rises">Han subido de precio</string>
    <string name="stats_cheapest">Dónde es más barato</string>
    <string name="stats_cheapest_line">%1$s · ahorras un %2$d %%</string>
    <string name="stats_none_yet">Sin datos suficientes todavía</string>
```

- [ ] **Step 2: Crear la pantalla**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseStatsScreen.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.MonthlySpend
import com.homepantry.app.data.TopBy
import com.homepantry.app.data.cheapestStores
import com.homepantry.app.data.lastMonthsSpend
import com.homepantry.app.data.monthSummary
import com.homepantry.app.data.priceRises
import com.homepantry.app.data.spendByStore
import com.homepantry.app.data.topProducts
import com.homepantry.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val esLocale = Locale("es", "ES")

private fun money(value: Double): String = String.format(esLocale, "%,.2f €", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseStatsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val purchases = state.purchases
    val now = remember { Date() }
    var topBy by rememberSaveable { mutableStateOf(TopBy.SPEND) }

    val summary = remember(purchases) { monthSummary(purchases, now) }
    val months = remember(purchases) { lastMonthsSpend(purchases, 6, now) }
    val stores = remember(purchases) { spendByStore(purchases, now) }
    val top = remember(purchases, topBy) { topProducts(purchases, topBy) }
    val rises = remember(purchases) { priceRises(purchases) }
    val cheapest = remember(purchases) { cheapestStores(purchases) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (purchases.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Section(stringResource(R.string.stats_this_month)) {
                    Text(money(summary.total), style = MaterialTheme.typography.headlineMedium)
                    summary.previousTotal?.let { previous ->
                        val diff = summary.total - previous
                        val sign = if (diff >= 0) "+" else "−"
                        val pct = if (previous > 0) " (${sign}${(kotlin.math.abs(diff) / previous * 100).roundToInt()} %)" else ""
                        Text(
                            stringResource(R.string.stats_vs_previous, "$sign${money(kotlin.math.abs(diff))}$pct"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        stringResource(R.string.stats_tickets_avg, summary.ticketCount, money(summary.averagePerTicket)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item { Section(stringResource(R.string.stats_last_months)) { MonthBars(months) } }
            item {
                Section(stringResource(R.string.stats_by_store)) {
                    if (stores.isEmpty()) Text(stringResource(R.string.stats_none_yet))
                    stores.forEach { store ->
                        LabeledBar(
                            label = store.displayName.ifEmpty { stringResource(R.string.stats_no_store) },
                            value = "${money(store.total)} · ${(store.fraction * 100).roundToInt()} %",
                            fraction = store.fraction.toFloat()
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.stats_top_products)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = topBy == TopBy.SPEND, onClick = { topBy = TopBy.SPEND },
                            label = { Text(stringResource(R.string.stats_by_spend)) }
                        )
                        FilterChip(
                            selected = topBy == TopBy.COUNT, onClick = { topBy = TopBy.COUNT },
                            label = { Text(stringResource(R.string.stats_by_count)) }
                        )
                    }
                    val max = top.maxOfOrNull { if (topBy == TopBy.SPEND) it.total else it.count.toDouble() } ?: 1.0
                    top.forEach { product ->
                        val value = if (topBy == TopBy.SPEND) product.total else product.count.toDouble()
                        Box(Modifier.clickable { onOpenProduct(product.normalizedName) }) {
                            LabeledBar(
                                label = product.displayName,
                                value = if (topBy == TopBy.SPEND) money(product.total)
                                else stringResource(R.string.stats_times, product.count),
                                fraction = (value / max).toFloat()
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.stats_price_rises)) {
                    if (rises.isEmpty()) Text(stringResource(R.string.stats_none_yet))
                    rises.forEach { rise ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenProduct(rise.normalizedName) },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(rise.displayName, Modifier.weight(1f))
                            Text(
                                "${money(rise.firstUnitPrice)} → ${money(rise.latestUnitPrice)} (+${(rise.changeFraction * 100).roundToInt()} %)",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.stats_cheapest)) {
                    if (cheapest.isEmpty()) Text(stringResource(R.string.stats_none_yet))
                    cheapest.forEach { c ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenProduct(c.normalizedName) }) {
                            Text(c.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(
                                    R.string.stats_cheapest_line, c.cheapestStore, (c.savingFraction * 100).roundToInt()
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Bar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

@Composable
private fun LabeledBar(label: String, value: String, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        Bar(fraction)
    }
}

@Composable
private fun MonthBars(months: List<MonthlySpend>) {
    val max = months.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
    val parse = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val label = remember { SimpleDateFormat("LLL", esLocale) }
    Row(
        Modifier.fillMaxWidth().height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        months.forEachIndexed { index, month ->
            val isCurrent = index == months.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (month.total > 0) month.total.roundToInt().toString() else "",
                    style = MaterialTheme.typography.labelSmall, maxLines = 1
                )
                Box(
                    Modifier.width(24.dp)
                        .height((80 * (month.total / max)).toFloat().coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                )
                Text(label.format(parse.parse(month.yearMonth)!!), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
```

- [ ] **Step 3: Botón en el historial**

En `PurchaseHistoryScreen.kt`, añadir el parámetro `onOpenStats: () -> Unit` tras `onOpenTicket` en la firma (línea 73-79) y las acciones en el `TopAppBar` (línea 170):

```kotlin
            TopAppBar(
                title = { Text(stringResource(R.string.purchase_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.stats_open_cd))
                    }
                }
            )
```

Y el import `androidx.compose.material.icons.filled.BarChart`.

- [ ] **Step 4: Ruta**

En `MainActivity.kt`, en `composable("purchaseHistory")` añadir `onOpenStats = { navController.navigate("purchaseStats") },` a la llamada de `PurchaseHistoryScreen`, y justo debajo de ese `composable` añadir:

```kotlin
            composable("purchaseStats") {
                PurchaseStatsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { normalizedName ->
                        navController.navigate("purchaseDetail/${Uri.encode(normalizedName)}")
                    }
                )
            }
```

Con el import `com.homepantry.app.ui.screens.PurchaseStatsScreen`.

- [ ] **Step 5: Compilar y ejecutar todos los tests**

Run: `./gradlew testDebugUnitTest assembleRelease`
Expected: BUILD SUCCESSFUL; todos los tests en verde; el APK sigue en ~4,5 MB.

- [ ] **Step 6: Revisión manual en el móvil (la hace el usuario)**

Instalar el APK, abrir Historial → icono de barras. Comprobar: estado vacío sin compras, las seis secciones con datos, y que tocar un producto abre su detalle.

- [ ] **Step 7: Commit (solo si el usuario lo pide)**

```bash
git add app/src/main/kotlin app/src/main/res/values/strings.xml app/src/test docs/superpowers
git commit -m "feat: pantalla de estadísticas de gasto"
```
