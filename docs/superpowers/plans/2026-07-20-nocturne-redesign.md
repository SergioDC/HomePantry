# Nocturne Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace "Lista de la Casa"'s light Mint/Lime theme and single-screen navigation with the approved Nocturne dark design — a 3-tab bottom nav (Lista/Almacén/Buscar), a new zones dashboard, a new zone detail screen, and a new search screen — without touching Firestore schema or repositories.

**Architecture:** Existing MVVM stays as-is (`AppViewModel` + `UiState` + pure functions in `ItemListLogic.kt`). Navigation moves from a bare `NavHost` to a `Scaffold(bottomBar = ...)` wrapping the `NavHost`, with a route-visibility rule to hide the bottom bar on pushed detail screens. Screens are added incrementally; existing screens (`ManageZonesScreen`, `AddItemSheet`, `JoinHouseholdScreen`, `EditNameDialog`) are reused unchanged.

**Tech Stack:** Kotlin, Jetpack Compose (`compose-bom 2024.06.00`, Material3 1.2.x, `material-icons-extended`), `navigation-compose:2.7.7`, Coil `2.6.0`, JUnit4 for pure-function unit tests (no Robolectric/Compose UI testing is set up in this repo).

## Global Constraints

- Implement **Nocturne only** — no Organic direction, no system light/dark theme following (always dark, same way the app is always light today).
- **No new Gradle dependencies.** `LazyVerticalGrid`/`itemsIndexed` ship with Compose Foundation, already on the classpath transitively via `material3`.
- **No Zone/Item schema changes, no repository changes, no Firestore changes.** Zone icon/color is always auto-derived from `zoneIconLetter(zone)` (first letter) + `zoneColorFor(index)` (existing rotating palette) — never stored.
- **System default font (Roboto).** Do not add bundled font resources or touch `Typography()`.
- Pure-logic changes (`ItemListLogic.kt`, `Zone.kt`, `AppViewModel.kt`) get real JUnit4 tests, following the existing style: backtick-named `@Test fun`, `org.junit.Assert.assertEquals`, per-file `Zone`/`Item` fixtures. Run with `./gradlew testDebugUnitTest --tests "com.listacasa.app.<package>.<ClassName>"`.
- Pure-Compose UI changes (new composables, screens, `MainActivity` wiring) have no test harness in this repo — their verification step is `./gradlew :app:compileDebugKotlin` instead of a unit test run.
- All new user-facing text goes in `app/src/main/res/values/strings.xml` (no hardcoded Spanish strings in Composables), matching the existing convention.
- Commit after every task.

---

### Task 1: Nocturne dark theme

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/theme/Color.kt`
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `NocturnePrimary`, `NocturneBackground`, `NocturneSurface`, `NocturneOnSurface`, `NocturneOnSurfaceVariant`, `NocturneOutline` (and supporting Nocturne* constants) in `Color.kt`; `ListaDeLaCasaTheme` (unchanged name/signature) now applies a dark scheme.

- [ ] **Step 1: Add the Nocturne color constants**

Append to the end of `app/src/main/kotlin/com/listacasa/app/ui/theme/Color.kt` (existing Mint/Lime constants stay untouched above):

```kotlin

// Nocturne (tema oscuro del rediseño de navegación). El Mint/Lime de arriba
// queda sin usar mientras no haya alternancia claro/oscuro.
val NocturneBackground = Color(0xFF161826)
val NocturneSurface = Color(0xFF232532)
val NocturneSurfaceVariant = Color(0xFF2A2C3C)
val NocturnePrimary = Color(0xFF9184D9)
val NocturneOnPrimary = Color(0xFF161826)
val NocturnePrimaryContainer = Color(0xFF2E2A44)
val NocturneOnPrimaryContainer = Color(0xFFE9E9ED)
val NocturneSecondary = Color(0xFF716A9E)
val NocturneOnSecondary = Color(0xFFE9E9ED)
val NocturneSecondaryContainer = Color(0xFF2E2A44)
val NocturneOnSecondaryContainer = Color(0xFFE9E9ED)
val NocturneError = Color(0xFFD85A30)
val NocturneOnError = Color(0xFF161826)
val NocturneErrorContainer = Color(0xFF4A2A22)
val NocturneOnErrorContainer = Color(0xFFE9E9ED)
val NocturneOnSurface = Color(0xFFE9E9ED)
val NocturneOnSurfaceVariant = Color(0xFFB8B8C4)
val NocturneOutline = Color(0xFFE9E9ED).copy(alpha = 0.16f)
```

- [ ] **Step 2: Switch the theme to `darkColorScheme` using the Nocturne constants**

Replace the entire content of `app/src/main/kotlin/com/listacasa/app/ui/theme/Theme.kt`:

```kotlin
package com.listacasa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ListaDeLaCasaColorScheme = darkColorScheme(
    primary = NocturnePrimary,
    onPrimary = NocturneOnPrimary,
    primaryContainer = NocturnePrimaryContainer,
    onPrimaryContainer = NocturneOnPrimaryContainer,
    secondary = NocturneSecondary,
    onSecondary = NocturneOnSecondary,
    secondaryContainer = NocturneSecondaryContainer,
    onSecondaryContainer = NocturneOnSecondaryContainer,
    error = NocturneError,
    onError = NocturneOnError,
    errorContainer = NocturneErrorContainer,
    onErrorContainer = NocturneOnErrorContainer,
    background = NocturneBackground,
    onBackground = NocturneOnSurface,
    surface = NocturneSurface,
    onSurface = NocturneOnSurface,
    surfaceVariant = NocturneSurfaceVariant,
    onSurfaceVariant = NocturneOnSurfaceVariant,
    outline = NocturneOutline
)

@Composable
fun ListaDeLaCasaTheme(content: @Composable () -> Unit) {
    // Nocturne: la app siempre usa este esquema oscuro, sin seguir el tema
    // del sistema (docs/superpowers/specs/2026-07-20-nocturne-redesign-design.md).
    MaterialTheme(
        colorScheme = ListaDeLaCasaColorScheme,
        typography = Typography(),
        content = content
    )
}
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/theme/Color.kt app/src/main/kotlin/com/listacasa/app/ui/theme/Theme.kt
git commit -m "feat: switch app theme to Nocturne dark color scheme"
```

---

### Task 2: `zoneIconLetter` — auto-derived zone icon

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/data/Zone.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/data/ZoneTest.kt`

