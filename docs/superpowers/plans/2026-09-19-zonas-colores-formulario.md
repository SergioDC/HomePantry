# Colores de zona fijos y formulario sin foto/escáner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que cada zona y subzona tenga un color fijo guardado, igual en todas las pantallas (con borde de ese color en las tarjetas de Almacén), y quitar los botones de foto y escáner del formulario de añadir producto.

**Architecture:** Dos funciones puras en `data/Zone.kt` (`nextZoneColor`, `zoneColorBackfill`) deciden los colores. `ZonesRepository` guarda el color al crear una zona y rellena de una vez las zonas antiguas sin color; `AppViewModel` lo dispara al crear zonas y al cargarlas. `ZoneCard` pinta el borde y `AddItemSheet` pierde la fila de botones (el resto de su código se conserva).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Firestore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-19-zonas-formulario-tickets-design.md` (Partes A y B). La Parte C (tickets) tiene su propio plan: `2026-09-19-tickets-borrado.md`; ambos planes son independientes.

## Global Constraints

- Toda zona nueva guarda su `color` al crearse (raíz, subzona y zonas por defecto de una casa nueva). Cada subzona recibe **su propio color** de `ZONE_COLORS`; no hereda el de su zona.
- `nextZoneColor(zones)`: el color de `ZONE_COLORS` menos usado entre las zonas (hex comparado sin distinguir mayúsculas); en empate, el primero de la paleta.
- Relleno de zonas existentes sin color (nulo o en blanco): raíces con `zoneColorFor(posición entre las raíces ordenadas por order)`; subzonas con `nextZoneColor`, en orden estable (`order` de la zona padre y luego el suyo; huérfanas al final), contando los colores asignados en la misma pasada. Es idempotente y se escribe en un solo `batch`.
- `resolvedZoneColor(zone, index)` se mantiene tal cual como respaldo.
- El borde de `ZoneCard` es de 2 dp y del mismo color que el círculo de la letra. La tarjeta «+ Nueva zona» no cambia.
- Del formulario solo se quita la fila con los botones «Foto» y «Escanear». Selector de foto, diálogo del escáner, OpenFoodFacts, aviso de duplicado por código de barras, vista previa de la foto actual y los textos `add_item_photo`/`add_item_scan` se conservan a propósito. Un comentario en el sitio del botón lo explica.
- Las reglas de Firestore y los modelos `Item`/`Zone` (campos) no cambian.
- Texto de interfaz en español. Comandos Gradle: `./gradlew.bat ...` desde la raíz del repo.
- Stage de rutas explícitas (`git add <rutas>`); nunca `git add -A` ni `git commit -a`. Cada commit termina con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

## File Structure

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `data/Zone.kt` | `nextZoneColor`, `zoneColorBackfill` (lógica pura) | 1 |
| `data/ZonesRepository.kt` | Guardar color al crear, `updateZoneColors` | 2 |
| `ui/AppViewModel.kt` | Color al crear zona; relleno al cargar zonas | 2 |
| `ui/components/ZoneCard.kt` | Borde del color de la zona | 3 |
| `ui/screens/AddItemSheet.kt` | Quitar la fila de botones Foto/Escanear | 3 |

Rutas de código bajo `app/src/main/kotlin/com/homepantry/app/`; tests bajo `app/src/test/kotlin/com/homepantry/app/`.

---

### Task 1: Lógica pura de colores de zona

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/Zone.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/ZoneTest.kt`

**Interfaces:**
- Produces (las usa la tarea 2):
  - `fun nextZoneColor(zones: List<Zone>): String`
  - `fun zoneColorBackfill(zones: List<Zone>): Map<String, String>` (id de zona a hex; solo las zonas que lo necesitan; vacío si todas tienen color)

- [ ] **Step 1: Escribir los tests que fallan**

Añadir dentro de `ZoneTest`, antes de la llave de cierre final:

