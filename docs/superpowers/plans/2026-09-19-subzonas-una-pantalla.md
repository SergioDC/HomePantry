# Subzonas en una sola pantalla Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ver de un vistazo una zona y todas sus subzonas en una sola pantalla, y poder añadir un producto directamente a una subzona.

**Architecture:** Una función pura (`zoneDetailSections`) produce las secciones de una zona raíz (la propia y cada subzona con sus productos). `ZoneDetailScreen` las pinta en una única `LazyColumn` con un "+" por sección, y `AddItemSheet` pasa a elegir zona raíz y, en una segunda fila, subzona. `Item.zone` sigue siendo un único id: no cambia el modelo de datos.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-19-historial-supermercado-subzonas-design.md` (Parte 2). La Parte 1 (historial) tiene su propio plan: `2026-09-19-historial-supermercado-precio-unitario.md`; ambos planes son independientes.

## Global Constraints

- Un solo nivel de subzonas: una subzona no tiene subzonas, y su pantalla se ve como hoy (sin secciones ni "Nueva subzona").
- La pantalla de zona sigue mostrando solo lo que se tiene (`done = true`); lo pendiente vive en la Lista de la compra.
- Una zona sin subzonas se ve como hoy: una sola sección, sin encabezado añadido.
- Las subzonas vacías también se muestran, para poder añadir en ellas.
- El valor guardado en `Item.zone` sigue siendo un único id de zona; no se tocan `firestore.rules` ni el modelo `Item`/`Zone`.
- Tocar el encabezado de una subzona abre su pantalla actual (`zoneDetail/{subzoneId}`), donde se sigue pudiendo renombrar, cambiar el color o eliminar.
- Texto de interfaz en español, en `app/src/main/res/values/strings.xml`.
- Comandos Gradle: `./gradlew.bat ...` desde la raíz del repo (`c:\Programacion\Proyectos\HomePantry`).
- Cada commit termina con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

## File Structure

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `ui/ItemListLogic.kt` | `ZoneDetailSection` y `zoneDetailSections` (lógica pura) | 1 |
| `ui/screens/ZoneDetailScreen.kt` | Pantalla única con secciones, "+" por sección, "Nueva subzona" | 2 |
| `MainActivity.kt` | `onAddItem` recibe la zona destino | 2 |
| `ui/screens/AddItemSheet.kt` | Fila de zonas raíz + fila "Subzona (opcional)" | 3 |
| `res/values/strings.xml` | Textos nuevos | 2, 3 |

Rutas de código bajo `app/src/main/kotlin/com/homepantry/app/`; tests bajo `app/src/test/kotlin/com/homepantry/app/`.

---

### Task 1: Lógica de secciones de una zona

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/ItemListLogic.kt` (añadir al final)
- Test: `app/src/test/kotlin/com/homepantry/app/ui/ItemListLogicTest.kt`

**Interfaces:**
- Produces (lo usa la tarea 2):
  - `data class ZoneDetailSection(val zone: Zone, val items: List<Item>, val isSubzone: Boolean)`
  - `fun zoneDetailSections(zoneId: String, items: List<Item>, zones: List<Zone>): List<ZoneDetailSection>` → primero la zona pedida (`isSubzone = false`); si es raíz, después cada subzona en su orden (`isSubzone = true`), incluidas las vacías; solo productos `done`. Zona desconocida → lista vacía. Si `zoneId` es una subzona → solo su sección.

- [ ] **Step 1: Escribir los tests que fallan**

Añadir dentro de `ItemListLogicTest`, antes de la llave de cierre final:

