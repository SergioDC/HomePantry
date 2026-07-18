# Cierre de huecos del SPEC v1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the 13 gap-closing decisions from `docs/superpowers/specs/2026-07-18-cierre-huecos-spec-v1-design.md`: full item editing, offline persistence + indicator, barcode-duplicate handling, a protected "Otros" zone, editable user name, lighter photo compression, a confirmation dialog for "vaciar comprados", quantity validation, a sort selector, and a search bar — on top of the already-working "Lista de la Casa" Android app.

**Architecture:** No new subsystems. Pure/testable logic (quantity validation, sort modes, search filter, duplicate detection, protected-zone check, image-resize math) is added as plain Kotlin functions with JUnit4 tests, mirroring the existing `ItemListLogic.kt`/`HouseholdCode.kt` pattern. UI changes extend the four existing Compose screens (`AddItemSheet`, `MainListScreen`, `ManageZonesScreen`, plus a new `EditNameDialog`) and wire through the existing `AppViewModel`/`UiState`. `StorageRepository` gains a `Context` dependency to compress photos before upload. A new `ConnectivityObserver` feeds an offline indicator. New composable parameters get safe no-op defaults so every task compiles and `./gradlew assembleDebug` succeeds independently; `MainActivity.kt` is fully rewired only in the last task.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Firebase (Firestore/Storage — offline persistence is already on by default for the Android SDK, no new config needed), Coil, `android.net.ConnectivityManager`, `android.graphics.Bitmap`, JUnit4 (unit tests only — no instrumented tests, no emulator in this environment, same as the rest of the project).

## Global Constraints

- Package `com.listacasa.app`; `minSdk 26`, `compileSdk`/`targetSdk 34` (`app/build.gradle.kts`) — unchanged. No new Gradle dependencies are needed; every new file uses libraries already declared (Compose, Coroutines/Flow, Coil, Firebase, or plain Android framework APIs like `ConnectivityManager`/`BitmapFactory`).
- Color palette is fixed (SPEC.md §6): coral (`MaterialTheme.colorScheme.error`/`errorContainer`, mapped in `ui/theme/Theme.kt` from `CoralError`/`CoralSoft`) is reserved for alerts and delete — the new offline banner and confirmation dialogs use `errorContainer`/`onErrorContainer`, which is consistent with that rule. Do not introduce any new hex color.
- No traditional login; `UserPrefs` (DataStore) remains the only local persistence for household code and name (SPEC.md §3). "Editar nombre" reuses the existing `UserPrefs.saveUserName()` — no new persistence layer.
- Zones: at least 1 must always exist (SPEC.md §1.3); "Otros" is additionally protected from rename/delete (cierre de huecos §4) — never let a code path delete or rename it.
- `Item.addedBy` / `Item.addedAt` must never be mutated by an edit (cierre de huecos §1) — every edit path must preserve them from the original document.
- The only new runtime permission is `android.permission.ACCESS_NETWORK_STATE` (manifest-only, no runtime prompt needed) — camera permission handling is unchanged.
- Every new pure-logic function ships with a JUnit4 test in `app/src/test/kotlin`. Android-framework-dependent glue (Compose screens, `Bitmap` I/O, `ConnectivityManager` callbacks) stays untested, consistent with `BarcodeScannerView`/`MainListScreen` today — verified instead by `./gradlew assembleDebug` / `./gradlew testDebugUnitTest` after each task.
- Do not push to GitHub without explicit user confirmation (standing constraint from the original plan).

---

### Task 1: Quantity validation (`parseQtyOrDefault`)

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/data/Validation.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/data/ValidationTest.kt`

**Interfaces:**
- Produces: `fun parseQtyOrDefault(input: String): Double?` — blank input → `1.0`; a numeric string > 0 → that value; anything else (`"0"`, negative, non-numeric) → `null`. Consumed later by `AddItemSheet` (Task 9).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.listacasa.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    @Test fun `blank input defaults to 1`() {
        assertEquals(1.0, parseQtyOrDefault(""), 0.0001)
        assertEquals(1.0, parseQtyOrDefault("   "), 0.0001)
    }

    @Test fun `valid positive number is parsed`() {
        assertEquals(2.5, parseQtyOrDefault("2.5"), 0.0001)
        assertEquals(3.0, parseQtyOrDefault("3"), 0.0001)
    }

    @Test fun `zero, negative or non-numeric input is invalid`() {
        assertNull(parseQtyOrDefault("0"))
        assertNull(parseQtyOrDefault("-1"))
        assertNull(parseQtyOrDefault("abc"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ValidationTest"`
Expected: FAIL (compile error — `parseQtyOrDefault` is unresolved).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.listacasa.app.data

/**
 * Valida el campo de cantidad de AddItemSheet (cierre de huecos §9): vacío se
 * asume 1, y cualquier valor debe ser estrictamente mayor que 0.
 * @return la cantidad parseada, o null si el texto no es un número > 0.
 */
fun parseQtyOrDefault(input: String): Double? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return 1.0
    val value = trimmed.toDoubleOrNull() ?: return null
    return if (value > 0) value else null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ValidationTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/data/Validation.kt app/src/test/kotlin/com/listacasa/app/data/ValidationTest.kt
git commit -m "feat: add quantity validation (must be > 0, blank defaults to 1)"
```

---

### Task 2: List logic extensions — sort modes, search filter, duplicate detection

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt`
- Modify: `app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt`

**Interfaces:**
- Consumes: `Item`, `Zone` (existing, `com.listacasa.app.data`).
- Produces: `enum class SortMode { NEWEST_FIRST, OLDEST_FIRST, ALPHABETICAL }`; `fun groupAndSort(items: List<Item>, zones: List<Zone>, filterZoneId: String?, sortMode: SortMode = SortMode.NEWEST_FIRST): List<ZoneSection>` (new `sortMode` param, default-compatible with existing callers); `fun filterItemsByQuery(items: List<Item>, query: String): List<Item>`; `fun findPendingDuplicateByBarcode(items: List<Item>, barcode: String): Item?`. All consumed later by `AppViewModel`/`UiState` (Task 7) and `AddItemSheet` (Task 9).

