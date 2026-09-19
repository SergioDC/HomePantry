# Historial por supermercado con precio unitario Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el historial de compras muestre el precio unitario (por ud, kg o l) de cada producto y esté agrupado por supermercado.

**Architecture:** `Purchase` gana `quantity`, `unit` y `store`; el precio unitario se calcula (`price / quantity`) y `price` sigue siendo el total de la línea. Gemini lee además supermercado, cantidad y unidad del ticket; la hoja de revisión permite corregirlos. La agrupación por supermercado es lógica pura en `PurchaseAggregation.kt` (`storeSections`), y `PurchaseHistoryScreen` la pinta como secciones.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Firestore, Gemini `v1beta/interactions`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-19-historial-supermercado-subzonas-design.md` (Parte 1). La Parte 2 (subzonas) tiene su propio plan: `2026-09-19-subzonas-una-pantalla.md`; ambos planes son independientes.

## Global Constraints

- `Purchase.price` **sigue siendo el total de la línea**; el precio unitario es `price / quantity` (calculado, no guardado). Cantidad `<= 0` se trata como 1.
- Compras antiguas: `quantity = 1.0`, `unit = "UD"`, `store = null`. Sin migración.
- Unidades válidas de compra: `UD`, `KG`, `L`. Lo desconocido o ausente se sanea a `1.0` / `UD` sin descartar la línea.
- Las reglas de Firestore (`firestore.rules`) no se tocan.
- Supermercado = texto libre por compra. Clave de agrupación: `trim` + minúsculas; se muestra la grafía de la compra más reciente del grupo. Sin supermercado → sección "Sin supermercado", siempre la última.
- Revisión obligatoria: nada se escribe en Firestore hasta pulsar "Guardar compras".
- Texto de interfaz en español, en `app/src/main/res/values/strings.xml`.
- Comandos Gradle: `./gradlew.bat ...` desde la raíz del repo (`c:\Programacion\Proyectos\HomePantry`).
- En `package com.homepantry.app.data` existe un enum `Unit` que tapa a `kotlin.Unit`: usa `Unit.UD.name` para el valor por defecto de unidad y `kotlin.Unit` si necesitas el tipo vacío.
- Cada commit termina con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

## File Structure

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `data/Purchase.kt` | Modelo Firestore de una compra (+3 campos) | 1 |
| `data/PurchaseAggregation.kt` | Lógica pura: precio unitario, secciones por supermercado, formato | 1 |
| `data/ReceiptParsing.kt` | `ParsedReceiptLine` (+cantidad/unidad), `ParsedReceipt`, `RECEIPT_UNITS` | 2 |
| `data/GeminiReceiptApi.kt` | Esquema JSON pedido a Gemini | 2 |
| `data/GeminiReceiptRecognizer.kt` | Prompt, mapeo/saneado de la respuesta | 2 |
| `ui/AppViewModel.kt` | `savePurchaseBatch(ParsedReceipt, ...)`, `UiState.purchaseStoreSections` | 3, 4 |
| `ui/screens/ReceiptReviewSheet.kt` | Hoja de revisión con supermercado, cantidad y unidad | 3 |
| `ui/screens/PurchaseHistoryScreen.kt` | Secciones por supermercado con precio unitario | 3, 4 |
| `ui/screens/PurchaseDetailScreen.kt` | Detalle con precio unitario y supermercado por compra | 4 |
| `res/values/strings.xml` | Textos nuevos | 3, 4 |

Rutas de código bajo `app/src/main/kotlin/com/homepantry/app/`; tests bajo `app/src/test/kotlin/com/homepantry/app/`.

---

### Task 1: Modelo de compra y lógica de agregación

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/Purchase.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/PurchaseAggregation.kt` (sustituir el archivo entero)
- Test: `app/src/test/kotlin/com/homepantry/app/data/PurchaseAggregationTest.kt`

**Interfaces:**
- Produces (las usan las tareas 3 y 4):
  - `Purchase(…, quantity: Double = 1.0, unit: String = Unit.UD.name, store: String? = null)`
  - `fun Purchase.unitPrice(): Double`
  - `fun storeKey(store: String?): String`
  - `fun unitSuffix(unit: String): String` → `"kg"`, `"l"` o `"ud"`
  - `fun formatQuantity(quantity: Double): String` → `"2"`, `"0,85"`
  - `fun knownStores(purchases: List<Purchase>): List<String>`
  - `ProductSummary(normalizedName, displayName, currentMonthTotal, allTimeTotal, latestUnitPrice: Double, latestUnit: String, purchases)`
  - `data class StoreSection(val storeKey: String, val displayName: String, val products: List<ProductSummary>)` (`storeKey`/`displayName` vacíos = sin supermercado)
  - `fun storeSections(purchases: List<Purchase>, referenceDate: Date = Date()): List<StoreSection>`

