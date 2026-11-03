# Productos normalizados y precio por kilo — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada compra lleva un nombre genérico (tipo + variedad) y el tamaño del envase, y el historial y las estadísticas agrupan por ese nombre y comparan en €/kg, €/l o €/ud.

**Architecture:** Lógica pura nueva en `data/` (extracción del tamaño, precio comparable, alias, filas de normalización, unir), con tests JUnit. `Purchase` gana `productName`, `packSize` y `packUnit`, y una colección nueva `productAliases` recuerda las correcciones. Gemini propone el nombre y el envase al leer tickets y, en una petición de solo texto, para el histórico.

**Tech Stack:** Kotlin, Jetpack Compose M3, Firestore, Retrofit + Gson (Gemini `v1beta/interactions`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-03-productos-normalizados-precio-kilo-design.md` (léelo antes de empezar).

## Global Constraints

- Lógica testeable en ficheros puros de `data/` sin dependencias de Android; tests en `app/src/test/kotlin/com/homepantry/app/data/`.
- Modelos de Firestore: `data class` en `data/` con valores por defecto en todos los campos, `@DocumentId` y `@ServerTimestamp`.
- Cambiar campos con `update(campo, valor)`, no con `set` del objeto entero; operaciones masivas en batches de 500 como máximo.
- `CancellationException` se relanza siempre.
- Textos visibles en `res/values/strings.xml` (con `plurals` para "N compras"); comentarios y KDoc en español.
- Pantallas nuevas del área Gastos: sin barra inferior y con flecha atrás.
- Nunca reescribir compras antiguas sin confirmación del usuario. Nunca perder `rawName`.
- Un fallo o la falta de clave de Gemini no puede impedir guardar un ticket.
- No mezclar €/kg, €/l y €/ud en una misma comparativa.
- Commits en español con prefijo convencional y la línea `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Verificación de cierre: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease` (0 fallos, 0 errores de lint).

## Review Focus

1. **Multipacks y formatos raros en el nombre** ("LECHE ENTERA 6X1L", "COCA COLA 33CL", "ACEITE 1,5 L", "ARANDANO 300GR BANDEJA"): el envase debe ser el total de una unidad comprada (6 l, 0,33 l, 1,5 l, 0,3 kg). → tests en la Task 1.
2. **Líneas pesadas** (unit KG o L): el precio comparable sale de `quantity`, y un `packSize` que sobre se ignora. → test en la Task 2.
3. **Compras antiguas sin `productName`**: se siguen mostrando y agrupando por `rawName`, como hoy. → test en la Task 2.
4. **Respuesta de Gemini incompleta o rara** (packUnit fuera de KG/L, packSize 0, productName vacío, nombres que no se pidieron o que faltan): la línea se queda con el respaldo por reglas y nada lanza. → tests en las Tasks 4 y 8.
5. **Nombre impreso que es solo un tamaño o que lleva "/"** ("500G", "PAN 1/2 KG"): la clave del alias tiene que ser un id de Firestore válido o no hay alias. → test en la Task 1.

---

## File Structure

| Fichero | Responsabilidad |
|---|---|
| `data/PackSizeParsing.kt` (nuevo) | `PackSize`, `splitPackSize`, `aliasKey`, `toPackSize(text, displayUnit)` |
| `data/Purchase.kt` | campos nuevos |
| `data/ProductAlias.kt` (nuevo) | modelo de Firestore del alias |
| `data/ProductAliasLogic.kt` (nuevo) | `ProductProposal`, `applyAlias`, `aliasForCorrection` |
| `data/ComparablePrice.kt` (nuevo) | `displayName()`, `measure()`, `comparablePrice()`, `productKey()` |
| `data/PurchaseAggregation.kt`, `data/PurchaseStats.kt` | agrupar y comparar con el precio comparable |
| `data/ProductAliasesRepository.kt` (nuevo) | observar y guardar alias |
| `data/PurchasesRepository.kt` | actualizar en bloque los campos de producto |
| `data/GeminiReceiptApi.kt`, `data/GeminiReceiptRecognizer.kt` | campos nuevos en el esquema, el prompt y el mapeo |
| `data/ReceiptParsing.kt` | `ParsedReceiptLine` con los campos nuevos |
| `data/ReceiptScanDraft.kt` | borrador con nombre genérico, envase y lo propuesto |
| `data/ProductNormalizationLogic.kt` (nuevo) | filas de revisión del histórico y el plan de "Unir" |
| `data/GeminiProductNamer.kt` (nuevo) | petición de solo texto para el histórico |
| `ui/ProductNormalizationViewModel.kt` (nuevo), `ui/screens/ProductNormalizationScreen.kt` (nuevo) | flujo 2 |
| `ui/AppViewModel.kt`, `ui/ReceiptScanViewModel.kt`, `MainActivity.kt` | cableado |
| `ui/screens/ReceiptReviewSheet.kt`, `PurchaseTicketDetailScreen.kt`, `PurchaseDetailScreen.kt`, `PurchaseProductsScreen.kt`, `PurchaseStatsScreen.kt`, `PurchaseHistoryScreen.kt` | UI |

Las rutas son relativas a `app/src/main/kotlin/com/homepantry/app/`.

---

### Task 1: Extraer el tamaño del nombre y clave del alias

**Files:**
- Create: `data/PackSizeParsing.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/PackSizeParsingTest.kt`

**Interfaces:**
- Produces:
  - `data class PackSize(val amount: Double, val unit: String)`: `unit` es `"KG"` o `"L"` y `amount` va en esa unidad (300 g → `PackSize(0.3, "KG")`).
  - `data class SplitName(val name: String, val pack: PackSize?)`
  - `fun splitPackSize(rawName: String): SplitName`
  - `fun aliasKey(rawName: String): String?`: `normalizeProductName(splitPackSize(rawName).name)` con `/` y `.` cambiados por `_`. Null si queda vacía.
  - `fun toPackSize(amountText: String, displayUnit: String): PackSize?`: `displayUnit` es `"G"`, `"KG"`, `"ML"` o `"L"`. Acepta coma decimal y devuelve null si no es un número > 0.
  - `fun packDisplay(pack: PackSize): Pair<String, String>`: texto y unidad de pantalla. Por debajo de 1 kg o 1 l va en G/ML ("300","G"); a partir de ahí, en KG/L ("1,5","L").

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `grams at the end`() = assertEquals(SplitName("ARANDANO", PackSize(0.3, "KG")), splitPackSize("ARANDANO 300GR"))
@Test fun `grams with space and lowercase`() = assertEquals(SplitName("Arándano", PackSize(0.15, "KG")), splitPackSize("Arándano 150 g"))
@Test fun `size in the middle`() = assertEquals(SplitName("ARANDANO BANDEJA", PackSize(0.3, "KG")), splitPackSize("ARANDANO 300GR BANDEJA"))
@Test fun `decimal comma litres`() = assertEquals(SplitName("ACEITE", PackSize(1.5, "L")), splitPackSize("ACEITE 1,5 L"))
@Test fun `centilitres and millilitres`() {
    assertEquals(PackSize(0.33, "L"), splitPackSize("COCA COLA 33CL").pack)
    assertEquals(PackSize(0.5, "L"), splitPackSize("ZUMO 500ML").pack)
}
@Test fun `kilos`() = assertEquals(PackSize(1.0, "KG"), splitPackSize("HARINA 1KG").pack)
@Test fun `multipack multiplies`() = assertEquals(SplitName("LECHE ENTERA", PackSize(6.0, "L")), splitPackSize("LECHE ENTERA 6X1L"))
@Test fun `no size leaves the name`() = assertEquals(SplitName("ARAND. CULTIVADO", null), splitPackSize("ARAND. CULTIVADO"))
@Test fun `number without unit is not a size`() = assertEquals(SplitName("PILAS AA 4", null), splitPackSize("PILAS AA 4"))
@Test fun `alias key ignores size`() = assertEquals(aliasKey("ARANDANO 300GR"), aliasKey("Arándanos 150 g"))
@Test fun `alias key is a valid document id`() {
    assertEquals(false, aliasKey("PAN 1/2 BARRA")!!.contains("/"))
    assertNull(aliasKey("500G"))
}
@Test fun `user pack text`() {
    assertEquals(PackSize(0.3, "KG"), toPackSize("300", "G"))
    assertEquals(PackSize(1.5, "L"), toPackSize("1,5", "L"))
    assertNull(toPackSize("0", "G"))
}
@Test fun `pack display`() {
    assertEquals("300" to "G", packDisplay(PackSize(0.3, "KG")))
    assertEquals("1,5" to "L", packDisplay(PackSize(1.5, "L")))
}
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PackSizeParsingTest"` → FAIL (unresolved references)
- [ ] **Step 3: Implement** the four functions in `data/PackSizeParsing.kt`. Usa un único regex, sin distinguir mayúsculas, sobre tokens delimitados por palabra: `(?:(\d+)\s*[xX]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|kgs|g|gr|grs|l|lt|cl|ml)\b`. Quita la primera coincidencia y colapsa los espacios. El multiplicador es opcional. Redondea `amount` a 3 decimales para que los `equals` de los tests sean exactos.
- [ ] **Step 4: Run** the same command → PASS
- [ ] **Step 5: Commit** `feat: separar el tamaño del envase del nombre impreso`

---

### Task 2: Campos nuevos de `Purchase` y precio comparable

**Files:**
- Modify: `data/Purchase.kt`, `data/PurchaseAggregation.kt`, `data/PurchaseStats.kt`
- Create: `data/ComparablePrice.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/ComparablePriceTest.kt`, y ajustes en `PurchaseAggregationTest.kt` y `PurchaseStatsTest.kt`

**Interfaces:**
- Consumes: `PackSize` (Task 1)
- Produces:
  - `Purchase.productName: String = ""`, `Purchase.packSize: Double? = null`, `Purchase.packUnit: String? = null` (KDoc tomado del spec).
  - `fun Purchase.displayName(): String` = `productName.ifBlank { rawName }`
  - `fun productKey(productName: String, rawName: String): String` = `normalizeProductName(productName.ifBlank { rawName })`
  - `data class Measure(val amount: Double, val unit: String)` y `fun Purchase.measure(): Measure`, con las reglas del spec ("Precio comparable").
  - `fun Purchase.comparablePrice(): Double`: una cantidad no positiva cuenta como 1.
  - `ProductSummary` gana `latestUnit` con el significado de "unidad de comparación dominante": la que tiene más compras y, en empate, la de la compra más reciente. `latestUnitPrice` pasa a ser el `comparablePrice()` de la compra más reciente en esa unidad.
  - `productSummaries`, `topProducts`, `priceRises`, `cheapestStores` y `storeSections` usan `displayName()` en lugar de `rawName`. `priceRises` y `cheapestStores` agrupan por `normalizedName to measure().unit` y usan `comparablePrice()`. `PriceRise.unit` y `StoreComparison.unit` son la unidad de comparación.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ComparablePriceTest
private fun p(price: Double, qty: Double = 1.0, unit: String = "UD", pack: Double? = null, packUnit: String? = null,
              raw: String = "ARANDANO 300GR", product: String = "") =
    Purchase(rawName = raw, productName = product, price = price, quantity = qty, unit = unit, packSize = pack, packUnit = packUnit)

@Test fun `pack gives price per kilo`() {
    assertEquals(Measure(0.3, "KG"), p(2.5, pack = 0.3, packUnit = "KG").measure())
    assertEquals(8.333, p(2.5, pack = 0.3, packUnit = "KG").comparablePrice(), 0.001)
}
@Test fun `two packs`() = assertEquals(10.0 / 1.2, p(5.0, qty = 2.0, pack = 0.6, packUnit = "KG").comparablePrice(), 0.001)
@Test fun `weighed line ignores pack`() = assertEquals(Measure(0.85, "KG"), p(3.39, qty = 0.85, unit = "KG", pack = 0.3, packUnit = "KG").measure())
@Test fun `no pack is per unit`() = assertEquals(Measure(1.0, "UD"), p(1.2).measure())
@Test fun `old purchase displays raw name`() {
    assertEquals("ARANDANO 300GR", p(1.0).displayName())
    assertEquals("Arándanos", p(1.0, product = "Arándanos").displayName())
}
// PurchaseStatsTest
@Test fun `cheapest store compares packs of different size per kilo`() {
    // Mercadona 300 g a 2,50 (8,33 €/kg) frente a Lidl 150 g a 1,60 (10,67 €/kg): Mercadona es más barato
}
@Test fun `price rises never mixes per kilo and per unit`() {
    // Mismo normalizedName: una compra UD sin envase a 1,00 y otra con envase de 0,3 kg a 2,50 → no hay subida
}
// PurchaseAggregationTest
@Test fun `summary header uses the dominant comparable unit`() {
    // 2 compras con envase en KG y 1 antigua en UD (más reciente) → latestUnit "KG", y el precio es el de la compra KG más reciente
}
```

Escribe con `Purchase` reales las aserciones que los comentarios describen.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.*"` → FAIL
- [ ] **Step 3: Implement** los campos, `ComparablePrice.kt` y los cambios en las agregaciones. Borra `Purchase.unitPrice()` cuando ya no lo use nadie. Los usos en `PurchaseDetailScreen` y `PurchaseTicketDetailScreen` pasan a `comparablePrice()` + `unitSuffix(measure().unit)`, y `PurchaseStatsScreen` muestra `unitSuffix(unit)` de los resultados.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug` → PASS (también los tests que ya existían)
- [ ] **Step 5: Commit** `feat: comparar compras por precio por kilo o litro`

---

### Task 3: Alias: modelo, lógica y repositorio

**Files:**
- Create: `data/ProductAlias.kt`, `data/ProductAliasLogic.kt`, `data/ProductAliasesRepository.kt`
- Modify: `data/PurchasesRepository.kt`, `ui/AppViewModel.kt`, `MainActivity.kt` (si hace falta para construir el repositorio)
- Test: `app/src/test/kotlin/com/homepantry/app/data/ProductAliasLogicTest.kt`

**Interfaces:**
- Consumes: `splitPackSize`, `aliasKey`, `PackSize` (Task 1)
- Produces:
  - `data class ProductAlias(@DocumentId val id: String = "", val rawName: String = "", val productName: String = "", val packKnown: Boolean = false, val packSize: Double? = null, val packUnit: String? = null, @ServerTimestamp val updatedAt: Date? = null)`
  - `data class ProductProposal(val productName: String, val pack: PackSize?)`
  - `fun applyAlias(proposal: ProductProposal, alias: ProductAlias?): ProductProposal`: el alias manda en el nombre (si no está vacío), y en el envase solo si `packKnown`.
  - `fun aliasForCorrection(rawName: String, proposed: ProductProposal, final: ProductProposal): ProductAlias?`: null si no ha cambiado nada o si `aliasKey(rawName)` es null. Guarda `packKnown`/`packSize`/`packUnit` solo cuando `splitPackSize(rawName).pack == null` y el envase cambió.
  - `ProductAliasesRepository(firestore, householdCode)` con `observeAliases(): Flow<List<ProductAlias>>` y `suspend fun saveAliases(aliases: List<ProductAlias>)` (`set` por id, en batches de 500). Colección `households/{code}/productAliases`.
  - `PurchasesRepository.updateProductFields(updates: List<ProductFieldsUpdate>)` con `data class ProductFieldsUpdate(val purchaseId: String, val productName: String, val normalizedName: String, val pack: PackSize?, val setPack: Boolean)`. Siempre actualiza `productName` y `normalizedName`, y `packSize`/`packUnit` solo si `setPack`. Batches de 500.
  - `UiState.productAliases: Map<String, ProductAlias>` (clave = id) en `AppViewModel`, y `fun saveAliases(aliases: List<ProductAlias>)`, que ignora los fallos en silencio salvo `CancellationException`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `alias name wins over gemini`() =
    assertEquals(ProductProposal("Arándanos", PackSize(0.3, "KG")),
        applyAlias(ProductProposal("Arándano cultivado", PackSize(0.3, "KG")), ProductAlias(productName = "Arándanos")))
@Test fun `alias pack only when known`() {
    val explicitNone = ProductAlias(productName = "Leche entera", packKnown = true, packSize = null)
    assertEquals(ProductProposal("Leche entera", null), applyAlias(ProductProposal("Leche", PackSize(1.0, "L")), explicitNone))
}
@Test fun `no change no alias`() = assertNull(aliasForCorrection("ARANDANO 300GR", ProductProposal("Arándanos", PackSize(0.3, "KG")), ProductProposal("Arándanos", PackSize(0.3, "KG"))))
@Test fun `name change creates alias without pack when printed name has size`() {
    val alias = aliasForCorrection("ARANDANO 300GR", ProductProposal("Arándano", PackSize(0.3, "KG")), ProductProposal("Arándanos", PackSize(0.25, "KG")))!!
    assertEquals(aliasKey("ARANDANO 300GR"), alias.id); assertEquals("Arándanos", alias.productName); assertEquals(false, alias.packKnown)
}
@Test fun `removing pack on sizeless name is remembered`() {
    val alias = aliasForCorrection("LECHE ENT. HACENDADO", ProductProposal("Leche entera", PackSize(1.0, "L")), ProductProposal("Leche entera", null))!!
    assertEquals(true, alias.packKnown); assertNull(alias.packSize)
}
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ProductAliasLogicTest"` → FAIL
- [ ] **Step 3: Implement** el modelo, la lógica, los dos repositorios y el cableado de `AppViewModel`. Observa los alias igual que el resto de colecciones.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug` → PASS
- [ ] **Step 5: Commit** `feat: recordar las correcciones de nombre de producto por casa`

---

### Task 4: Gemini devuelve nombre genérico y envase al leer el ticket

**Files:**
- Modify: `data/ReceiptParsing.kt`, `data/GeminiReceiptApi.kt`, `data/GeminiReceiptRecognizer.kt`
- Test: `GeminiReceiptRecognizerTest.kt`

**Interfaces:**
- Produces: `ParsedReceiptLine` gana `productName: String = ""` y `pack: PackSize? = null`. `mapGeminiOutputText` los rellena.

- [ ] **Step 1: Write the failing tests** (en `GeminiReceiptRecognizerTest`)

```kotlin
@Test fun `maps product name and pack`() {
    val line = mapGeminiOutputText("""{"products":[{"name":"ARANDANO 300GR","price":2.5,"productName":"Arándanos","packSize":0.3,"packUnit":"KG"}]}""").lines.single()
    assertEquals("Arándanos", line.productName); assertEquals(PackSize(0.3, "KG"), line.pack)
}
@Test fun `invalid pack is dropped`() {
    val line = mapGeminiOutputText("""{"products":[{"name":"X","price":1.0,"productName":"X","packSize":0,"packUnit":"KG"},{"name":"Y","price":1.0,"packSize":1,"packUnit":"UD"}]}""").lines
    assertNull(line[0].pack); assertNull(line[1].pack)
}
@Test fun `missing product name falls back to rules`() {
    val line = mapGeminiOutputText("""{"products":[{"name":"ARANDANO 300GR","price":2.5}]}""").lines.single()
    assertEquals("ARANDANO", line.productName); assertEquals(PackSize(0.3, "KG"), line.pack)
}
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.GeminiReceiptRecognizerTest"` → FAIL
- [ ] **Step 3: Implement.** En `PRODUCTS_JSON_SCHEMA`, añade a `items.properties` `productName` (string), `packSize` (number) y `packUnit` (string, enum `["KG","L"]`). En `GeminiProduct`, los mismos tres campos como nullable. Con un `packSize` ≤ 0 o un `packUnit` fuera de KG/L, `pack` es null. Si falta `productName` (o falta el envase), se completa con `splitPackSize(name)`. Añade esto a `RECEIPT_PROMPT` después del punto `quantity y unit`:

```
    - productName: el nombre genérico del producto en español, tipo y
      variedad, sin marca ni tamaño ni formato, con la primera letra en
      mayúscula ("ARANDANO 300GR" → "Arándanos", "LECHE ENT. HACENDADO 1L"
      → "Leche entera", "LECHE SEMI PASCUAL" → "Leche semidesnatada").
    - packSize y packUnit: el peso o volumen de UNA unidad cuando el nombre
      impreso lo indica, en KG o L ("300GR" → 0.3 KG, "1,5L" → 1.5 L,
      "6X1L" → 6 L); omítelos si no aparece.
```

- [ ] **Step 4: Run** the same command → PASS
- [ ] **Step 5: Commit** `feat: Gemini propone el nombre genérico y el envase de cada línea`

---

### Task 5: Borrador del ticket con nombre genérico, envase y alias

**Files:**
- Modify: `data/ReceiptScanDraft.kt`, `ui/ReceiptScanViewModel.kt`, `ui/screens/PurchaseHistoryScreen.kt` (la llamada a `scanReceipt`)
- Test: `ReceiptScanDraftTest.kt`

**Interfaces:**
- Consumes: `applyAlias`, `aliasForCorrection`, `ProductProposal` (Task 3); `splitPackSize`, `toPackSize`, `packDisplay` (Task 1)
- Produces:
  - `ReceiptDraftLine` gana `productName: String = ""`, `packText: String = ""`, `packUnit: String = "G"` (unidad de pantalla G/KG/ML/L) y `proposedProductName: String = ""`, `proposedPackText: String = ""`, `proposedPackUnit: String = "G"`.
  - `fun receiptScanDraft(parsed: ParsedReceipt, photoUri: String?, notice: String?, aliases: Map<String, ProductAlias> = emptyMap()): ReceiptScanDraft`. Por línea: propuesta = `productName`/`pack` de la línea, o `splitPackSize(name)` si `productName` está vacío (camino OCR); después `applyAlias(propuesta, aliases[aliasKey(name)])`. El resultado va tanto a los campos editables como a los `proposed*`.
  - `toParsedReceipt()` pasa `productName` (o, si está vacío, `splitPackSize(name).name`) y `pack = toPackSize(packText, packUnit)` solo cuando `unit == "UD"`.
  - `fun ReceiptScanDraft.aliasesToSave(): List<ProductAlias>`: `aliasForCorrection` por línea válida, sin repetir id (gana la última).
  - `receiptScanDraftFromJson` rellena con valores por defecto los campos nuevos que lleguen nulos (borradores antiguos en disco).
  - `ReceiptScanViewModel.scanReceipt(..., aliases: Map<String, ProductAlias>)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `ocr line gets name and pack from rules`() {
    val line = receiptScanDraft(ParsedReceipt(null, listOf(ParsedReceiptLine("ARANDANO 300GR", 2.5))), null, null).lines.single()
    assertEquals("ARANDANO", line.productName); assertEquals("300", line.packText); assertEquals("G", line.packUnit)
}
@Test fun `alias applies when building the draft`() {
    val aliases = mapOf(aliasKey("ARAND. CULTIVADO")!! to ProductAlias(id = aliasKey("ARAND. CULTIVADO")!!, productName = "Arándanos"))
    val line = receiptScanDraft(ParsedReceipt(null, listOf(ParsedReceiptLine("ARAND. CULTIVADO", 2.0, productName = "Arándano cultivado"))), null, null, aliases).lines.single()
    assertEquals("Arándanos", line.productName); assertEquals("Arándanos", line.proposedProductName)
}
@Test fun `edited name produces an alias, untouched does not`() {
    val draft = receiptScanDraft(ParsedReceipt(null, listOf(ParsedReceiptLine("ARANDANO 300GR", 2.5), ParsedReceiptLine("PAN", 1.0))), null, null)
    val edited = draft.copy(lines = draft.lines.mapIndexed { i, l -> if (i == 0) l.copy(productName = "Arándanos") else l })
    assertEquals(listOf("Arándanos"), edited.aliasesToSave().map { it.productName })
}
@Test fun `pack is ignored for weighed lines`() {
    val draft = receiptScanDraft(ParsedReceipt(null, listOf(ParsedReceiptLine("PLATANO 300GR", 0.9, quantity = 0.45, unit = "KG"))), null, null)
    assertNull(draft.toParsedReceipt().lines.single().pack)
}
@Test fun `old draft json without new fields loads`() { /* JSON con solo name/qtyText/unit/priceText → productName "" y packUnit "G" */ }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ReceiptScanDraftTest"` → FAIL
- [ ] **Step 3: Implement**, y pasa `state.productAliases` desde `PurchaseHistoryScreen` a `scanReceipt`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug` → PASS
- [ ] **Step 5: Commit** `feat: el borrador del ticket lleva nombre genérico y envase`

---

### Task 6: Guardar con los campos nuevos y revisar en la hoja

**Files:**
- Modify: `ui/AppViewModel.kt` (`savePurchaseBatch`, `writePurchaseBatch`, `updatePurchase`), `ui/ReceiptScanViewModel.kt` (`saveDraft`), `ui/screens/ReceiptReviewSheet.kt`, `ui/screens/PurchaseTicketDetailScreen.kt` (`EditPurchaseDialog`), `res/values/strings.xml`

**Interfaces:**
- Consumes: `ParsedReceiptLine.productName`/`pack` (Task 4), `aliasesToSave()` (Task 5), `saveAliases`, `aliasForCorrection` (Task 3), `productKey` (Task 2)
- Produces: `savePurchaseBatch(receipt: ParsedReceipt, ticketPhotoLocalUri: Uri?, aliases: List<ProductAlias>): Boolean`. Las compras llevan `productName`, `packSize`, `packUnit` y `normalizedName = productKey(productName, rawName)`. Los alias se escriben **después** de que las compras se hayan guardado bien, y un fallo al escribirlos no cambia el resultado.

- [ ] **Step 1: Implement guardar.** `writePurchaseBatch` mapea los campos nuevos. `saveDraft` pasa `draft.aliasesToSave()`. `updatePurchase(purchase, previous: Purchase)` recalcula `normalizedName` con `productKey` y escribe `aliasForCorrection(rawName, propuesta de previous, propuesta de purchase)` si no es null.
- [ ] **Step 2: Implement la hoja de revisión.** En cada línea, el campo principal es "Producto" (`productName`) y debajo va el nombre impreso, en `bodySmall` y no editable. Con unidad UD aparece una fila "Envase" con `packText` + selector G/KG/ML/L; con KG o L no aparece. Strings nuevos: `receipt_review_product`, `receipt_review_pack`, `receipt_review_printed_name`.
- [ ] **Step 3: Implement `EditPurchaseDialog`.** Edita `productName` (rellenado con `displayName()`) en lugar de `rawName`, más el envase como en la hoja. `rawName` no se edita.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug lintDebug` → PASS, 0 errores de lint.
- [ ] **Step 5: Probar en el móvil** (debug): escanea un ticket con arándanos, corrige el nombre y guarda. Escanea otro con el mismo nombre impreso: la corrección ya viene aplicada. El detalle del producto muestra €/kg.
- [ ] **Step 6: Commit** `feat: revisar y guardar el nombre genérico y el envase de cada línea`

---

### Task 7: Pantallas con nombre genérico y precio comparable

**Files:**
- Modify: `ui/screens/PurchaseDetailScreen.kt`, `PurchaseProductsScreen.kt`, `PurchaseStatsScreen.kt`, `PurchaseTicketDetailScreen.kt`

- [ ] **Step 1: Implement.** En todas las listas de compras, título = `displayName()` y precio = `comparablePrice()` con `"€/" + unitSuffix(measure().unit)`. En el detalle del producto, cada compra lleva su propio sufijo y la cabecera usa `latestUnitPrice`/`latestUnit` del `ProductSummary`. En el detalle del ticket, debajo del nombre genérico va el impreso en `bodySmall` cuando no coinciden.
- [ ] **Step 2: Run** `./gradlew assembleDebug lintDebug` → PASS
- [ ] **Step 3: Commit** `feat: mostrar el nombre genérico y el precio por kilo en el historial`

---

### Task 8: Lógica del histórico: filas de revisión y petición a Gemini

**Files:**
- Create: `data/ProductNormalizationLogic.kt`, `data/GeminiProductNamer.kt`
- Test: `ProductNormalizationLogicTest.kt`, `GeminiProductNamerTest.kt`

**Interfaces:**
- Consumes: `aliasKey`, `splitPackSize`, `PackSize` (Task 1); `ProductAlias`, `applyAlias`, `ProductProposal`, `ProductFieldsUpdate`, `aliasForCorrection` (Task 3); `productKey` (Task 2)
- Produces:
  - `data class NormalizationRow(val key: String, val rawName: String, val purchases: List<Purchase>, val proposed: ProductProposal, val final: ProductProposal, val selected: Boolean = true, val fromAlias: Boolean)`
  - `fun pendingRawNames(purchases: List<Purchase>, aliases: Map<String, ProductAlias>): Pair<List<NormalizationRow>, List<String>>`: agrupa las compras con `productName` vacío por `aliasKey(rawName)` (las de clave null van por `normalizeProductName(rawName)`). Las que tienen alias salen ya como filas (`fromAlias = true`) y el resto como lista de nombres impresos (uno por grupo, el más reciente) para pedir a Gemini.
  - `fun rowsFromProposals(purchases: List<Purchase>, rawNames: List<String>, proposals: Map<String, ProductProposal>): List<NormalizationRow>`: un nombre que falta en `proposals` usa `splitPackSize`, y las propuestas de nombres que no se pidieron se ignoran.
  - `fun sortedForReview(rows: List<NormalizationRow>): List<NormalizationRow>`: por `final.productName` sin acentos ni mayúsculas, y después por `rawName`.
  - `fun applyPlan(rows: List<NormalizationRow>): Pair<List<ProductFieldsUpdate>, List<ProductAlias>>`: solo las filas `selected`. Con `setPack = true` cuando la fila tiene envase; los alias, con `aliasForCorrection(rawName, ProductProposal("", null), final)` para que cada fila aplicada quede recordada (el spec pide un alias por fila).
  - `const val NAMER_BATCH_SIZE = 100`
  - `internal fun mapNamerOutput(text: String): Map<String, ProductProposal>` (por `rawName`, sin lanzar con entradas raras; un JSON roto lanza `GeminiResponseException`).
  - `suspend fun nameProductsWithGemini(rawNames: List<String>, apiKey: String, model: String): Map<String, ProductProposal>`: una sola tanda, con `callGemini`, solo texto y salida estructurada. Esquema: `{"products":[{"rawName","productName","packSize","packUnit"}]}`. El prompt repite las reglas de `productName`/`packSize` de la Task 4 y la lista de nombres, uno por línea.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `same printed name in different sizes is one row`() { /* "ARANDANO 300GR" y "ARANDANO 150GR" sin alias → un único nombre para Gemini */ }
@Test fun `aliased names do not go to gemini`() { /* alias de "ARAND. CULTIVADO" → fila fromAlias, lista de Gemini vacía */ }
@Test fun `already normalized purchases are skipped`() { /* productName no vacío → ni fila ni nombre */ }
@Test fun `missing proposal falls back to rules`() { /* rowsFromProposals sin propuesta para "ARANDANO 300GR" → final ProductProposal("ARANDANO", 0,3 KG) */ }
@Test fun `unselected rows are not applied`() { /* 2 filas y 1 desmarcada → updates y alias solo de la marcada */ }
@Test fun `plan keeps pack per purchase line`() { /* fila con 300GR y 150GR: cada compra recibe el envase de su propio rawName (splitPackSize por compra cuando el nombre impreso lleva tamaño; si no, el de la fila) */ }
@Test fun `namer output ignores bad entries`() { /* packUnit "UD" → pack null; entrada sin rawName → descartada */ }
```

`plan keeps pack per purchase line` fija una decisión: el envase de cada compra sale de **su propio** `rawName` cuando lleva tamaño, igual que al importar. Por eso `ProductFieldsUpdate.pack` se calcula por compra, no por fila, y la fila lleva las compras (`purchases`), no solo sus ids.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ProductNormalizationLogicTest" --tests "com.homepantry.app.data.GeminiProductNamerTest"` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** the same → PASS
- [ ] **Step 5: Commit** `feat: lógica para normalizar el histórico de productos`

---

### Task 9: Pantalla de normalización del histórico

**Files:**
- Create: `ui/ProductNormalizationViewModel.kt`, `ui/screens/ProductNormalizationScreen.kt`
- Modify: `MainActivity.kt` (ViewModel a nivel de actividad con clave `"productNormalization-$code"`, como `receiptScanViewModel`; ruta `purchaseNormalize`), `ui/screens/PurchaseHistoryScreen.kt` (acción en la barra superior), `res/values/strings.xml`

**Interfaces:**
- Consumes: Task 8 completa; `AppViewModel.state` (`purchases`, `productAliases`), `PurchasesRepository.updateProductFields`, `AppViewModel.saveAliases`; `GeminiApiKeyStore`
- Produces: `ProductNormalizationViewModel` con `rows: StateFlow<List<NormalizationRow>>`, `loading: StateFlow<Boolean>`, `error: StateFlow<String?>`, `start(purchases, aliases, apiKeyStore)`, `editRow(key, final: ProductProposal)`, `toggleRow(key)`, `apply(save: suspend (List<ProductFieldsUpdate>, List<ProductAlias>) -> Boolean)`. `AppViewModel` gana `suspend fun applyProductUpdates(updates, aliases): Boolean`.

- [ ] **Step 1: Implement el ViewModel.** `start` llama a `pendingRawNames`, pide a Gemini en tandas de `NAMER_BATCH_SIZE` y va acumulando filas. Si una tanda falla, se guarda el mensaje de `GeminiReceiptException` en `error`, se para y las filas ya recibidas se quedan para revisar. Volver a llamar a `start` solo pide los nombres que aún no tienen fila. Sin clave, `error` = string `normalize_needs_key`. `apply` vacía las filas solo si `save` devuelve true.
- [ ] **Step 2: Implement la pantalla.** `TopAppBar` con flecha atrás y título `normalize_title`. Lista de `sortedForReview(rows)`, una fila por elemento: checkbox, "rawName → productName · envase · N compras" (plural `normalize_purchase_count`) y toque para editar el nombre y el envase en un diálogo. Mientras carga, indicador de progreso. Si hay error, se muestra el texto con un botón "Reintentar". Botón inferior "Aplicar" (`normalize_apply`), activo con al menos una fila marcada.
- [ ] **Step 3: Implement la entrada.** En la barra superior de `PurchaseHistoryScreen`, un `IconButton` (icono `AutoFixHigh`, descripción `normalize_title`), visible solo si alguna compra tiene `productName` vacío.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug lintDebug` → PASS
- [ ] **Step 5: Probar en el móvil**: normaliza el histórico real, gira la pantalla durante la carga (no se pierde nada), desmarca una fila y aplica. Comprueba en el historial que los arándanos son un solo producto en €/kg.
- [ ] **Step 6: Commit** `feat: normalizar con revisión los productos del histórico`

---

### Task 10: Unir productos a mano

**Files:**
- Modify: `data/ProductNormalizationLogic.kt`, `ui/screens/PurchaseProductsScreen.kt`, `ui/AppViewModel.kt`, `res/values/strings.xml`
- Test: `ProductNormalizationLogicTest.kt`

**Interfaces:**
- Consumes: `applyProductUpdates` (Task 9), `ProductFieldsUpdate`, `ProductAlias` (Task 3)
- Produces:
  - `fun defaultMergeName(purchases: List<Purchase>, normalizedNames: Set<String>): String`: el `displayName()` más reciente del producto con más compras.
  - `fun mergePlan(purchases: List<Purchase>, normalizedNames: Set<String>, finalName: String, aliases: Map<String, ProductAlias>): Pair<List<ProductFieldsUpdate>, List<ProductAlias>>`: `setPack = false` en todas las compras, y un alias por `aliasKey` distinto con `productName = finalName`, conservando el `packKnown`/`packSize`/`packUnit` del alias que ya hubiera.
  - `AppViewModel.mergeProducts(normalizedNames: Set<String>, finalName: String)`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `merge renames every purchase and keeps packs`() { /* 2 productos, 3 compras → 3 updates con setPack=false y el mismo normalizedName */ }
@Test fun `merge keeps existing alias pack`() { /* alias previo con packKnown=true → el nuevo alias conserva el envase */ }
@Test fun `default name is the most bought product`() { /* 3 compras de A y 1 de B → displayName de A */ }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ProductNormalizationLogicTest"` → FAIL
- [ ] **Step 3: Implement la lógica y `mergeProducts`.**
- [ ] **Step 4: Implement la selección.** En `PurchaseProductsScreen`, una pulsación larga sobre un producto entra en modo selección (por `normalizedName`, así que un producto que sale en dos tiendas queda marcado en las dos). La barra superior muestra el número de seleccionados, un botón para cancelar y "Unir" (`merge_products`) con 2 o más. "Unir" abre un diálogo con el nombre de `defaultMergeName`, editable. Strings: `merge_products`, `merge_products_title`, `merge_products_name`, `merge_products_selected` (plural).
- [ ] **Step 5: Run** `./gradlew testDebugUnitTest assembleDebug lintDebug` → PASS
- [ ] **Step 6: Commit** `feat: unir productos a mano desde el historial`

---

### Task 11: Verificación completa

- [ ] **Step 1: Run** `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease` → 0 fallos, 0 errores de lint, `app-release.apk` de unos 4,5 MB.
- [ ] **Step 2: Probar el APK release en el S25** (R8): escanear un ticket, revisar, normalizar el histórico y unir productos. Comprobar que los alias se leen bien en release (`ProductAlias` está en `data/`, así que lo cubre la regla de ProGuard).
- [ ] **Step 3: Commit** de lo que quede, si hay algo.