- [ ] **Step 1: Write the failing tests** — replace the full contents of the test file:

```kotlin
package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemListLogicTest {
    private val nevera = Zone(id = "z1", name = "Nevera", order = 0)
    private val despensa = Zone(id = "z2", name = "Despensa", order = 1)

    @Test fun `progress text counts done vs total`() {
        val items = listOf(
            Item(id = "1", name = "Leche", done = true),
            Item(id = "2", name = "Pan", done = false),
            Item(id = "3", name = "Huevos", done = true)
        )
        assertEquals("2 de 3 listos", progressText(items))
    }

    @Test fun `progress text with empty list`() {
        assertEquals("0 de 0 listos", progressText(emptyList()))
    }

    @Test fun `groups by zone order, pending before done within a zone`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = true),
            Item(id = "2", name = "Pan", zone = "z1", done = false),
            Item(id = "3", name = "Arroz", zone = "z2", done = false)
        )
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = "ALL")
        assertEquals(listOf("z1", "z2"), sections.map { it.zone.id })
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `filters to a single zone when requested`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1"),
            Item(id = "2", name = "Arroz", zone = "z2")
        )
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = "z2")
        assertEquals(listOf("z2"), sections.map { it.zone.id })
    }

    @Test fun `zones with no items are omitted from the unfiltered view`() {
        val items = listOf(Item(id = "1", name = "Leche", zone = "z1"))
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = "ALL")
        assertEquals(listOf("z1"), sections.map { it.zone.id })
    }

    @Test fun `sort mode newest first orders by addedAt descending within a group`() {
        val older = Item(id = "1", name = "Leche", zone = "z1", addedAt = java.util.Date(1000))
        val newer = Item(id = "2", name = "Pan", zone = "z1", addedAt = java.util.Date(2000))
        val sections = groupAndSort(
            listOf(older, newer), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.NEWEST_FIRST
        )
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `sort mode oldest first orders by addedAt ascending within a group`() {
        val older = Item(id = "1", name = "Leche", zone = "z1", addedAt = java.util.Date(1000))
        val newer = Item(id = "2", name = "Pan", zone = "z1", addedAt = java.util.Date(2000))
        val sections = groupAndSort(
            listOf(older, newer), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.OLDEST_FIRST
        )
        assertEquals(listOf("1", "2"), sections.first().items.map { it.id })
    }

    @Test fun `sort mode alphabetical orders by name case-insensitively`() {
        val zebra = Item(id = "1", name = "zanahoria", zone = "z1")
        val apple = Item(id = "2", name = "Arroz", zone = "z1")
        val sections = groupAndSort(
            listOf(zebra, apple), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.ALPHABETICAL
        )
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `pending items always precede done items regardless of sort mode`() {
        val doneOld = Item(id = "1", name = "A", zone = "z1", done = true, addedAt = java.util.Date(5000))
        val pendingNew = Item(id = "2", name = "B", zone = "z1", done = false, addedAt = java.util.Date(1000))
        val sections = groupAndSort(
            listOf(doneOld, pendingNew), listOf(nevera), filterZoneId = "ALL", sortMode = SortMode.OLDEST_FIRST
        )
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `filterItemsByQuery matches case-insensitive partial names`() {
        val items = listOf(
            Item(id = "1", name = "Leche entera"),
            Item(id = "2", name = "Pan de molde"),
            Item(id = "3", name = "Leche de avena")
        )
        val result = filterItemsByQuery(items, "leche")
        assertEquals(listOf("1", "3"), result.map { it.id })
    }

    @Test fun `filterItemsByQuery with blank query returns all items unchanged`() {
        val items = listOf(Item(id = "1", name = "Leche"), Item(id = "2", name = "Pan"))
        assertEquals(items, filterItemsByQuery(items, "  "))
    }

    @Test fun `findPendingDuplicateByBarcode matches only pending items with same barcode`() {
        val pendingMatch = Item(id = "1", name = "Leche", barcode = "123", done = false)
        val doneMatch = Item(id = "2", name = "Leche vieja", barcode = "123", done = true)
        val items = listOf(pendingMatch, doneMatch)
        assertEquals(pendingMatch, findPendingDuplicateByBarcode(items, "123"))
        assertEquals(null, findPendingDuplicateByBarcode(listOf(doneMatch), "123"))
        assertEquals(null, findPendingDuplicateByBarcode(items, "999"))
    }
}
```

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.ItemListLogicTest"`
Expected: FAIL (compile error — `SortMode`, `filterItemsByQuery`, `findPendingDuplicateByBarcode` unresolved).

- [ ] **Step 3: Write the implementation** — replace the full contents of `ItemListLogic.kt`:

```kotlin
package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone

data class ZoneSection(val zone: Zone, val items: List<Item>)

enum class SortMode { NEWEST_FIRST, OLDEST_FIRST, ALPHABETICAL }

/** "X de Y listos" — comprados vs total (SPEC.md sec 1.4). */
fun progressText(items: List<Item>): String {
    val done = items.count { it.done }
    return "$done de ${items.size} listos"
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

    val withinGroupComparator: Comparator<Item> = when (sortMode) {
        SortMode.NEWEST_FIRST -> compareByDescending { it.addedAt }
        SortMode.OLDEST_FIRST -> compareBy { it.addedAt }
        SortMode.ALPHABETICAL -> compareBy { it.name.lowercase() }
    }

    return relevantZones.mapNotNull { zone ->
        val zoneItems = items.filter { it.zone == zone.id }
            .sortedWith(compareBy<Item> { it.done }.then(withinGroupComparator))
        if (zoneItems.isEmpty() && !isFiltered) null else ZoneSection(zone, zoneItems)
    }
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.ItemListLogicTest"`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt
git commit -m "feat: add sort modes, search filter and barcode-duplicate detection to list logic"
```

---

### Task 3: Protected zone helper (`isProtectedZone`)

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/data/Zone.kt`
- Create: `app/src/test/kotlin/com/listacasa/app/data/ZoneTest.kt`