- [ ] **Step 1: Escribir los tests que fallan**

Añade estos tests dentro de la clase `PurchaseAggregationTest`, antes de la llave de cierre final:

```kotlin
    @Test fun `unitPrice divides the line total by the quantity`() {
        assertEquals(1.25, Purchase(price = 2.5, quantity = 2.0).unitPrice(), 0.001)
        // 0,850 kg por 3,39 EUR -> 3,99 EUR/kg (redondeo del ticket)
        assertEquals(3.99, Purchase(price = 3.39, quantity = 0.85, unit = "KG").unitPrice(), 0.01)
    }

    @Test fun `unitPrice treats a non-positive quantity as one`() {
        assertEquals(2.0, Purchase(price = 2.0, quantity = 0.0).unitPrice(), 0.001)
        assertEquals(2.0, Purchase(price = 2.0, quantity = -3.0).unitPrice(), 0.001)
    }

    @Test fun `an old purchase without the new fields has unit price equal to its total`() {
        val old = Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.5)
        assertEquals(1.0, old.quantity, 0.001)
        assertEquals("UD", old.unit)
        assertEquals(null, old.store)
        assertEquals(1.5, old.unitPrice(), 0.001)
    }

    @Test fun `productSummaries exposes the unit price and unit of the latest purchase`() {
        val purchases = listOf(
            Purchase(rawName = "Leche", normalizedName = "leche", price = 2.0, quantity = 2.0, date = date("2026-03-01")),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 1.1, quantity = 1.0, date = date("2026-03-20"))
        )
        val leche = productSummaries(purchases, referenceDate = date("2026-03-25")).single()
        assertEquals(1.1, leche.latestUnitPrice, 0.001)
        assertEquals("UD", leche.latestUnit)
    }

    @Test fun `storeKey ignores case and surrounding spaces and maps null to empty`() {
        assertEquals("mercadona", storeKey("  MERCADONA "))
        assertEquals("", storeKey(null))
        assertEquals("", storeKey("   "))
    }

    @Test fun `storeSections groups by normalized store, newest store first, and puts no-store last`() {
        val purchases = listOf(
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-01"), store = "Lidl"),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 1.0, date = date("2026-03-10"), store = "MERCADONA"),
            Purchase(rawName = "Pan", normalizedName = "pan", price = 1.2, date = date("2026-03-12"), store = " Mercadona "),
            Purchase(rawName = "Viejo", normalizedName = "viejo", price = 2.0, date = date("2026-04-01"))
        )
        val sections = storeSections(purchases, referenceDate = date("2026-04-02"))

        assertEquals(listOf("mercadona", "lidl", ""), sections.map { it.storeKey })
        // La grafía mostrada es la de la compra más reciente del grupo, recortada.
        assertEquals("Mercadona", sections[0].displayName)
        assertEquals("", sections[2].displayName)
        // Dentro del súper, el producto comprado más recientemente primero.
        assertEquals(listOf("pan", "leche"), sections[0].products.map { it.normalizedName })
    }

    @Test fun `a product bought in two stores appears in both with its own unit price`() {
        val purchases = listOf(
            Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9, date = date("2026-03-01"), store = "Lidl"),
            Purchase(rawName = "Leche", normalizedName = "leche", price = 1.0, date = date("2026-03-10"), store = "Mercadona")
        )
        val sections = storeSections(purchases, referenceDate = date("2026-03-25"))

        assertEquals(1.0, sections.first { it.storeKey == "mercadona" }.products.single().latestUnitPrice, 0.001)
        assertEquals(0.9, sections.first { it.storeKey == "lidl" }.products.single().latestUnitPrice, 0.001)
    }

    @Test fun `storeSections is empty when there are no purchases`() {
        assertEquals(emptyList<StoreSection>(), storeSections(emptyList()))
    }

    @Test fun `knownStores lists each store once, most recent first, ignoring blanks`() {
        val purchases = listOf(
            Purchase(price = 1.0, date = date("2026-03-01"), store = "Lidl"),
            Purchase(price = 1.0, date = date("2026-03-10"), store = "MERCADONA"),
            Purchase(price = 1.0, date = date("2026-03-12"), store = "Mercadona"),
            Purchase(price = 1.0, date = date("2026-03-13"), store = "  "),
            Purchase(price = 1.0, date = date("2026-03-14"))
        )
        assertEquals(listOf("Mercadona", "Lidl"), knownStores(purchases))
    }

    @Test fun `unitSuffix maps the receipt units and falls back to ud`() {
        assertEquals("kg", unitSuffix("KG"))
        assertEquals("l", unitSuffix("L"))
        assertEquals("ud", unitSuffix("UD"))
        assertEquals("ud", unitSuffix("G"))
    }

    @Test fun `formatQuantity drops the decimals of whole numbers and trims trailing zeros`() {
        assertEquals("2", formatQuantity(2.0))
        assertEquals("0,85", formatQuantity(0.85))
        assertEquals("1,5", formatQuantity(1.5))
    }
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.PurchaseAggregationTest"`
Expected: FAIL de compilación, `Unresolved reference: unitPrice` (y `quantity`, `storeSections`, ...).