```kotlin
    @Test fun `nextZoneColor picks the first palette color when no zone has one`() {
        assertEquals(ZONE_COLORS[0], nextZoneColor(emptyList()))
        assertEquals(ZONE_COLORS[0], nextZoneColor(listOf(Zone(id = "z1", name = "Nevera"))))
    }

    @Test fun `nextZoneColor skips colors already in use`() {
        val zones = listOf(
            Zone(id = "z1", name = "A", color = ZONE_COLORS[0]),
            Zone(id = "z2", name = "B", color = ZONE_COLORS[1])
        )
        assertEquals(ZONE_COLORS[2], nextZoneColor(zones))
    }

    @Test fun `nextZoneColor ignores the case of the hex`() {
        val zones = listOf(Zone(id = "z1", name = "A", color = ZONE_COLORS[0].lowercase()))
        assertEquals(ZONE_COLORS[1], nextZoneColor(zones))
    }

    @Test fun `nextZoneColor picks the least used color once the palette is exhausted`() {
        val zones = ZONE_COLORS.mapIndexed { index, hex -> Zone(id = "z$index", name = "Z$index", color = hex) } +
            Zone(id = "extra", name = "Extra", color = ZONE_COLORS[0])
        // El color 0 se usa dos veces y los demás una: el primero menos usado es el 1.
        assertEquals(ZONE_COLORS[1], nextZoneColor(zones))
    }

    @Test fun `zoneColorBackfill colors uncolored roots by their position among roots`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0),
            Zone(id = "z2", name = "Despensa", order = 1),
            Zone(id = "z3", name = "Otros", order = 2)
        )
        assertEquals(
            mapOf("z1" to zoneColorFor(0), "z2" to zoneColorFor(1), "z3" to zoneColorFor(2)),
            zoneColorBackfill(zones)
        )
    }

    @Test fun `zoneColorBackfill leaves colored zones alone but still counts their position`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0, color = "#123456"),
            Zone(id = "z2", name = "Despensa", order = 1)
        )
        assertEquals(mapOf("z2" to zoneColorFor(1)), zoneColorBackfill(zones))
    }

    @Test fun `zoneColorBackfill is empty when every zone already has a color`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", color = "#123456"),
            Zone(id = "z2", name = "Cajón", parentZoneId = "z1", color = "#654321")
        )
        assertEquals(emptyMap<String, String>(), zoneColorBackfill(zones))
    }

    @Test fun `zoneColorBackfill gives each uncolored subzone its own color in a stable order`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0),
            Zone(id = "z2", name = "Despensa", order = 1),
            Zone(id = "s2", name = "Estante", order = 6, parentZoneId = "z1"),
            Zone(id = "s1", name = "Cajón", order = 5, parentZoneId = "z1"),
            Zone(id = "s3", name = "Balda", order = 4, parentZoneId = "z2")
        )
        val result = zoneColorBackfill(zones)

        assertEquals(zoneColorFor(0), result["z1"])
        assertEquals(zoneColorFor(1), result["z2"])
        // Primero las subzonas de Nevera (por order) y luego las de Despensa; con los colores 0 y 1
        // ya en uso, la paleta sigue por el 2, el 3 y el 4.
        assertEquals(ZONE_COLORS[2], result["s1"])
        assertEquals(ZONE_COLORS[3], result["s2"])
        assertEquals(ZONE_COLORS[4], result["s3"])
    }

    @Test fun `zoneColorBackfill puts a subzone whose parent is missing last`() {
        val zones = listOf(
            Zone(id = "z1", name = "Nevera", order = 0),
            Zone(id = "orphan", name = "Huérfana", order = 0, parentZoneId = "missing"),
            Zone(id = "s1", name = "Cajón", order = 9, parentZoneId = "z1")
        )
        val result = zoneColorBackfill(zones)

        assertEquals(ZONE_COLORS[1], result["s1"])
        assertEquals(ZONE_COLORS[2], result["orphan"])
    }
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.ZoneTest"`
Expected: FAIL de compilación, `Unresolved reference: nextZoneColor` (y `zoneColorBackfill`).

- [ ] **Step 3: Implementar**

En `Zone.kt`, justo debajo de `resolvedZoneColor` (línea 33), añadir:

```kotlin

/**
 * Color de la paleta para una zona nueva: el menos usado entre las zonas existentes
 * (el hex se compara sin distinguir mayúsculas); en un empate, el primero de la paleta.
 */
fun nextZoneColor(zones: List<Zone>): String {
    val usage = zones.mapNotNull { it.color?.trim()?.uppercase()?.takeIf { hex -> hex.isNotEmpty() } }
        .groupingBy { it }
        .eachCount()
    return ZONE_COLORS.minByOrNull { usage[it.uppercase()] ?: 0 } ?: ZONE_COLORS.first()
}

/**
 * Colores a fijar en zonas que aún no tienen ninguno (zonas anteriores a los colores fijos):
 * las raíces reciben el color que Almacén ya mostraba (por su posición entre las raíces) y
 * cada subzona recibe uno propio de la paleta, en un orden estable (por el `order` de su
 * zona padre y luego el suyo; las huérfanas al final). Devuelve id de zona a hex, solo para
 * las zonas que lo necesitan.
 */
fun zoneColorBackfill(zones: List<Zone>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    val roots = zones.filter { it.parentZoneId == null }.sortedBy { it.order }
    roots.forEachIndexed { index, zone ->
        if (zone.color.isNullOrBlank()) result[zone.id] = zoneColorFor(index)
    }

    val rootPosition = roots.withIndex().associate { (index, zone) -> zone.id to index }
    val working = zones.map { zone -> zone.copy(color = result[zone.id] ?: zone.color) }.toMutableList()
    zones.filter { it.parentZoneId != null && it.color.isNullOrBlank() }
        .sortedWith(
            compareBy<Zone>(
                { zone -> zone.parentZoneId?.let { parentId -> rootPosition[parentId] } ?: Int.MAX_VALUE },
                { zone -> zone.order }
            )
        )
        .forEach { subzone ->
            val color = nextZoneColor(working)
            result[subzone.id] = color
            val position = working.indexOfFirst { it.id == subzone.id }
            working[position] = working[position].copy(color = color)
        }
    return result
}
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.ZoneTest"`
Expected: PASS (los tests originales y los 9 nuevos).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/Zone.kt app/src/test/kotlin/com/homepantry/app/data/ZoneTest.kt
git commit -m "feat: add next zone color and color backfill logic"
```

---

### Task 2: Guardar el color al crear zonas y rellenar las antiguas

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/ZonesRepository.kt:33-35,51-55`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt` (imports, observador de zonas en `init`, `createZone`)

**Interfaces:**
- Consumes (tarea 1): `nextZoneColor(zones)`, `zoneColorBackfill(zones)`.
- Produces: `ZonesRepository.addZone(name, order, parentZoneId = null, color: String? = null)`, `ZonesRepository.updateZoneColors(colors: Map<String, String>)`.

- [ ] **Step 1: `ZonesRepository`**

Sustituir `addZone` (líneas 33-35):

```kotlin
    suspend fun addZone(name: String, order: Int, parentZoneId: String? = null) {
        collection().add(Zone(name = name, order = order, parentZoneId = parentZoneId)).await()
    }
```

por:

```kotlin
    suspend fun addZone(name: String, order: Int, parentZoneId: String? = null, color: String? = null) {
        collection().add(Zone(name = name, order = order, color = color, parentZoneId = parentZoneId)).await()
    }
```

Tras `updateZoneColor` (líneas 41-43) añadir:

```kotlin

    /** Fija varios colores de golpe (relleno de zonas antiguas que aún no tenían ninguno). */
    suspend fun updateZoneColors(colors: Map<String, String>) {
        if (colors.isEmpty()) return
        val batch = firestore.batch()
        colors.forEach { (zoneId, colorHex) -> batch.update(collection().document(zoneId), "color", colorHex) }
        batch.commit().await()
    }
```

En `seedDefaultZones` (líneas 51-55) sustituir `addZone(name, index)` por:

```kotlin
            addZone(name, index, color = zoneColorFor(index))
```

- [ ] **Step 2: `AppViewModel`**

Añadir a los imports de `data` (orden alfabético, junto a `normalizeProductName`): 

```kotlin
import com.homepantry.app.data.nextZoneColor
import com.homepantry.app.data.zoneColorBackfill
```

En el observador de zonas del bloque `init` (líneas 97-105), sustituir:

```kotlin
                zonesRepository.observeZones().collect { zones ->
                    _state.value = _state.value.copy(zones = zones)
                }
```

por:

```kotlin
                zonesRepository.observeZones().collect { zones ->
                    _state.value = _state.value.copy(zones = zones)
                    // Zonas anteriores a los colores fijos: se les fija uno una sola vez para que todas
                    // las pantallas coincidan. Es idempotente: con los colores ya guardados el mapa queda vacío.
                    val backfill = zoneColorBackfill(zones)
                    if (backfill.isNotEmpty()) {
                        runCatching { zonesRepository.updateZoneColors(backfill) }
                            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
                    }
                }