**Interfaces:**
- Produces: `const val PROTECTED_ZONE_NAME = "Otros"`; `fun isProtectedZone(zone: Zone): Boolean`. Consumed later by `ManageZonesScreen` (Task 12).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.listacasa.app.data

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ZoneTest"`
Expected: FAIL (compile error — `isProtectedZone` unresolved).

- [ ] **Step 3: Write the implementation** — replace the full contents of `Zone.kt`:

```kotlin
package com.listacasa.app.data

import com.google.firebase.firestore.DocumentId

/**
 * Zona de la despensa/casa donde se guarda o usa un producto
 * (ej. Nevera, Congelador, Despensa, Otros). Configurable por el usuario:
 * crear, renombrar, eliminar (con reasignación de productos a "Otros").
 *
 * households/{householdCode}/zones/{zoneId}
 */
data class Zone(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val order: Int = 0
)

val DEFAULT_ZONE_NAMES = listOf("Nevera", "Congelador", "Despensa", "Otros")

val ZONE_COLORS = listOf(
    "#6C5CE7", "#FF7F6B", "#00B37D", "#F5A623",
    "#3D9CD8", "#E8628C", "#8A8DBF"
)

fun zoneColorFor(index: Int): String = ZONE_COLORS[index % ZONE_COLORS.size]

const val PROTECTED_ZONE_NAME = "Otros"

/** "Otros" es la zona de reserva de reasignación y no se puede renombrar/eliminar (cierre de huecos §4). */
fun isProtectedZone(zone: Zone): Boolean = zone.name.equals(PROTECTED_ZONE_NAME, ignoreCase = true)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ZoneTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/data/Zone.kt app/src/test/kotlin/com/listacasa/app/data/ZoneTest.kt
git commit -m "feat: add isProtectedZone to guard the reserved 'Otros' zone"
```

---

### Task 4: Image compression — resize math + `ImageCompressor`

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/data/ImageCompression.kt`
- Create: `app/src/test/kotlin/com/listacasa/app/data/ImageCompressionTest.kt`

**Interfaces:**
- Produces: `fun computeResizedDimensions(width: Int, height: Int, maxSide: Int = 1024): Pair<Int, Int>` (pure, tested); `object ImageCompressor { fun compress(context: Context, sourceUri: Uri): Uri }` (Android-dependent, not unit tested). Consumed later by `StorageRepository` (Task 5).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.listacasa.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressionTest {
    @Test fun `images already within the max side are left unchanged`() {
        assertEquals(800 to 600, computeResizedDimensions(800, 600, maxSide = 1024))
    }

    @Test fun `landscape image is scaled down keeping aspect ratio`() {
        assertEquals(1024 to 576, computeResizedDimensions(2048, 1152, maxSide = 1024))
    }

    @Test fun `portrait image is scaled down keeping aspect ratio`() {
        assertEquals(576 to 1024, computeResizedDimensions(1152, 2048, maxSide = 1024))
    }

    @Test fun `square image exactly at the limit is unchanged`() {
        assertEquals(1024 to 1024, computeResizedDimensions(1024, 1024, maxSide = 1024))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ImageCompressionTest"`
Expected: FAIL (compile error — `computeResizedDimensions` unresolved).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.listacasa.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

private const val MAX_PHOTO_SIDE = 1024
private const val JPEG_QUALITY = 80

/**
 * Calcula el tamaño destino manteniendo el ratio de aspecto, sin superar
 * `maxSide` en el lado mayor (cierre de huecos §6: compresión ligera de fotos).
 */
fun computeResizedDimensions(width: Int, height: Int, maxSide: Int = MAX_PHOTO_SIDE): Pair<Int, Int> {
    require(width > 0 && height > 0) { "width and height must be positive" }
    val largestSide = maxOf(width, height)
    if (largestSide <= maxSide) return width to height
    val scale = maxSide.toDouble() / largestSide
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)
    return newWidth to newHeight
}

/**
 * Redimensiona y recomprime una foto local antes de subirla a Storage.
 * Sin test unitario (depende de android.graphics.Bitmap, sin Robolectric en
 * este proyecto) — se verifica manualmente añadiendo una foto en la app.
 */