```kotlin
    @Test fun `zoneDetailSections lists the zone itself then each subzone, with only owned items`() {
        val cajon = Zone(id = "z3", name = "Cajón", order = 2, parentZoneId = "z1")
        val estante = Zone(id = "z4", name = "Estante", order = 3, parentZoneId = "z1")
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = true),
            Item(id = "2", name = "Pan", zone = "z1", done = false),
            Item(id = "3", name = "Queso", zone = "z3", done = true),
            Item(id = "4", name = "Arroz", zone = "z2", done = true)
        )
        val sections = zoneDetailSections("z1", items, listOf(nevera, despensa, cajon, estante))

        assertEquals(listOf("z1", "z3", "z4"), sections.map { it.zone.id })
        assertEquals(listOf(false, true, true), sections.map { it.isSubzone })
        assertEquals(listOf("1"), sections[0].items.map { it.id })
        assertEquals(listOf("3"), sections[1].items.map { it.id })
        // Una subzona vacía se incluye igualmente, para poder añadir en ella.
        assertEquals(emptyList<String>(), sections[2].items.map { it.id })
    }

    @Test fun `zoneDetailSections orders subzones by their order field`() {
        val segunda = Zone(id = "z4", name = "Segunda", order = 5, parentZoneId = "z1")
        val primera = Zone(id = "z3", name = "Primera", order = 2, parentZoneId = "z1")
        val sections = zoneDetailSections("z1", emptyList(), listOf(nevera, segunda, primera))
        assertEquals(listOf("z1", "z3", "z4"), sections.map { it.zone.id })
    }

    @Test fun `zoneDetailSections of a subzone is just that subzone`() {
        val cajon = Zone(id = "z3", name = "Cajón", order = 2, parentZoneId = "z1")
        val items = listOf(Item(id = "3", name = "Queso", zone = "z3", done = true))
        val sections = zoneDetailSections("z3", items, listOf(nevera, cajon))

        assertEquals(listOf("z3"), sections.map { it.zone.id })
        assertEquals(listOf(false), sections.map { it.isSubzone })
        assertEquals(listOf("3"), sections.single().items.map { it.id })
    }

    @Test fun `zoneDetailSections of a zone without subzones is a single section`() {
        val items = listOf(Item(id = "4", name = "Arroz", zone = "z2", done = true))
        val sections = zoneDetailSections("z2", items, listOf(nevera, despensa))
        assertEquals(listOf("z2"), sections.map { it.zone.id })
        assertEquals(listOf("4"), sections.single().items.map { it.id })
    }

    @Test fun `zoneDetailSections of an unknown zone is empty`() {
        assertEquals(emptyList<ZoneDetailSection>(), zoneDetailSections("nope", emptyList(), listOf(nevera)))
    }
```

- [ ] **Step 2: Ejecutar y ver que fallan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.ui.ItemListLogicTest"`
Expected: FAIL de compilación, `Unresolved reference: zoneDetailSections`.

- [ ] **Step 3: Implementar**

Añadir al final de `ItemListLogic.kt`:

```kotlin
/** Sección de la pantalla de una zona raíz: la propia zona o una de sus subzonas, con lo que tienes en ella. */
data class ZoneDetailSection(val zone: Zone, val items: List<Item>, val isSubzone: Boolean)

/**
 * Secciones de la pantalla de detalle de una zona: primero la propia zona y,
 * si es raíz, después cada una de sus subzonas (también las vacías, para poder
 * añadir en ellas). Solo cuenta lo que tienes (done=true); lo pendiente vive en
 * la Lista de la compra. Una subzona (sin subzonas propias) devuelve solo su
 * sección; una zona desconocida, ninguna.
 */
fun zoneDetailSections(zoneId: String, items: List<Item>, zones: List<Zone>): List<ZoneDetailSection> {
    val zone = zones.firstOrNull { it.id == zoneId } ?: return emptyList()
    val owned = items.filter { it.done }
    fun itemsOf(id: String): List<Item> =
        groupAndSort(owned, zones, filterZoneId = id).firstOrNull()?.items ?: emptyList()

    val own = ZoneDetailSection(zone, itemsOf(zone.id), isSubzone = false)
    if (zone.parentZoneId != null) return listOf(own)
    return listOf(own) + subzonesOf(zone.id, zones).map { ZoneDetailSection(it, itemsOf(it.id), isSubzone = true) }
}
```

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.ui.ItemListLogicTest"`
Expected: PASS (los tests originales y los 5 nuevos).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/ItemListLogic.kt app/src/test/kotlin/com/homepantry/app/ui/ItemListLogicTest.kt
git commit -m "feat: add zone detail sections logic (zone plus its subzones)"
```

---

### Task 2: Pantalla de zona con subzonas en una sola lista

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/ZoneDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt:213-226`
- Modify: `app/src/main/res/values/strings.xml` (bloque `ZoneDetailScreen`)

