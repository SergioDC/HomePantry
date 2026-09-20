# Menú semanal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir una 4ª pestaña «Menú» con calendario semana/mes, hoja del día con desayuno/comida/cena, lista de platos con comprobación de ingredientes contra las zonas, duplicar semana y compartir la semana como imagen.

**Architecture:** Un documento Firestore por entrada de menú (`mealEntries`) y otro por plato (`dishes`), con repositorios como `ItemsRepository`. La lógica de fechas, ingredientes y duplicado es Kotlin puro (con tests JUnit). Un `MenuViewModel` propio, aparte de `AppViewModel`, escucha solo el rango de fechas visible (página actual ± 1). La UI es Compose con un `HorizontalPager`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, BOM 2024.10.01), Firestore, `java.time` (minSdk 26), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-20-menu-semanal-design.md`

## Global Constraints

- Sin dependencias nuevas en `app/build.gradle.kts` (el `HorizontalPager` está en `compose.foundation`).
- `minSdk = 26`: se usa `java.time` directamente, sin desugaring.
- La semana empieza en lunes. Las fechas de `MealEntry.date` son texto ISO `yyyy-MM-dd`.
- Texto de interfaz en español, en `app/src/main/res/values/strings.xml`, dentro del bloque `<!-- Menú semanal -->` … `<!-- /Menú semanal -->`.
- **Cuidado con `Unit`:** en el paquete `com.homepantry.app.data` existe `enum class Unit`, que tapa a `kotlin.Unit`. En archivos que lo importen, usar `import com.homepantry.app.data.Unit as QtyUnit`, y en `data` no escribir `: Unit` como tipo de retorno.
- Colores por `MaterialTheme.colorScheme` (tema Nocturne); no hardcodear colores salvo en la imagen compartida.
- Los fallos de escritura se muestran por el `error` del ViewModel (Snackbar), como el resto de la app.
- Comandos Gradle desde la raíz del repo: `./gradlew :app:testDebugUnitTest --tests "…"` y `./gradlew :app:compileDebugKotlin`.
- Cada commit termina con estas dos líneas de pie, en un segundo `-m`:
  `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy`. No hacer `git push`.

---

### Task 1: Modelos y lógica de calendario

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/Dish.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/MealEntry.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/MenuCalendar.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/MenuCalendarTest.kt`

**Interfaces:**
- Produces (todo en `com.homepantry.app.data`):
  - `data class Ingredient(name: String = "", qty: Double? = null, unit: String? = null)`
  - `data class Dish(id, name, ingredients: List<Ingredient>, note: String?, addedBy: String?, addedAt: Date?)`
  - `enum class MealSlot(val label: String) { BREAKFAST, LUNCH, DINNER }`
  - `data class MealEntry(id, date: String, slot: String, dishId: String?, name: String, order: Int, addedBy: String?)`
  - `enum class CalendarMode { WEEK, MONTH }`, `const val PAGER_ANCHOR_PAGE = 5000`, `const val PAGER_PAGE_COUNT = 10000`
  - `weekStart(LocalDate): LocalDate`, `weekDays(LocalDate): List<LocalDate>`, `monthGrid(YearMonth): List<LocalDate>`
  - `pageWeekStart(anchor: LocalDate, page: Int, anchorPage: Int): LocalDate`, `pageMonth(anchor: YearMonth, page: Int, anchorPage: Int): YearMonth`
  - `pageForWeek(anchor: LocalDate, date: LocalDate, anchorPage: Int): Int`, `pageForMonth(anchor: YearMonth, month: YearMonth, anchorPage: Int): Int`
  - `visibleRange(mode: CalendarMode, date: LocalDate): Pair<String, String>`
  - `shiftEntries(entries: List<MealEntry>, fromWeekStart: LocalDate, toWeekStart: LocalDate): List<MealEntry>`
  - `weekRangeLabel(start: LocalDate): String`, `monthLabel(month: YearMonth): String`, `dayLabel(date: LocalDate): String`
  - `val DAY_NAMES: List<String>` (lunes…domingo, índice `dayOfWeek.value - 1`)

- [ ] **Step 0: Crear la rama de trabajo**

Run: `git switch -c feat/menu-semanal`
Expected: `Switched to a new branch 'feat/menu-semanal'`

- [ ] **Step 1: Write the failing test**

Crear `app/src/test/kotlin/com/homepantry/app/data/MenuCalendarTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.homepantry.app.data.MenuCalendarTest"`
Expected: FAIL (compilation error: unresolved reference `weekStart`, `MealEntry`, …).

- [ ] **Step 3: Write the models**

Crear `app/src/main/kotlin/com/homepantry/app/data/Dish.kt`:

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** Ingrediente de un plato: texto libre, con cantidad y unidad opcionales (nombre de [Unit]). */
data class Ingredient(
    val name: String = "",
    val qty: Double? = null,
    val unit: String? = null
)

/**
 * Plato de la lista de la casa.
 * households/{householdCode}/dishes/{dishId}
 *
 * El constructor sin argumentos es requerido por el deserializador de Firestore.
 */
data class Dish(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val note: String? = null,
    val addedBy: String? = null,
    @ServerTimestamp
    val addedAt: Date? = null
)
```

Crear `app/src/main/kotlin/com/homepantry/app/data/MealEntry.kt`:

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId

enum class MealSlot(val label: String) {
    BREAKFAST("Desayuno"),
    LUNCH("Comida"),
    DINNER("Cena")
}

/**
 * Una línea del menú: un plato (o texto libre) en una franja de un día.
 * households/{householdCode}/mealEntries/{entryId}
 *
 * `name` es una copia del nombre del plato al añadirlo: si el plato se borra o se
 * renombra, la entrada sigue mostrándose. `dishId == null` (o un id que ya no existe)
 * significa texto libre. `date` es "yyyy-MM-dd" para poder consultar una semana por rango.
 */
data class MealEntry(
    @DocumentId
    val id: String = "",
    val date: String = "",
    val slot: String = MealSlot.LUNCH.name,
    val dishId: String? = null,
    val name: String = "",
    val order: Int = 0,
    val addedBy: String? = null
)
```

- [ ] **Step 4: Write the calendar logic**

Crear `app/src/main/kotlin/com/homepantry/app/data/MenuCalendar.kt`:

```kotlin
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.homepantry.app.data.MenuCalendarTest"`
Expected: PASS (16 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/Dish.kt app/src/main/kotlin/com/homepantry/app/data/MealEntry.kt app/src/main/kotlin/com/homepantry/app/data/MenuCalendar.kt app/src/test/kotlin/com/homepantry/app/data/MenuCalendarTest.kt
git commit -m "feat: menu models and calendar date logic" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

---

### Task 2: Comprobación de ingredientes

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/IngredientCheck.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/IngredientCheckTest.kt`

**Interfaces:**
- Consumes: `Ingredient`, `Dish` (Task 1); `Item`, `Unit`, `normalizeProductName` (existentes en `data`).
- Produces:
  - `fun missingIngredients(dish: Dish, items: List<Item>): List<Ingredient>`
  - `data class MissingChoice(val ingredient: Ingredient, val zoneId: String)`
  - `fun itemsForMissing(choices: List<MissingChoice>, addedBy: String): List<Item>`
  - `fun parseIngredient(name: String, qtyText: String, unit: String): Ingredient?`

- [ ] **Step 1: Write the failing test**

Crear `app/src/test/kotlin/com/homepantry/app/data/IngredientCheckTest.kt`:

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientCheckTest {
    private fun dish(vararg names: String) = Dish(name = "Plato", ingredients = names.map { Ingredient(name = it) })
    private fun item(name: String, done: Boolean) = Item(name = name, done = done, zone = "z")

    @Test fun `does not suggest what you already have`() {
        val missing = missingIngredients(dish("tomate"), listOf(item("Tomates", done = true)))
        assertEquals(emptyList<Ingredient>(), missing)
    }

    @Test fun `does not suggest what is already on the shopping list`() {
        val missing = missingIngredients(dish("leche"), listOf(item("Leche", done = false)))
        assertEquals(emptyList<Ingredient>(), missing)
    }

    @Test fun `suggests ingredients without a matching product`() {
        val missing = missingIngredients(dish("Harina", "Huevos"), listOf(item("Leche", done = true)))
        assertEquals(listOf("Harina", "Huevos"), missing.map { it.name })
    }

    @Test fun `matching ignores case, accents and simple plural`() {
        val items = listOf(item("PLÁTANOS", done = true))
        assertEquals(emptyList<Ingredient>(), missingIngredients(dish("platano"), items))
    }

    @Test fun `repeated ingredients are returned once`() {
        val missing = missingIngredients(dish("Huevos", "huevo"), emptyList())
        assertEquals(listOf("Huevos"), missing.map { it.name })
    }

    @Test fun `blank ingredient names are ignored`() {
        val missing = missingIngredients(dish("  ", "Sal"), emptyList())
        assertEquals(listOf("Sal"), missing.map { it.name })
    }

    @Test fun `itemsForMissing builds pending items with the chosen zone`() {
        val items = itemsForMissing(
            listOf(
                MissingChoice(Ingredient(name = "Harina ", qty = 500.0, unit = "G"), "z1"),
                MissingChoice(Ingredient(name = "Sal"), "z2")
            ),
            addedBy = "Ana"
        )
        assertEquals(Item(name = "Harina", qty = 500.0, unit = "G", zone = "z1", done = false, addedBy = "Ana"), items[0])
        assertEquals(Item(name = "Sal", qty = 1.0, unit = "UD", zone = "z2", done = false, addedBy = "Ana"), items[1])
    }

    @Test fun `parseIngredient trims the name and parses quantity with unit`() {
        assertEquals(Ingredient("Harina", 500.0, "G"), parseIngredient("  Harina ", "500", "G"))
    }

    @Test fun `parseIngredient accepts a decimal comma`() {
        assertEquals(1.5, parseIngredient("Leche", "1,5", "L")?.qty)
    }

    @Test fun `parseIngredient drops the unit when there is no valid quantity`() {
        assertEquals(Ingredient("Sal", null, null), parseIngredient("Sal", "", "G"))
        assertEquals(Ingredient("Sal", null, null), parseIngredient("Sal", "0", "KG"))
        assertEquals(Ingredient("Pan", null, null), parseIngredient("Pan", "abc", "UD"))
    }

    @Test fun `parseIngredient returns null for a blank name`() {
        assertNull(parseIngredient("   ", "2", "UD"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.homepantry.app.data.IngredientCheckTest"`
Expected: FAIL (unresolved reference `missingIngredients`).

- [ ] **Step 3: Write minimal implementation**

Crear `app/src/main/kotlin/com/homepantry/app/data/IngredientCheck.kt`:

```kotlin
package com.homepantry.app.data

/**
 * Ingredientes del plato que no están en ninguna zona ni en la lista de la compra.
 * Compara por [normalizeProductName]: un producto con el mismo nombre normalizado cuenta
 * como "lo tengo" (done) o "ya está pedido" (pendiente), y en ambos casos no se sugiere.
 * Un ingrediente repetido en el plato se devuelve una sola vez; los nombres en blanco se ignoran.
 */
fun missingIngredients(dish: Dish, items: List<Item>): List<Ingredient> {
    val known = items.map { normalizeProductName(it.name) }.toSet()
    val seen = mutableSetOf<String>()
    return dish.ingredients.filter { ingredient ->
        val key = normalizeProductName(ingredient.name)
        key.isNotEmpty() && key !in known && seen.add(key)
    }
}

/** Ingrediente que falta junto con la zona elegida para añadirlo a la lista de la compra. */
data class MissingChoice(val ingredient: Ingredient, val zoneId: String)

/** Productos pendientes (`done = false`) para las elecciones del usuario; sin cantidad, 1 unidad. */
fun itemsForMissing(choices: List<MissingChoice>, addedBy: String): List<Item> =
    choices.map { choice ->
        Item(
            name = choice.ingredient.name.trim(),
            qty = choice.ingredient.qty ?: 1.0,
            unit = choice.ingredient.unit ?: Unit.UD.name,
            zone = choice.zoneId,
            done = false,
            addedBy = addedBy
        )
    }

/**
 * Convierte una fila del formulario en un [Ingredient]. Acepta coma decimal; una cantidad vacía,
 * no numérica o menor o igual que 0 se descarta y con ella la unidad. Nombre en blanco: null.
 */
fun parseIngredient(name: String, qtyText: String, unit: String): Ingredient? {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return null
    val qty = qtyText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    return Ingredient(name = cleanName, qty = qty, unit = if (qty != null) unit else null)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.homepantry.app.data.IngredientCheckTest"`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/IngredientCheck.kt app/src/test/kotlin/com/homepantry/app/data/IngredientCheckTest.kt
git commit -m "feat: ingredient check against zones and shopping list" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

### Task 3: Lógica de entradas y duplicado de semana

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/MealEntriesLogic.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/MealEntriesLogicTest.kt`

**Interfaces:**
- Consumes: `MealEntry`, `MealSlot`, `Dish`, `shiftEntries` (Task 1); `normalizeProductName` (existente).
- Produces:
  - `enum class DuplicateMode { REPLACE, MERGE }`
  - `data class DuplicatePlan(val toWrite: List<MealEntry>, val toDelete: List<String>)`
  - `fun planDuplicateWeek(source: List<MealEntry>, target: List<MealEntry>, fromWeekStart: LocalDate, toWeekStart: LocalDate, mode: DuplicateMode): DuplicatePlan`
  - `fun nextEntryOrder(entries: List<MealEntry>, date: String, slot: MealSlot): Int`
  - `fun entriesFor(entries: List<MealEntry>, date: String, slot: MealSlot): List<MealEntry>` (ordenadas por `order`)
  - `fun matchDish(text: String, dishes: List<Dish>): Dish?`
  - `fun suggestDishes(text: String, dishes: List<Dish>, limit: Int = 5): List<Dish>`
  - `fun resolvedDishId(entry: MealEntry, dishes: List<Dish>): String?`

- [ ] **Step 1: Write the failing test**

Crear `app/src/test/kotlin/com/homepantry/app/data/MealEntriesLogicTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.homepantry.app.data.MealEntriesLogicTest"`
Expected: FAIL (unresolved reference `planDuplicateWeek`).

- [ ] **Step 3: Write minimal implementation**

Crear `app/src/main/kotlin/com/homepantry/app/data/MealEntriesLogic.kt`:

```kotlin
package com.homepantry.app.data

import java.time.LocalDate

enum class DuplicateMode { REPLACE, MERGE }

/** Qué escribir (entradas nuevas, sin id) y qué borrar (ids) al duplicar una semana. */
data class DuplicatePlan(val toWrite: List<MealEntry>, val toDelete: List<String>)

/**
 * Plan para copiar la semana [fromWeekStart] a la [toWeekStart].
 * - REPLACE: borra las entradas del destino y escribe las copias.
 * - MERGE: no borra nada; las copias van detrás de lo que ya haya en cada franja del destino.
 */
fun planDuplicateWeek(
    source: List<MealEntry>,
    target: List<MealEntry>,
    fromWeekStart: LocalDate,
    toWeekStart: LocalDate,
    mode: DuplicateMode
): DuplicatePlan {
    val copies = shiftEntries(source, fromWeekStart, toWeekStart)
    return when (mode) {
        DuplicateMode.REPLACE -> DuplicatePlan(toWrite = copies, toDelete = target.map { it.id })
        DuplicateMode.MERGE -> {
            val highestOrder = target.groupBy { it.date to it.slot }.mapValues { (_, list) -> list.maxOf { it.order } }
            val shifted = copies.map { copy ->
                val base = highestOrder[copy.date to copy.slot]
                if (base == null) copy else copy.copy(order = copy.order + base + 1)
            }
            DuplicatePlan(toWrite = shifted, toDelete = emptyList())
        }
    }
}

/** Orden para una entrada nueva al final de la franja. */
fun nextEntryOrder(entries: List<MealEntry>, date: String, slot: MealSlot): Int =
    (entries.filter { it.date == date && it.slot == slot.name }.maxOfOrNull { it.order } ?: -1) + 1

fun entriesFor(entries: List<MealEntry>, date: String, slot: MealSlot): List<MealEntry> =
    entries.filter { it.date == date && it.slot == slot.name }.sortedBy { it.order }

/** El plato cuyo nombre normalizado coincide exactamente con el texto escrito, o null (texto libre). */
fun matchDish(text: String, dishes: List<Dish>): Dish? {
    val key = normalizeProductName(text)
    if (key.isEmpty()) return null
    return dishes.firstOrNull { normalizeProductName(it.name) == key }
}

/** Platos cuyo nombre contiene el texto (sin acentos ni mayúsculas), por nombre; con texto en blanco, todos. */
fun suggestDishes(text: String, dishes: List<Dish>, limit: Int = 5): List<Dish> {
    val key = normalizeProductName(text)
    return dishes
        .filter { key.isEmpty() || normalizeProductName(it.name).contains(key) }
        .sortedBy { it.name.lowercase() }
        .take(limit)
}

/** Id del plato de la entrada solo si ese plato sigue existiendo; si no, se trata como texto libre. */
fun resolvedDishId(entry: MealEntry, dishes: List<Dish>): String? =
    entry.dishId?.takeIf { id -> dishes.any { it.id == id } }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.homepantry.app.data.MealEntriesLogicTest"`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/MealEntriesLogic.kt app/src/test/kotlin/com/homepantry/app/data/MealEntriesLogicTest.kt
git commit -m "feat: meal entry ordering, dish matching and week duplication plan" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

---

### Task 4: Repositorios de Firestore

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/DishesRepository.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/MealEntriesRepository.kt`

**Interfaces:**
- Consumes: `Dish`, `MealEntry` (Task 1).
- Produces:
  - `class DishesRepository(firestore: FirebaseFirestore, householdCode: String)`: `observeDishes(): Flow<List<Dish>>`, `suspend addDish(dish: Dish): String`, `suspend updateDish(dish: Dish)`, `suspend deleteDish(id: String)`.
  - `class MealEntriesRepository(firestore: FirebaseFirestore, householdCode: String)`: `observeRange(from: String, to: String): Flow<List<MealEntry>>`, `suspend getRange(from: String, to: String): List<MealEntry>`, `suspend addEntry(entry: MealEntry): String`, `suspend updateEntry(entry: MealEntry)`, `suspend deleteEntry(id: String)`, `suspend restoreEntry(entry: MealEntry)`, `suspend countByDish(dishId: String): Int`, `suspend applyBatch(toWrite: List<MealEntry>, toDelete: List<String>)`.

No hay tests unitarios (Firestore); se comprueba compilando y en el móvil.

- [ ] **Step 1: Write DishesRepository**

Crear `app/src/main/kotlin/com/homepantry/app/data/DishesRepository.kt`:

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Platos de la casa: households/{householdCode}/dishes. Mismo patrón que ItemsRepository. */
class DishesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("dishes")

    fun observeDishes(): Flow<List<Dish>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(Dish::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** @return el id generado por Firestore para el nuevo plato. */
    suspend fun addDish(dish: Dish): String = collection().add(dish).await().id

    suspend fun updateDish(dish: Dish) {
        collection().document(dish.id).set(dish).await()
    }

    suspend fun deleteDish(id: String) {
        collection().document(id).delete().await()
    }
}
```

- [ ] **Step 2: Write MealEntriesRepository**

Crear `app/src/main/kotlin/com/homepantry/app/data/MealEntriesRepository.kt`:

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Entradas del menú: households/{householdCode}/mealEntries.
 * Las semanas se consultan por rango sobre el campo `date` ("yyyy-MM-dd"), que al ser un
 * único campo no necesita índice compuesto; el orden por franja se aplica en el cliente.
 */
class MealEntriesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("mealEntries")

    private fun rangeQuery(from: String, to: String) =
        collection().whereGreaterThanOrEqualTo("date", from).whereLessThanOrEqualTo("date", to)

    /** Emite las entradas de [from] a [to] (ambos incluidos) cada vez que cambia algo en ese rango. */
    fun observeRange(from: String, to: String): Flow<List<MealEntry>> = callbackFlow {
        val registration = rangeQuery(from, to).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(MealEntry::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** Lectura única del rango, para duplicar semanas. */
    suspend fun getRange(from: String, to: String): List<MealEntry> =
        rangeQuery(from, to).get().await().documents.mapNotNull { it.toObject(MealEntry::class.java) }

    suspend fun addEntry(entry: MealEntry): String = collection().add(entry).await().id

    suspend fun updateEntry(entry: MealEntry) {
        collection().document(entry.id).set(entry).await()
    }

    suspend fun deleteEntry(id: String) {
        collection().document(id).delete().await()
    }

    /** Deshace un borrado: vuelve a escribir la entrada en su mismo documento. */
    suspend fun restoreEntry(entry: MealEntry) {
        collection().document(entry.id).set(entry).await()
    }

    /** Cuántas entradas del menú usan un plato (para avisar antes de borrarlo). */
    suspend fun countByDish(dishId: String): Int =
        collection().whereEqualTo("dishId", dishId).get().await().size()

    /**
     * Duplicado de semana: escribe las entradas nuevas y luego borra las indicadas, en lotes de
     * como mucho 500 operaciones. Se escribe primero: si algo falla a medias, quedan duplicados
     * pero no se pierde nada.
     */
    suspend fun applyBatch(toWrite: List<MealEntry>, toDelete: List<String>) {
        toWrite.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { entry -> batch.set(collection().document(), entry) }
            batch.commit().await()
        }
        toDelete.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id -> batch.delete(collection().document(id)) }
            batch.commit().await()
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/DishesRepository.kt app/src/main/kotlin/com/homepantry/app/data/MealEntriesRepository.kt
git commit -m "feat: dishes and meal entries repositories" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

---

### Task 5: MenuViewModel

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/MenuViewModel.kt`

**Interfaces:**
- Consumes: `DishesRepository`, `MealEntriesRepository`, `ItemsRepository` (existente, `addItems`), `visibleRange`, `matchDish`, `nextEntryOrder`, `planDuplicateWeek`, `itemsForMissing`, `MissingChoice`, `CalendarMode`, `DuplicateMode` (Tasks 1–4).
- Produces:
  - `data class CalendarPosition(val mode: CalendarMode, val date: LocalDate)`
  - `data class MenuUiState(dishes, entries, position: CalendarPosition, error: String?)`
  - `class MenuViewModel(dishesRepository, entriesRepository, itemsRepository, userName)` con `val state: StateFlow<MenuUiState>` y:
    - `setVisibleDate(date: LocalDate)`, `setMode(mode: CalendarMode)`, `openWeekOf(date: LocalDate)`, `clearError()`
    - `saveDish(dish: Dish)`, `deleteDish(dishId: String)`, `suspend countEntriesForDish(dishId: String): Int`, `addMissingToShoppingList(choices: List<MissingChoice>)`
    - `addEntry(date: LocalDate, slot: MealSlot, text: String)`, `editEntry(entry: MealEntry, text: String)`, `deleteEntry(entry: MealEntry)`, `restoreEntry(entry: MealEntry)`
    - `suspend countEntriesInWeek(weekStart: LocalDate): Int`, `duplicateWeek(from: LocalDate, to: LocalDate, mode: DuplicateMode)`

- [ ] **Step 1: Write the ViewModel**

Crear `app/src/main/kotlin/com/homepantry/app/ui/MenuViewModel.kt`:

```kotlin
package com.homepantry.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.CalendarMode
import com.homepantry.app.data.Dish
import com.homepantry.app.data.DishesRepository
import com.homepantry.app.data.DuplicateMode
import com.homepantry.app.data.ItemsRepository
import com.homepantry.app.data.MealEntriesRepository
import com.homepantry.app.data.MealEntry
import com.homepantry.app.data.MealSlot
import com.homepantry.app.data.MissingChoice
import com.homepantry.app.data.itemsForMissing
import com.homepantry.app.data.matchDish
import com.homepantry.app.data.nextEntryOrder
import com.homepantry.app.data.planDuplicateWeek
import com.homepantry.app.data.visibleRange
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Modo del calendario y fecha de referencia (un día de la semana o del mes visible). */
data class CalendarPosition(val mode: CalendarMode, val date: LocalDate)

data class MenuUiState(
    val dishes: List<Dish> = emptyList(),
    /** Entradas del rango visible (página actual, anterior y siguiente). */
    val entries: List<MealEntry> = emptyList(),
    val position: CalendarPosition = CalendarPosition(CalendarMode.WEEK, LocalDate.now()),
    val error: String? = null
)

/**
 * Estado y acciones del menú semanal, aparte de AppViewModel (que ya tiene cinco repositorios).
 * Los productos y zonas ya cargados los toma la UI del estado de AppViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModel(
    private val dishesRepository: DishesRepository,
    private val entriesRepository: MealEntriesRepository,
    private val itemsRepository: ItemsRepository,
    private val userName: String
) : ViewModel() {

    private val dishes = MutableStateFlow<List<Dish>>(emptyList())
    private val entries = MutableStateFlow<List<MealEntry>>(emptyList())
    private val position = MutableStateFlow(CalendarPosition(CalendarMode.WEEK, LocalDate.now()))
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<MenuUiState> = combine(dishes, entries, position, error) { d, e, p, err ->
        MenuUiState(dishes = d, entries = e, position = p, error = err)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MenuUiState())

    init {
        viewModelScope.launch {
            dishesRepository.observeDishes()
                .catch { e -> error.value = e.message }
                .collect { dishes.value = it }
        }
        viewModelScope.launch {
            position
                .map { visibleRange(it.mode, it.date) }
                .distinctUntilChanged()
                .flatMapLatest { (from, to) ->
                    entriesRepository.observeRange(from, to).catch { e -> error.value = e.message }
                }
                .collect { entries.value = it }
        }
    }

    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { e -> error.value = e.message }
    }

    fun setVisibleDate(date: LocalDate) {
        position.value = position.value.copy(date = date)
    }

    fun setMode(mode: CalendarMode) {
        position.value = position.value.copy(mode = mode)
    }

    /** Pasa a la vista semana en la semana de [date] (al pulsar un día de la vista mes). */
    fun openWeekOf(date: LocalDate) {
        position.value = CalendarPosition(CalendarMode.WEEK, date)
    }

    fun clearError() {
        error.value = null
    }

    // ---- Platos ----

    fun saveDish(dish: Dish) = launchCatching {
        if (dish.id.isBlank()) {
            dishesRepository.addDish(dish.copy(addedBy = userName))
        } else {
            dishesRepository.updateDish(dish)
        }
    }

    /** Las entradas del menú que usaban el plato se quedan como texto libre (conservan su nombre). */
    fun deleteDish(dishId: String) = launchCatching { dishesRepository.deleteDish(dishId) }

    suspend fun countEntriesForDish(dishId: String): Int =
        runCatching { entriesRepository.countByDish(dishId) }.getOrDefault(0)

    fun addMissingToShoppingList(choices: List<MissingChoice>) = launchCatching {
        itemsRepository.addItems(itemsForMissing(choices, userName))
    }

    // ---- Entradas del menú ----

    /** Añade texto al menú; si coincide con un plato creado se enlaza a él, si no queda como texto libre. */
    fun addEntry(date: LocalDate, slot: MealSlot, text: String) = launchCatching {
        val name = text.trim()
        if (name.isEmpty()) return@launchCatching
        val dish = matchDish(name, dishes.value)
        val key = date.toString()
        entriesRepository.addEntry(
            MealEntry(
                date = key,
                slot = slot.name,
                dishId = dish?.id,
                name = dish?.name ?: name,
                order = nextEntryOrder(entries.value, key, slot),
                addedBy = userName
            )
        )
    }

    fun editEntry(entry: MealEntry, text: String) = launchCatching {
        val name = text.trim()
        if (name.isEmpty()) return@launchCatching
        val dish = matchDish(name, dishes.value)
        entriesRepository.updateEntry(entry.copy(dishId = dish?.id, name = dish?.name ?: name))
    }

    fun deleteEntry(entry: MealEntry) = launchCatching { entriesRepository.deleteEntry(entry.id) }

    fun restoreEntry(entry: MealEntry) = launchCatching { entriesRepository.restoreEntry(entry) }

    // ---- Duplicar semana ----

    suspend fun countEntriesInWeek(weekStart: LocalDate): Int =
        runCatching {
            entriesRepository.getRange(weekStart.toString(), weekStart.plusDays(6).toString()).size
        }.getOrDefault(0)

    fun duplicateWeek(from: LocalDate, to: LocalDate, mode: DuplicateMode) = launchCatching {
        val source = entriesRepository.getRange(from.toString(), from.plusDays(6).toString())
        val target = entriesRepository.getRange(to.toString(), to.plusDays(6).toString())
        val plan = planDuplicateWeek(source, target, from, to, mode)
        entriesRepository.applyBatch(plan.toWrite, plan.toDelete)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/MenuViewModel.kt
git commit -m "feat: MenuViewModel with ranged meal entries and dish actions" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

### Task 6: Esqueleto de navegación (4ª pestaña «Menú»)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/MenuScreen.kt` (esqueleto; la Task 12 lo reemplaza entero)
- Modify: `app/src/main/res/values/strings.xml` (bloque nuevo al final, antes de `</resources>`)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`

**Interfaces:**
- Consumes: `MenuViewModel` (Task 5), `DishesRepository`, `MealEntriesRepository` (Task 4).
- Produces: `@Composable fun MenuScreen(menuViewModel: MenuViewModel, appViewModel: AppViewModel)`; ruta `menu`; strings `nav_menu`, `menu_tab_menu`, `menu_tab_dishes`.

- [ ] **Step 1: Add the strings block**

En `app/src/main/res/values/strings.xml`, antes de la línea `</resources>`, añadir:

```xml

    <!-- Menú semanal -->
    <string name="nav_menu">Menú</string>
    <string name="menu_tab_menu">Menú</string>
    <string name="menu_tab_dishes">Platos</string>
    <!-- /Menú semanal -->
```

- [ ] **Step 2: Create the skeleton screen**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/MenuScreen.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.MenuViewModel

/** Pestaña «Menú»: calendario y lista de platos. Esqueleto: la Task 12 lo completa. */
@Composable
fun MenuScreen(menuViewModel: MenuViewModel, appViewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.menu_tab_menu)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.menu_tab_dishes)) }
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(if (selectedTab == 0) R.string.menu_tab_menu else R.string.menu_tab_dishes))
            }
        }
    }
}
```

- [ ] **Step 3: Wire MainActivity**

En `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`:

1. Imports (mantener el orden alfabético existente): añadir
```kotlin
import androidx.compose.material.icons.filled.RestaurantMenu
import com.homepantry.app.data.DishesRepository
import com.homepantry.app.data.MealEntriesRepository
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.screens.MenuScreen
```

2. Cambiar la línea `private val BOTTOM_NAV_ROUTES = setOf("mainList", "zonesDashboard", "search")` por:
```kotlin
private val BOTTOM_NAV_ROUTES = setOf("mainList", "zonesDashboard", "menu", "search")
```

3. Justo después del bloque `val viewModel: AppViewModel = viewModel(...)` y antes de `val state by viewModel.state.collectAsState()`, añadir:
```kotlin
    val menuViewModel: MenuViewModel = viewModel(
        key = "menu-$code",
        factory = remember(code, name) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MenuViewModel(
                        dishesRepository = DishesRepository(firestore, code),
                        entriesRepository = MealEntriesRepository(firestore, code),
                        itemsRepository = ItemsRepository(firestore, code),
                        userName = name
                    ) as T
                }
            }
        }
    )
```

4. En la lista de `BottomNavItem`, entre «Lista» y «Buscar», añadir la pestaña:
```kotlin
                        BottomNavItem("menu", stringResource(R.string.nav_menu), Icons.Filled.RestaurantMenu),
```

5. En el `NavHost`, después del bloque `composable("mainList") { ... }`, añadir:
```kotlin
            composable("menu") {
                MenuScreen(menuViewModel = menuViewModel, appViewModel = viewModel)
            }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/MenuScreen.kt app/src/main/res/values/strings.xml app/src/main/kotlin/com/homepantry/app/MainActivity.kt
git commit -m "feat: add Menu tab skeleton to the bottom navigation" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

---

### Task 7: Pestaña Menú: calendario con pager (semana y mes)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/MenuCalendarTab.kt`
- Modify: `app/src/main/res/values/strings.xml` (antes de `<!-- /Menú semanal -->`)

**Interfaces:**
- Consumes: `MenuUiState`, `MenuViewModel` (Task 5); `CalendarMode`, `PAGER_*`, `weekStart`, `weekDays`, `monthGrid`, `pageWeekStart`, `pageMonth`, `pageForWeek`, `pageForMonth`, `weekRangeLabel`, `monthLabel`, `DAY_NAMES`, `entriesFor`, `MealSlot` (Tasks 1, 3); `SegmentedToggle` (existente).
- Produces: `@Composable fun MenuCalendarTab(state: MenuUiState, viewModel: MenuViewModel, onOpenDay: (LocalDate) -> Unit, onShare: (LocalDate) -> Unit, onDuplicate: (LocalDate) -> Unit)`. `onShare` y `onDuplicate` reciben el lunes de la semana visible y solo se ofrecen en la vista semana.

- [ ] **Step 1: Add the strings**

Antes de `<!-- /Menú semanal -->` añadir:

```xml
    <string name="menu_prev_cd">Anterior</string>
    <string name="menu_next_cd">Siguiente</string>
    <string name="menu_today">Hoy</string>
    <string name="menu_mode_week">Semana</string>
    <string name="menu_mode_month">Mes</string>
    <string name="menu_more_cd">Más opciones</string>
    <string name="menu_share_week">Compartir semana</string>
    <string name="menu_duplicate_week">Duplicar semana</string>
    <string name="menu_empty_week">Aún no hay menú</string>
    <string name="menu_empty_week_start">Empezar</string>
```

- [ ] **Step 2: Write the calendar tab**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/MenuCalendarTab.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.CalendarMode
import com.homepantry.app.data.DAY_NAMES
import com.homepantry.app.data.MealEntry
import com.homepantry.app.data.MealSlot
import com.homepantry.app.data.PAGER_ANCHOR_PAGE
import com.homepantry.app.data.PAGER_PAGE_COUNT
import com.homepantry.app.data.entriesFor
import com.homepantry.app.data.monthGrid
import com.homepantry.app.data.monthLabel
import com.homepantry.app.data.pageForMonth
import com.homepantry.app.data.pageForWeek
import com.homepantry.app.data.pageMonth
import com.homepantry.app.data.pageWeekStart
import com.homepantry.app.data.weekDays
import com.homepantry.app.data.weekRangeLabel
import com.homepantry.app.data.weekStart
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.components.SegmentedToggle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Calendario del menú: un `HorizontalPager` con una página por semana (o por mes). Deslizar en
 * horizontal cambia de semana o mes; dentro de cada semana se hace scroll vertical.
 * Las flechas y «Hoy» hacen lo mismo con animación.
 */
@Composable
fun MenuCalendarTab(
    state: MenuUiState,
    viewModel: MenuViewModel,
    onOpenDay: (LocalDate) -> Unit,
    onShare: (LocalDate) -> Unit,
    onDuplicate: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val mode = state.position.mode
    val scope = rememberCoroutineScope()

    // El pager se recrea al cambiar de modo para colocarse en la fecha de referencia.
    key(mode) {
        val initialPage = remember {
            when (mode) {
                CalendarMode.WEEK -> pageForWeek(today, state.position.date, PAGER_ANCHOR_PAGE)
                CalendarMode.MONTH ->
                    pageForMonth(YearMonth.from(today), YearMonth.from(state.position.date), PAGER_ANCHOR_PAGE)
            }
        }
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGER_PAGE_COUNT })

        // La fecha de referencia sigue a la página asentada (no a la intermedia de un deslizamiento).
        LaunchedEffect(pagerState, mode) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                val date = when (mode) {
                    CalendarMode.WEEK -> pageWeekStart(today, page, PAGER_ANCHOR_PAGE)
                    CalendarMode.MONTH -> pageMonth(YearMonth.from(today), page, PAGER_ANCHOR_PAGE).atDay(1)
                }
                viewModel.setVisibleDate(date)
            }
        }

        suspend fun goTo(page: Int) {
            // Saltos largos (p.ej. «Hoy» desde muy lejos) sin animar para no recorrer páginas vacías.
            if (abs(page - pagerState.currentPage) > 1) pagerState.scrollToPage(page)
            else pagerState.animateScrollToPage(page)
        }

        val visibleWeekStart = weekStart(state.position.date)
        val title = when (mode) {
            CalendarMode.WEEK -> weekRangeLabel(visibleWeekStart)
            CalendarMode.MONTH -> monthLabel(YearMonth.from(state.position.date))
        }
        var menuOpen by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { goTo(pagerState.currentPage - 1) } }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.menu_prev_cd))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { scope.launch { goTo(pagerState.currentPage + 1) } }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.menu_next_cd))
                }
                if (mode == CalendarMode.WEEK) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more_cd))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_week)) },
                                onClick = {
                                    menuOpen = false
                                    onShare(visibleWeekStart)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_duplicate_week)) },
                                onClick = {
                                    menuOpen = false
                                    onDuplicate(visibleWeekStart)
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SegmentedToggle(
                    options = listOf(
                        CalendarMode.WEEK to stringResource(R.string.menu_mode_week),
                        CalendarMode.MONTH to stringResource(R.string.menu_mode_month)
                    ),
                    selected = mode,
                    onSelect = { viewModel.setMode(it) }
                )
                TextButton(onClick = { scope.launch { goTo(PAGER_ANCHOR_PAGE) } }) {
                    Text(stringResource(R.string.menu_today))
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (mode) {
                    CalendarMode.WEEK -> WeekPage(
                        weekStart = pageWeekStart(today, page, PAGER_ANCHOR_PAGE),
                        entries = state.entries,
                        today = today,
                        onOpenDay = onOpenDay
                    )
                    CalendarMode.MONTH -> MonthPage(
                        month = pageMonth(YearMonth.from(today), page, PAGER_ANCHOR_PAGE),
                        entries = state.entries,
                        today = today,
                        onPickDay = { viewModel.openWeekOf(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekPage(
    weekStart: LocalDate,
    entries: List<MealEntry>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit
) {
    val days = remember(weekStart) { weekDays(weekStart) }
    val weekEntries = remember(entries, weekStart) {
        val keys = days.map { it.toString() }.toSet()
        entries.filter { it.date in keys }
    }
    val byDate = remember(weekEntries) { weekEntries.groupBy { it.date } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (weekEntries.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.menu_empty_week),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onOpenDay(if (today in days) today else weekStart) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text(stringResource(R.string.menu_empty_week_start)) }
                }
            }
        }
        items(days, key = { it.toString() }) { day ->
            DayRow(
                day = day,
                entries = byDate[day.toString()].orEmpty(),
                isToday = day == today,
                onClick = { onOpenDay(day) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayRow(day: LocalDate, entries: List<MealEntry>, isToday: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = DAY_NAMES[day.dayOfWeek.value - 1].take(3),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MealSlot.values().forEach { slot ->
                    val slotEntries = entriesFor(entries, day.toString(), slot)
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = slot.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp).padding(top = 4.dp)
                        )
                        if (slotEntries.isEmpty()) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                slotEntries.forEach { MealChip(it.name) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealChip(name: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MonthPage(
    month: YearMonth,
    entries: List<MealEntry>,
    today: LocalDate,
    onPickDay: (LocalDate) -> Unit
) {
    val grid = remember(month) { monthGrid(month) }
    val daysWithEntries = remember(entries) { entries.map { it.date }.toSet() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DAY_NAMES.forEach { name ->
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val inMonth = day.month == month.month
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (day == today) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onPickDay(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (inMonth) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (day.toString() in daysWithEntries) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Si falla por un import o por una API experimental, corregirlo con el `@OptIn` o el import que indique el compilador, sin cambiar el comportamiento.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/MenuCalendarTab.kt app/src/main/res/values/strings.xml
git commit -m "feat: menu calendar tab with week and month pager" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

---

### Task 8: Hoja del día (desayuno, comida y cena)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/DayMealsSheet.kt`
- Modify: `app/src/main/res/values/strings.xml` (antes de `<!-- /Menú semanal -->`)

**Interfaces:**
- Consumes: `MenuUiState`, `MenuViewModel.addEntry/editEntry/deleteEntry/restoreEntry` (Task 5); `entriesFor`, `suggestDishes`, `dayLabel`, `MealSlot` (Tasks 1, 3).
- Produces: `@Composable fun DayMealsSheet(date: LocalDate, state: MenuUiState, viewModel: MenuViewModel, onDismiss: () -> Unit)`.

- [ ] **Step 1: Add the strings**

Antes de `<!-- /Menú semanal -->` añadir:

```xml
    <string name="menu_day_empty">Nada planeado para %1$s</string>
    <string name="menu_entry_hint">Plato o comida</string>
    <string name="menu_entry_add">Añadir</string>
    <string name="menu_entry_save">Guardar</string>
    <string name="menu_entry_cancel_edit">Cancelar</string>
    <string name="menu_entry_edit_cd">Editar</string>
    <string name="menu_entry_delete_cd">Borrar</string>
    <string name="menu_entry_deleted">Eliminado</string>
    <string name="menu_entry_undo">Deshacer</string>
```

- [ ] **Step 2: Write the sheet**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/DayMealsSheet.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.MealEntry
import com.homepantry.app.data.MealSlot
import com.homepantry.app.data.dayLabel
import com.homepantry.app.data.entriesFor
import com.homepantry.app.data.suggestDishes
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import java.time.LocalDate

/**
 * Hoja de un día del menú, con una pestaña por franja. Se puede añadir un plato escribiendo
 * (con sugerencias de los platos creados) y editar o borrar (con deshacer) los ya puestos.
 * Las entradas salen del estado en vivo, así que reflejan también los cambios de otras personas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMealsSheet(
    date: LocalDate,
    state: MenuUiState,
    viewModel: MenuViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var slotIndex by rememberSaveable { mutableIntStateOf(1) }
    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MealEntry?>(null) }
    var lastDeleted by remember { mutableStateOf<MealEntry?>(null) }

    val slot = MealSlot.values()[slotIndex]
    val dayEntries = entriesFor(state.entries, date.toString(), slot)
    val suggestions = if (text.isBlank()) emptyList() else suggestDishes(text, state.dishes)

    fun resetInput() {
        text = ""
        editing = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = dayLabel(date), style = MaterialTheme.typography.titleLarge)
            TabRow(selectedTabIndex = slotIndex) {
                MealSlot.values().forEachIndexed { index, mealSlot ->
                    Tab(
                        selected = index == slotIndex,
                        onClick = {
                            slotIndex = index
                            resetInput()
                        },
                        text = { Text(mealSlot.label) }
                    )
                }
            }

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                if (dayEntries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.menu_day_empty, slot.label.lowercase()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                items(dayEntries, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            editing = entry
                            text = entry.name
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.menu_entry_edit_cd))
                        }
                        IconButton(onClick = {
                            viewModel.deleteEntry(entry)
                            lastDeleted = entry
                            if (editing?.id == entry.id) resetInput()
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.menu_entry_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Un Snackbar quedaría tapado por la hoja modal, así que el deshacer va dentro de ella.
            lastDeleted?.let { deleted ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.menu_entry_deleted)}: ${deleted.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        viewModel.restoreEntry(deleted)
                        lastDeleted = null
                    }) { Text(stringResource(R.string.menu_entry_undo)) }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.menu_entry_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            suggestions.forEach { dish ->
                TextButton(onClick = { text = dish.name }) { Text(dish.name) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (editing != null) {
                    TextButton(onClick = { resetInput() }) {
                        Text(stringResource(R.string.menu_entry_cancel_edit))
                    }
                }
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val target = editing
                        if (target != null) viewModel.editEntry(target, text)
                        else viewModel.addEntry(date, slot, text)
                        resetInput()
                    }
                ) {
                    Text(
                        stringResource(
                            if (editing != null) R.string.menu_entry_save else R.string.menu_entry_add
                        )
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/DayMealsSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat: day sheet with breakfast, lunch and dinner entries" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

### Task 9: Pestaña Platos, formulario y sugerencia de ingredientes que faltan

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/DishesTab.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/DishFormSheet.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/MissingIngredientsSheet.kt`
- Modify: `app/src/main/res/values/strings.xml` (antes de `<!-- /Menú semanal -->`)

**Interfaces:**
- Consumes: `MenuUiState`, `UiState`, `MenuViewModel.saveDish/deleteDish/countEntriesForDish/addMissingToShoppingList` (Task 5); `Dish`, `Ingredient`, `MissingChoice`, `missingIngredients`, `parseIngredient` (Tasks 1–2); `Zone`, `zoneDisplayLabel`, `PROTECTED_ZONE_NAME`, `Unit`, `formatQuantity` (existentes en `data`).
- Produces:
  - `@Composable fun DishesTab(state: MenuUiState, appState: UiState, viewModel: MenuViewModel)`
  - `@Composable fun DishFormSheet(initial: Dish?, onDismiss: () -> Unit, onSave: (Dish) -> Unit)`
  - `@Composable fun MissingIngredientsSheet(missing: List<Ingredient>, zones: List<Zone>, onAdd: (List<MissingChoice>) -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Add the strings**

Antes de `<!-- /Menú semanal -->` añadir:

```xml
    <string name="menu_dishes_search">Buscar plato…</string>
    <string name="menu_dishes_empty">Aún no tienes platos. Crea el primero con el botón +.</string>
    <string name="menu_dishes_no_results">Ningún plato coincide</string>
    <string name="menu_dish_add_cd">Nuevo plato</string>
    <string name="menu_dish_form_new">Nuevo plato</string>
    <string name="menu_dish_form_edit">Editar plato</string>
    <string name="menu_dish_name">Nombre del plato</string>
    <string name="menu_dish_note">Nota (opcional)</string>
    <string name="menu_dish_ingredients">Ingredientes</string>
    <string name="menu_dish_ingredient_hint">Ingrediente</string>
    <string name="menu_dish_qty_hint">Cant.</string>
    <string name="menu_dish_add_ingredient">+ Ingrediente</string>
    <string name="menu_dish_remove_ingredient_cd">Quitar ingrediente</string>
    <string name="menu_dish_save">Guardar</string>
    <string name="menu_dish_delete_cd">Borrar plato</string>
    <string name="menu_dish_delete_title">Borrar \"%1$s\"</string>
    <string name="menu_dish_delete_message_unused">Este plato no está en ningún menú.</string>
    <string name="menu_dish_delete_confirm">Borrar</string>
    <string name="menu_cancel">Cancelar</string>
    <plurals name="menu_dish_ingredient_count">
        <item quantity="one">%1$d ingrediente</item>
        <item quantity="other">%1$d ingredientes</item>
    </plurals>
    <plurals name="menu_dish_delete_message_used">
        <item quantity="one">Está en %1$d entrada del menú, que se quedará como texto libre.</item>
        <item quantity="other">Está en %1$d entradas del menú, que se quedarán como texto libre.</item>
    </plurals>
    <string name="menu_missing_title">Te faltan estos ingredientes</string>
    <string name="menu_missing_subtitle">Elige cuáles añadir a la lista de la compra y en qué zona.</string>
    <string name="menu_missing_add">Añadir a la lista (%1$d)</string>
    <string name="menu_missing_skip">Ahora no</string>
```

- [ ] **Step 2: Write the dish form**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/DishFormSheet.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Dish
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.parseIngredient
import com.homepantry.app.data.Unit as QtyUnit

private data class IngredientRow(val name: String, val qty: String, val unit: String)

private fun blankRow() = IngredientRow(name = "", qty = "", unit = QtyUnit.UD.name)

/**
 * Formulario de un plato (crear o editar): nombre, ingredientes en filas (nombre, cantidad y
 * unidad opcionales) y nota. Las filas sin nombre o con cantidad inválida se limpian al guardar
 * con `parseIngredient`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishFormSheet(initial: Dish?, onDismiss: () -> Unit, onSave: (Dish) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    val rows = remember {
        mutableStateListOf<IngredientRow>().apply {
            initial?.ingredients?.forEach { ingredient ->
                add(
                    IngredientRow(
                        name = ingredient.name,
                        qty = ingredient.qty?.let { formatQuantity(it) }.orEmpty(),
                        unit = ingredient.unit ?: QtyUnit.UD.name
                    )
                )
            }
            if (isEmpty()) add(blankRow())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(if (initial == null) R.string.menu_dish_form_new else R.string.menu_dish_form_edit),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.menu_dish_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.menu_dish_ingredients),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            rows.forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { rows[index] = row.copy(name = it) },
                        placeholder = { Text(stringResource(R.string.menu_dish_ingredient_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.qty,
                        onValueChange = { rows[index] = row.copy(qty = it) },
                        placeholder = { Text(stringResource(R.string.menu_dish_qty_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(76.dp)
                    )
                    UnitPicker(unit = row.unit, onSelect = { rows[index] = row.copy(unit = it) })
                    IconButton(onClick = {
                        rows.removeAt(index)
                        if (rows.isEmpty()) rows.add(blankRow())
                    }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.menu_dish_remove_ingredient_cd)
                        )
                    }
                }
            }
            TextButton(onClick = { rows.add(blankRow()) }) {
                Text(stringResource(R.string.menu_dish_add_ingredient))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.menu_dish_note)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Dish(
                            id = initial?.id.orEmpty(),
                            name = name.trim(),
                            ingredients = rows.mapNotNull { parseIngredient(it.name, it.qty, it.unit) },
                            note = note.trim().ifEmpty { null },
                            addedBy = initial?.addedBy,
                            addedAt = initial?.addedAt
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) { Text(stringResource(R.string.menu_dish_save)) }
        }
    }
}

/** Selector de unidad: muestra el nombre corto (ud, kg, g…) y abre un menú con todas. */
@Composable
private fun UnitPicker(unit: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(unit.lowercase()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QtyUnit.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option.name)
                    }
                )
            }
        }
    }
}
```

Nota: si el compilador se queja del import sin usar `mutableIntStateOf`, quitarlo.

- [ ] **Step 3: Write the missing-ingredients sheet**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/MissingIngredientsSheet.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Ingredient
import com.homepantry.app.data.MissingChoice
import com.homepantry.app.data.PROTECTED_ZONE_NAME
import com.homepantry.app.data.Zone
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.zoneDisplayLabel
import com.homepantry.app.data.Unit as QtyUnit

/**
 * «Te faltan estos ingredientes»: todos marcados, cada uno con su zona (por defecto «Otros», o la
 * primera zona si no existe). «Añadir a la lista» entrega solo los marcados con su zona elegida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingIngredientsSheet(
    missing: List<Ingredient>,
    zones: List<Zone>,
    onAdd: (List<MissingChoice>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultZoneId = remember(zones) {
        (zones.firstOrNull { it.name.equals(PROTECTED_ZONE_NAME, ignoreCase = true) } ?: zones.firstOrNull())
            ?.id.orEmpty()
    }
    val checked = remember(missing) { mutableStateListOf<Boolean>().apply { repeat(missing.size) { add(true) } } }
    val zoneIds = remember(missing, defaultZoneId) {
        mutableStateListOf<String>().apply { repeat(missing.size) { add(defaultZoneId) } }
    }
    val selectedCount = checked.count { it }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.menu_missing_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.menu_missing_subtitle),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            missing.forEachIndexed { index, ingredient ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                    Column(Modifier.weight(1f)) {
                        Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                        ingredient.qty?.let { qty ->
                            val unitLabel = ingredient.unit?.let { runCatching { QtyUnit.valueOf(it).label }.getOrNull() }
                            Text(
                                text = listOfNotNull(formatQuantity(qty), unitLabel).joinToString(" "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ZonePicker(
                        zones = zones,
                        selectedId = zoneIds[index],
                        onSelect = { zoneIds[index] = it }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_missing_skip)) }
                Button(
                    enabled = selectedCount > 0 && defaultZoneId.isNotEmpty(),
                    onClick = {
                        onAdd(
                            missing.indices
                                .filter { checked[it] }
                                .map { MissingChoice(missing[it], zoneIds[it]) }
                        )
                        onDismiss()
                    }
                ) { Text(stringResource(R.string.menu_missing_add, selectedCount)) }
            }
        }
    }
}

@Composable
private fun ZonePicker(zones: List<Zone>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = zones.firstOrNull { it.id == selectedId }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected?.let { zoneDisplayLabel(it, zones) }.orEmpty())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            zones.forEach { zone ->
                DropdownMenuItem(
                    text = { Text(zoneDisplayLabel(zone, zones)) },
                    onClick = {
                        expanded = false
                        onSelect(zone.id)
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 4: Write the dishes tab**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/DishesTab.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Dish
import com.homepantry.app.data.Ingredient
import com.homepantry.app.data.missingIngredients
import com.homepantry.app.data.normalizeProductName
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.UiState

/**
 * Lista de platos con buscador. Al guardar un plato (nuevo o editado) se comprueba qué
 * ingredientes no están en ninguna zona ni en la lista de la compra y, si falta alguno, se
 * ofrece añadirlos con `MissingIngredientsSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishesTab(state: MenuUiState, appState: UiState, viewModel: MenuViewModel) {
    val resources = LocalContext.current.resources
    var query by rememberSaveable { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }
    var deleteUsage by remember { mutableIntStateOf(0) }
    var missing by remember { mutableStateOf<List<Ingredient>>(emptyList()) }

    LaunchedEffect(dishToDelete) {
        deleteUsage = dishToDelete?.let { viewModel.countEntriesForDish(it.id) } ?: 0
    }

    val normalizedQuery = normalizeProductName(query)
    val visible = state.dishes
        .filter { normalizedQuery.isEmpty() || normalizeProductName(it.name).contains(normalizedQuery) }
        .sortedBy { it.name.lowercase() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.menu_dishes_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            when {
                state.dishes.isEmpty() -> EmptyDishesText(stringResource(R.string.menu_dishes_empty))
                visible.isEmpty() -> EmptyDishesText(stringResource(R.string.menu_dishes_no_results))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { dish ->
                        Card(
                            onClick = {
                                editingDish = dish
                                showForm = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dish.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = resources.getQuantityString(
                                            R.plurals.menu_dish_ingredient_count,
                                            dish.ingredients.size,
                                            dish.ingredients.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { dishToDelete = dish }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.menu_dish_delete_cd),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editingDish = null
                showForm = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.menu_dish_add_cd))
        }
    }

    if (showForm) {
        DishFormSheet(
            initial = editingDish,
            onDismiss = { showForm = false },
            onSave = { dish ->
                viewModel.saveDish(dish)
                missing = missingIngredients(dish, appState.items)
                showForm = false
            }
        )
    }

    if (missing.isNotEmpty()) {
        MissingIngredientsSheet(
            missing = missing,
            zones = appState.zones,
            onAdd = { choices -> viewModel.addMissingToShoppingList(choices) },
            onDismiss = { missing = emptyList() }
        )
    }

    dishToDelete?.let { dish ->
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = { Text(stringResource(R.string.menu_dish_delete_title, dish.name)) },
            text = {
                Text(
                    if (deleteUsage == 0) stringResource(R.string.menu_dish_delete_message_unused)
                    else resources.getQuantityString(R.plurals.menu_dish_delete_message_used, deleteUsage, deleteUsage)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDish(dish.id)
                    dishToDelete = null
                }) {
                    Text(
                        stringResource(R.string.menu_dish_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dishToDelete = null }) { Text(stringResource(R.string.menu_cancel)) }
            }
        )
    }
}

@Composable
private fun EmptyDishesText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Si el compilador marca imports sin usar o falta alguno, corregirlos sin cambiar el comportamiento.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/DishesTab.kt app/src/main/kotlin/com/homepantry/app/ui/screens/DishFormSheet.kt app/src/main/kotlin/com/homepantry/app/ui/screens/MissingIngredientsSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat: dishes tab with form and missing-ingredients suggestion" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

### Task 10: Diálogo de duplicar semana

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/DuplicateWeekDialog.kt`
- Modify: `app/src/main/res/values/strings.xml` (antes de `<!-- /Menú semanal -->`)

**Interfaces:**
- Consumes: `MenuViewModel.countEntriesInWeek`, `MenuViewModel.duplicateWeek` (Task 5); `DuplicateMode`, `weekRangeLabel` (Tasks 1, 3); strings `menu_prev_cd`, `menu_next_cd`, `menu_cancel` (Tasks 7, 9).
- Produces: `@Composable fun DuplicateWeekDialog(source: LocalDate, viewModel: MenuViewModel, onDismiss: () -> Unit, onDone: () -> Unit)`; `source` es el lunes de la semana a copiar. `onDone` se llama cuando la copia ya se ha lanzado.

- [ ] **Step 1: Add the strings**

Antes de `<!-- /Menú semanal -->` añadir:

```xml
    <string name="menu_duplicate_title">Duplicar semana</string>
    <string name="menu_duplicate_message">Copiar la semana %1$s a:</string>
    <string name="menu_duplicate_confirm">Duplicar</string>
    <string name="menu_duplicate_conflict_title">La semana ya tiene platos</string>
    <string name="menu_duplicate_conflict_message">La semana de destino ya tiene %1$d entradas del menú. Puedes reemplazarlas o combinarlas con las copiadas.</string>
    <string name="menu_duplicate_replace">Reemplazar</string>
    <string name="menu_duplicate_merge">Combinar</string>
    <string name="menu_duplicate_done">Semana duplicada</string>
```

- [ ] **Step 2: Write the dialog**

Crear `app/src/main/kotlin/com/homepantry/app/ui/screens/DuplicateWeekDialog.kt`:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.DuplicateMode
import com.homepantry.app.data.weekRangeLabel
import com.homepantry.app.ui.MenuViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Duplica la semana [source] en otra semana a elegir (por defecto la siguiente). Si la semana
 * destino está vacía copia directamente; si ya tiene entradas pregunta entre reemplazar y combinar.
 * No se puede duplicar una semana sobre sí misma.
 */
@Composable
fun DuplicateWeekDialog(
    source: LocalDate,
    viewModel: MenuViewModel,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var target by remember { mutableStateOf(source.plusWeeks(1)) }
    var conflictCount by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (conflictCount == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.menu_duplicate_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.menu_duplicate_message, weekRangeLabel(source)))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { target = target.minusWeeks(1) }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.menu_prev_cd))
                        }
                        Text(text = weekRangeLabel(target), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { target = target.plusWeeks(1) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.menu_next_cd))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = target != source && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val count = viewModel.countEntriesInWeek(target)
                            if (count == 0) {
                                viewModel.duplicateWeek(source, target, DuplicateMode.MERGE)
                                onDone()
                            } else {
                                conflictCount = count
                                busy = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.menu_duplicate_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_cancel)) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.menu_duplicate_conflict_title)) },
            text = { Text(stringResource(R.string.menu_duplicate_conflict_message, conflictCount)) },
            confirmButton = {
                Row {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_cancel)) }
                    TextButton(onClick = {
                        viewModel.duplicateWeek(source, target, DuplicateMode.MERGE)
                        onDone()
                    }) { Text(stringResource(R.string.menu_duplicate_merge)) }
                    TextButton(onClick = {
                        viewModel.duplicateWeek(source, target, DuplicateMode.REPLACE)
                        onDone()
                    }) {
                        Text(
                            stringResource(R.string.menu_duplicate_replace),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/DuplicateWeekDialog.kt app/src/main/res/values/strings.xml
git commit -m "feat: duplicate week dialog with replace or merge" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

---

### Task 11: Compartir la semana como imagen

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/WeekShare.kt`
- Modify: `app/src/main/res/values/strings.xml` (antes de `<!-- /Menú semanal -->`)

**Interfaces:**
- Consumes: `MealEntry`, `MealSlot`, `weekDays`, `weekRangeLabel`, `dayLabel`, `entriesFor` (Tasks 1, 3). El `FileProvider` ya existe (`${applicationId}.fileprovider`, `cache-path path="."`), no se toca el manifest.
- Produces: `object WeekShare { fun render(weekStart: LocalDate, entries: List<MealEntry>): Bitmap; fun share(context: Context, bitmap: Bitmap, chooserTitle: String) }`; string `menu_share_chooser`.

La imagen se dibuja con `android.graphics.Canvas` y `StaticLayout` en vez de un composable: tamaño fijo, independiente del tema y de la pantalla, y sin montar Compose fuera de pantalla. Es una tarjeta clara, pensada para verse bien en una captura, con la marca SNHome y los 7 días con sus 3 franjas (una franja vacía se muestra como «—»).

- [ ] **Step 1: Add the string**

Antes de `<!-- /Menú semanal -->` añadir:

```xml
    <string name="menu_share_chooser">Compartir menú</string>
```

- [ ] **Step 2: Write WeekShare**

Crear `app/src/main/kotlin/com/homepantry/app/data/WeekShare.kt`:

```kotlin
package com.homepantry.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/** Imagen de la semana del menú y su envío con el menú de compartir de Android. */
object WeekShare {
    private const val WIDTH = 1080
    private const val MARGIN = 56
    private const val LABEL_WIDTH = 240

    private val BACKGROUND = 0xFFFFFFFF.toInt()
    private val INK = 0xFF232532.toInt()
    private val SOFT = 0xFF6E6F80.toInt()
    private val BRAND = 0xFF6C5CE7.toInt()
    private val LINE = 0xFFE3E3EA.toInt()

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Dibuja la semana que empieza en [weekStart]; [entries] puede traer entradas de otras semanas. */
    fun render(weekStart: LocalDate, entries: List<MealEntry>): Bitmap {
        val byDate = entries.groupBy { it.date }
        val brandPaint = textPaint(40f, BRAND, bold = true)
        val titlePaint = textPaint(68f, INK, bold = true)
        val dayPaint = textPaint(46f, BRAND, bold = true)
        val labelPaint = textPaint(34f, SOFT)
        val valuePaint = textPaint(38f, INK)
        val linePaint = Paint().apply {
            color = LINE
            strokeWidth = 3f
        }
        val contentWidth = WIDTH - 2 * MARGIN

        // Mismo código para medir (sin lienzo) y para dibujar: devuelve el alto total.
        fun draw(canvas: Canvas?): Int {
            var y = MARGIN

            fun block(text: String, paint: TextPaint, x: Int, width: Int): Int {
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
                if (canvas != null) {
                    canvas.save()
                    canvas.translate(x.toFloat(), y.toFloat())
                    layout.draw(canvas)
                    canvas.restore()
                }
                return layout.height
            }

            y += block("SNHome · Menú semanal", brandPaint, MARGIN, contentWidth) + 8
            y += block("${weekRangeLabel(weekStart)} ${weekStart.plusDays(6).year}", titlePaint, MARGIN, contentWidth) + 36
            weekDays(weekStart).forEach { day ->
                y += block(dayLabel(day), dayPaint, MARGIN, contentWidth) + 10
                val dayEntries = byDate[day.toString()].orEmpty()
                MealSlot.values().forEach { slot ->
                    val names = entriesFor(dayEntries, day.toString(), slot).joinToString(", ") { it.name }
                    val labelHeight = block(slot.label, labelPaint, MARGIN, LABEL_WIDTH)
                    val valueHeight = block(
                        names.ifEmpty { "—" },
                        valuePaint,
                        MARGIN + LABEL_WIDTH,
                        contentWidth - LABEL_WIDTH
                    )
                    y += maxOf(labelHeight, valueHeight) + 10
                }
                y += 14
                canvas?.drawLine(MARGIN.toFloat(), y.toFloat(), (WIDTH - MARGIN).toFloat(), y.toFloat(), linePaint)
                y += 34
            }
            return y - 34 + MARGIN
        }

        val bitmap = Bitmap.createBitmap(WIDTH, draw(null), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        draw(canvas)
        return bitmap
    }

    /** Guarda la imagen como PNG en la caché y abre el menú de compartir de Android. */
    fun share(context: Context, bitmap: Bitmap, chooserTitle: String) {
        val dir = File(context.cacheDir, "menu_share").apply { mkdirs() }
        val file = File(dir, "menu-semanal.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/WeekShare.kt app/src/main/res/values/strings.xml
git commit -m "feat: render and share the week menu as an image" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

---

### Task 12: Integrar en MenuScreen y verificación final

**Files:**
- Modify (reemplazo completo): `app/src/main/kotlin/com/homepantry/app/ui/screens/MenuScreen.kt`

**Interfaces:**
- Consumes: `MenuCalendarTab` (Task 7), `DayMealsSheet` (Task 8), `DishesTab` (Task 9), `DuplicateWeekDialog` (Task 10), `WeekShare` (Task 11), `MenuViewModel`/`MenuUiState` (Task 5), `weekDays` (Task 1); strings `menu_tab_*`, `menu_duplicate_done`, `menu_share_chooser`.
- Produces: `MenuScreen(menuViewModel, appViewModel)` completa (misma firma que el esqueleto de la Task 6, así que `MainActivity` no cambia).

- [ ] **Step 1: Replace MenuScreen**

Reemplazar todo el contenido de `app/src/main/kotlin/com/homepantry/app/ui/screens/MenuScreen.kt` por:

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.data.WeekShare
import com.homepantry.app.data.weekDays
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.MenuViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Pestaña «Menú»: calendario (semana/mes) y lista de platos. Aquí viven el día abierto, el
 * diálogo de duplicar y el Snackbar de errores y avisos; el detalle está en cada componente.
 */
@Composable
fun MenuScreen(menuViewModel: MenuViewModel, appViewModel: AppViewModel) {
    val state by menuViewModel.state.collectAsState()
    val appState by appViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var openDay by remember { mutableStateOf<LocalDate?>(null) }
    var duplicateFrom by remember { mutableStateOf<LocalDate?>(null) }
    val duplicatedMessage = stringResource(R.string.menu_duplicate_done)
    val chooserTitle = stringResource(R.string.menu_share_chooser)

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            menuViewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.menu_tab_menu)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.menu_tab_dishes)) }
                )
            }
            when (selectedTab) {
                0 -> MenuCalendarTab(
                    state = state,
                    viewModel = menuViewModel,
                    onOpenDay = { openDay = it },
                    onShare = { weekStart ->
                        val keys = weekDays(weekStart).map { it.toString() }.toSet()
                        val image = WeekShare.render(weekStart, state.entries.filter { it.date in keys })
                        WeekShare.share(context, image, chooserTitle)
                    },
                    onDuplicate = { duplicateFrom = it }
                )
                else -> DishesTab(state = state, appState = appState, viewModel = menuViewModel)
            }
        }
    }

    openDay?.let { day ->
        DayMealsSheet(date = day, state = state, viewModel = menuViewModel, onDismiss = { openDay = null })
    }

    duplicateFrom?.let { source ->
        DuplicateWeekDialog(
            source = source,
            viewModel = menuViewModel,
            onDismiss = { duplicateFrom = null },
            onDone = {
                duplicateFrom = null
                scope.launch { snackbarHostState.showSnackbar(duplicatedMessage) }
            }
        )
    }
}
```

- [ ] **Step 2: Run the whole unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos los tests en verde (los tres nuevos y los que ya había).

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` generado.

- [ ] **Step 4: Manual check on the device**

Instalar el APK y comprobar, con la casa ya unida:
1. La barra inferior muestra 4 pestañas y «Menú» abre el calendario en la semana de hoy.
2. Deslizar en horizontal cambia de semana, con scroll vertical dentro de cada una; las flechas y «Hoy» funcionan; el cambio Semana/Mes conserva la fecha; en mes, pulsar un día lleva a su semana.
3. Pulsar un día abre la hoja con Desayuno/Comida/Cena; añadir texto libre, añadir un plato sugerido, editar, borrar y deshacer.
4. Pestaña Platos: crear un plato con un ingrediente que no exista en las zonas → sale «Te faltan estos ingredientes»; «Añadir a la lista» lo deja pendiente en la zona elegida. Con un ingrediente que ya tienes o ya está pendiente, no sale nada.
5. Borrar un plato usado en el menú avisa de cuántas entradas y estas se quedan como texto libre.
6. ⋮ → «Duplicar semana»: destino vacío copia directamente; destino con platos ofrece Reemplazar/Combinar.
7. ⋮ → «Compartir semana» abre el menú de compartir con la imagen (7 días, franjas vacías como «—»).
8. Con dos móviles en la misma casa, un cambio en uno aparece en el otro.

Anotar cualquier fallo como tarea aparte; no dar el trabajo por hecho sin haber pasado esta lista.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/MenuScreen.kt
git commit -m "feat: wire the menu screen with calendar, dishes, duplicate and share" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018zGvMwFQAwPLFtRhz6KZQy"
```