object ImageCompressor {
    fun compress(context: Context, sourceUri: Uri): Uri {
        val original = context.contentResolver.openInputStream(sourceUri).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return sourceUri

        val (targetWidth, targetHeight) = computeResizedDimensions(original.width, original.height)
        val resized = if (targetWidth == original.width && targetHeight == original.height) {
            original
        } else {
            Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        }

        val outFile = File.createTempFile("compressed_", ".jpg", context.cacheDir)
        FileOutputStream(outFile).use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return Uri.fromFile(outFile)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.ImageCompressionTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/data/ImageCompression.kt app/src/test/kotlin/com/listacasa/app/data/ImageCompressionTest.kt
git commit -m "feat: add photo resize/compression (max 1024px, JPEG 80%)"
```

---

### Task 5: Wire photo compression into `StorageRepository`

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/data/StorageRepository.kt`
- Modify: `app/src/main/kotlin/com/listacasa/app/MainActivity.kt:99`

**Interfaces:**
- Consumes: `ImageCompressor.compress(context, uri)` (Task 4).
- Produces: `StorageRepository(storage: FirebaseStorage, householdCode: String, context: Context)` — constructor now takes a third `context` parameter (breaking change for the single call site, fixed in this same task).

- [ ] **Step 1: Update `StorageRepository.kt`** — replace the full file contents:

```kotlin
package com.listacasa.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Sube fotos de productos a Firebase Storage.
 * households/{householdCode}/photos/{itemId}.jpg (SPEC.md §2)
 * Comprime la imagen (ImageCompressor) antes de subirla (cierre de huecos §6).
 */
class StorageRepository(
    private val storage: FirebaseStorage,
    private val householdCode: String,
    private val context: Context
) {
    suspend fun uploadPhoto(itemId: String, localUri: Uri): String {
        val compressedUri = ImageCompressor.compress(context, localUri)
        val ref = storage.reference.child("households/$householdCode/photos/$itemId.jpg")
        ref.putFile(compressedUri).await()
        return ref.downloadUrl.await().toString()
    }
}
```

- [ ] **Step 2: Update the call site in `MainActivity.kt`**

Find (around line 99):

```kotlin
                        storageRepository = StorageRepository(storage, code),
```

Replace with:

```kotlin
                        storageRepository = StorageRepository(storage, code, context),
```

- [ ] **Step 3: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/data/StorageRepository.kt app/src/main/kotlin/com/listacasa/app/MainActivity.kt
git commit -m "feat: compress photos before uploading to Firebase Storage"
```

---

### Task 6: Connectivity observer + manifest permission

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/data/ConnectivityObserver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `class ConnectivityObserver(context: Context) { fun observe(): Flow<Boolean> }` — emits `true`/`false` on every connectivity change, `distinctUntilChanged`. Consumed later by `MainActivity.kt` (Task 14).

- [ ] **Step 1: Add the manifest permission**

Find in `app/src/main/AndroidManifest.xml`:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

Replace with:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

- [ ] **Step 2: Write `ConnectivityObserver.kt`**

```kotlin
package com.listacasa.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observa si el dispositivo tiene conexión a internet, para el indicador
 * "sin conexión" de MainListScreen (cierre de huecos §2). Usa
 * ConnectivityManager en vez de los listeners de Firestore porque estos no
 * reflejan de forma fiable la conectividad real del dispositivo.
 */
class ConnectivityObserver(private val context: Context) {
    fun observe(): Flow<Boolean> = callbackFlow {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun hasInternet(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        trySend(hasInternet())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hasInternet())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
```

- [ ] **Step 3: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/data/ConnectivityObserver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add ConnectivityObserver for the offline indicator"
```

---

### Task 7: `AppViewModel` + `UiState` wiring (edit, increment qty, search, sort)

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/AppViewModel.kt`
- Create: `app/src/test/kotlin/com/listacasa/app/ui/UiStateTest.kt`

**Interfaces:**
- Consumes: `SortMode`, `filterItemsByQuery`, `groupAndSort` (Task 2); `ItemsRepository.updateItem` (existing).
- Produces: `UiState` gains `searchQuery: String = ""` and `sortMode: SortMode = SortMode.NEWEST_FIRST`, and `sections` now filters by `searchQuery` before grouping/sorting; `AppViewModel` gains `fun editItem(item: Item)`, `fun incrementQty(item: Item, addQty: Double)`, `fun setSearchQuery(query: String)`, `fun setSortMode(mode: SortMode)`. Consumed later by `AddItemSheet` (Task 9) and `MainListScreen` (Task 11).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {
    private val nevera = Zone(id = "z1", name = "Nevera", order = 0)

    @Test fun `sections applies the search query before grouping and sorting`() {
        val state = UiState(
            items = listOf(
                Item(id = "1", name = "Leche", zone = "z1"),
                Item(id = "2", name = "Pan", zone = "z1")
            ),
            zones = listOf(nevera),
            selectedZoneId = "ALL",
            searchQuery = "leche"
        )
        assertEquals(listOf("1"), state.sections.single().items.map { it.id })
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.UiStateTest"`
Expected: FAIL (compile error — `UiState` has no `searchQuery`/`sortMode` parameters).

- [ ] **Step 3: Write the implementation** — replace the full contents of `AppViewModel.kt`:

```kotlin
package com.listacasa.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listacasa.app.data.Item
import com.listacasa.app.data.ItemsRepository
import com.listacasa.app.data.StorageRepository
import com.listacasa.app.data.Zone
import com.listacasa.app.data.ZonesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val items: List<Item> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val selectedZoneId: String = "ALL",
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NEWEST_FIRST,
    val loading: Boolean = true,
    val error: String? = null
) {
    val progress: String get() = progressText(items)
    val sections: List<ZoneSection> get() =
        groupAndSort(filterItemsByQuery(items, searchQuery), zones, selectedZoneId, sortMode)
}

/**
 * Une ItemsRepository/ZonesRepository/StorageRepository en un único StateFlow
 * para las pantallas de Compose. Sin login tradicional: el nombre de usuario
 * ya viene resuelto desde DataStore antes de crear este ViewModel.
 */
class AppViewModel(
    private val itemsRepository: ItemsRepository,
    private val zonesRepository: ZonesRepository,
    val storageRepository: StorageRepository,
    private val userName: String
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { zonesRepository.seedIfEmpty() }
        }
        viewModelScope.launch {
            runCatching {
                itemsRepository.observeItems().collect { items ->
                    _state.value = _state.value.copy(items = items, loading = false, error = null)
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
        viewModelScope.launch {
            runCatching {
                zonesRepository.observeZones().collect { zones ->
                    _state.value = _state.value.copy(zones = zones)
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun selectZone(zoneId: String) {
        _state.value = _state.value.copy(selectedZoneId = zoneId)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
    }

    /** @return el id Firestore del producto creado, para poder subir su foto después. */
    suspend fun addItem(item: Item): String =
        itemsRepository.addItem(item.copy(addedBy = userName))

    /** Guarda los cambios de un producto ya existente (cierre de huecos §1: edición completa). */
    fun editItem(item: Item) = viewModelScope.launch {
        itemsRepository.updateItem(item)
    }

    /** Suma cantidad a un producto pendiente ya existente en vez de duplicarlo (cierre de huecos §3). */
    fun incrementQty(item: Item, addQty: Double) = viewModelScope.launch {
        itemsRepository.updateItem(item.copy(qty = item.qty + addQty))
    }

    /** Sube la foto y adjunta su URL al producto ya creado. */
    fun attachPhoto(itemId: String, localUri: android.net.Uri) = viewModelScope.launch {
        val url = storageRepository.uploadPhoto(itemId, localUri)
        itemsRepository.updatePhotoUrl(itemId, url)
    }

    fun toggleDone(item: Item) = viewModelScope.launch {
        itemsRepository.updateItem(item.copy(done = !item.done))
    }

    fun deleteItem(itemId: String) = viewModelScope.launch {
        itemsRepository.deleteItem(itemId)
    }

    fun clearDone() = viewModelScope.launch {
        val doneIds = _state.value.items.filter { it.done }.map { it.id }
        itemsRepository.clearDone(doneIds)
    }

    fun createZone(name: String) = viewModelScope.launch {
        val nextOrder = (_state.value.zones.maxOfOrNull { it.order } ?: -1) + 1
        zonesRepository.addZone(name, nextOrder)
    }

    fun renameZone(zoneId: String, newName: String) = viewModelScope.launch {
        zonesRepository.renameZone(zoneId, newName)
    }

    /** Reasigna los productos de la zona eliminada a "Otros" antes de borrarla (SPEC.md sec 1.3). */
    fun deleteZone(zoneId: String, otrosZoneId: String) = viewModelScope.launch {
        if (_state.value.zones.size <= 1) return@launch
        itemsRepository.reassignZone(zoneId, otrosZoneId)
        zonesRepository.deleteZone(zoneId)
        if (_state.value.selectedZoneId == zoneId) {
            _state.value = _state.value.copy(selectedZoneId = "ALL")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.UiStateTest"`
Expected: PASS (2 tests). Also run the full suite to make sure nothing else broke: `./gradlew testDebugUnitTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/AppViewModel.kt app/src/test/kotlin/com/listacasa/app/ui/UiStateTest.kt
git commit -m "feat: wire edit/increment/search/sort into AppViewModel and UiState"
```

---

### Task 8: New string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: all string resource IDs consumed by Tasks 9, 11, 12 and 13 (listed below). No code logic in this task.

- [ ] **Step 1: Replace the full contents of `strings.xml`**

```xml
<resources>
    <string name="app_name">Lista de la Casa</string>

    <!-- JoinHouseholdScreen -->
    <string name="join_title">Bienvenido/a</string>
    <string name="join_subtitle">Une tu móvil a la lista compartida de tu casa</string>
    <string name="join_name_label">Tu nombre</string>
    <string name="join_code_label">Código de casa</string>
    <string name="join_create_code">Crear código nuevo</string>
    <string name="join_code_invalid">El código debe tener el formato CASA-1234</string>
    <string name="join_continue">Continuar</string>

    <!-- MainListScreen -->
    <string name="main_empty_list">No hay productos todavía. Toca + para añadir el primero.</string>
    <string name="main_clear_done">Vaciar comprados</string>
    <string name="main_all_zones_tab">Todos</string>
    <string name="main_error_generic">No se pudo sincronizar. Comprueba tu conexión.</string>
    <string name="main_settings_cd">Ajustes</string>
    <string name="main_add_item_cd">Añadir producto</string>
    <string name="main_delete_item_cd">Eliminar producto</string>
    <string name="main_search_cd">Buscar</string>
    <string name="main_search_hint">Buscar producto…</string>
    <string name="main_sort_cd">Ordenar</string>
    <string name="main_sort_newest">Más reciente primero</string>
    <string name="main_sort_oldest">Más antiguo primero</string>
    <string name="main_sort_alpha">Alfabético A-Z</string>
    <string name="main_offline_indicator">Sin conexión — los cambios se sincronizarán al reconectar</string>
    <string name="main_clear_done_confirm_title">Vaciar comprados</string>
    <string name="main_clear_done_confirm_message">¿Borrar %1$d producto(s) comprados? Esta acción no se puede deshacer.</string>
    <string name="main_edit_name">Cambiar nombre</string>
    <string name="main_edit_name_title">Tu nombre</string>
    <string name="main_edit_name_save">Guardar</string>

    <!-- AddItemSheet -->
    <string name="add_item_title">Añadir producto</string>
    <string name="add_item_edit_title">Editar producto</string>
    <string name="add_item_name">Nombre</string>
    <string name="add_item_qty">Cantidad</string>
    <string name="add_item_unit">Unidad</string>
    <string name="add_item_note">Nota (opcional)</string>
    <string name="add_item_photo">📷 Foto</string>
    <string name="add_item_scan">📊 Escanear</string>
    <string name="add_item_new_zone">+ nueva zona</string>
    <string name="add_item_save">Guardar</string>
    <string name="add_item_save_edit">Guardar cambios</string>
    <string name="add_item_barcode_not_found">No se encontró el producto. Completa el nombre a mano.</string>
    <string name="add_item_scanning_hint">Apunta la cámara al código de barras</string>
    <string name="add_item_camera_permission_needed">Se necesita permiso de cámara para escanear</string>
    <string name="add_item_existing_photo_cd">Foto actual del producto</string>
    <string name="add_item_duplicate_title">Producto ya en la lista</string>
    <string name="add_item_duplicate_message">Ya tienes \"%1$s\" en la lista. ¿Qué quieres hacer?</string>
    <string name="add_item_duplicate_sum">Sumar cantidad</string>
    <string name="add_item_duplicate_new">Añadir como nuevo</string>

    <!-- ManageZonesScreen -->
    <string name="zones_title">Gestionar zonas</string>
    <string name="zones_delete_confirm_title">Eliminar zona</string>
    <string name="zones_delete_confirm_message">%1$d producto(s) se moverán a \"Otros\". ¿Continuar?</string>
    <string name="zones_new_zone_hint">Nombre de la nueva zona</string>
    <string name="zones_add_cd">Añadir zona</string>
    <string name="zones_delete_cd">Eliminar zona</string>
    <string name="zones_back_cd">Volver</string>
    <string name="zones_protected_hint">Zona de reserva, no se puede modificar.</string>
</resources>
```

- [ ] **Step 2: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (resource compilation catches any XML/string errors).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add string resources for edit/search/sort/offline/protected-zone UI"
```

---

### Task 9: `AddItemSheet` — edit mode, duplicate-barcode dialog, quantity validation

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/screens/AddItemSheet.kt`

**Interfaces:**
- Consumes: `parseQtyOrDefault` (Task 1), `findPendingDuplicateByBarcode` (Task 2), `AppViewModel.editItem`/`incrementQty` (Task 7), strings from Task 8.
- Produces: `AddItemSheet(viewModel, initialZoneId, itemToEdit: Item? = null, onDismiss)` — new optional `itemToEdit` param. Consumed later by `MainActivity.kt` (Task 14).

- [ ] **Step 1: Replace the full contents of `AddItemSheet.kt`**

```kotlin
package com.listacasa.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.OpenFoodFactsClient
import com.listacasa.app.data.parseQtyOrDefault
import com.listacasa.app.data.zoneColorFor
import com.listacasa.app.data.Unit as ItemUnit
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.components.BarcodeScannerView
import com.listacasa.app.ui.components.ZoneChip
import com.listacasa.app.ui.findPendingDuplicateByBarcode
import kotlinx.coroutines.launch

/**
 * SPEC.md sec 1.5: añadir producto, con foto y escaneo de código de barras.
 * Si `itemToEdit` no es null, el formulario se precarga y "Guardar" actualiza
 * ese producto en vez de crear uno nuevo (cierre de huecos §1: edición completa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    viewModel: AppViewModel,
    initialZoneId: String?,
    itemToEdit: Item? = null,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val zones = state.zones.sortedBy { it.order }
    val isEditing = itemToEdit != null

    var name by remember(itemToEdit) { mutableStateOf(itemToEdit?.name ?: "") }
    var qtyText by remember(itemToEdit) { mutableStateOf(itemToEdit?.qty?.toString() ?: "1") }
    var unit by remember(itemToEdit) {
        mutableStateOf(
            itemToEdit?.let { runCatching { ItemUnit.valueOf(it.unit) }.getOrDefault(ItemUnit.UD) } ?: ItemUnit.UD
        )
    }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var note by remember(itemToEdit) { mutableStateOf(itemToEdit?.note ?: "") }
    var selectedZoneId by remember(zones, itemToEdit) {
        mutableStateOf(itemToEdit?.zone ?: initialZoneId?.takeIf { it != "ALL" } ?: zones.firstOrNull()?.id ?: "")
    }
    var newZoneName by remember { mutableStateOf("") }
    var photoUri by remember(itemToEdit) { mutableStateOf<Uri?>(null) }
    var barcode by remember(itemToEdit) { mutableStateOf(itemToEdit?.barcode) }
    var barcodeNotFound by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var duplicateItem by remember { mutableStateOf<Item?>(null) }

    val scope = rememberCoroutineScope()
    val qty = parseQtyOrDefault(qtyText)

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        photoUri = uri
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = if (isEditing) stringResource(R.string.add_item_edit_title) else stringResource(R.string.add_item_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_item_name)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            item {
                if (barcodeNotFound) {
                    Text(
                        stringResource(R.string.add_item_barcode_not_found),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (barcode != null) {
                    Text(
                        "Código: $barcode",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text(stringResource(R.string.add_item_qty)) },
                        isError = qty == null,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        OutlinedTextField(
                            value = unit.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.add_item_unit)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { unitMenuExpanded = true }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            ItemUnit.entries.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.label) },
                                    onClick = {
                                        unit = candidate
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.zones_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    zones.forEachIndexed { index, zone ->
                        ZoneChip(
                            zone = zone,
                            colorHex = zoneColorFor(index),
                            selected = zone.id == selectedZoneId,
                            onClick = { selectedZoneId = zone.id }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text(stringResource(R.string.add_item_new_zone)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            if (newZoneName.isNotBlank()) {
                                viewModel.createZone(newZoneName.trim())
                                newZoneName = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("+") }
                }
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.add_item_note)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            item {
                if (isEditing && photoUri == null && !itemToEdit?.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = itemToEdit?.photoUrl,
                        contentDescription = stringResource(R.string.add_item_existing_photo_cd),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    )
                }
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
            }
            item {
                Button(
                    onClick = {
                        val validQty = qty ?: return@Button
                        val zoneToUse = selectedZoneId
                        val localPhotoUri = photoUri
                        val existing = itemToEdit
                        if (existing != null) {
                            val updated = existing.copy(
                                name = name.trim(),
                                qty = validQty,
                                unit = unit.name,
                                note = note.trim().ifBlank { null },
                                zone = zoneToUse,
                                barcode = barcode
                            )
                            scope.launch {
                                viewModel.editItem(updated)
                                if (localPhotoUri != null) {
                                    runCatching { viewModel.attachPhoto(updated.id, localPhotoUri) }
                                }
                            }
                        } else {
                            val newItem = Item(
                                name = name.trim(),
                                qty = validQty,
                                unit = unit.name,
                                note = note.trim().ifBlank { null },
                                zone = zoneToUse,
                                barcode = barcode
                            )
                            scope.launch {
                                val newItemId = viewModel.addItem(newItem)
                                if (localPhotoUri != null) {
                                    runCatching { viewModel.attachPhoto(newItemId, localPhotoUri) }
                                }
                            }
                        }
                        onDismiss()
                    },
                    enabled = name.isNotBlank() && selectedZoneId.isNotBlank() && qty != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
                ) {
                    Text(if (isEditing) stringResource(R.string.add_item_save_edit) else stringResource(R.string.add_item_save))
                }
            }
        }
    }

    if (showScanner) {
        Dialog(onDismissRequest = { showScanner = false }) {
            Box(modifier = Modifier.fillMaxSize()) {
                BarcodeScannerView(
                    onBarcodeDetected = { detected ->
                        showScanner = false
                        barcode = detected
                        scope.launch {
                            runCatching { OpenFoodFactsClient.api().getProduct(detected) }
                                .onSuccess { response ->
                                    val productName = response.product?.productName
                                    if (response.status == 1 && !productName.isNullOrBlank()) {
                                        name = productName
                                        barcodeNotFound = false
                                    } else {
                                        barcodeNotFound = true
                                    }
                                }
                                .onFailure { barcodeNotFound = true }
                        }
                        if (!isEditing) {
                            duplicateItem = findPendingDuplicateByBarcode(state.items, detected)
                        }
                    }
                )
            }
        }
    }

    val pendingDuplicate = duplicateItem
    if (pendingDuplicate != null) {
        AlertDialog(
            onDismissRequest = { duplicateItem = null },
            title = { Text(stringResource(R.string.add_item_duplicate_title)) },
            text = { Text(stringResource(R.string.add_item_duplicate_message, pendingDuplicate.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.incrementQty(pendingDuplicate, qty ?: 1.0)
                    duplicateItem = null
                    onDismiss()
                }) { Text(stringResource(R.string.add_item_duplicate_sum)) }
            },
            dismissButton = {
                TextButton(onClick = { duplicateItem = null }) { Text(stringResource(R.string.add_item_duplicate_new)) }
            }
        )
    }
}
```

- [ ] **Step 2: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/AddItemSheet.kt
git commit -m "feat: add edit mode, duplicate-barcode dialog and qty validation to AddItemSheet"
```

---

### Task 10: `ProductCard` — tap to edit

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/components/ProductCard.kt`

**Interfaces:**
- Produces: `ProductCard(item, onToggleDone, onDelete, onEdit: () -> Unit = {})` — new `onEdit` param with a no-op default so this task compiles standalone. Consumed later by `MainListScreen` (Task 11).

- [ ] **Step 1: Replace the full contents of `ProductCard.kt`**

```kotlin
package com.listacasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.Unit as ItemUnit

/** Tocar el nombre/detalles del producto abre el formulario en modo edición (cierre de huecos §1). */
@Composable
fun ProductCard(item: Item, onToggleDone: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit = {}) {
    val alpha = if (item.done) 0.5f else 1f
    val unitLabel = runCatching { ItemUnit.valueOf(item.unit).label }.getOrDefault(item.unit)

    Card(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleDone) {
                Icon(
                    imageVector = if (item.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (!item.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                )
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
                Text(
                    text = "${item.qty} $unitLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.note.isNullOrBlank()) {
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.addedBy.isNullOrBlank()) {
                    Text(
                        text = "pedido por ${item.addedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.main_delete_item_cd),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/components/ProductCard.kt
git commit -m "feat: tap a product card to edit it"
```

---

### Task 11: `MainListScreen` — search, sort, offline indicator, clear-done confirmation, edit wiring

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/screens/MainListScreen.kt`

**Interfaces:**
- Consumes: `SortMode` (Task 2), `state.searchQuery`/`sortMode`/`setSearchQuery`/`setSortMode` (Task 7), `ProductCard(onEdit)` (Task 10), strings from Task 8.
- Produces: `MainListScreen(viewModel, isOnline: Boolean = true, onAddItem, onEditItem: (Item) -> Unit = {}, onManageZones, onEditName: () -> Unit = {})` — three new params with no-op/`true` defaults so this task compiles standalone. Consumed later by `MainActivity.kt` (Task 14).

- [ ] **Step 1: Replace the full contents of `MainListScreen.kt`**

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.SortMode
import com.listacasa.app.ui.components.ProductCard
import com.listacasa.app.ui.components.ProgressBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SPEC.md sec 1.4: pantalla principal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: AppViewModel,
    isOnline: Boolean = true,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit = {},
    onManageZones: () -> Unit,
    onEditName: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale("es", "ES")).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(today.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.main_search_cd))
                    }
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.main_sort_cd))
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
                    IconButton(onClick = onManageZones) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_cd))
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.main_add_item_cd))
            }
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

            if (showSearchBar) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.main_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            ProgressBar(items = state.items, modifier = Modifier.padding(16.dp))

            val allTabs = listOf("ALL" to stringResource(R.string.main_all_zones_tab)) +
                state.zones.sortedBy { it.order }.map { it.id to it.name }
            val selectedIndex = allTabs.indexOfFirst { it.first == state.selectedZoneId }.coerceAtLeast(0)

            ScrollableTabRow(selectedTabIndex = selectedIndex) {
                allTabs.forEachIndexed { index, (id, label) ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectZone(id) },
                        text = { Text(label) }
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
                            Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                ProductCard(
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

- [ ] **Step 2: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/MainListScreen.kt
git commit -m "feat: add search bar, sort selector, offline indicator and clear-done confirmation"
```

---

### Task 12: `ManageZonesScreen` — protect "Otros"

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/ui/screens/ManageZonesScreen.kt`

**Interfaces:**
- Consumes: `isProtectedZone` (Task 3), `zones_protected_hint` string (Task 8).
- Produces: no signature change — `ManageZonesScreen(viewModel, onBack)` is unchanged.

- [ ] **Step 1: Replace the full contents of `ManageZonesScreen.kt`**

```kotlin
package com.listacasa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.listacasa.app.data.Zone
import com.listacasa.app.data.isProtectedZone
import com.listacasa.app.ui.AppViewModel

/** SPEC.md sec 1.6: crear, renombrar y eliminar zonas. "Otros" está protegida (cierre de huecos §4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageZonesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val zones = state.zones.sortedBy { it.order }
    var newZoneName by remember { mutableStateOf("") }
    var zoneToDelete by remember { mutableStateOf<Zone?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zones_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(zones, key = { it.id }) { zone ->
                var editedName by remember(zone.id) { mutableStateOf(zone.name) }
                val itemCount = state.items.count { it.zone == zone.id }
                val protected = isProtectedZone(zone)

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            readOnly = protected
                        )
                        Text(
                            text = "$itemCount",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        if (!protected && editedName != zone.name && editedName.isNotBlank()) {
                            TextButton(onClick = { viewModel.renameZone(zone.id, editedName.trim()) }) {
                                Text("OK")
                            }
                        }
                        if (!protected && zones.size > 1) {
                            IconButton(onClick = { zoneToDelete = zone }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.zones_delete_cd),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (protected) {
                        Text(
                            text = stringResource(R.string.zones_protected_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text(stringResource(R.string.zones_new_zone_hint)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newZoneName.isNotBlank()) {
                                viewModel.createZone(newZoneName.trim())
                                newZoneName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.zones_add_cd))
                    }
                }
            }
        }
    }

    val pendingZone = zoneToDelete
    if (pendingZone != null) {
        val otros = zones.firstOrNull { isProtectedZone(it) && it.id != pendingZone.id }
            ?: zones.firstOrNull { it.id != pendingZone.id }
        val affectedCount = state.items.count { it.zone == pendingZone.id }
        AlertDialog(
            onDismissRequest = { zoneToDelete = null },
            title = { Text(stringResource(R.string.zones_delete_confirm_title)) },
            text = { Text(stringResource(R.string.zones_delete_confirm_message, affectedCount)) },
            confirmButton = {
                TextButton(onClick = {
                    if (otros != null) {
                        viewModel.deleteZone(pendingZone.id, otros.id)
                    }
                    zoneToDelete = null
                }) { Text(stringResource(R.string.zones_delete_confirm_title)) }
            },
            dismissButton = {
                TextButton(onClick = { zoneToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
```

- [ ] **Step 2: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/ManageZonesScreen.kt
git commit -m "feat: protect the 'Otros' zone from rename/delete in ManageZonesScreen"
```

---

### Task 13: `EditNameDialog` (new screen)

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/EditNameDialog.kt`

**Interfaces:**
- Consumes: `UserPrefs.saveUserName(String)` (existing, `data/UserPrefs.kt`), `main_edit_name_title`/`main_edit_name_save` strings (Task 8).
- Produces: `EditNameDialog(userPrefs: UserPrefs, currentName: String, onDismiss: () -> Unit)`. Consumed later by `MainActivity.kt` (Task 14).

- [ ] **Step 1: Write `EditNameDialog.kt`**

```kotlin
package com.listacasa.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.listacasa.app.R
import com.listacasa.app.data.UserPrefs
import kotlinx.coroutines.launch

/** Permite corregir el nombre local guardado en DataStore (cierre de huecos §5). */
@Composable
fun EditNameDialog(userPrefs: UserPrefs, currentName: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_edit_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        scope.launch {
                            userPrefs.saveUserName(trimmed)
                            onDismiss()
                        }
                    }
                }
            ) { Text(stringResource(R.string.main_edit_name_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 2: Verify the project still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/ui/screens/EditNameDialog.kt
git commit -m "feat: add EditNameDialog to let users correct their saved name"
```

---

### Task 14: `MainActivity` — final wiring

**Files:**
- Modify: `app/src/main/kotlin/com/listacasa/app/MainActivity.kt`

**Interfaces:**
- Consumes: `ConnectivityObserver` (Task 6), `MainListScreen(isOnline, onEditItem, onEditName)` (Task 11), `AddItemSheet(itemToEdit)` (Task 9), `EditNameDialog` (Task 13).
- Produces: nothing further downstream — this is the last task.

- [ ] **Step 1: Replace the full contents of `MainActivity.kt`**

```kotlin
package com.listacasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.listacasa.app.ui.screens.AddItemSheet
import com.listacasa.app.ui.screens.EditNameDialog
import com.listacasa.app.ui.screens.JoinHouseholdScreen
import com.listacasa.app.ui.screens.MainListScreen
import com.listacasa.app.ui.screens.ManageZonesScreen
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
                        storageRepository = StorageRepository(storage, code, context),
                        userName = name
                    ) as T
                }
            }
        }
    )

    var showAddItem by remember { mutableStateOf(false) }
    var itemBeingEdited by remember { mutableStateOf<Item?>(null) }
    var showEditName by remember { mutableStateOf(false) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "mainList") {
        composable("mainList") {
            MainListScreen(
                viewModel = viewModel,
                isOnline = isOnline,
                onAddItem = { showAddItem = true },
                onEditItem = { item -> itemBeingEdited = item },
                onManageZones = { navController.navigate("manageZones") },
                onEditName = { showEditName = true }
            )
        }
        composable("manageZones") {
            ManageZonesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }

    if (showAddItem || itemBeingEdited != null) {
        val state by viewModel.state.collectAsState()
        AddItemSheet(
            viewModel = viewModel,
            initialZoneId = state.selectedZoneId,
            itemToEdit = itemBeingEdited,
            onDismiss = {
                showAddItem = false
                itemBeingEdited = null
            }
        )
    }

    if (showEditName) {
        EditNameDialog(userPrefs = userPrefs, currentName = name, onDismiss = { showEditName = false })
    }
}
```

- [ ] **Step 2: Verify the project compiles and all unit tests still pass**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew testDebugUnitTest`
Expected: all tests pass (existing `HouseholdCodeTest`, `OpenFoodFactsApiTest` plus the new/extended `ItemListLogicTest`, `ZoneTest`, `ImageCompressionTest`, `ValidationTest`, `UiStateTest`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/listacasa/app/MainActivity.kt
git commit -m "feat: wire offline indicator, item editing and name editing into MainActivity"
```

---

### Task 15: Final verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Run a full debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` generated.

- [ ] **Step 3: Manual smoke test (per superpowers:verify / running the app)**

Install the debug APK on a device or emulator and walk through, in order: add an item, tap it to edit and confirm changes persist, scan a barcode matching an existing pending item and confirm the duplicate dialog offers "sumar cantidad"/"añadir como nuevo", toggle airplane mode and confirm the offline banner appears/disappears and edits made offline sync back, try to rename/delete "Otros" in Gestionar zonas and confirm it's blocked, use the search bar and the sort selector, and confirm "vaciar comprados" now asks for confirmation. Report any behavior that doesn't match `docs/superpowers/specs/2026-07-18-cierre-huecos-spec-v1-design.md` before considering this plan done.