**Interfaces:**
- Consumes (tarea 1): `zoneDetailSections`, `ZoneDetailSection`.
- Produces (lo usa la tarea 3, vía `MainActivity`): `ZoneDetailScreen(..., onAddItem: (String) -> Unit, ...)`, que recibe el id de la zona o subzona destino. `MainActivity` lo guarda en `addItemZoneOverride`, que ya llega a `AddItemSheet` como `initialZoneId`.

- [ ] **Step 1: Strings nuevos**

En `strings.xml`, bloque `<!-- ZoneDetailScreen -->`, tras `zone_detail_new_subzone_title`, añadir:

```xml
    <string name="zone_detail_section_empty">Nada por aquí todavía</string>
    <string name="zone_detail_add_to_section_cd">Añadir a %1$s</string>
```

- [ ] **Step 2: `MainActivity` pasa la zona destino**

En `MainActivity.kt`, sustituir el bloque `onAddItem` de `composable("zoneDetail/{zoneId}")` (líneas 219-222):

```kotlin
                    onAddItem = {
                        addItemZoneOverride = zoneId
                        showAddItem = true
                    },
```

por:

```kotlin
                    onAddItem = { targetZoneId ->
                        addItemZoneOverride = targetZoneId
                        showAddItem = true
                    },
```

- [ ] **Step 3: Cambios en `ZoneDetailScreen.kt`**

1. **Firma** (línea 74): `onAddItem: () -> Unit,` → `onAddItem: (String) -> Unit,`.

2. **Imports.** Añadir:

```kotlin
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedButton
import com.homepantry.app.ui.zoneDetailSections
```

   Eliminar los que dejan de usarse: `androidx.compose.foundation.horizontalScroll`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.material3.AssistChip`, `com.homepantry.app.ui.components.ZoneChip` y `com.homepantry.app.ui.groupAndSort`. El resto (`lazy.items`, `Arrangement`, `Row`, `Box`...) se sigue usando.

3. **Estado derivado.** Sustituir las líneas 79-85:

```kotlin
    val zone = state.zones.firstOrNull { it.id == zoneId }
    // Solo lo que tienes (done=true): lo pendiente de esta zona vive en Lista de la
    // compra, no aquí -- si no, marcar "agotado" o "añadir a la lista" no lo quitaba
    // realmente de la vista de la zona (seguía apareciendo, solo sin tachar).
    val zoneItems = (groupAndSort(state.items, state.zones, filterZoneId = zoneId).firstOrNull()?.items ?: emptyList())
        .filter { it.done }
    val subzones = subzonesOf(zoneId, state.zones)
```

por:

```kotlin
    val zone = state.zones.firstOrNull { it.id == zoneId }
    // Solo lo que tienes (done=true): lo pendiente de esta zona vive en Lista de la
    // compra, no aquí -- si no, marcar "agotado" o "añadir a la lista" no lo quitaba
    // realmente de la vista de la zona (seguía apareciendo, solo sin tachar).
    // Una zona raíz muestra además sus subzonas, cada una como sección propia.
    val sections = zoneDetailSections(zoneId, state.items, state.zones)
    val subzones = subzonesOf(zoneId, state.zones)
