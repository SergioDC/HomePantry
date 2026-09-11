# Historial de compras a partir de foto de ticket Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user photograph a purchase receipt and get a reviewable list of product+price lines saved to a standalone purchase history, with per-product monthly/total spend, at zero recurring cost.

**Architecture:** Same MVVM + Firestore pattern already used for `Item`/`Zone` (a repository per collection, all data funneled into the single `AppViewModel`/`UiState`). OCR and parsing are on-device and pure-Kotlin where possible (ML Kit Text Recognition for OCR, hand-written regex/rules for parsing — no paid AI API, no backend). Camera capture reuses Android's system camera app via `TakePicture` rather than a custom CameraX screen.

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Firestore/Storage, ML Kit Text Recognition (on-device, free), AndroidX `FileProvider`, JUnit4 for pure-logic tests.

**Spec:** `docs/superpowers/specs/2026-09-11-historial-compras-ticket-design.md`

## Global Constraints

- No paid/cloud AI API calls anywhere in this feature — OCR must be ML Kit's on-device `text-recognition` model, parsing must be plain Kotlin (regex/rules), aggregation must run client-side. (Spec "Contexto y objetivo", "Flujo de captura y parseo")
- Purchase catalog is independent of `Item` — no field on `Purchase` references an `Item` id. (Spec "Alcance")
- Only capture line name + total price per line — no quantity/unit field on `Purchase`. (Spec "Alcance", "Modelo de datos")
- User must review/confirm every line before anything is written to Firestore — no silent auto-save path. (Spec "Alcance")
- Ticket photo is compressed with the existing `ImageCompressor` (same as item photos) before upload. (Spec "Modelo de datos")
- Follow existing repository/error patterns exactly: Firestore/Storage failures surface via `UiState.error`, set from `AppViewModel` with `.onFailure { e -> _state.value = _state.value.copy(error = e.message) }`. (Spec "Manejo de errores")

---

### Task 1: `Purchase` data model + product name normalization

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/Purchase.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/ProductNormalization.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/ProductNormalizationTest.kt`

**Interfaces:**
- Produces: `data class Purchase(id: String, rawName: String, normalizedName: String, price: Double, date: Date, addedBy: String?, ticketPhotoUrl: String?)` — used by every later task.
- Produces: `fun normalizeProductName(name: String): String` — used by Task 3 (aggregation) and Task 8 (`AppViewModel.savePurchaseBatch`).

- [ ] **Step 1: Write the failing test for normalization**

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductNormalizationTest {
    @Test fun `lowercases and trims`() {
        assertEquals("tomate", normalizeProductName("  Tomate  "))
    }

    @Test fun `strips accents`() {
        assertEquals("platano", normalizeProductName("Plátano"))
    }

    @Test fun `collapses internal whitespace`() {
        assertEquals("aceite oliva", normalizeProductName("Aceite   Oliva"))
    }

    @Test fun `strips a simple trailing plural s when longer than 3 chars`() {
        assertEquals("tomate", normalizeProductName("Tomates"))
        assertEquals("tomate", normalizeProductName("TOMATE"))
    }

    @Test fun `does not strip trailing s from short words`() {
        assertEquals("gas", normalizeProductName("Gas"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ProductNormalizationTest"`
Expected: FAIL (compilation error — `normalizeProductName` not defined)

- [ ] **Step 3: Write `Purchase.kt`**

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * Línea de una compra registrada a partir de un ticket. Independiente de
 * `Item` (catálogo de compras separado del stock de despensa, ver spec
 * "Historial de compras a partir de foto de ticket").
 * households/{householdCode}/purchases/{purchaseId}
 */
data class Purchase(
    @DocumentId
    val id: String = "",
    val rawName: String = "",
    val normalizedName: String = "",
    val price: Double = 0.0,
    val date: Date = Date(),
    val addedBy: String? = null,
    val ticketPhotoUrl: String? = null
)
```

- [ ] **Step 4: Write `ProductNormalization.kt`**

```kotlin
package com.homepantry.app.data

import java.text.Normalizer

/**
 * Clave de agrupación para el histórico de compras: minúsculas, sin
 * acentos, espacios colapsados, y sin una "s" final simple (plural naive).
 * No pretende ser perfecta -- el usuario puede editar el nombre en la
 * pantalla de revisión para forzar que dos líneas se agrupen igual.
 */