```

Sustituir `createZone` (líneas 250-254):

```kotlin
    fun createZone(name: String, parentZoneId: String? = null) = viewModelScope.launch {
        val nextOrder = (_state.value.zones.maxOfOrNull { it.order } ?: -1) + 1
        runCatching { zonesRepository.addZone(name, nextOrder, parentZoneId) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
```

por:

```kotlin
    fun createZone(name: String, parentZoneId: String? = null) = viewModelScope.launch {
        val zones = _state.value.zones
        val nextOrder = (zones.maxOfOrNull { it.order } ?: -1) + 1
        runCatching { zonesRepository.addZone(name, nextOrder, parentZoneId, nextZoneColor(zones)) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
```

- [ ] **Step 3: Compilar y ejecutar la suite**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/ZonesRepository.kt app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt
git commit -m "feat: save a fixed color when creating zones and backfill old ones"
```

---

### Task 3: Borde de color en las tarjetas y formulario sin botones de foto/escáner

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/components/ZoneCard.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/AddItemSheet.kt:418-429`

**Interfaces:**
- Consumes: nada de las tareas anteriores (los colores ya llegan por `colorHex`).
- Produces: nada que use otra tarea.

- [ ] **Step 1: Borde en `ZoneCard`**

En `ZoneCard.kt` añadir el import, junto a `androidx.compose.foundation.background`:

```kotlin
import androidx.compose.foundation.BorderStroke
```

Sustituir el comentario de la función (línea 20):

```kotlin
/** Tarjeta de zona del dashboard "Almacén": icono de letra + color auto-derivados. */
```

por:

```kotlin
/** Tarjeta de zona del dashboard "Almacén": icono de letra y borde con el color de la zona. */
```

Sustituir la línea del `Card` (línea 33):

```kotlin
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
```

por:

```kotlin
    Card(
        onClick = onClick,
        border = BorderStroke(2.dp, dotColor),
        modifier = modifier.fillMaxWidth().aspectRatio(1f)
    ) {
```

- [ ] **Step 2: Quitar los botones de `AddItemSheet`**

En `AddItemSheet.kt`, dentro del `item { ... }` que empieza en la línea 407 (el de la vista previa de la foto actual), eliminar la fila de botones y dejar en su lugar un comentario. Sustituir:

```kotlin
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.add_item_photo)) }
                    OutlinedButton(
                        onClick = { showScanner = true },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) { Text(stringResource(R.string.add_item_scan)) }
                }
```

por:

```kotlin
                // Los botones de foto (`photoPicker`) y de escáner (`showScanner`) se quitaron de la
                // interfaz; su código se conserva a propósito porque la consulta a OpenFoodFacts
                // puede volver a usarse más adelante.
```

No borres nada más: `photoPicker`, el diálogo del escáner y el aviso de duplicado por código siguen en el archivo. El compilador avisará de que `photoPicker` no se usa; es esperado. `OutlinedButton`, `Row` y el resto de imports se siguen usando en otras partes del archivo.

- [ ] **Step 3: Compilar, ejecutar la suite y generar el APK**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL (solo avisos; el único nuevo esperado es `photoPicker` sin usar).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/components/ZoneCard.kt app/src/main/kotlin/com/homepantry/app/ui/screens/AddItemSheet.kt
git commit -m "feat: color border on zone cards and remove photo/scan buttons from the add form"
```

- [ ] **Step 5: Comprobación manual en el dispositivo (la hace el usuario)**

1. Abrir la app con una casa que ya tenga zonas sin color: en Almacén, cada tarjeta tiene borde del color de su zona y las zonas conservan el color que mostraban.
2. Crear una zona nueva y una subzona nueva: cada una sale con un color distinto y el mismo color en Almacén, en la pantalla de la zona y en el formulario de añadir producto.
3. En una zona con subzonas, comprobar que las subzonas tienen colores propios, iguales en la pantalla de la zona y en el formulario.
4. En el formulario de añadir producto ya no hay botones «Foto» ni «Escanear»; al editar un producto con foto, sigue viéndose su foto actual.