```

4. **FAB** (línea 135): `NocturneFab(onClick = onAddItem, ...)` → `NocturneFab(onClick = { onAddItem(zoneId) }, contentDescription = stringResource(R.string.main_add_item_cd))`.

5. **Cuerpo.** Sustituir todo el `Column(modifier = Modifier.fillMaxSize().padding(padding)) { ... }` del `Scaffold` (líneas 138-187: la fila de chips, el mensaje vacío y el `LazyColumn`) por:

```kotlin
        // Con subzonas hay un encabezado por sección; sin ellas la lista es la de siempre.
        val showHeaders = sections.size > 1
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // bottom = 96.dp: deja hueco para que el último producto no quede tapado
            // detrás del FAB flotante de "añadir".
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp)
        ) {
            sections.forEach { section ->
                if (showHeaders) {
                    item(key = "header/${section.zone.id}") {
                        ZoneSectionHeader(
                            title = section.zone.name,
                            count = section.items.size,
                            colorHex = if (section.isSubzone) {
                                resolvedZoneColor(section.zone, subzones.indexOfFirst { it.id == section.zone.id }.coerceAtLeast(0))
                            } else {
                                currentColor
                            },
                            onOpen = if (section.isSubzone) ({ onOpenZone(section.zone.id) }) else null,
                            onAdd = { onAddItem(section.zone.id) }
                        )
                    }
                }
                if (section.items.isEmpty()) {
                    item(key = "empty/${section.zone.id}") {
                        Text(
                            text = stringResource(
                                if (showHeaders) R.string.zone_detail_section_empty else R.string.main_empty_list
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }
                items(section.items, key = { it.id }) { product ->
                    ItemPillRow(
                        item = product,
                        dimWhenDone = false,
                        onToggleDone = { viewModel.toggleDone(product) },
                        onDelete = { itemPendingDelete = product },
                        onEdit = { onEditItem(product) }
                    )
                }
            }
            if (zone != null && zone.parentZoneId == null) {
                item(key = "new-subzone") {
                    OutlinedButton(
                        onClick = { showAddSubzoneDialog = true },
                        modifier = Modifier.padding(top = 16.dp)
                    ) { Text(stringResource(R.string.zone_detail_new_subzone_chip)) }
                }
            }
        }
```

6. **Encabezado de sección.** Añadir al final del archivo (fuera de `ZoneDetailScreen`):

```kotlin
/**
 * Encabezado de una sección (zona o subzona) con su color, contador y un "+"
 * que añade un producto directamente a ella. Si `onOpen` no es null, tocar el
 * encabezado abre la pantalla propia de esa subzona.
 */
@Composable
private fun ZoneSectionHeader(
    title: String,
    count: Int,
    colorHex: String?,
    onOpen: (() -> Unit)?,
    onAdd: () -> Unit
) {
    val dotColor = colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dotColor != null) {
            Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = if (dotColor != null) 8.dp else 0.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.zone_detail_add_to_section_cd, title))
        }
    }
}
```

- [ ] **Step 4: Compilar y ejecutar la suite**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Si el compilador avisa de imports sin usar, elimínalos.

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/ZoneDetailScreen.kt app/src/main/kotlin/com/homepantry/app/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: show a zone and its subzones on one screen with per-section add"
```

---

### Task 3: Selector de subzona en el formulario de añadir

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/AddItemSheet.kt`
- Modify: `app/src/main/res/values/strings.xml` (bloque `AddItemSheet`)

**Interfaces:**
- Consumes: `initialZoneId` (puede ser ya un id de subzona, viene del "+" de la tarea 2) y `Zone.parentZoneId`, `subzonesOf` (ya existentes en `data/Zone.kt`).
- Produces: el mismo `selectedZoneId` único que ya usaba el guardado; `Item.zone` no cambia de forma.

- [ ] **Step 1: String nuevo**

En `strings.xml`, bloque `<!-- AddItemSheet -->`, tras `add_item_new_zone`, añadir:

```xml
    <string name="add_item_subzone">Subzona (opcional)</string>