- [ ] **Step 3: Añadir los campos a `Purchase`**

En `Purchase.kt`, sustituir:

```kotlin
    val addedBy: String? = null,
    val ticketPhotoUrl: String? = null
)
```

por:

```kotlin
    val addedBy: String? = null,
    val ticketPhotoUrl: String? = null,
    /** Cantidad comprada en la línea (en `unit`). `price` sigue siendo el total de la línea. */
    val quantity: Double = 1.0,
    /** Nombre de un valor de [Unit]: UD, KG o L. */
    val unit: String = Unit.UD.name,
    /** Supermercado de la compra, tal y como se muestra. Null = desconocido (compras antiguas). */
    val store: String? = null
)
```

- [ ] **Step 4: Reescribir `PurchaseAggregation.kt`**

Sustituir el archivo entero por:

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
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.PurchaseAggregationTest"`
Expected: PASS (los 3 tests originales y los nuevos).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/Purchase.kt app/src/main/kotlin/com/homepantry/app/data/PurchaseAggregation.kt app/src/test/kotlin/com/homepantry/app/data/PurchaseAggregationTest.kt
git commit -m "feat: add quantity, unit and store to purchases with unit price and per-store grouping"
```

---

### Task 2: Gemini lee supermercado, cantidad y unidad

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/ReceiptParsing.kt:3-4`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptApi.kt:39-56`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptRecognizer.kt` (líneas 34-35, 117-129, 150-158, 245-267)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt:94`
- Test: `app/src/test/kotlin/com/homepantry/app/data/GeminiReceiptRecognizerTest.kt`

**Interfaces:**
- Consumes: nada de la tarea 1.
- Produces (las usan las tareas 3 y 4):
  - `data class ParsedReceiptLine(val name: String, val price: Double, val quantity: Double = 1.0, val unit: String = Unit.UD.name)`
  - `data class ParsedReceipt(val store: String?, val lines: List<ParsedReceiptLine>)`
  - `val RECEIPT_UNITS: List<String>` = `listOf("UD", "KG", "L")`
  - `internal fun mapGeminiOutputText(outputText: String): ParsedReceipt` (sustituye a `mapGeminiOutputTextToLines`)
  - `suspend fun recognizeReceiptWithGemini(context, imageUri, apiKey, model): ParsedReceipt`

- [ ] **Step 1: Actualizar los tests existentes al nuevo nombre y escribir los nuevos**

Renombra la función en los tests existentes (los que comparan listas usan `.lines`):

```bash
sed -i -e 's/mapGeminiOutputTextToLines(json)/mapGeminiOutputText(json).lines/' -e 's/mapGeminiOutputTextToLines(/mapGeminiOutputText(/' app/src/test/kotlin/com/homepantry/app/data/GeminiReceiptRecognizerTest.kt
```

Comprueba con `grep -n "mapGeminiOutputText" app/src/test/kotlin/com/homepantry/app/data/GeminiReceiptRecognizerTest.kt` que quedan 5 usos y ninguno con `ToLines`. Después añade estos tests dentro de la clase, justo debajo de `skips entries with a zero or negative price`:

```kotlin
    @Test fun `reads store, quantity and unit when Gemini provides them`() {
        val json = """{"store":"  Mercadona ","products":[
            {"name":"PLATANO","price":3.39,"quantity":0.85,"unit":"KG"},
            {"name":"YOGUR","price":2.5,"quantity":2,"unit":"UD"}
        ]}"""
        val receipt = mapGeminiOutputText(json)
        assertEquals("Mercadona", receipt.store)
        assertEquals(
            listOf(
                ParsedReceiptLine("PLATANO", 3.39, 0.85, "KG"),
                ParsedReceiptLine("YOGUR", 2.5, 2.0, "UD")
            ),
            receipt.lines
        )
    }

    @Test fun `defaults quantity to one and unit to UD when they are missing or invalid`() {
        val json = """{"products":[
            {"name":"A","price":1.0},
            {"name":"B","price":1.0,"quantity":0,"unit":"CAJAS"},
            {"name":"C","price":1.0,"quantity":-2,"unit":null},
            {"name":"D","price":1.0,"quantity":1.5,"unit":"l"}
        ]}"""
        assertEquals(
            listOf(
                ParsedReceiptLine("A", 1.0, 1.0, "UD"),
                ParsedReceiptLine("B", 1.0, 1.0, "UD"),
                ParsedReceiptLine("C", 1.0, 1.0, "UD"),
                ParsedReceiptLine("D", 1.0, 1.5, "L")
            ),
            mapGeminiOutputText(json).lines
        )
    }

    @Test fun `store is null when Gemini omits it or returns a blank one`() {
        assertNull(mapGeminiOutputText("""{"products":[]}""").store)
        assertNull(mapGeminiOutputText("""{"store":"   ","products":[]}""").store)
        assertNull(mapGeminiOutputText("""{"store":null,"products":[]}""").store)
    }
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.GeminiReceiptRecognizerTest"`
Expected: FAIL de compilación, `Unresolved reference: mapGeminiOutputText`.