fun normalizeProductName(name: String): String {
    val lowerTrimmed = name.trim().lowercase()
    val collapsedSpaces = lowerTrimmed.replace(Regex("\\s+"), " ")
    val withoutAccents = Normalizer.normalize(collapsedSpaces, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return if (withoutAccents.length > 3 && withoutAccents.endsWith("s")) {
        withoutAccents.dropLast(1)
    } else {
        withoutAccents
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ProductNormalizationTest"`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/Purchase.kt app/src/main/kotlin/com/homepantry/app/data/ProductNormalization.kt app/src/test/kotlin/com/homepantry/app/data/ProductNormalizationTest.kt
git commit -m "feat: add Purchase model and product name normalization"
```

---

### Task 2: Receipt line parsing (rule-based, no AI)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/ReceiptParsing.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/ReceiptParsingTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `data class ParsedReceiptLine(name: String, price: Double)` and `fun parseReceiptLines(rawLines: List<String>): List<ParsedReceiptLine>` — used by Task 7 (OCR wrapper caller in `PurchaseHistoryScreen`) and Task 9 (`ReceiptReviewSheet` converts back to this type on save).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptParsingTest {
    @Test fun `extracts name and price from a simple line`() {
        assertEquals(
            listOf(ParsedReceiptLine("TOMATE RAMA", 1.50)),
            parseReceiptLines(listOf("TOMATE RAMA 1,50"))
        )
    }

    @Test fun `handles a trailing euro sign`() {
        assertEquals(
            listOf(ParsedReceiptLine("LECHE ENTERA 1L", 0.89)),
            parseReceiptLines(listOf("LECHE ENTERA 1L 0,89 €"))
        )
    }

    @Test fun `parses thousand separators`() {
        assertEquals(
            listOf(ParsedReceiptLine("ACEITE OLIVA PACK", 1234.56)),
            parseReceiptLines(listOf("ACEITE OLIVA PACK 1.234,56"))
        )
    }

    @Test fun `discards summary lines even if they end in a price-shaped number`() {
        assertEquals(
            emptyList<ParsedReceiptLine>(),
            parseReceiptLines(listOf("TOTAL 12,40", "IVA 21% 2,10", "SUBTOTAL 10,30", "CAMBIO 0,00"))
        )
    }

    @Test fun `discards lines without a recognizable price`() {
        assertEquals(emptyList<ParsedReceiptLine>(), parseReceiptLines(listOf("PAN", "GRACIAS POR SU COMPRA")))
    }

    @Test fun `discards blank lines`() {
        assertEquals(emptyList<ParsedReceiptLine>(), parseReceiptLines(listOf("", "   ")))
    }

    @Test fun `keeps line order and skips invalid lines in a mixed ticket`() {
        val lines = listOf(
            "SUPERMERCADO EJEMPLO",
            "TOMATE RAMA 1,50",
            "LECHE ENTERA 1L 0,89",
            "SUBTOTAL 2,39",
            "TOTAL 2,39"
        )
        assertEquals(
            listOf(
                ParsedReceiptLine("TOMATE RAMA", 1.50),
                ParsedReceiptLine("LECHE ENTERA 1L", 0.89)
            ),
            parseReceiptLines(lines)
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ReceiptParsingTest"`
Expected: FAIL (compilation error — `parseReceiptLines`/`ParsedReceiptLine` not defined)

- [ ] **Step 3: Write `ReceiptParsing.kt`**

```kotlin
package com.homepantry.app.data

/** Una línea de ticket ya separada en nombre de producto + precio total. */
data class ParsedReceiptLine(val name: String, val price: Double)

private val PRICE_AT_END = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})\s*€?\s*$""")

/** Líneas de resumen de ticket que nunca son un producto (spec "Flujo de captura y parseo"). */
private val SUMMARY_LINE_KEYWORDS = listOf(
    "TOTAL", "SUBTOTAL", "IVA", "CAMBIO", "TARJETA", "EFECTIVO", "BASE IMPONIBLE"
)

/**
 * Parseo por reglas (sin motor de IA): busca un precio en formato español al
 * final de cada línea y usa el resto como nombre de producto. Descarta
 * líneas de resumen (TOTAL/IVA/...) y líneas sin precio reconocible.
 */
fun parseReceiptLines(rawLines: List<String>): List<ParsedReceiptLine> =
    rawLines.mapNotNull(::parseReceiptLine)

private fun parseReceiptLine(line: String): ParsedReceiptLine? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null

    val upper = trimmed.uppercase()
    if (SUMMARY_LINE_KEYWORDS.any { keyword -> upper.contains(keyword) }) return null

    val match = PRICE_AT_END.find(trimmed) ?: return null
    val price = parseSpanishDecimal(match.groupValues[1]) ?: return null

    val name = trimmed.substring(0, match.range.first).trim()
    if (name.isEmpty()) return null

    return ParsedReceiptLine(name = name, price = price)
}

private fun parseSpanishDecimal(text: String): Double? =
    text.replace(".", "").replace(",", ".").toDoubleOrNull()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ReceiptParsingTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/ReceiptParsing.kt app/src/test/kotlin/com/homepantry/app/data/ReceiptParsingTest.kt
git commit -m "feat: add rule-based receipt line parsing"
```

---

### Task 3: Monthly/total spend aggregation

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/PurchaseAggregation.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/PurchaseAggregationTest.kt`

**Interfaces:**
- Consumes: `Purchase` (Task 1).
- Produces: `data class MonthlySpend(yearMonth: String, total: Double)`, `fun monthlySpend(purchases: List<Purchase>): List<MonthlySpend>`, `data class ProductSummary(normalizedName: String, displayName: String, currentMonthTotal: Double, allTimeTotal: Double, purchases: List<Purchase>)`, `fun productSummaries(purchases: List<Purchase>, referenceDate: Date = Date()): List<ProductSummary>` — used by Task 8 (`UiState`), Task 10 (`PurchaseHistoryScreen`), Task 11 (`PurchaseDetailScreen`).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PurchaseAggregationTest {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun date(s: String) = format.parse(s)!!

    @Test fun `monthlySpend groups purchases by year-month regardless of product`() {
        val purchases = listOf(
            Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.5, date = date("2026-03-05")),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-20")),
            Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.6, date = date("2026-04-01"))
        )
        assertEquals(
            listOf(MonthlySpend("2026-04", 1.6), MonthlySpend("2026-03", 2.4)),
            monthlySpend(purchases)
        )
    }

    @Test fun `productSummaries groups by normalizedName and sums current-month and all-time totals`() {
        val purchases = listOf(
            Purchase(rawName = "Tomates", normalizedName = "tomate", price = 1.5, date = date("2026-03-05")),
            Purchase(rawName = "TOMATE RAMA", normalizedName = "tomate", price = 1.7, date = date("2026-03-20")),
            Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.2, date = date("2026-02-01")),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-10"))
        )
        val summaries = productSummaries(purchases, referenceDate = date("2026-03-25"))

        val tomate = summaries.first { it.normalizedName == "tomate" }
        assertEquals("TOMATE RAMA", tomate.displayName)
        assertEquals(3.2, tomate.currentMonthTotal, 0.001)
        assertEquals(4.4, tomate.allTimeTotal, 0.001)
        assertEquals(3, tomate.purchases.size)

        val leche = summaries.first { it.normalizedName == "leche" }
        assertEquals(0.9, leche.currentMonthTotal, 0.001)
        assertEquals(0.9, leche.allTimeTotal, 0.001)
    }

    @Test fun `productSummaries is sorted by all-time total descending`() {
        val purchases = listOf(
            Purchase(rawName = "Barato", normalizedName = "barato", price = 1.0, date = date("2026-03-01")),
            Purchase(rawName = "Caro", normalizedName = "caro", price = 50.0, date = date("2026-03-01"))
        )
        val summaries = productSummaries(purchases, referenceDate = date("2026-03-25"))
        assertEquals(listOf("caro", "barato"), summaries.map { it.normalizedName })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PurchaseAggregationTest"`
Expected: FAIL (compilation error — types not defined)

- [ ] **Step 3: Write `PurchaseAggregation.kt`**

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PurchaseAggregationTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/PurchaseAggregation.kt app/src/test/kotlin/com/homepantry/app/data/PurchaseAggregationTest.kt
git commit -m "feat: add monthly/total spend aggregation for purchase history"
```

---

### Task 4: `PurchasesRepository` (Firestore)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/PurchasesRepository.kt`

**Interfaces:**
- Consumes: `Purchase` (Task 1).
- Produces: `class PurchasesRepository(firestore: FirebaseFirestore, householdCode: String)` with `fun observePurchases(): Flow<List<Purchase>>` and `suspend fun addPurchases(purchases: List<Purchase>)` — used by Task 8 (`AppViewModel`) and Task 12 (`MainActivity` factory).

No unit test for this task: it is a thin Firestore wrapper with the exact same shape as `ItemsRepository` (`app/src/main/kotlin/com/homepantry/app/data/ItemsRepository.kt`), which also has no unit test in this codebase — Firestore calls aren't mocked here, they're verified manually once wired into the UI (Task 13).

- [ ] **Step 1: Write `PurchasesRepository.kt`**

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio del histórico de compras, sincronizado en tiempo real con
 * Firestore. Mismo patrón que ItemsRepository, colección independiente:
 * households/{householdCode}/purchases
 */
class PurchasesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("purchases")

    fun observePurchases(): Flow<List<Purchase>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val purchases = snapshot?.documents?.mapNotNull { it.toObject(Purchase::class.java) } ?: emptyList()
            trySend(purchases)
        }
        awaitClose { registration.remove() }
    }

    /** Alta masiva: todas las líneas confirmadas de un mismo ticket se escriben juntas. */
    suspend fun addPurchases(purchases: List<Purchase>) {
        if (purchases.isEmpty()) return
        val batch = firestore.batch()
        purchases.forEach { purchase -> batch.set(collection().document(), purchase) }
        batch.commit().await()
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/PurchasesRepository.kt
git commit -m "feat: add PurchasesRepository for Firestore-backed purchase history"
```

---

### Task 5: Ticket photo upload in `StorageRepository`

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/StorageRepository.kt`

**Interfaces:**
- Consumes: nothing new (reuses `ImageCompressor` from `app/src/main/kotlin/com/homepantry/app/data/ImageCompression.kt`).
- Produces: `suspend fun StorageRepository.uploadReceiptPhoto(batchId: String, localUri: Uri): String` — used by Task 8 (`AppViewModel.savePurchaseBatch`).

No unit test: mirrors the existing untested `uploadPhoto` (same file), which depends on `android.graphics.Bitmap`/Firebase Storage and is verified manually (see the comment on `ImageCompressor` in `ImageCompression.kt`).

- [ ] **Step 1: Add `uploadReceiptPhoto` to `StorageRepository`**

Modify `app/src/main/kotlin/com/homepantry/app/data/StorageRepository.kt` — add this method inside the `StorageRepository` class, after `uploadPhoto`:

```kotlin
    /**
     * Sube la foto de un ticket ya escaneado y confirmado.
     * households/{householdCode}/receipts/{batchId}.jpg -- una foto por
     * ticket, referenciada desde cada Purchase generada en ese escaneo.
     */
    suspend fun uploadReceiptPhoto(batchId: String, localUri: Uri): String {
        val compressedUri = ImageCompressor.compress(context, localUri)
        val ref = storage.reference.child("households/$householdCode/receipts/$batchId.jpg")
        ref.putFile(compressedUri).await()
        return ref.downloadUrl.await().toString()
    }
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/StorageRepository.kt
git commit -m "feat: add receipt photo upload to StorageRepository"
```

---

### Task 6: Camera capture plumbing (`FileProvider`)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/kotlin/com/homepantry/app/data/CameraCapture.kt`

**Interfaces:**
- Produces: `fun createReceiptCaptureUri(context: Context): Uri` — used by Task 10 (`PurchaseHistoryScreen`, to hand a writable `content://` URI to `ActivityResultContracts.TakePicture`).

`ActivityResultContracts.TakePicture()` needs a `content://` URI it can write the full-resolution photo into (a plain file URI is rejected by the system camera app on modern Android). This requires a `FileProvider` entry, which the app does not have yet.

- [ ] **Step 1: Add the `FileProvider` entry to the manifest**

Modify `app/src/main/AndroidManifest.xml` — add this `<provider>` inside `<application>`, after the closing `</activity>` tag and before `</application>`:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 2: Declare the exposed path**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- ImageCompressor y CameraCapture escriben sus temporales en cacheDir -->
    <cache-path name="temp_images" path="." />
</paths>
```

- [ ] **Step 3: Write `CameraCapture.kt`**

```kotlin
package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Crea un fichero temporal en cacheDir y devuelve su URI vía FileProvider
 * (content://), el formato que exige ActivityResultContracts.TakePicture
 * para escribir la foto capturada por la cámara del sistema.
 */
fun createReceiptCaptureUri(context: Context): Uri {
    val file = File.createTempFile("receipt_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/main/kotlin/com/homepantry/app/data/CameraCapture.kt
git commit -m "feat: add FileProvider and temp-file helper for receipt capture"
```

---

### Task 7: On-device OCR wrapper (ML Kit Text Recognition)

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/homepantry/app/data/ReceiptTextRecognizer.kt`

**Interfaces:**
- Produces: `suspend fun recognizeReceiptTextLines(context: Context, imageUri: Uri): List<String>` — used by Task 10 (`PurchaseHistoryScreen`, feeds into `parseReceiptLines` from Task 2).

No unit test: ML Kit's recognizer needs a real (or emulated) camera/vision pipeline and isn't mockable at the unit-test level in this codebase (same reasoning as `BarcodeScannerView`, which also has no test). Verified manually in Task 13.

- [ ] **Step 1: Add the ML Kit Text Recognition dependency**

Modify `app/build.gradle.kts` — in the `dependencies` block, add this line right after `implementation("com.google.mlkit:barcode-scanning:17.3.0")`:

```kotlin
    implementation("com.google.mlkit:text-recognition:16.0.1")
```

- [ ] **Step 2: Sync Gradle to confirm the dependency resolves**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep text-recognition`
Expected: shows `com.google.mlkit:text-recognition:16.0.1` resolved with no errors

- [ ] **Step 3: Write `ReceiptTextRecognizer.kt`**

```kotlin
package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * OCR on-device con ML Kit (gratis, offline, sin backend propio -- spec
 * "Flujo de captura y parseo"). Devuelve el texto detectado, una entrada
 * por línea, en el orden en que ML Kit las reporta.
 */
suspend fun recognizeReceiptTextLines(context: Context, imageUri: Uri): List<String> {
    val image = InputImage.fromFilePath(context, imageUri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val result = recognizer.process(image).await()
    return result.textBlocks.flatMap { block -> block.lines.map { line -> line.text } }
}
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/com/homepantry/app/data/ReceiptTextRecognizer.kt
git commit -m "feat: add on-device OCR wrapper using ML Kit Text Recognition"
```

---

### Task 8: Wire purchases into `AppViewModel`/`UiState` and `MainActivity`

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/ui/UiStateTest.kt`

**Interfaces:**
- Consumes: `Purchase`, `ParsedReceiptLine`, `normalizeProductName` (Tasks 1-2), `productSummaries` (Task 3), `PurchasesRepository` (Task 4), `StorageRepository.uploadReceiptPhoto` (Task 5).
- Produces: `UiState.purchases: List<Purchase>`, `UiState.purchaseSummaries: List<ProductSummary>` (computed), `AppViewModel.savePurchaseBatch(lines: List<ParsedReceiptLine>, ticketPhotoLocalUri: Uri?)` — used by Task 9 (`ReceiptReviewSheet`) and Tasks 10-11 (screens read `state.purchaseSummaries`).

- [ ] **Step 1: Write the failing test for the new computed property**

Add to `app/src/test/kotlin/com/homepantry/app/ui/UiStateTest.kt` (new test, keep existing ones):

```kotlin
    @Test fun `purchaseSummaries exposes productSummaries computed from purchases`() {
        val state = UiState(
            purchases = listOf(
                com.homepantry.app.data.Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.5),
                com.homepantry.app.data.Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.2)
            )
        )
        assertEquals(1, state.purchaseSummaries.size)
        assertEquals(2.7, state.purchaseSummaries.single().allTimeTotal, 0.001)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.ui.UiStateTest"`
Expected: FAIL (compilation error — `UiState` has no `purchases` parameter, no `purchaseSummaries` property)

- [ ] **Step 3: Extend `UiState` and `AppViewModel`**

Modify `app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt`:

Add imports (alongside the existing `com.homepantry.app.data.*` imports):
```kotlin
import com.homepantry.app.data.ParsedReceiptLine
import com.homepantry.app.data.ProductSummary
import com.homepantry.app.data.Purchase
import com.homepantry.app.data.PurchasesRepository
import com.homepantry.app.data.normalizeProductName
import com.homepantry.app.data.productSummaries
```

Add a field to `UiState`'s constructor (after `val members: List<Member> = emptyList()`):
```kotlin
    val members: List<Member> = emptyList(),
    val purchases: List<Purchase> = emptyList()
```

Add a computed property inside `UiState` (alongside `val progress: String get() = ...`):
```kotlin
    val purchaseSummaries: List<ProductSummary> get() = productSummaries(purchases)
```

Add a constructor parameter to `AppViewModel` (after `private val membersRepository: MembersRepository,`):
```kotlin
    private val membersRepository: MembersRepository,
    private val purchasesRepository: PurchasesRepository,
```

Add an observer in `init` (alongside the existing zone/member observers):
```kotlin
        viewModelScope.launch {
            runCatching {
                purchasesRepository.observePurchases().collect { purchases ->
                    _state.value = _state.value.copy(purchases = purchases)
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.message)
            }
        }
```

Add a method (alongside `createItem`/`editItem`):
```kotlin
    /**
     * Guarda todas las líneas confirmadas de un ticket escaneado como
     * Purchase independientes, subiendo la foto del ticket una vez y
     * enlazándola desde cada línea (spec "Flujo de captura y parseo").
     */
    fun savePurchaseBatch(lines: List<ParsedReceiptLine>, ticketPhotoLocalUri: android.net.Uri?) = viewModelScope.launch {
        if (lines.isEmpty()) return@launch
        runCatching {
            val batchId = java.util.UUID.randomUUID().toString()
            val photoUrl = ticketPhotoLocalUri?.let { uri -> storageRepository.uploadReceiptPhoto(batchId, uri) }
            val now = java.util.Date()
            val purchases = lines.map { line ->
                Purchase(
                    rawName = line.name,
                    normalizedName = normalizeProductName(line.name),
                    price = line.price,
                    date = now,
                    addedBy = userName,
                    ticketPhotoUrl = photoUrl
                )
            }
            purchasesRepository.addPurchases(purchases)
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
```

- [ ] **Step 4: Wire `PurchasesRepository` into the `AppViewModel` factory**

Modify `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`:

Add the import:
```kotlin
import com.homepantry.app.data.PurchasesRepository
```

In the `AppViewModel(...)` construction inside `ListaDeLaCasaApp()`'s factory, add the new argument (after `membersRepository = MembersRepository(firestore, code),`):
```kotlin
                        membersRepository = MembersRepository(firestore, code),
                        purchasesRepository = PurchasesRepository(firestore, code),
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.ui.UiStateTest"`
Expected: PASS (all `UiStateTest` tests, including the new one)

- [ ] **Step 6: Verify the whole module still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt app/src/main/kotlin/com/homepantry/app/MainActivity.kt app/src/test/kotlin/com/homepantry/app/ui/UiStateTest.kt
git commit -m "feat: wire purchase history into AppViewModel and UiState"
```

---

### Task 9: `ReceiptReviewSheet` (editable line review before saving)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/ReceiptReviewSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ParsedReceiptLine` (Task 2), `AppViewModel.savePurchaseBatch` (Task 8).
- Produces: `@Composable fun ReceiptReviewSheet(viewModel: AppViewModel, initialLines: List<ParsedReceiptLine>, ticketPhotoUri: Uri?, onDismiss: () -> Unit)` — used by Task 10 (`PurchaseHistoryScreen`).

No automated UI test: this codebase has no `androidTest`/Compose UI test setup (checked — only JUnit unit tests under `app/src/test`). Verified manually in Task 13, same as `AddItemSheet`.

- [ ] **Step 1: Add the new strings**

Modify `app/src/main/res/values/strings.xml` — add this block right before the closing `</resources>`:

```xml
    <!-- ReceiptReviewSheet -->
    <string name="receipt_review_title">Revisar ticket</string>
    <string name="receipt_review_name">Producto</string>
    <string name="receipt_review_price">Precio</string>
    <string name="receipt_review_delete_line_cd">Eliminar línea</string>
    <string name="receipt_review_add_line">+ Añadir línea</string>
    <string name="receipt_review_empty">No quedan líneas. Añade una manualmente o cierra sin guardar.</string>
    <string name="receipt_review_save">Guardar compras</string>
```

- [ ] **Step 2: Write `ReceiptReviewSheet.kt`**

```kotlin
package com.homepantry.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.ParsedReceiptLine
import com.homepantry.app.ui.AppViewModel
import java.util.Locale

private data class EditableLine(val name: String, val priceText: String)

private fun formatPrice(price: Double): String =
    String.format(Locale("es", "ES"), "%.2f", price)

/**
 * Lista editable de las líneas detectadas por OCR antes de guardarlas.
 * Nada se escribe en Firestore hasta que el usuario pulsa "Guardar
 * compras" (spec "Alcance": revisión obligatoria, sin guardado silencioso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewSheet(
    viewModel: AppViewModel,
    initialLines: List<ParsedReceiptLine>,
    ticketPhotoUri: Uri?,
    onDismiss: () -> Unit
) {
    val lines = remember(initialLines) {
        mutableStateListOf(*initialLines.map { EditableLine(it.name, formatPrice(it.price)) }.toTypedArray())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            item {
                Text(stringResource(R.string.receipt_review_title), style = MaterialTheme.typography.titleLarge)
            }
            itemsIndexed(lines) { index, line ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = line.name,
                        onValueChange = { newValue -> lines[index] = line.copy(name = newValue) },
                        label = { Text(stringResource(R.string.receipt_review_name)) },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = line.priceText,
                        onValueChange = { newValue -> lines[index] = line.copy(priceText = newValue) },
                        label = { Text(stringResource(R.string.receipt_review_price)) },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { lines.removeAt(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.receipt_review_delete_line_cd))
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { lines.add(EditableLine("", "")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(stringResource(R.string.receipt_review_add_line)) }
            }
            if (lines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.receipt_review_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val parsed = lines.mapNotNull { line ->
                            val price = line.priceText.replace(",", ".").toDoubleOrNull()
                            if (line.name.isBlank() || price == null) null
                            else ParsedReceiptLine(name = line.name.trim(), price = price)
                        }
                        viewModel.savePurchaseBatch(parsed, ticketPhotoUri)
                        onDismiss()
                    },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
                ) { Text(stringResource(R.string.receipt_review_save)) }
            }
        }
    }
}
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/ReceiptReviewSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat: add ReceiptReviewSheet for editing scanned lines before saving"
```

---

### Task 10: `PurchaseHistoryScreen` (list + scan entry point)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `state.purchaseSummaries` (Task 8), `createReceiptCaptureUri` (Task 6), `recognizeReceiptTextLines` (Task 7), `parseReceiptLines` (Task 2), `ReceiptReviewSheet` (Task 9).
- Produces: `@Composable fun PurchaseHistoryScreen(viewModel: AppViewModel, onBack: () -> Unit, onOpenProduct: (String) -> Unit)` — used by Task 12 (`MainActivity` navigation).

No automated UI test (same reasoning as Task 9). Verified manually in Task 13.

- [ ] **Step 1: Add the new strings**

Modify `app/src/main/res/values/strings.xml` — add this block right before the closing `</resources>`:

```xml
    <!-- PurchaseHistoryScreen -->
    <string name="purchase_history_title">Historial de compras</string>
    <string name="purchase_history_scan_cd">Escanear ticket</string>
    <string name="purchase_history_empty">Aún no hay compras. Toca el botón de la cámara para escanear un ticket.</string>
    <string name="purchase_history_ocr_empty">No se ha detectado texto en el ticket. Prueba con mejor luz o añade productos a mano.</string>
    <string name="purchase_history_summary">Este mes: %1$.2f € · Total: %2$.2f €</string>
```

- [ ] **Step 2: Write `PurchaseHistoryScreen.kt`**

```kotlin
package com.homepantry.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.homepantry.app.R
import com.homepantry.app.data.ParsedReceiptLine
import com.homepantry.app.data.createReceiptCaptureUri
import com.homepantry.app.data.parseReceiptLines
import com.homepantry.app.data.recognizeReceiptTextLines
import com.homepantry.app.ui.AppViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla "Historial de compras": lista de productos agrupados (spec
 * "Histórico y gasto mensual") con acceso al escaneo de ticket.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PurchaseHistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var reviewLines by remember { mutableStateOf<List<ParsedReceiptLine>?>(null) }
    var reviewPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var processingOcr by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            processingOcr = true
            scope.launch {
                val textLines = runCatching { recognizeReceiptTextLines(context, uri) }.getOrDefault(emptyList())
                val parsed = parseReceiptLines(textLines)
                processingOcr = false
                if (parsed.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.purchase_history_ocr_empty)) }
                }
                reviewLines = parsed
                reviewPhotoUri = uri
            }
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchase_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (!cameraPermissionState.status.isGranted) {
                    cameraPermissionState.launchPermissionRequest()
                } else {
                    val uri = createReceiptCaptureUri(context)
                    pendingCaptureUri = uri
                    takePicture.launch(uri)
                }
            }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.purchase_history_scan_cd))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        when {
            processingOcr -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.purchaseSummaries.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.purchase_history_empty))
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(state.purchaseSummaries, key = { it.normalizedName }) { summary ->
                        ListItem(
                            headlineContent = { Text(summary.displayName) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.purchase_history_summary,
                                        summary.currentMonthTotal,
                                        summary.allTimeTotal
                                    )
                                )
                            },
                            modifier = Modifier.clickable { onOpenProduct(summary.normalizedName) }
                        )
                    }
                }
            }
        }
    }

    val lines = reviewLines
    if (lines != null) {
        ReceiptReviewSheet(
            viewModel = viewModel,
            initialLines = lines,
            ticketPhotoUri = reviewPhotoUri,
            onDismiss = {
                reviewLines = null
                reviewPhotoUri = null
            }
        )
    }
}
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: add PurchaseHistoryScreen with receipt scan entry point"
```

---

### Task 11: `PurchaseDetailScreen` (per-product monthly breakdown)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseDetailScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `state.purchaseSummaries` (Task 8), `monthlySpend` (Task 3).
- Produces: `@Composable fun PurchaseDetailScreen(viewModel: AppViewModel, normalizedName: String, onBack: () -> Unit)` — used by Task 12 (`MainActivity` navigation).

No automated UI test (same reasoning as Task 9). Verified manually in Task 13.

- [ ] **Step 1: Add the new strings**

Modify `app/src/main/res/values/strings.xml` — add this block right before the closing `</resources>`:

```xml
    <!-- PurchaseDetailScreen -->
    <string name="purchase_detail_not_found">No se encontró el producto</string>
    <string name="purchase_detail_monthly_title">Gasto por mes</string>
    <string name="purchase_detail_history_title">Historial de compras</string>
    <string name="purchase_detail_amount">%1$.2f €</string>
```

- [ ] **Step 2: Write `PurchaseDetailScreen.kt`**

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.monthlySpend
import com.homepantry.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Detalle de un producto del histórico: gasto por mes + lista de compras. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    viewModel: AppViewModel,
    normalizedName: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val summary = remember(state.purchaseSummaries, normalizedName) {
        state.purchaseSummaries.firstOrNull { it.normalizedName == normalizedName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.displayName ?: normalizedName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.purchase_detail_not_found))
            }
        } else {
            val monthly = remember(summary) { monthlySpend(summary.purchases) }
            val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        stringResource(R.string.purchase_detail_monthly_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(monthly, key = { it.yearMonth }) { month ->
                    ListItem(
                        headlineContent = { Text(month.yearMonth) },
                        trailingContent = { Text(stringResource(R.string.purchase_detail_amount, month.total)) }
                    )
                }
                item {
                    Text(
                        stringResource(R.string.purchase_detail_history_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(summary.purchases, key = { it.id }) { purchase ->
                    ListItem(
                        headlineContent = { Text(dateFormat.format(purchase.date)) },
                        trailingContent = { Text(stringResource(R.string.purchase_detail_amount, purchase.price)) }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseDetailScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: add PurchaseDetailScreen with per-month spend breakdown"
```

---

### Task 12: Navigation wiring (routes + entry icon)

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/ZonesDashboardScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PurchaseHistoryScreen` (Task 10), `PurchaseDetailScreen` (Task 11).
- Produces: reachable routes `"purchaseHistory"` and `"purchaseDetail/{normalizedName}"`.

No automated test: pure navigation wiring, verified manually in Task 13.

- [ ] **Step 1: Add the new string**

Modify `app/src/main/res/values/strings.xml` — add this line inside the `<!-- ZonesDashboardScreen -->` block (after `dashboard_new_zone`):

```xml
    <string name="dashboard_purchase_history_cd">Historial de compras</string>
```

- [ ] **Step 2: Add the entry icon to `ZonesDashboardScreen`**

Modify `app/src/main/kotlin/com/homepantry/app/ui/screens/ZonesDashboardScreen.kt`:

Add the import:
```kotlin
import androidx.compose.material.icons.filled.Receipt
```

Add a parameter to the function signature (after `onManageZones: () -> Unit,`):
```kotlin
fun ZonesDashboardScreen(
    viewModel: AppViewModel,
    userName: String,
    onManageZones: () -> Unit,
    onOpenPurchaseHistory: () -> Unit,
    onOpenZone: (String) -> Unit
) {
```

Add a second `IconButton` in the `TopAppBar`'s `actions`, before the existing `Settings` one:
```kotlin
                actions = {
                    IconButton(onClick = onOpenPurchaseHistory) {
                        Icon(Icons.Filled.Receipt, contentDescription = stringResource(R.string.dashboard_purchase_history_cd))
                    }
                    IconButton(onClick = onManageZones) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_cd))
                    }
                }
```

- [ ] **Step 3: Wire the routes and the new callback in `MainActivity`**

Modify `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`:

Add the imports:
```kotlin
import android.net.Uri
import com.homepantry.app.ui.screens.PurchaseDetailScreen
import com.homepantry.app.ui.screens.PurchaseHistoryScreen
```

In the `composable("zonesDashboard") { ... }` block, add the new callback argument:
```kotlin
            composable("zonesDashboard") {
                ZonesDashboardScreen(
                    viewModel = viewModel,
                    userName = name,
                    onManageZones = { navController.navigate("manageZones") },
                    onOpenPurchaseHistory = { navController.navigate("purchaseHistory") },
                    onOpenZone = { zoneId -> navController.navigate("zoneDetail/$zoneId") }
                )
            }
```

Add two new routes after the `composable("manageZones") { ... }` block:
```kotlin
            composable("purchaseHistory") {
                PurchaseHistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { normalizedName ->
                        navController.navigate("purchaseDetail/${Uri.encode(normalizedName)}")
                    }
                )
            }
            composable("purchaseDetail/{normalizedName}") { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("normalizedName") ?: return@composable
                PurchaseDetailScreen(
                    viewModel = viewModel,
                    normalizedName = Uri.decode(encodedName),
                    onBack = { navController.popBackStack() }
                )
            }
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/MainActivity.kt app/src/main/kotlin/com/homepantry/app/ui/screens/ZonesDashboardScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: wire purchase history navigation into the app"
```

---

### Task 13: Full build, unit tests, and manual on-device verification

**Files:** none (verification only)

**Interfaces:** none — this task exercises everything built in Tasks 1-12.

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (including the new `ProductNormalizationTest`, `ReceiptParsingTest`, `PurchaseAggregationTest`, and the updated `UiStateTest`)

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Install and manually verify on a device/emulator**

Run: `./gradlew installDebug`

Then, in the running app:
1. Open Almacén → tap the new receipt icon in the top bar → confirm "Historial de compras" opens empty with the expected empty-state message.
2. Tap the camera FAB → grant camera permission if prompted → take a photo of a real receipt (good lighting).
3. Confirm the review sheet opens with detected name/price lines; edit one line's name and price, delete one line, add one manual line.
4. Tap "Guardar compras" → confirm the sheet closes and the product list now shows the saved products with month/total spend.
5. Tap a product → confirm the detail screen shows the monthly breakdown and the individual purchase with the right date/price.
6. Repeat step 2-4 with a second, blurry or poorly-lit photo → confirm the "no text detected" message appears and you can still add lines manually via "+ Añadir línea" in the (empty) review sheet.
7. Kill and reopen the app → confirm the saved purchases and their totals are still there (Firestore persistence).

Expected: all seven checks pass with no crashes and no silent failures (errors, if any, must surface as a snackbar per the existing `state.error` pattern).

- [ ] **Step 4: Commit any fixes found during manual verification**

If manual verification surfaces a bug, fix it in the relevant file from Tasks 1-12, re-run `./gradlew testDebugUnitTest assembleDebug`, then:

```bash
git add -A
git commit -m "fix: address issues found during manual verification of receipt scanning"
```

If no issues are found, skip this step — there is nothing to commit.