**Interfaces:**
- Consumes: `Zone` data class (existing).
- Produces: `fun zoneIconLetter(zone: Zone): String` — used by Task 12 (`ZonesDashboardScreen`) via `ZoneCard`.

- [ ] **Step 1: Write the failing tests**

Replace the content of `app/src/test/kotlin/com/listacasa/app/data/ZoneTest.kt`:

```kotlin
package com.listacasa.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneTest {
    @Test fun `Otros is protected regardless of case`() {
        assertTrue(isProtectedZone(Zone(id = "z1", name = "Otros")))
        assertTrue(isProtectedZone(Zone(id = "z2", name = "otros")))
    }

    @Test fun `any other zone name is not protected`() {
        assertFalse(isProtectedZone(Zone(id = "z3", name = "Nevera")))
    }

    @Test fun `zoneIconLetter returns the uppercased first letter of the zone name`() {
        assertEquals("N", zoneIconLetter(Zone(id = "z1", name = "Nevera")))
    }

    @Test fun `zoneIconLetter uppercases even when the name starts lowercase`() {
        assertEquals("C", zoneIconLetter(Zone(id = "z2", name = "congelador")))
    }

    @Test fun `zoneIconLetter falls back to a question mark for a blank name`() {
        assertEquals("?", zoneIconLetter(Zone(id = "z3", name = "")))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ZoneTest"`
Expected: FAIL — `Unresolved reference: zoneIconLetter`

- [ ] **Step 3: Implement `zoneIconLetter`**

Append to the end of `app/src/main/kotlin/com/listacasa/app/data/Zone.kt`:

```kotlin

/** Icono auto-derivado: primera letra del nombre en mayúscula (Nocturne redesign, sin campo de icono editable). */
fun zoneIconLetter(zone: Zone): String = zone.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ZoneTest"`
Expected: `BUILD SUCCESSFUL`, 5 tests passing

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/data/Zone.kt app/src/test/kotlin/com/listacasa/app/data/ZoneTest.kt
git commit -m "feat: add zoneIconLetter to auto-derive a zone's dashboard icon"
```

---

### Task 3: `ListViewMode` / `FlatRow` / `flattenAndSort`

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt`