- [ ] **Step 3: Tipos de recibo en `ReceiptParsing.kt`**

Sustituir las líneas 3-4:

```kotlin
/** Una línea de ticket ya separada en nombre de producto + precio total. */
data class ParsedReceiptLine(val name: String, val price: Double)
```

por:

```kotlin
/** Unidades que admite una línea de ticket (nombres de [Unit]). */
val RECEIPT_UNITS: List<String> = listOf(Unit.UD.name, Unit.KG.name, Unit.L.name)

/**
 * Una línea de ticket ya separada en nombre de producto + precio total de la
 * línea, con la cantidad comprada (1 ud si el ticket no la indica).
 */
data class ParsedReceiptLine(
    val name: String,
    val price: Double,
    val quantity: Double = 1.0,
    val unit: String = Unit.UD.name
)

/** Resultado de leer un ticket: supermercado (si se detectó) y sus líneas. */
data class ParsedReceipt(val store: String?, val lines: List<ParsedReceiptLine>)
```

- [ ] **Step 4: Esquema JSON en `GeminiReceiptApi.kt`**

Sustituir el bloque `PRODUCTS_JSON_SCHEMA` completo (líneas 40-56) por:

```kotlin
val PRODUCTS_JSON_SCHEMA: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "store" to mapOf("type" to "string"),
        "products" to mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string"),
                    "price" to mapOf("type" to "number"),
                    "quantity" to mapOf("type" to "number"),
                    "unit" to mapOf("type" to "string", "enum" to RECEIPT_UNITS)
                ),
                "required" to listOf("name", "price")
            )
        )
    ),
    "required" to listOf("products")
)
```

- [ ] **Step 5: Mapeo, prompt y retorno en `GeminiReceiptRecognizer.kt`**

Sustituir las líneas 34-35:

```kotlin
private data class GeminiProduct(val name: String?, val price: Double?)
private data class GeminiProductsPayload(val products: List<GeminiProduct>?)
```

por:

```kotlin
private data class GeminiProduct(val name: String?, val price: Double?, val quantity: Double?, val unit: String?)
private data class GeminiProductsPayload(val store: String?, val products: List<GeminiProduct>?)
```

Sustituir la función `mapGeminiOutputTextToLines` completa (líneas 117-129) por:

```kotlin
internal fun mapGeminiOutputText(outputText: String): ParsedReceipt {
    val payload = try {
        geminiGson.fromJson(outputText, GeminiProductsPayload::class.java)
    } catch (e: JsonSyntaxException) {
        throw GeminiResponseException("el JSON de productos no es válido")
    }
    val products = payload?.products ?: throw GeminiResponseException("falta el campo 'products'")
    val lines = products.mapNotNull { product ->
        val name = product.name?.trim()
        val price = product.price
        if (name.isNullOrEmpty() || price == null || price <= 0) {
            null
        } else {
            ParsedReceiptLine(
                name = name,
                price = price,
                quantity = product.quantity?.takeIf { it > 0 } ?: 1.0,
                unit = product.unit?.trim()?.uppercase()?.takeIf { it in RECEIPT_UNITS } ?: Unit.UD.name
            )
        }
    }
    return ParsedReceipt(store = payload.store?.trim()?.takeIf { it.isNotEmpty() }, lines = lines)
}
```

Sustituir `RECEIPT_PROMPT` (líneas 150-158) por:

```kotlin
private val RECEIPT_PROMPT = """
    Eres un asistente que extrae la lista de la compra de la foto de un
    ticket de supermercado español. Devuelve TODOS los productos comprados
    con estos campos:
    - name: el nombre del producto tal y como aparece impreso.
    - price: el importe final de esa línea (el total impreso, ya con los
      descuentos de esa línea aplicados; NO el precio por unidad).
    - quantity y unit: cuánto se compró cuando el ticket lo indica
      ("2 x 1,25" es quantity 2 y unit UD; "0,850 kg x 3,99 €/kg" es
      quantity 0.85 y unit KG; los líquidos por litros, unit L). Si el
      ticket no lo indica, quantity 1 y unit UD.
    Devuelve además store: el nombre del supermercado o cadena que aparece en
    la cabecera del ticket (por ejemplo "Mercadona" o "Lidl"), sin dirección
    ni CIF; omítelo si no se lee con claridad.
    Ignora totales, subtotales, líneas de IVA, cambio, tarjeta/efectivo,
    promociones sueltas y cualquier línea que no sea un producto comprado.
""".trimIndent()
```

Sustituir la firma y el final de `recognizeReceiptWithGemini` (líneas 244-267): cambia el comentario y el tipo de retorno de la firma:

```kotlin
/** Manda una foto de ticket a Gemini y devuelve el ticket ya estructurado (supermercado y líneas; sustituye a ReceiptTextRecognizer + parseReceiptLines para este escaneo). */
suspend fun recognizeReceiptWithGemini(context: Context, imageUri: Uri, apiKey: String, model: String): ParsedReceipt {
```

y la última línea de esa función:

```kotlin
    return mapGeminiOutputText(outputText)
```

- [ ] **Step 6: Mantener `PurchaseHistoryScreen` compilando**

En `PurchaseHistoryScreen.kt:94`, sustituir:

```kotlin
                val geminiLines = geminiResult.getOrNull()
```

por:

```kotlin
                val geminiLines = geminiResult.getOrNull()?.lines
```

(La tarea 3 reescribe este flujo para conservar también el supermercado.)

- [ ] **Step 7: Compilar y ejecutar los tests**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS, toda la suite (incluye `ReceiptParsingTest`, que sigue construyendo `ParsedReceiptLine(name, price)` gracias a los valores por defecto).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/ReceiptParsing.kt app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptApi.kt app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptRecognizer.kt app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt app/src/test/kotlin/com/homepantry/app/data/GeminiReceiptRecognizerTest.kt
git commit -m "feat: read store, quantity and unit from receipts with Gemini"
```

---

### Task 3: Guardar supermercado, cantidad y unidad desde la hoja de revisión

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt:11,185-203`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/ReceiptReviewSheet.kt` (sustituir el archivo entero)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt` (import, líneas 76-79, 83-126, 236-249)
- Modify: `app/src/main/res/values/strings.xml` (bloque `ReceiptReviewSheet`)

**Interfaces:**
- Consumes (tareas 1 y 2): `ParsedReceipt`, `ParsedReceiptLine`, `RECEIPT_UNITS`, `unitSuffix`, `formatQuantity`, `knownStores`, `Purchase(quantity, unit, store)`.
- Produces: `AppViewModel.savePurchaseBatch(receipt: ParsedReceipt, ticketPhotoLocalUri: android.net.Uri?)`; `ReceiptReviewSheet(viewModel, initialReceipt: ParsedReceipt, ticketPhotoUri, notice, onDismiss)`.

- [ ] **Step 1: Strings nuevos**

En `strings.xml`, dentro del bloque `<!-- ReceiptReviewSheet -->`, tras `receipt_review_price`, añadir:

```xml
    <string name="receipt_review_store">Supermercado</string>
    <string name="receipt_review_qty">Cantidad</string>
    <string name="receipt_review_unit">Unidad</string>
```

- [ ] **Step 2: `savePurchaseBatch` recibe el ticket completo**

En `AppViewModel.kt`, cambiar el import de la línea 11:

```kotlin
import com.homepantry.app.data.ParsedReceipt
```

(sustituye a `import com.homepantry.app.data.ParsedReceiptLine`). Sustituir `savePurchaseBatch` (líneas 180-203) por:

```kotlin
    /**
     * Guarda todas las líneas confirmadas de un ticket escaneado como
     * Purchase independientes, subiendo la foto del ticket una vez y
     * enlazándola desde cada línea (spec "Flujo de captura y parseo"). El
     * supermercado del ticket se copia a cada línea; en blanco se guarda null.
     */
    fun savePurchaseBatch(receipt: ParsedReceipt, ticketPhotoLocalUri: android.net.Uri?) = viewModelScope.launch {
        if (receipt.lines.isEmpty()) return@launch
        runCatching {
            val batchId = java.util.UUID.randomUUID().toString()
            val photoUrl = ticketPhotoLocalUri?.let { uri -> storageRepository.uploadReceiptPhoto(batchId, uri) }
            val now = java.util.Date()
            val store = receipt.store?.trim()?.takeIf { it.isNotEmpty() }
            val purchases = receipt.lines.map { line ->
                Purchase(
                    rawName = line.name,
                    normalizedName = normalizeProductName(line.name),
                    price = line.price,
                    quantity = line.quantity,
                    unit = line.unit,
                    store = store,
                    date = now,
                    addedBy = userName,
                    ticketPhotoUrl = photoUrl
                )
            }
            purchasesRepository.addPurchases(purchases)
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
```

- [ ] **Step 3: Reescribir `ReceiptReviewSheet.kt`**

Sustituir el archivo entero por:

```kotlin
package com.homepantry.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.homepantry.app.data.ParsedReceipt
import com.homepantry.app.data.ParsedReceiptLine
import com.homepantry.app.data.RECEIPT_UNITS
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.knownStores
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import java.util.Locale

private data class EditableLine(val name: String, val qtyText: String, val unit: String, val priceText: String)

private fun formatPrice(price: Double): String =
    String.format(Locale("es", "ES"), "%.2f", price)

/**
 * Lista editable de las líneas detectadas en el ticket antes de guardarlas,
 * con el supermercado detectado y, por línea, cantidad y unidad.
 * Nada se escribe en Firestore hasta que el usuario pulsa "Guardar
 * compras" (spec "Alcance": revisión obligatoria, sin guardado silencioso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewSheet(
    viewModel: AppViewModel,
    initialReceipt: ParsedReceipt,
    ticketPhotoUri: Uri?,
    notice: String? = null,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var store by remember(initialReceipt) { mutableStateOf(initialReceipt.store.orEmpty()) }
    val lines = remember(initialReceipt) {
        mutableStateListOf(
            *initialReceipt.lines.map {
                EditableLine(it.name, formatQuantity(it.quantity), it.unit, formatPrice(it.price))
            }.toTypedArray()
        )
    }
    // Autocompletar el supermercado contra los ya usados en compras anteriores.
    val previousStores = remember(state.purchases) { knownStores(state.purchases) }
    val storeSuggestions = remember(store, previousStores) {
        if (store.isBlank()) {
            emptyList()
        } else {
            previousStores.filter { it.contains(store, ignoreCase = true) && !it.equals(store, ignoreCase = true) }
                .take(5)
        }
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
            // Un Snackbar del Scaffold queda detrás de esta hoja modal y caduca
            // antes de que el usuario la cierre; el aviso va aquí, fijo.
            if (notice != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text(stringResource(R.string.receipt_review_store)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            items(storeSuggestions, key = { "store-suggestion/$it" }) { suggestion ->
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { store = suggestion }
                        .padding(vertical = 8.dp)
                )
            }
            itemsIndexed(lines) { index, line ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = line.name,
                            onValueChange = { newValue -> lines[index] = line.copy(name = newValue) },
                            label = { Text(stringResource(R.string.receipt_review_name)) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { lines.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.receipt_review_delete_line_cd))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = line.qtyText,
                            onValueChange = { newValue -> lines[index] = line.copy(qtyText = newValue) },
                            label = { Text(stringResource(R.string.receipt_review_qty)) },
                            modifier = Modifier.weight(1f)
                        )
                        UnitField(
                            unit = line.unit,
                            onSelected = { newUnit -> lines[index] = line.copy(unit = newUnit) },
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        OutlinedTextField(
                            value = line.priceText,
                            onValueChange = { newValue -> lines[index] = line.copy(priceText = newValue) },
                            label = { Text(stringResource(R.string.receipt_review_price)) },
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { lines.add(EditableLine("", "1", RECEIPT_UNITS.first(), "")) },
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
                            if (line.name.isBlank() || price == null) {
                                null
                            } else {
                                // Una cantidad ilegible o no positiva no bloquea el guardado: cuenta como 1.
                                val quantity = line.qtyText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
                                ParsedReceiptLine(name = line.name.trim(), price = price, quantity = quantity, unit = line.unit)
                            }
                        }
                        viewModel.savePurchaseBatch(
                            ParsedReceipt(store = store.trim().ifBlank { null }, lines = parsed),
                            ticketPhotoUri
                        )
                        onDismiss()
                    },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
                ) { Text(stringResource(R.string.receipt_review_save)) }
            }
        }
    }
}

/** Selector de unidad (ud/kg/l) de una línea; mismo patrón de campo + menú que `AddItemSheet`. */
@Composable
private fun UnitField(unit: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = unitSuffix(unit),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.receipt_review_unit)) },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = true }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RECEIPT_UNITS.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(unitSuffix(candidate)) },
                    onClick = {
                        onSelected(candidate)
                        expanded = false
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 4: `PurchaseHistoryScreen` conserva el supermercado detectado**

En `PurchaseHistoryScreen.kt`:

1. Import: sustituir `import com.homepantry.app.data.ParsedReceiptLine` por `import com.homepantry.app.data.ParsedReceipt`.
2. Estado (línea 77): sustituir
   `var reviewLines by remember { mutableStateOf<List<ParsedReceiptLine>?>(null) }`
   por
   `var reviewReceipt by remember { mutableStateOf<ParsedReceipt?>(null) }`
3. En `processReceiptUri` (líneas 83-126) aplicar estos cambios:
   - `var parsed: List<ParsedReceiptLine> = emptyList()` → `var parsed = ParsedReceipt(store = null, lines = emptyList())`
   - `val geminiLines = geminiResult.getOrNull()?.lines` → `val geminiReceipt = geminiResult.getOrNull()`
   - `if (geminiLines != null) {` → `if (geminiReceipt != null) {`
   - `parsed = geminiLines` → `parsed = geminiReceipt`
   - `parsed = parseReceiptLines(ocrResult.getOrDefault(emptyList()))` → `parsed = ParsedReceipt(store = null, lines = parseReceiptLines(ocrResult.getOrDefault(emptyList())))`
   - `parsed.isEmpty() -> context.getString(R.string.purchase_history_ocr_empty)` → `parsed.lines.isEmpty() -> context.getString(R.string.purchase_history_ocr_empty)`
   - `reviewLines = parsed` → `reviewReceipt = parsed`
4. Al final del archivo, sustituir el bloque `val lines = reviewLines ... }` (líneas 236-249) por:

```kotlin
    val receipt = reviewReceipt
    if (receipt != null) {
        ReceiptReviewSheet(
            viewModel = viewModel,
            initialReceipt = receipt,
            ticketPhotoUri = reviewPhotoUri,
            notice = reviewNotice,
            onDismiss = {
                reviewReceipt = null
                reviewPhotoUri = null
                reviewNotice = null
            }
        )
    }
```

- [ ] **Step 5: Compilar y ejecutar toda la suite**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt app/src/main/kotlin/com/homepantry/app/ui/screens/ReceiptReviewSheet.kt app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: review and save store, quantity and unit for scanned receipts"
```

---

### Task 4: Historial por supermercado y detalle con precio unitario

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt` (imports y `UiState`, líneas ~36-40)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt` (imports y bloque `LazyColumn`, líneas 179-203)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseDetailScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `docs/superpowers/specs/2026-09-19-historial-supermercado-subzonas-design.md` (nombre de la propiedad)
- Test: `app/src/test/kotlin/com/homepantry/app/ui/UiStateTest.kt`

**Interfaces:**
- Consumes (tarea 1): `StoreSection`, `storeSections`, `unitSuffix`, `formatQuantity`, `Purchase.unitPrice()`.
- Produces: `UiState.purchaseStoreSections: List<StoreSection>`.

- [ ] **Step 1: Escribir el test que falla**

Añadir en `UiStateTest`, tras `purchaseSummaries exposes productSummaries computed from purchases`:

```kotlin
    @Test fun `purchaseStoreSections groups purchases by store with no-store last`() {
        val state = UiState(
            purchases = listOf(
                com.homepantry.app.data.Purchase(rawName = "Tomate", normalizedName = "tomate", price = 1.5, store = "Lidl"),
                com.homepantry.app.data.Purchase(rawName = "Leche", normalizedName = "leche", price = 0.9)
            )
        )
        assertEquals(listOf("lidl", ""), state.purchaseStoreSections.map { it.storeKey })
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.ui.UiStateTest"`
Expected: FAIL de compilación, `Unresolved reference: purchaseStoreSections`.

- [ ] **Step 3: Exponer las secciones en `UiState`**

En `AppViewModel.kt` añadir los imports `com.homepantry.app.data.StoreSection` y `com.homepantry.app.data.storeSections` junto a los demás de `data`, y bajo `val purchaseSummaries` (línea 40) añadir:

```kotlin
    /** Historial agrupado por supermercado (el nombre difiere de `storeSections` para no tapar la función). */
    val purchaseStoreSections: List<StoreSection> get() = storeSections(purchases)
```

En el spec (`docs/superpowers/specs/2026-09-19-historial-supermercado-subzonas-design.md`) sustituir la línea
`- \`UiState\` expone \`storeSections\` junto a \`purchaseSummaries\`.`
por
`- \`UiState\` expone \`purchaseStoreSections\` junto a \`purchaseSummaries\`.`

Y en el apartado "Pruebas" del spec, `UiStateTest`: `storeSections expuesto en el estado` → `purchaseStoreSections expuesto en el estado`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.ui.UiStateTest"`
Expected: PASS.

- [ ] **Step 5: Strings**

En `strings.xml`, bloque `PurchaseHistoryScreen`: eliminar `purchase_history_summary` y añadir en su lugar:

```xml
    <string name="purchase_history_no_store">Sin supermercado</string>
    <string name="purchase_history_unit_price">%1$.2f €/%2$s</string>
    <string name="purchase_history_last_purchase">Última compra %1$s · %2$d compra(s)</string>
```

En el bloque `PurchaseDetailScreen` añadir:

```xml
    <string name="purchase_detail_line_info">%1$s · %2$s %3$s</string>
```

- [ ] **Step 6: Pantalla del historial por supermercado**

En `PurchaseHistoryScreen.kt` añadir imports:

```kotlin
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.homepantry.app.data.unitSuffix
import java.text.SimpleDateFormat
import java.util.Locale
```

Justo antes de `Scaffold(` declarar el formateador:

```kotlin
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale("es", "ES")) }
```

Sustituir la rama `state.purchaseSummaries.isEmpty() -> {` por `state.purchaseStoreSections.isEmpty() -> {`, y el cuerpo de la rama `else ->` (el `LazyColumn`, líneas 184-202) por:

```kotlin
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    state.purchaseStoreSections.forEach { section ->
                        item(key = "store/${section.storeKey}") {
                            Text(
                                text = section.displayName.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(section.products, key = { "${section.storeKey}/${it.normalizedName}" }) { summary ->
                            ListItem(
                                headlineContent = { Text(summary.displayName) },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            R.string.purchase_history_last_purchase,
                                            dateFormat.format(summary.purchases.first().date),
                                            summary.purchases.size
                                        )
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        stringResource(
                                            R.string.purchase_history_unit_price,
                                            summary.latestUnitPrice,
                                            unitSuffix(summary.latestUnit)
                                        )
                                    )
                                },
                                modifier = Modifier.clickable { onOpenProduct(summary.normalizedName) }
                            )
                        }
                    }
                }
            }
```

- [ ] **Step 7: Detalle del producto**

En `PurchaseDetailScreen.kt` añadir imports:

```kotlin
import androidx.compose.foundation.layout.Column
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.unitPrice
import com.homepantry.app.data.unitSuffix
```

Sustituir el bloque `items(summary.purchases, key = { it.id }) { purchase -> ... }` (líneas 86-91) por:

```kotlin
                items(summary.purchases, key = { it.id }) { purchase ->
                    ListItem(
                        headlineContent = { Text(dateFormat.format(purchase.date)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.purchase_detail_line_info,
                                    purchase.store?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.purchase_history_no_store),
                                    formatQuantity(purchase.quantity),
                                    unitSuffix(purchase.unit)
                                )
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(
                                        R.string.purchase_history_unit_price,
                                        purchase.unitPrice(),
                                        unitSuffix(purchase.unit)
                                    )
                                )
                                Text(
                                    stringResource(R.string.purchase_detail_amount, purchase.price),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
```

(`Alignment` y `MaterialTheme` ya están importados en ese archivo.)

- [ ] **Step 8: Compilar, ejecutar la suite y generar el APK**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Sin referencias colgando a `purchase_history_summary` (el build de recursos fallaría).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseDetailScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/homepantry/app/ui/UiStateTest.kt docs/superpowers/specs/2026-09-19-historial-supermercado-subzonas-design.md
git commit -m "feat: show purchase history by store with unit prices"
```

- [ ] **Step 10: Comprobación manual en el dispositivo (la hace el usuario)**

Lista para comprobar en el móvil:
1. Escanear un ticket con Gemini: la hoja trae el supermercado ya rellenado, y cantidad/unidad por línea (un producto al peso sale en kg). Corregir una cantidad y guardar.
2. El historial muestra una sección por supermercado, con "€/ud" o "€/kg" por producto, y las compras antiguas bajo "Sin supermercado".
3. Un producto comprado en dos súperes aparece en ambas secciones. Su detalle lista cada compra con súper, cantidad, precio unitario y total.
4. Si Gemini rechaza el esquema (error de "respuesta inesperada" tras este cambio), quitar `"enum" to RECEIPT_UNITS` de `PRODUCTS_JSON_SCHEMA`: el saneado de `mapGeminiOutputText` ya cubre unidades inválidas.