```

- [ ] **Step 2: Estado derivado (raíces y subzonas)**

En `AddItemSheet.kt`, imports: añadir `import com.homepantry.app.data.subzonesOf` y eliminar `import com.homepantry.app.data.zoneDisplayLabel` (deja de usarse).

Sustituir la línea 90:

```kotlin
    val zones = state.zones.sortedBy { it.order }
```

por:

```kotlin
    val zones = state.zones.sortedBy { it.order }
    val rootZones = zones.filter { it.parentZoneId == null }
```

Justo después de la declaración de `selectedZoneId` (líneas 103-105), añadir:

```kotlin
    // El destino es siempre un único id de zona; si es una subzona, la fila de raíces
    // marca su padre y la fila "Subzona" la marca a ella.
    val selectedZone = zones.firstOrNull { it.id == selectedZoneId }
    val selectedRootId = selectedZone?.parentZoneId ?: selectedZone?.id
    val subzoneOptions = selectedRootId?.let { subzonesOf(it, state.zones) } ?: emptyList()
```

- [ ] **Step 3: Zona por defecto = primera raíz**

En el `LaunchedEffect(zones)` (líneas 160-164), sustituir `zones.firstOrNull()?.let { selectedZoneId = it.id }` por `rootZones.firstOrNull()?.let { selectedZoneId = it.id }`.

- [ ] **Step 4: Filas de zona raíz y de subzona**

Sustituir el `item { Row(... horizontalScroll ...) { zones.forEachIndexed ... } }` de los chips de zona (líneas 297-311) por:

```kotlin
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rootZones.forEach { zone ->
                        ZoneChip(
                            label = zone.name,
                            colorHex = resolvedZoneColor(zone, zones.indexOfFirst { it.id == zone.id }),
                            selected = zone.id == selectedRootId,
                            onClick = { selectedZoneId = zone.id }
                        )
                    }
                }
            }
            if (subzoneOptions.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.add_item_subzone),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subzoneOptions.forEach { subzone ->
                            ZoneChip(
                                label = subzone.name,
                                colorHex = resolvedZoneColor(subzone, zones.indexOfFirst { it.id == subzone.id }),
                                selected = subzone.id == selectedZoneId,
                                // Tocar la subzona ya elegida la deselecciona y vuelve a la raíz.
                                onClick = {
                                    selectedZoneId = if (subzone.id == selectedZoneId) selectedRootId.orEmpty() else subzone.id
                                }
                            )
                        }
                    }
                }
            }
```

(`selectedRootId` no es nulo aquí porque `subzoneOptions` no está vacío; `.orEmpty()` solo evita un `!!`.)

- [ ] **Step 5: Compilar, ejecutar la suite y generar el APK**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/AddItemSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat: pick a subzone from the add item form"
```

- [ ] **Step 7: Comprobación manual en el dispositivo (la hace el usuario)**

Lista para comprobar en el móvil:
1. Abrir una zona con subzonas (p. ej. Nevera con "Cajón"): una sola lista con la sección de la zona y una por subzona, cada una con su contador y su "+"; una subzona vacía aparece con "Nada por aquí todavía".
2. Tocar el "+" de una subzona: el formulario abre con la raíz marcada y esa subzona marcada; guardar deja el producto dentro de esa subzona en la lista.
3. En el formulario, elegir otra zona raíz con subzonas: aparece la fila "Subzona (opcional)"; volver a tocar la subzona marcada la deselecciona.
4. Tocar el encabezado de una subzona abre su pantalla (renombrar, color, eliminar siguen funcionando) y allí no hay secciones ni "Nueva subzona".
5. Una zona sin subzonas se ve igual que antes, con el botón "+ Subzona" al final de la lista.
6. Crear una subzona con "+ Subzona": aparece como sección nueva, vacía.