**Interfaces:**
- Consumes: `Item`, `Zone`, `SortMode` (existing).
- Produces: `enum class ListViewMode { GROUPED, FLAT }`, `data class FlatRow(val item: Item, val zoneName: String)`, `fun flattenAndSort(items: List<Item>, zones: List<Zone>, sortMode: SortMode = SortMode.NEWEST_FIRST): List<FlatRow>` — used by Task 5 (`UiState.flatRows`) and Task 15 (`MainListScreen` flat mode).

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt`, just before the final closing `}` of the class:

```kotlin

    @Test fun `flattenAndSort orders pending before done, ungrouped across zones`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = true),
            Item(id = "2", name = "Pan", zone = "z2", done = false)
        )
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals(listOf("2", "1"), rows.map { it.item.id })
    }

    @Test fun `flattenAndSort attaches the zone name to each row`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "z1"))
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals("Nevera", rows.single().zoneName)
    }

    @Test fun `flattenAndSort leaves the zone name blank when the zone is unknown`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "missing"))
        val rows = flattenAndSort(items, listOf(nevera, despensa), SortMode.NEWEST_FIRST)
        assertEquals("", rows.single().zoneName)
    }

    @Test fun `flattenAndSort respects sort mode within the pending and done groups`() {
        val zebra = Item(id = "1", name = "zanahoria", zone = "z1")
        val apple = Item(id = "2", name = "Arroz", zone = "z2")
        val rows = flattenAndSort(listOf(zebra, apple), listOf(nevera, despensa), SortMode.ALPHABETICAL)
        assertEquals(listOf("2", "1"), rows.map { it.item.id })
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.ItemListLogicTest"`
Expected: FAIL — `Unresolved reference: flattenAndSort`

- [ ] **Step 3: Implement `ListViewMode`, `FlatRow`, `flattenAndSort`, and DRY up the sort comparator**

Replace the entire content of `app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt`:

```kotlin
package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone

data class ZoneSection(val zone: Zone, val items: List<Item>)

enum class SortMode { NEWEST_FIRST, OLDEST_FIRST, ALPHABETICAL }

/** Vista Lista: agrupada por zona (hoy) o plana (Nocturne, cierre de huecos §Nocturne). */
enum class ListViewMode { GROUPED, FLAT }

data class FlatRow(val item: Item, val zoneName: String)

/** "X de Y listos" — comprados vs total (SPEC.md sec 1.4). */
fun progressText(items: List<Item>): String {
    val done = items.count { it.done }
    return "$done de ${items.size} listos"
}

private fun withinGroupComparator(sortMode: SortMode): Comparator<Item> = when (sortMode) {
    SortMode.NEWEST_FIRST -> compareByDescending { it.addedAt }
    SortMode.OLDEST_FIRST -> compareBy { it.addedAt }
    SortMode.ALPHABETICAL -> compareBy { it.name.lowercase() }
}

/**
 * Agrupa por zona (orden de zona) y dentro de cada zona ordena pendientes
 * primero y luego comprados (SPEC.md sec 1.4). `filterZoneId` == "ALL" (o null)
 * no filtra; cualquier otro valor limita el resultado a esa zona. `sortMode`
 * decide el orden dentro de cada subgrupo pendiente/comprado (cierre de
 * huecos §10).
 */
fun groupAndSort(
    items: List<Item>,
    zones: List<Zone>,
    filterZoneId: String?,
    sortMode: SortMode = SortMode.NEWEST_FIRST
): List<ZoneSection> {
    val isFiltered = filterZoneId != null && filterZoneId != "ALL"
    val relevantZones = zones.sortedBy { it.order }
        .filter { !isFiltered || it.id == filterZoneId }

    val comparator = compareBy<Item> { it.done }.then(withinGroupComparator(sortMode))

    return relevantZones.mapNotNull { zone ->
        val zoneItems = items.filter { it.zone == zone.id }.sortedWith(comparator)
        if (zoneItems.isEmpty() && !isFiltered) null else ZoneSection(zone, zoneItems)
    }
}

/**
 * Igual que `groupAndSort` pero sin agrupar: una única lista con pendientes
 * antes que comprados, cada fila con el nombre de su zona para el chip de
 * etiqueta (pantalla "Todo" de Nocturne).
 */
fun flattenAndSort(
    items: List<Item>,
    zones: List<Zone>,
    sortMode: SortMode = SortMode.NEWEST_FIRST
): List<FlatRow> {
    val zoneNameById = zones.associate { it.id to it.name }
    val comparator = compareBy<Item> { it.done }.then(withinGroupComparator(sortMode))
    return items.sortedWith(comparator).map { FlatRow(item = it, zoneName = zoneNameById[it.zone] ?: "") }
}

/** Filtro de búsqueda por texto, client-side (cierre de huecos §11). */
fun filterItemsByQuery(items: List<Item>, query: String): List<Item> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return items
    return items.filter { it.name.lowercase().contains(normalized) }
}

/**
 * Busca un producto pendiente con el mismo código de barras, para ofrecer
 * sumar cantidad en vez de crear un duplicado (cierre de huecos §3).
 */
fun findPendingDuplicateByBarcode(items: List<Item>, barcode: String): Item? =
    items.firstOrNull { it.barcode == barcode && !it.done }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.ItemListLogicTest"`
Expected: `BUILD SUCCESSFUL`, all tests (existing `groupAndSort`/`filterItemsByQuery`/`findPendingDuplicateByBarcode` tests plus the 4 new ones) passing

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt
git commit -m "feat: add ListViewMode/FlatRow/flattenAndSort for the flat 'Todo' list view"
```

---

### Task 4: `ZoneSummary` / `zoneSummaries`

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt`

**Interfaces:**
- Consumes: `Item`, `Zone` (existing).
- Produces: `data class ZoneSummary(val zone: Zone, val itemCount: Int, val pendingCount: Int)`, `fun zoneSummaries(items: List<Item>, zones: List<Zone>): List<ZoneSummary>` — used by Task 5 (`UiState.zoneCards`) and Task 12 (`ZonesDashboardScreen`).

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt`, just before the final closing `}` of the class:

```kotlin

    @Test fun `zoneSummaries counts total and pending items per zone, in zone order`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = false),
            Item(id = "2", name = "Pan", zone = "z1", done = true),
            Item(id = "3", name = "Arroz", zone = "z2", done = false)
        )
        val summaries = zoneSummaries(items, listOf(despensa, nevera))
        assertEquals(listOf("z1", "z2"), summaries.map { it.zone.id })
        assertEquals(2, summaries[0].itemCount)
        assertEquals(1, summaries[0].pendingCount)
        assertEquals(1, summaries[1].itemCount)
        assertEquals(1, summaries[1].pendingCount)
    }

    @Test fun `zoneSummaries reports zero pending for a zone with only done items`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "z1", done = true))
        val summaries = zoneSummaries(items, listOf(nevera))
        assertEquals(0, summaries.single().pendingCount)
    }

    @Test fun `zoneSummaries includes zones with no items at all`() {
        val summaries = zoneSummaries(emptyList(), listOf(nevera, despensa))
        assertEquals(listOf(0, 0), summaries.map { it.itemCount })
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.ItemListLogicTest"`
Expected: FAIL — `Unresolved reference: zoneSummaries`

- [ ] **Step 3: Implement `ZoneSummary` and `zoneSummaries`**

Append to the end of `app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt`:

```kotlin

data class ZoneSummary(val zone: Zone, val itemCount: Int, val pendingCount: Int)

/** Resumen por zona para las tarjetas del dashboard "Almacén" (Nocturne). */
fun zoneSummaries(items: List<Item>, zones: List<Zone>): List<ZoneSummary> =
    zones.sortedBy { it.order }.map { zone ->
        val zoneItems = items.filter { it.zone == zone.id }
        ZoneSummary(zone = zone, itemCount = zoneItems.size, pendingCount = zoneItems.count { !it.done })
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.ItemListLogicTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt
git commit -m "feat: add zoneSummaries for the Almacen dashboard zone cards"
```

---

### Task 5: `UiState` / `AppViewModel` wiring

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/AppViewModel.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/ui/UiStateTest.kt`

**Interfaces:**
- Consumes: `ListViewMode`, `FlatRow`, `flattenAndSort` (Task 3), `ZoneSummary`, `zoneSummaries` (Task 4).
- Produces: `UiState.listViewMode: ListViewMode` (default `GROUPED`), `UiState.flatRows: List<FlatRow>`, `UiState.zoneCards: List<ZoneSummary>`, `AppViewModel.setListViewMode(mode: ListViewMode)`. **Breaking change:** `UiState.sections` no longer applies the search-query filter (inline search on Lista is removed in Task 15 — filtering by query becomes the Buscar tab's job, via `filterItemsByQuery` called directly).

- [ ] **Step 1: Write the failing/updated tests**

Replace the entire content of `app/src/test/kotlin/com/listacasa/app/ui/UiStateTest.kt`:

```kotlin
package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {
    private val nevera = Zone(id = "z1", name = "Nevera", order = 0)
    private val despensa = Zone(id = "z2", name = "Despensa", order = 1)

    @Test fun `sections no longer filters by search query -- that is the Buscar tab's job`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Pan", zone = "z1")
            ),
            zones = listOf(nevera),
            selectedZoneId = "ALL",
            searchQuery = "leche"
        )
        assertEquals(listOf("1", "2"), state.sections.single().items.map { it.id })
    }

    @Test fun `sections applies the selected sort mode`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "zanahoria", zone = "z1"),
                Item(id = "2", name = "arroz", zone = "z1")
            ),
            zones = listOf(nevera),
            selectedZoneId = "ALL",
            sortMode = SortMode.ALPHABETICAL
        )
        assertEquals(listOf("2", "1"), state.sections.single().items.map { it.id })
    }

    @Test fun `flatRows filters by the selected zone before flattening`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Arroz", zone = "z2")
            ),
            zones = listOf(nevera, despensa),
            selectedZoneId = "z2"
        )
        assertEquals(listOf("2"), state.flatRows.map { it.item.id })
    }

    @Test fun `flatRows includes every zone when ALL is selected`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Arroz", zone = "z2")
            ),
            zones = listOf(nevera, despensa),
            selectedZoneId = "ALL"
        )
        assertEquals(setOf("1", "2"), state.flatRows.map { it.item.id }.toSet())
    }

    @Test fun `zoneCards summarizes every zone regardless of the selected zone filter`() {
        val state = UiState(
            items = listOf(Item(id = "1", name = "Leche", zone = "z1")),
            zones = listOf(nevera, despensa),
            selectedZoneId = "z1"
        )
        assertEquals(listOf("z1", "z2"), state.zoneCards.map { it.zone.id })
    }

    @Test fun `listViewMode defaults to GROUPED`() {
        assertEquals(ListViewMode.GROUPED, UiState().listViewMode)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.UiStateTest"`
Expected: FAIL — `Unresolved reference: flatRows` (and `zoneCards`, `listViewMode`)

- [ ] **Step 3: Update `UiState` and `AppViewModel`**

In `app/src/main/kotlin/com/listacasa/app/ui/AppViewModel.kt`, replace the `UiState` data class (lines 15-27):

```kotlin
data class UiState(
    val items: List<Item> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val selectedZoneId: String = "ALL",
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NEWEST_FIRST,
    val listViewMode: ListViewMode = ListViewMode.GROUPED,
    val loading: Boolean = true,
    val error: String? = null
) {
    val progress: String get() = progressText(items)
    val sections: List<ZoneSection> get() = groupAndSort(items, zones, selectedZoneId, sortMode)
    val flatRows: List<FlatRow> get() {
        val zoneFiltered = if (selectedZoneId == "ALL") items else items.filter { it.zone == selectedZoneId }
        return flattenAndSort(zoneFiltered, zones, sortMode)
    }
    val zoneCards: List<ZoneSummary> get() = zoneSummaries(items, zones)
}
```

Then, in the same file, add a new method right after `setSortMode` (after line 78):

```kotlin

    fun setListViewMode(mode: ListViewMode) {
        _state.value = _state.value.copy(listViewMode = mode)
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.UiStateTest"`
Expected: `BUILD SUCCESSFUL`

Also run the full unit test suite to confirm nothing else broke:
Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/AppViewModel.kt app/src/test/kotlin/com/listacasa/app/ui/UiStateTest.kt
git commit -m "feat: wire listViewMode/flatRows/zoneCards into UiState and AppViewModel"
```

---

### Task 6: New string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: string resources consumed by Tasks 12-16 (`R.string.nav_lista`, `R.string.nav_almacen`, `R.string.nav_buscar`, `R.string.dashboard_title`, `R.string.dashboard_greeting`, `R.string.dashboard_summary_up_to_date`, `R.string.dashboard_summary_pending`, `R.string.dashboard_new_zone`, `R.string.zone_detail_back_cd`, `R.string.zone_detail_edit_cd`, `R.string.search_hint`, `R.string.search_results_count`, `R.string.search_empty`, `R.string.main_title`, `R.string.main_view_grouped`, `R.string.main_view_flat`, `R.string.main_updated_in_zone`, `R.string.main_overflow_cd`).

- [ ] **Step 1: Add the new strings**

In `app/src/main/res/values/strings.xml`, insert this block right before the closing `</resources>` tag (after the existing `zones_protected_hint` line):

```xml

    <!-- Bottom navigation (Nocturne) -->
    <string name="nav_lista">Lista</string>
    <string name="nav_almacen">Almacén</string>
    <string name="nav_buscar">Buscar</string>

    <!-- ZonesDashboardScreen -->
    <string name="dashboard_title">Alacena</string>
    <string name="dashboard_greeting">Hola, %1$s</string>
    <string name="dashboard_summary_up_to_date">%1$d productos · al día</string>
    <string name="dashboard_summary_pending">%1$d productos · %2$d pendientes</string>
    <string name="dashboard_new_zone">+ Nueva zona</string>

    <!-- ZoneDetailScreen -->
    <string name="zone_detail_back_cd">Volver</string>
    <string name="zone_detail_edit_cd">Editar zona</string>

    <!-- SearchScreen -->
    <string name="search_hint">Buscar producto…</string>
    <string name="search_results_count">%1$d resultados</string>
    <string name="search_empty">Escribe para buscar productos</string>

    <!-- MainListScreen (Nocturne restyle) -->
    <string name="main_title">Lista de la compra</string>
    <string name="main_view_grouped">Agrupado</string>
    <string name="main_view_flat">Todo</string>
    <string name="main_updated_in_zone">Actualizado en %1$s</string>
    <string name="main_overflow_cd">Más opciones</string>
</resources>
```

(Note the final `</resources>` above replaces the original closing tag — don't duplicate it.)

- [ ] **Step 2: Verify the module compiles (resource merge succeeds)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add strings for the Nocturne bottom nav, dashboard, zone detail and search screens"
```

---

### Task 7: `BottomNavBar` component

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/BottomNavBar.kt`

**Interfaces:**
- Consumes: nothing project-specific.
- Produces: `data class BottomNavItem(val route: String, val label: String)`, `@Composable fun BottomNavBar(items: List<BottomNavItem>, selectedRoute: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)` — used by Task 16 (`MainActivity`).

- [ ] **Step 1: Write the component**

Create `app/src/main/kotlin/com/listacasa/app/ui/components/BottomNavBar.kt`:

```kotlin
package com.listacasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

data class BottomNavItem(val route: String, val label: String)

/** Barra de navegación inferior Nocturne: 3 destinos, indicador de punto (no iconos Material). */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { navItem ->
                val isSelected = navItem.route == selectedRoute
                Column(
                    modifier = Modifier.clickable { onSelect(navItem.route) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val dotColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = navItem.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/components/BottomNavBar.kt
git commit -m "feat: add BottomNavBar component for the Nocturne 3-tab navigation"
```

---

### Task 8: `NocturneFab` component

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/NocturneFab.kt`

**Interfaces:**
- Consumes: nothing project-specific.
- Produces: `@Composable fun NocturneFab(onClick: () -> Unit, contentDescription: String?, modifier: Modifier = Modifier)` — used by Task 13 (`ZoneDetailScreen`) and Task 15 (`MainListScreen`).

- [ ] **Step 1: Write the component**

Create `app/src/main/kotlin/com/listacasa/app/ui/components/NocturneFab.kt`:

```kotlin
package com.listacasa.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** FAB estilo Nocturne: fondo transparente, borde de acento, "+" de acento. */
@Composable
fun NocturneFab(onClick: () -> Unit, contentDescription: String?, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        Icon(Icons.Filled.Add, contentDescription = contentDescription)
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/components/NocturneFab.kt
git commit -m "feat: add NocturneFab (outlined transparent FAB) for Lista and Zone Detail"
```

---

### Task 9: `SegmentedToggle` component

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/SegmentedToggle.kt`

**Interfaces:**
- Consumes: nothing project-specific (generic).
- Produces: `@Composable fun <T> SegmentedToggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier)` — used by Task 15 (`MainListScreen`, with `T = ListViewMode`).

- [ ] **Step 1: Write the component**

Create `app/src/main/kotlin/com/listacasa/app/ui/components/SegmentedToggle.kt`:

```kotlin
package com.listacasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Toggle Agrupado/Todo de la pestaña Lista (Nocturne). */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/components/SegmentedToggle.kt
git commit -m "feat: add SegmentedToggle component for Agrupado/Todo list view switch"
```

---

### Task 10: `ItemPillRow` component

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/ItemPillRow.kt`

**Interfaces:**
- Consumes: `Item`, `Unit` (data layer, existing), `R.string.main_delete_item_cd` (existing string).
- Produces: `@Composable fun ItemPillRow(item: Item, zoneName: String? = null, updatedInZoneLabel: String? = null, showDeleteAction: Boolean = true, onToggleDone: () -> Unit, onDelete: () -> Unit = {}, onEdit: () -> Unit = {})` — used by Task 13 (`ZoneDetailScreen`), Task 14 (`SearchScreen`, with `showDeleteAction = false` to show a trailing chevron instead, per the design doc's "pill rows + zone tag chip + trailing chevron"), Task 15 (`MainListScreen`).

- [ ] **Step 1: Write the component**

Create `app/src/main/kotlin/com/listacasa/app/ui/components/ItemPillRow.kt`:

```kotlin
package com.listacasa.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.Unit as ItemUnit

/**
 * Fila de producto estilo "pill" (Nocturne), reemplaza ProductCard en Lista,
 * Zone Detail y Buscar. `zoneName` (opcional) muestra el chip de etiqueta de
 * zona. `updatedInZoneLabel` (opcional) muestra la nota "Actualizado en
 * {zona}" bajo productos comprados en la vista "Todo". `showDeleteAction`
 * controla el elemento final de la fila: "×" para borrar (Lista/Zone
 * Detail, por defecto) o una flecha ">" (Buscar, donde tocar la fila ya
 * abre la edición y no se ofrece borrar directamente).
 */
@Composable
fun ItemPillRow(
    item: Item,
    zoneName: String? = null,
    updatedInZoneLabel: String? = null,
    showDeleteAction: Boolean = true,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val unitLabel = runCatching { ItemUnit.valueOf(item.unit).label }.getOrDefault(item.unit)
    val rowAlpha = if (item.done) 0.5f else 1f

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedIconButton(onClick = onToggleDone, modifier = Modifier.size(28.dp)) {
            if (item.done) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
                .clickable(onClick = onEdit)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null
            )
            val addedBySuffix = item.addedBy?.takeIf { it.isNotBlank() }?.let { " · pedido por $it" } ?: ""
            Text(
                text = "${item.qty} $unitLabel$addedBySuffix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!item.note.isNullOrBlank()) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!updatedInZoneLabel.isNullOrBlank()) {
                Text(
                    text = updatedInZoneLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!zoneName.isNullOrBlank()) {
            Text(
                text = zoneName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (showDeleteAction) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.main_delete_item_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/components/ItemPillRow.kt
git commit -m "feat: add ItemPillRow component for the Nocturne pill-style item rows"
```

---

### Task 11: `ZoneCard` component

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/ZoneCard.kt`

**Interfaces:**
- Consumes: nothing project-specific (takes pre-computed `letter`/`colorHex`/`summaryText` strings).
- Produces: `@Composable fun ZoneCard(letter: String, colorHex: String, name: String, summaryText: String, onClick: () -> Unit, modifier: Modifier = Modifier)` — used by Task 12 (`ZonesDashboardScreen`).

- [ ] **Step 1: Write the component**

Create `app/src/main/kotlin/com/listacasa/app/ui/components/ZoneCard.kt`:

```kotlin
package com.listacasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Tarjeta de zona del dashboard "Almacén": icono de letra + color auto-derivados. */
@Composable
fun ZoneCard(
    letter: String,
    colorHex: String,
    name: String,
    summaryText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)

    Card(onClick = onClick, modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(dotColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = letter, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/components/ZoneCard.kt
git commit -m "feat: add ZoneCard component for the Almacen dashboard grid"
```

---

### Task 12: `ZonesDashboardScreen`

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/ZonesDashboardScreen.kt`

**Interfaces:**
- Consumes: `AppViewModel` / `UiState.zoneCards` (Task 5), `ZoneCard` (Task 11), `zoneIconLetter`/`zoneColorFor` (`data/Zone.kt`), `R.string.dashboard_*` (Task 6).
- Produces: `@Composable fun ZonesDashboardScreen(viewModel: AppViewModel, userName: String, onManageZones: () -> Unit, onOpenZone: (String) -> Unit)` — used by Task 16 (`MainActivity`, route `zonesDashboard`).

- [ ] **Step 1: Write the screen**

Create `app/src/main/kotlin/com/listacasa/app/ui/screens/ZonesDashboardScreen.kt`:

```kotlin
package com.listacasa.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.item
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.listacasa.app.R
import com.listacasa.app.data.zoneColorFor
import com.listacasa.app.data.zoneIconLetter
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.components.ZoneCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pestaña "Almacén": dashboard de zonas (Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesDashboardScreen(
    viewModel: AppViewModel,
    userName: String,
    onManageZones: () -> Unit,
    onOpenZone: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showCreateZone by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale("es", "ES")).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onManageZones) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_cd))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.dashboard_greeting, userName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, top = 8.dp)
            )
            Text(
                text = today.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp, bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(state.zoneCards, key = { _, summary -> summary.zone.id }) { index, summary ->
                    val summaryText = if (summary.pendingCount == 0) {
                        stringResource(R.string.dashboard_summary_up_to_date, summary.itemCount)
                    } else {
                        stringResource(R.string.dashboard_summary_pending, summary.itemCount, summary.pendingCount)
                    }
                    ZoneCard(
                        letter = zoneIconLetter(summary.zone),
                        colorHex = zoneColorFor(index),
                        name = summary.zone.name,
                        summaryText = summaryText,
                        onClick = { onOpenZone(summary.zone.id) }
                    )
                }
                item {
                    OutlinedCard(
                        onClick = { showCreateZone = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_new_zone),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateZone) {
        AlertDialog(
            onDismissRequest = { showCreateZone = false; newZoneName = "" },
            title = { Text(stringResource(R.string.dashboard_new_zone)) },
            text = {
                OutlinedTextField(
                    value = newZoneName,
                    onValueChange = { newZoneName = it },
                    label = { Text(stringResource(R.string.zones_new_zone_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newZoneName.isNotBlank()) {
                        viewModel.createZone(newZoneName.trim())
                    }
                    newZoneName = ""
                    showCreateZone = false
                }) { Text(stringResource(R.string.add_item_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateZone = false; newZoneName = "" }) { Text("Cancelar") }
            }
        )
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/ZonesDashboardScreen.kt
git commit -m "feat: add ZonesDashboardScreen (Almacen tab)"
```

---

### Task 13: `ZoneDetailScreen`

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/ZoneDetailScreen.kt`

**Interfaces:**
- Consumes: `AppViewModel`, `groupAndSort` (existing, `ui/ItemListLogic.kt`), `ItemPillRow` (Task 10), `NocturneFab` (Task 8), `R.string.zone_detail_*`/`main_add_item_cd`/`main_empty_list` (existing + Task 6).
- Produces: `@Composable fun ZoneDetailScreen(viewModel: AppViewModel, zoneId: String, onBack: () -> Unit, onManageZones: () -> Unit, onAddItem: () -> Unit, onEditItem: (Item) -> Unit)` — used by Task 16 (`MainActivity`, route `zoneDetail/{zoneId}`).

- [ ] **Step 1: Write the screen**

Create `app/src/main/kotlin/com/listacasa/app/ui/screens/ZoneDetailScreen.kt`:

```kotlin
package com.listacasa.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.components.ItemPillRow
import com.listacasa.app.ui.components.NocturneFab
import com.listacasa.app.ui.groupAndSort

/** Pantalla de detalle de una zona, abierta desde el dashboard "Almacén" (Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailScreen(
    viewModel: AppViewModel,
    zoneId: String,
    onBack: () -> Unit,
    onManageZones: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val zone = state.zones.firstOrNull { it.id == zoneId }
    val zoneItems = groupAndSort(state.items, state.zones, filterZoneId = zoneId).firstOrNull()?.items ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zone?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zone_detail_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = onManageZones) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.zone_detail_edit_cd))
                    }
                }
            )
        },
        floatingActionButton = {
            NocturneFab(onClick = onAddItem, contentDescription = stringResource(R.string.main_add_item_cd))
        }
    ) { padding ->
        if (zoneItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.main_empty_list))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(zoneItems, key = { it.id }) { product ->
                    ItemPillRow(
                        item = product,
                        onToggleDone = { viewModel.toggleDone(product) },
                        onDelete = { viewModel.deleteItem(product.id) },
                        onEdit = { onEditItem(product) }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/ZoneDetailScreen.kt
git commit -m "feat: add ZoneDetailScreen"
```

---

### Task 14: `SearchScreen`

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/SearchScreen.kt`

**Interfaces:**
- Consumes: `AppViewModel`, `filterItemsByQuery` (existing, `ui/ItemListLogic.kt`), `ItemPillRow(item, zoneName, showDeleteAction, onToggleDone, onEdit)` (Task 10), `R.string.nav_buscar`/`search_*` (Task 6).
- Produces: `@Composable fun SearchScreen(viewModel: AppViewModel, onEditItem: (Item) -> Unit)` — used by Task 16 (`MainActivity`, route `search`).

- [ ] **Step 1: Write the screen**

Create `app/src/main/kotlin/com/listacasa/app/ui/screens/SearchScreen.kt`:

```kotlin
package com.listacasa.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.components.ItemPillRow
import com.listacasa.app.ui.filterItemsByQuery

/** Pestaña "Buscar": búsqueda siempre visible, sin toggle (Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: AppViewModel, onEditItem: (Item) -> Unit) {
    val state by viewModel.state.collectAsState()
    val zoneNameById = state.zones.associate { it.id to it.name }
    val results = filterItemsByQuery(state.items, state.searchQuery)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_buscar)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            Text(
                text = stringResource(R.string.search_results_count, results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(results, key = { it.id }) { product ->
                        ItemPillRow(
                            item = product,
                            zoneName = zoneNameById[product.zone],
                            showDeleteAction = false,
                            onToggleDone = { viewModel.toggleDone(product) },
                            onEdit = { onEditItem(product) }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/SearchScreen.kt
git commit -m "feat: add SearchScreen (Buscar tab)"
```

---

### Task 15: Restyle `MainListScreen` (+ delete `ProductCard`)

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/screens/MainListScreen.kt`
- Delete: `app/src/main/kotlin/com/listacasa/app/ui/components/ProductCard.kt`

**Interfaces:**
- Consumes: `ItemPillRow` (Task 10), `NocturneFab` (Task 8), `SegmentedToggle` (Task 9), `ZoneChip` (existing, unchanged), `ListViewMode` (Task 3), `UiState.flatRows`/`UiState.sections` (Task 5), `R.string.main_title`/`main_view_*`/`main_updated_in_zone`/`main_overflow_cd` (Task 6).
- Produces: `MainListScreen` with a **changed signature** — the `onManageZones: () -> Unit` parameter is removed (Nocturne reaches zone management only via the Almacén tab's gear icon and Zone Detail's pencil icon, never from Lista). Used by Task 16 (`MainActivity`, route `mainList`).

- [ ] **Step 1: Replace `MainListScreen.kt`**

Replace the entire content of `app/src/main/kotlin/com/listacasa/app/ui/screens/MainListScreen.kt`:

```kotlin
package com.listacasa.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.zoneColorFor
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.ListViewMode
import com.listacasa.app.ui.SortMode
import com.listacasa.app.ui.components.ItemPillRow
import com.listacasa.app.ui.components.NocturneFab
import com.listacasa.app.ui.components.ProgressBar
import com.listacasa.app.ui.components.SegmentedToggle
import com.listacasa.app.ui.components.ZoneChip

/** Pestaña "Lista": la lista de la compra (SPEC.md sec 1.4, restyle Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: AppViewModel,
    isOnline: Boolean = true,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit = {},
    onEditName: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.main_overflow_cd))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_cd)) },
                            onClick = {
                                menuExpanded = false
                                sortMenuExpanded = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_clear_done)) },
                            onClick = {
                                menuExpanded = false
                                showClearConfirm = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_edit_name)) },
                            onClick = {
                                menuExpanded = false
                                onEditName()
                            }
                        )
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_newest)) },
                            onClick = { viewModel.setSortMode(SortMode.NEWEST_FIRST); sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_oldest)) },
                            onClick = { viewModel.setSortMode(SortMode.OLDEST_FIRST); sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_alpha)) },
                            onClick = { viewModel.setSortMode(SortMode.ALPHABETICAL); sortMenuExpanded = false }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            NocturneFab(onClick = onAddItem, contentDescription = stringResource(R.string.main_add_item_cd))
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isOnline) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.main_offline_indicator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            ProgressBar(items = state.items, modifier = Modifier.padding(16.dp))

            SegmentedToggle(
                options = listOf(
                    ListViewMode.GROUPED to stringResource(R.string.main_view_grouped),
                    ListViewMode.FLAT to stringResource(R.string.main_view_flat)
                ),
                selected = state.listViewMode,
                onSelect = { viewModel.setListViewMode(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoneChip(
                    label = stringResource(R.string.main_all_zones_tab),
                    colorHex = null,
                    selected = state.selectedZoneId == "ALL",
                    onClick = { viewModel.selectZone("ALL") }
                )
                state.zones.sortedBy { it.order }.forEachIndexed { index, zone ->
                    ZoneChip(
                        zone = zone,
                        colorHex = zoneColorFor(index),
                        selected = state.selectedZoneId == zone.id,
                        onClick = { viewModel.selectZone(zone.id) }
                    )
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.main_error_generic))
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.main_empty_list))
                }
                state.listViewMode == ListViewMode.FLAT -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.flatRows, key = { it.item.id }) { row ->
                        ItemPillRow(
                            item = row.item,
                            zoneName = row.zoneName,
                            updatedInZoneLabel = if (row.item.done) {
                                stringResource(R.string.main_updated_in_zone, row.zoneName)
                            } else null,
                            onToggleDone = { viewModel.toggleDone(row.item) },
                            onDelete = { viewModel.deleteItem(row.item.id) },
                            onEdit = { onEditItem(row.item) }
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    state.sections.forEach { section ->
                        item {
                            Text(
                                text = section.zone.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(section.items, key = { it.id }) { product ->
                            ItemPillRow(
                                item = product,
                                onToggleDone = { viewModel.toggleDone(product) },
                                onDelete = { viewModel.deleteItem(product.id) },
                                onEdit = { onEditItem(product) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        val doneCount = state.items.count { it.done }
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.main_clear_done_confirm_title)) },
            text = { Text(stringResource(R.string.main_clear_done_confirm_message, doneCount)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearDone()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.main_clear_done_confirm_title)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
```

- [ ] **Step 2: Delete `ProductCard.kt` (its last usage is gone)**

```bash
git rm app/src/main/kotlin/com/listacasa/app/ui/components/ProductCard.kt
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` — this also confirms no other file still references `ProductCard` or the removed `onManageZones` parameter (a leftover reference would fail compilation here).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/MainListScreen.kt
git commit -m "feat: restyle MainListScreen for Nocturne (grouped/flat toggle, pill rows, overflow menu) and drop ProductCard"
```

---

### Task 16: `MainActivity` navigation wiring

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/MainActivity.kt`

**Interfaces:**
- Consumes: `BottomNavBar`/`BottomNavItem` (Task 7), `ZonesDashboardScreen` (Task 12), `ZoneDetailScreen` (Task 13), `SearchScreen` (Task 14), restyled `MainListScreen` (Task 15, no `onManageZones` param), `R.string.nav_*` (Task 6).
- Produces: final navigation graph with routes `mainList`, `zonesDashboard`, `zoneDetail/{zoneId}`, `search`, `manageZones`; bottom bar visible on the first four, hidden on `manageZones`.

- [ ] **Step 1: Replace `MainActivity.kt`**

Replace the entire content of `app/src/main/kotlin/com/listacasa/app/MainActivity.kt`:

```kotlin
package com.listacasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.listacasa.app.data.ConnectivityObserver
import com.listacasa.app.data.Item
import com.listacasa.app.data.ItemsRepository
import com.listacasa.app.data.StorageRepository
import com.listacasa.app.data.UserPrefs
import com.listacasa.app.data.ZonesRepository
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.components.BottomNavBar
import com.listacasa.app.ui.components.BottomNavItem
import com.listacasa.app.ui.screens.AddItemSheet
import com.listacasa.app.ui.screens.EditNameDialog
import com.listacasa.app.ui.screens.JoinHouseholdScreen
import com.listacasa.app.ui.screens.MainListScreen
import com.listacasa.app.ui.screens.ManageZonesScreen
import com.listacasa.app.ui.screens.SearchScreen
import com.listacasa.app.ui.screens.ZoneDetailScreen
import com.listacasa.app.ui.screens.ZonesDashboardScreen
import com.listacasa.app.ui.theme.ListaDeLaCasaTheme
import kotlinx.coroutines.tasks.await

/**
 * Punto de entrada. Firma anónimamente (Firebase Auth) antes de tocar
 * Firestore, luego decide JoinHousehold vs. la app según haya o no código
 * de casa guardado en DataStore (SPEC.md sec 1.1 y 3).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ListaDeLaCasaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ListaDeLaCasaApp()
                }
            }
        }
    }
}

private val BOTTOM_NAV_ROUTES = setOf("mainList", "zonesDashboard", "search")

@Composable
fun ListaDeLaCasaApp() {
    val context = LocalContext.current
    val userPrefs = remember { UserPrefs(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    var authReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        authReady = true
    }

    if (!authReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val householdCode by userPrefs.householdCode.collectAsState(initial = null)
    val userName by userPrefs.userName.collectAsState(initial = null)

    val code = householdCode
    val name = userName

    if (code.isNullOrBlank() || name.isNullOrBlank()) {
        JoinHouseholdScreen(userPrefs = userPrefs, onJoined = { /* recomposes from the DataStore flow */ })
        return
    }

    val firestore = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val connectivityObserver = remember { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observe().collectAsState(initial = true)

    val viewModel: AppViewModel = viewModel(
        key = code,
        factory = remember(code, name) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        itemsRepository = ItemsRepository(firestore, code),
                        zonesRepository = ZonesRepository(firestore, code),
                        storageRepository = StorageRepository(storage, code, context.applicationContext),
                        userName = name
                    ) as T
                }
            }
        }
    )

    var showAddItem by remember { mutableStateOf(false) }
    var addItemZoneOverride by remember { mutableStateOf<String?>(null) }
    var itemBeingEdited by remember { mutableStateOf<Item?>(null) }
    var showEditName by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == null ||
        BOTTOM_NAV_ROUTES.contains(currentRoute) ||
        currentRoute.startsWith("zoneDetail/")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val selectedRoute = if (currentRoute?.startsWith("zoneDetail/") == true) "zonesDashboard" else currentRoute
                BottomNavBar(
                    items = listOf(
                        BottomNavItem("mainList", stringResource(R.string.nav_lista)),
                        BottomNavItem("zonesDashboard", stringResource(R.string.nav_almacen)),
                        BottomNavItem("search", stringResource(R.string.nav_buscar))
                    ),
                    selectedRoute = selectedRoute ?: "mainList",
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "mainList",
            modifier = Modifier.padding(padding)
        ) {
            composable("mainList") {
                MainListScreen(
                    viewModel = viewModel,
                    isOnline = isOnline,
                    onAddItem = {
                        addItemZoneOverride = null
                        showAddItem = true
                    },
                    onEditItem = { item -> itemBeingEdited = item },
                    onEditName = { showEditName = true }
                )
            }
            composable("zonesDashboard") {
                ZonesDashboardScreen(
                    viewModel = viewModel,
                    userName = name,
                    onManageZones = { navController.navigate("manageZones") },
                    onOpenZone = { zoneId -> navController.navigate("zoneDetail/$zoneId") }
                )
            }
            composable("zoneDetail/{zoneId}") { backStackEntry ->
                val zoneId = backStackEntry.arguments?.getString("zoneId") ?: return@composable
                ZoneDetailScreen(
                    viewModel = viewModel,
                    zoneId = zoneId,
                    onBack = { navController.popBackStack() },
                    onManageZones = { navController.navigate("manageZones") },
                    onAddItem = {
                        addItemZoneOverride = zoneId
                        showAddItem = true
                    },
                    onEditItem = { item -> itemBeingEdited = item }
                )
            }
            composable("search") {
                SearchScreen(viewModel = viewModel, onEditItem = { item -> itemBeingEdited = item })
            }
            composable("manageZones") {
                ManageZonesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }

    if (showAddItem || itemBeingEdited != null) {
        val state by viewModel.state.collectAsState()
        AddItemSheet(
            viewModel = viewModel,
            initialZoneId = addItemZoneOverride ?: state.selectedZoneId,
            itemToEdit = itemBeingEdited,
            onDismiss = {
                showAddItem = false
                addItemZoneOverride = null
                itemBeingEdited = null
            }
        )
    }

    if (showEditName) {
        EditNameDialog(userPrefs = userPrefs, currentName = name, onDismiss = { showEditName = false })
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the full unit test suite as a final regression check**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests passing

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/MainActivity.kt
git commit -m "feat: wire bottom-nav Scaffold and the new Nocturne routes into MainActivity"
```

---

## Manual verification (after all tasks)

Since this repo has no Compose UI test harness, do a manual pass in an emulator/device after Task 16:
1. Launch the app — confirm dark Nocturne background/colors everywhere (no leftover light Mint/Lime surfaces).
2. Bottom nav: tap Lista/Almacén/Buscar — confirm each tab preserves its own scroll/back-stack state (multiple-back-stacks behavior) and the dot indicator highlights correctly.
3. Almacén → tap a zone card → Zone Detail opens with bottom nav still visible and Almacén still highlighted; back chevron returns to the dashboard.
4. Almacén → gear icon, and Zone Detail → pencil icon — both open `ManageZonesScreen` with no bottom bar, and back returns to where you came from.
5. Lista → toggle Agrupado/Todo — confirm flat mode shows the zone tag chip and, for done items, the "Actualizado en {zona}" note.
6. Lista → zone filter chips — confirm they filter both Agrupado and Todo the same way the old tabs did.
7. Lista → overflow menu — sort modes, "Vaciar comprados", and "Cambiar nombre" all still work.
8. Buscar → type a query — confirm live filtering and the results count string.
9. FAB on Lista and Zone Detail open `AddItemSheet` with the right `initialZoneId` (no zone preselected from Lista unless a zone chip is active; the opened zone preselected from Zone Detail).
