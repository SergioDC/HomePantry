# Tickets del historial con borrado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Poder ver los tickets escaneados, marcar los posibles duplicados y borrar un ticket entero o líneas sueltas (con «Deshacer» en las líneas).

**Architecture:** `Purchase` gana `ticketId`. La agrupación en tickets y la marca de duplicado son lógica pura en `PurchaseAggregation.kt` (`tickets`). `PurchasesRepository` borra por lotes y restaura; `AppViewModel` expone `purchaseTickets`, `deleteTicket`, `deletePurchase` y `restorePurchase`. Dos pantallas nuevas (lista y detalle de ticket) se abren desde un botón «Tickets» de la pantalla actual del historial, que no cambia por lo demás.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Firestore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-19-zonas-formulario-tickets-design.md` (Parte C). Mockups: `docs/superpowers/mockups/2026-09-19-historial-tickets-mockups.md`. La Parte A+B tiene su propio plan: `2026-09-19-zonas-colores-formulario.md`; ambos planes son independientes.

## Global Constraints

- `Purchase.ticketId: String? = null`; `savePurchaseBatch` lo rellena con el `batchId` del escaneo. Las compras antiguas (`ticketId` nulo) tienen clave de ticket `legacy:<storeKey>:<milisegundos de la fecha>`.
- Posible duplicado: mismo `storeKey`, mismo total en céntimos y mismo día (zona horaria local). Se marcan todos los tickets del grupo **menos el más antiguo** (empate por clave). Solo avisa; no borra.
- Tickets ordenados del más reciente al más antiguo (empate por clave); líneas de un ticket ordenadas por `rawName` sin distinguir mayúsculas; total = suma de `price`.
- Borrado por lotes de como mucho 500 operaciones (límite de Firestore). Las reglas de Firestore ya permiten borrar; no se tocan.
- Borrar un ticket entero siempre pide confirmación. Borrar una línea muestra «Línea eliminada» con «Deshacer», que la vuelve a escribir en su mismo documento. Si el ticket tiene una sola línea, la papelera de esa línea abre la confirmación de borrar el ticket (no hay «Deshacer» posible porque la pantalla se cierra).
- Un ticket que ya no existe (borrado aquí o desde otro móvil) hace volver a la pantalla anterior.
- La foto del ticket en Storage no se borra. No hay «Ver el otro», ni selección múltiple, ni borrado desde el detalle de producto.
- La pantalla actual del historial solo gana el botón «Tickets»; nada más cambia en ella.
- Texto de interfaz en español, en `app/src/main/res/values/strings.xml`. Comandos Gradle: `./gradlew.bat ...` desde la raíz del repo.
- En `package com.homepantry.app.data` existe un enum `Unit` que tapa a `kotlin.Unit`.
- Stage de rutas explícitas (`git add <rutas>`); nunca `git add -A` ni `git commit -a`. Cada commit termina con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

## File Structure

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `data/Purchase.kt` | Campo `ticketId` | 1 |
| `data/PurchaseAggregation.kt` | `ticketKey`, `Ticket`, `tickets` (lógica pura) | 1 |
| `data/PurchasesRepository.kt` | `deletePurchases`, `restorePurchase` | 2 |
| `ui/AppViewModel.kt` | `ticketId` al guardar, `purchaseTickets`, borrar/restaurar | 2 |
| `ui/components/DeleteTicketDialog.kt` | Diálogo de confirmación compartido | 3 |
| `ui/screens/PurchaseTicketDetailScreen.kt` | Detalle del ticket con borrado | 3 |
| `ui/screens/PurchaseTicketsScreen.kt` | Lista de tickets con filtro y duplicados | 4 |
| `ui/screens/PurchaseHistoryScreen.kt` | Botón «Tickets» | 4 |
| `MainActivity.kt` | Rutas `purchaseTickets` y `purchaseTicket/{ticketKey}` | 3, 4 |
| `res/values/strings.xml` | Textos nuevos | 3, 4 |

Rutas de código bajo `app/src/main/kotlin/com/homepantry/app/`; tests bajo `app/src/test/kotlin/com/homepantry/app/`.

---

### Task 1: Modelo y lógica pura de tickets

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/Purchase.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/PurchaseAggregation.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/PurchaseAggregationTest.kt`

**Interfaces:**
- Produces (las usan las tareas 2, 3 y 4):
  - `Purchase(..., ticketId: String? = null)`
  - `fun ticketKey(purchase: Purchase): String`
  - `data class Ticket(val key: String, val storeKey: String, val store: String, val date: Date, val purchases: List<Purchase>, val total: Double, val possibleDuplicate: Boolean)` (`store` vacío = sin supermercado)
  - `fun tickets(purchases: List<Purchase>): List<Ticket>`

- [ ] **Step 1: Escribir los tests que fallan**

En `PurchaseAggregationTest.kt` añadir el import `import org.junit.Assert.assertTrue` junto a los demás, y estas funciones auxiliares justo debajo de `private fun date(s: String) = format.parse(s)!!`:

```kotlin
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private fun at(s: String) = timeFormat.parse(s)!!

    private fun ticketLine(id: String, store: String, total: Double, time: String) =
        Purchase(rawName = "X", price = total, date = at(time), store = store, ticketId = id)
```

Y estos tests dentro de la clase, antes de la llave de cierre final:

```kotlin
    @Test fun `ticketKey uses the ticketId, or store plus exact scan time for old purchases`() {
        val scanned = at("2026-03-12 10:00")
        assertEquals("t1", ticketKey(Purchase(ticketId = "t1", store = "Lidl", date = scanned)))
        assertEquals("legacy:mercadona:${scanned.time}", ticketKey(Purchase(store = " Mercadona ", date = scanned)))
        assertEquals("legacy::${scanned.time}", ticketKey(Purchase(date = scanned)))
    }

    @Test fun `tickets groups the lines of a scan, sorted newest first with lines by name`() {
        val purchases = listOf(
            Purchase(rawName = "Pan", price = 1.2, date = at("2026-03-10 18:00"), store = "Lidl", ticketId = "a"),
            Purchase(rawName = "leche", price = 0.9, date = at("2026-03-10 18:00"), store = "Lidl", ticketId = "a"),
            Purchase(rawName = "Atún", price = 2.0, date = at("2026-03-12 09:00"), store = "Mercadona", ticketId = "b")
        )
        val result = tickets(purchases)

        assertEquals(listOf("b", "a"), result.map { it.key })
        val a = result[1]
        assertEquals("Lidl", a.store)
        assertEquals("lidl", a.storeKey)
        assertEquals(2.1, a.total, 0.001)
        assertEquals(listOf("leche", "Pan"), a.purchases.map { it.rawName })
    }

    @Test fun `tickets groups old purchases by store and exact scan time`() {
        val first = at("2026-03-10 18:00")
        val second = at("2026-03-11 12:00")
        val purchases = listOf(
            Purchase(rawName = "A", price = 1.0, date = first),
            Purchase(rawName = "B", price = 2.0, date = first),
            Purchase(rawName = "C", price = 4.0, date = second)
        )
        val result = tickets(purchases)

        assertEquals(listOf(4.0, 3.0), result.map { it.total })
        assertEquals(listOf("", ""), result.map { it.store })
    }

    @Test fun `tickets is empty when there are no purchases`() {
        assertEquals(emptyList<Ticket>(), tickets(emptyList()))
    }

    @Test fun `tickets flags every copy but the oldest when store, total and day match`() {
        val result = tickets(
            listOf(
                ticketLine("a", "Mercadona", 47.32, "2026-03-12 10:00"),
                ticketLine("b", "MERCADONA", 47.32, "2026-03-12 10:05"),
                ticketLine("c", "mercadona ", 47.32, "2026-03-12 18:30")
            )
        )
        assertEquals(
            mapOf("a" to false, "b" to true, "c" to true),
            result.associate { it.key to it.possibleDuplicate }
        )
    }

    @Test fun `tickets does not flag a different total, day or store`() {
        val result = tickets(
            listOf(
                ticketLine("a", "Mercadona", 47.32, "2026-03-12 10:00"),
                ticketLine("b", "Mercadona", 47.33, "2026-03-12 10:05"),
                ticketLine("c", "Mercadona", 47.32, "2026-03-13 10:05"),
                ticketLine("d", "Lidl", 47.32, "2026-03-12 10:05")
            )
        )
        assertTrue(result.none { it.possibleDuplicate })
    }
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.PurchaseAggregationTest"`
Expected: FAIL de compilación, `Unresolved reference: ticketKey` (y `ticketId`, `tickets`, `Ticket`).

- [ ] **Step 3: Campo `ticketId` en `Purchase`**

En `Purchase.kt`, tras el campo `store`:

```kotlin
    val store: String? = null
)
```

sustituir por:

```kotlin
    val store: String? = null,
    /** Id del escaneo (ticket) al que pertenece la línea. Null en compras anteriores a los tickets. */
    val ticketId: String? = null
)
```

- [ ] **Step 4: Lógica en `PurchaseAggregation.kt`**

Tras `data class StoreSection(...)` añadir:

```kotlin

/**
 * Un ticket escaneado: las líneas de un mismo escaneo con su total. `store` vacío
 * corresponde a un ticket sin supermercado.
 */
data class Ticket(
    val key: String,
    val storeKey: String,
    val store: String,
    val date: Date,
    val purchases: List<Purchase>,
    val total: Double,
    val possibleDuplicate: Boolean
)
```

Y al final del archivo:

```kotlin

/**
 * Clave del ticket de una línea: su `ticketId` o, en las compras antiguas, el supermercado
 * más la fecha exacta del escaneo (todas las líneas de un escaneo comparten la misma `date`).
 */
fun ticketKey(purchase: Purchase): String =
    purchase.ticketId ?: "legacy:${storeKey(purchase.store)}:${purchase.date.time}"

private fun dayOf(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

/**
 * Agrupa las compras en tickets, del más reciente al más antiguo. Un ticket es un posible
 * duplicado cuando otro comparte supermercado, total (en céntimos) y día: se marcan todos
 * menos el más antiguo del grupo.
 */
fun tickets(purchases: List<Purchase>): List<Ticket> {
    val built = purchases.groupBy { ticketKey(it) }.map { (key, group) ->
        val newest = group.maxBy { it.date }
        Ticket(
            key = key,
            storeKey = storeKey(newest.store),
            store = newest.store?.trim().orEmpty(),
            date = newest.date,
            purchases = group.sortedBy { it.rawName.lowercase() },
            total = group.sumOf { it.price },
            possibleDuplicate = false
        )
    }
    val duplicateKeys = built
        .groupBy { Triple(it.storeKey, Math.round(it.total * 100), dayOf(it.date)) }
        .values
        .filter { it.size > 1 }
        .flatMap { group -> group.sortedWith(compareBy<Ticket>({ it.date }, { it.key })).drop(1).map { it.key } }
        .toSet()
    return built
        .map { it.copy(possibleDuplicate = it.key in duplicateKeys) }
        .sortedWith(compareByDescending<Ticket> { it.date }.thenBy { it.key })
}
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.PurchaseAggregationTest"`
Expected: PASS (los tests anteriores y los 6 nuevos).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/Purchase.kt app/src/main/kotlin/com/homepantry/app/data/PurchaseAggregation.kt app/src/test/kotlin/com/homepantry/app/data/PurchaseAggregationTest.kt
git commit -m "feat: group purchases into tickets and flag possible duplicates"
```

---

### Task 2: Repositorio y ViewModel de tickets

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/PurchasesRepository.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt` (imports, `UiState`, `savePurchaseBatch`, nuevas funciones)
- Test: `app/src/test/kotlin/com/homepantry/app/ui/UiStateTest.kt`

**Interfaces:**
- Consumes (tarea 1): `Ticket`, `tickets`, `Purchase.ticketId`.
- Produces (lo usan las tareas 3 y 4):
  - `UiState.purchaseTickets: List<Ticket>`
  - `AppViewModel.deleteTicket(ticketKey: String)`, `deletePurchase(purchaseId: String)`, `restorePurchase(purchase: Purchase)`

- [ ] **Step 1: Escribir el test que falla**

Añadir en `UiStateTest`, tras `purchaseStoreSections groups purchases by store with no-store last`:

```kotlin
    @Test fun `purchaseTickets groups purchases by ticket`() {
        val state = UiState(
            purchases = listOf(
                com.homepantry.app.data.Purchase(rawName = "Tomate", price = 1.5, ticketId = "t1"),
                com.homepantry.app.data.Purchase(rawName = "Leche", price = 0.9, ticketId = "t1"),
                com.homepantry.app.data.Purchase(rawName = "Pan", price = 1.2, ticketId = "t2")
            )
        )
        assertEquals(setOf("t1", "t2"), state.purchaseTickets.map { it.key }.toSet())
        assertEquals(2, state.purchaseTickets.first { it.key == "t1" }.purchases.size)
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.ui.UiStateTest"`
Expected: FAIL de compilación, `Unresolved reference: purchaseTickets`.

- [ ] **Step 3: `PurchasesRepository`**

Añadir al final de la clase, tras `addPurchases`:

```kotlin

    /** Borra las compras indicadas; Firestore admite como máximo 500 operaciones por lote. */
    suspend fun deletePurchases(ids: List<String>) {
        ids.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id -> batch.delete(collection().document(id)) }
            batch.commit().await()
        }
    }

    /** Deshace un borrado: vuelve a escribir la compra en su mismo documento. */
    suspend fun restorePurchase(purchase: Purchase) {
        collection().document(purchase.id).set(purchase).await()
    }
```

- [ ] **Step 4: `AppViewModel`**

Imports de `data` (orden alfabético): añadir `import com.homepantry.app.data.Ticket` (tras `StoreSection`) e `import com.homepantry.app.data.tickets` (tras `storeSections`).

En `UiState`, bajo `purchaseStoreSections`:

```kotlin

    /** Historial agrupado en tickets (un escaneo = un ticket), del más reciente al más antiguo. */
    val purchaseTickets: List<Ticket> get() = tickets(purchases)
```

En `savePurchaseBatch`, dentro de `Purchase(...)`, añadir `ticketId = batchId,` (por ejemplo tras `store = store,`).

Tras `savePurchaseBatch` añadir:

```kotlin

    /** Borra todas las líneas de un ticket (por su clave de `UiState.purchaseTickets`). */
    fun deleteTicket(ticketKey: String) = viewModelScope.launch {
        val ids = _state.value.purchaseTickets.firstOrNull { it.key == ticketKey }?.purchases?.map { it.id }
            ?: return@launch
        runCatching { purchasesRepository.deletePurchases(ids) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun deletePurchase(purchaseId: String) = viewModelScope.launch {
        runCatching { purchasesRepository.deletePurchases(listOf(purchaseId)) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    /** Deshace el borrado de una línea. */
    fun restorePurchase(purchase: Purchase) = viewModelScope.launch {
        runCatching { purchasesRepository.restorePurchase(purchase) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
```

- [ ] **Step 5: Compilar y ejecutar la suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (incluido el test nuevo de `UiStateTest`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/PurchasesRepository.kt app/src/main/kotlin/com/homepantry/app/ui/AppViewModel.kt app/src/test/kotlin/com/homepantry/app/ui/UiStateTest.kt
git commit -m "feat: save ticket ids and delete or restore purchases"
```

---

### Task 3: Detalle del ticket y diálogo de borrado

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/components/DeleteTicketDialog.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseTicketDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (import y ruta)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes (tareas 1 y 2): `Ticket`, `UiState.purchaseTickets`, `AppViewModel.deleteTicket/deletePurchase/restorePurchase`, `formatQuantity`, `unitSuffix`, `Purchase.unitPrice()`.
- Produces (lo usa la tarea 4):
  - `@Composable fun DeleteTicketDialog(ticket: Ticket, onConfirm: () -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun PurchaseTicketDetailScreen(viewModel: AppViewModel, ticketKey: String, onBack: () -> Unit)`
  - Ruta `purchaseTicket/{ticketKey}` (la clave va con `Uri.encode`).

- [ ] **Step 1: Strings**

En `strings.xml`, tras el bloque `<!-- PurchaseDetailScreen -->` añadir:

```xml

    <!-- PurchaseTicketDetailScreen / DeleteTicketDialog -->
    <string name="purchase_ticket_title">%1$s · %2$s</string>
    <string name="purchase_ticket_summary">%1$d línea(s) · %2$.2f €</string>
    <string name="purchase_ticket_line_info">%1$s %2$s · %3$s</string>
    <string name="purchase_ticket_delete_cd">Eliminar ticket</string>
    <string name="purchase_ticket_delete_line_cd">Eliminar línea</string>
    <string name="purchase_ticket_delete_title">¿Eliminar este ticket?</string>
    <string name="purchase_ticket_delete_message">%1$s · %2$s · %3$.2f €\nSe borrarán sus %4$d línea(s).</string>
    <string name="purchase_ticket_delete_confirm">Eliminar</string>
    <string name="purchase_ticket_delete_cancel">Cancelar</string>
    <string name="purchase_ticket_line_deleted">Línea eliminada</string>
    <string name="purchase_ticket_undo">Deshacer</string>
```

- [ ] **Step 2: `DeleteTicketDialog.kt`**

```kotlin
package com.homepantry.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.data.Ticket
import java.text.SimpleDateFormat
import java.util.Locale

/** Confirmación de borrar un ticket entero (lo usan la lista y el detalle de tickets). */
@Composable
fun DeleteTicketDialog(
    ticket: Ticket,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }
    val store = ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_ticket_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.purchase_ticket_delete_message,
                    store,
                    dateFormat.format(ticket.date),
                    ticket.total,
                    ticket.purchases.size
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.purchase_ticket_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_ticket_delete_cancel)) }
        }
    )
}
```

- [ ] **Step 3: `PurchaseTicketDetailScreen.kt`**

```kotlin
package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Purchase
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.unitPrice
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.DeleteTicketDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Detalle de un ticket: sus líneas con cantidad, precio unitario y total. La papelera de
 * arriba borra el ticket entero (con confirmación); la de cada línea la borra con «Deshacer».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseTicketDetailScreen(
    viewModel: AppViewModel,
    ticketKey: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val ticket = remember(state.purchases, ticketKey) {
        state.purchaseTickets.firstOrNull { it.key == ticketKey }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }
    var showDeleteTicket by remember { mutableStateOf(false) }
    val lineDeletedText = stringResource(R.string.purchase_ticket_line_deleted)
    val undoText = stringResource(R.string.purchase_ticket_undo)

    // Un ticket que ya no existe (borrado aquí o desde otro móvil) devuelve a la pantalla anterior.
    LaunchedEffect(ticket == null) {
        if (ticket == null) onBack()
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    fun deleteLine(purchase: Purchase) {
        viewModel.deletePurchase(purchase.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = lineDeletedText,
                actionLabel = undoText,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restorePurchase(purchase)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (ticket != null) {
                        Text(
                            stringResource(
                                R.string.purchase_ticket_title,
                                ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                                dateFormat.format(ticket.date)
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    if (ticket != null) {
                        IconButton(onClick = { showDeleteTicket = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.purchase_ticket_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        if (ticket != null) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item(key = "summary") {
                    Text(
                        text = stringResource(R.string.purchase_ticket_summary, ticket.purchases.size, ticket.total),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(ticket.purchases, key = { it.id }) { purchase ->
                    ListItem(
                        headlineContent = { Text(purchase.rawName) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.purchase_ticket_line_info,
                                    formatQuantity(purchase.quantity),
                                    unitSuffix(purchase.unit),
                                    stringResource(
                                        R.string.purchase_history_unit_price,
                                        purchase.unitPrice(),
                                        unitSuffix(purchase.unit)
                                    )
                                )
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.purchase_detail_amount, purchase.price))
                                IconButton(
                                    onClick = {
                                        // Con una sola línea, borrarla equivale a borrar el ticket: se pide
                                        // confirmación (la pantalla se cerraría y no habría dónde deshacer).
                                        if (ticket.purchases.size == 1) showDeleteTicket = true else deleteLine(purchase)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.purchase_ticket_delete_line_cd)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDeleteTicket && ticket != null) {
        DeleteTicketDialog(
            ticket = ticket,
            onConfirm = {
                viewModel.deleteTicket(ticket.key)
                showDeleteTicket = false
            },
            onDismiss = { showDeleteTicket = false }
        )
    }
}
```

- [ ] **Step 4: Ruta en `MainActivity.kt`**

Añadir el import (junto a los demás `com.homepantry.app.ui.screens.*`, en orden alfabético):

```kotlin
import com.homepantry.app.ui.screens.PurchaseTicketDetailScreen
```

Y, tras el bloque `composable("purchaseDetail/{normalizedName}") { ... }`, dentro del `NavHost`:

```kotlin
            composable("purchaseTicket/{ticketKey}") { backStackEntry ->
                val encodedKey = backStackEntry.arguments?.getString("ticketKey") ?: return@composable
                PurchaseTicketDetailScreen(
                    viewModel = viewModel,
                    ticketKey = Uri.decode(encodedKey),
                    onBack = { navController.popBackStack() }
                )
            }
```

- [ ] **Step 5: Compilar y ejecutar la suite**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, sin avisos nuevos en los archivos creados.

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/components/DeleteTicketDialog.kt app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseTicketDetailScreen.kt app/src/main/kotlin/com/homepantry/app/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: ticket detail screen with delete ticket and delete line with undo"
```

---

### Task 4: Lista de tickets y botón «Tickets»

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseTicketsScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt` (firma y barra superior)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (import, ruta y `onOpenTickets`)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes (tareas 1-3): `Ticket`, `UiState.purchaseTickets`, `AppViewModel.deleteTicket`, `DeleteTicketDialog`, ruta `purchaseTicket/{ticketKey}`, strings `purchase_ticket_summary`, `purchase_history_no_store`.
- Produces: `@Composable fun PurchaseTicketsScreen(viewModel: AppViewModel, onBack: () -> Unit, onOpenTicket: (String) -> Unit)`, ruta `purchaseTickets`; `PurchaseHistoryScreen(..., onOpenTickets: () -> Unit)`.

- [ ] **Step 1: Strings**

En `strings.xml`, en el bloque `<!-- PurchaseHistoryScreen -->` añadir `purchase_history_tickets`, y tras el bloque de la tarea 3 añadir el de la lista:

```xml
    <string name="purchase_history_tickets">Tickets</string>
```

```xml

    <!-- PurchaseTicketsScreen -->
    <string name="purchase_tickets_title">Tickets</string>
    <string name="purchase_tickets_empty">Aún no hay tickets. Escanea uno desde el historial.</string>
    <string name="purchase_tickets_all">Todos</string>
    <string name="purchase_tickets_duplicate">Posible duplicado</string>
    <string name="purchase_tickets_delete">Eliminar</string>
```

- [ ] **Step 2: `PurchaseTicketsScreen.kt`**

```kotlin
package com.homepantry.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Ticket
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.DeleteTicketDialog
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lista de tickets escaneados: filtro por supermercado, agrupados por mes. Un ticket que
 * parece un duplicado lleva una marca y un botón «Eliminar» (con confirmación).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseTicketsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedStoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    var ticketPendingDelete by remember { mutableStateOf<Ticket?>(null) }
    val dayFormat = remember { SimpleDateFormat("d MMM", Locale("es", "ES")) }
    val monthFormat = remember { SimpleDateFormat("LLLL yyyy", Locale("es", "ES")) }

    val allTickets = remember(state.purchases) { state.purchaseTickets }
    // Supermercados presentes, del que tiene el ticket más reciente al más antiguo (`storeKey` vacío = sin súper).
    val stores = remember(allTickets) { allTickets.distinctBy { it.storeKey }.map { it.storeKey to it.store } }
    // Si el filtro elegido ya no existe (se borraron todos sus tickets) se vuelve a «Todos».
    val selected = selectedStoreKey?.takeIf { key -> stores.any { it.first == key } }
    val visible = allTickets.filter { selected == null || it.storeKey == selected }

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
                title = { Text(stringResource(R.string.purchase_tickets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        if (allTickets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.purchase_tickets_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "filters") {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selected == null,
                            onClick = { selectedStoreKey = null },
                            label = { Text(stringResource(R.string.purchase_tickets_all)) }
                        )
                        stores.forEach { (key, name) ->
                            FilterChip(
                                selected = selected == key,
                                onClick = { selectedStoreKey = key },
                                label = { Text(name.ifEmpty { stringResource(R.string.purchase_history_no_store) }) }
                            )
                        }
                    }
                }
                visible.groupBy { monthFormat.format(it.date) }.forEach { (month, monthTickets) ->
                    item(key = "month/$month") {
                        Text(
                            text = month.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(monthTickets, key = { it.key }) { ticket ->
                        TicketCard(
                            ticket = ticket,
                            dateText = dayFormat.format(ticket.date),
                            onOpen = { onOpenTicket(ticket.key) },
                            onDelete = { ticketPendingDelete = ticket }
                        )
                    }
                }
            }
        }
    }

    val pending = ticketPendingDelete
    if (pending != null) {
        DeleteTicketDialog(
            ticket = pending,
            onConfirm = {
                viewModel.deleteTicket(pending.key)
                ticketPendingDelete = null
            },
            onDismiss = { ticketPendingDelete = null }
        )
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    dateText: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.purchase_ticket_summary, ticket.purchases.size, ticket.total),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (ticket.possibleDuplicate) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.purchase_tickets_duplicate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.purchase_tickets_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Botón «Tickets» en `PurchaseHistoryScreen`**

Firma: añadir `onOpenTickets: () -> Unit,` tras `onOpenProduct: (String) -> Unit`. Cambiar la coma final de esa línea: queda

```kotlin
    onOpenProduct: (String) -> Unit,
    onOpenTickets: () -> Unit
) {
```

En el `TopAppBar` del `Scaffold`, `navigationIcon = { ... }` es hoy el último argumento (sin coma final): añade una coma tras su llave de cierre y a continuación:

```kotlin
                actions = {
                    TextButton(onClick = onOpenTickets) {
                        Text(stringResource(R.string.purchase_history_tickets))
                    }
                }
```

(`TextButton` ya está importado en ese archivo por el diálogo de escanear.)

- [ ] **Step 4: Rutas en `MainActivity.kt`**

Import: `import com.homepantry.app.ui.screens.PurchaseTicketsScreen` (junto a los demás, orden alfabético).

En `composable("purchaseHistory") { ... }`, añadir a la llamada de `PurchaseHistoryScreen`, tras `onOpenProduct = { ... }`:

```kotlin
                    onOpenTickets = { navController.navigate("purchaseTickets") }
```

(añade la coma tras la llave de cierre del `onOpenProduct` existente). Y, junto a la ruta `purchaseTicket/{ticketKey}` de la tarea 3, añadir:

```kotlin
            composable("purchaseTickets") {
                PurchaseTicketsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTicket = { ticketKey ->
                        navController.navigate("purchaseTicket/${Uri.encode(ticketKey)}")
                    }
                )
            }
```

- [ ] **Step 5: Compilar, ejecutar la suite y generar el APK**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL, sin avisos nuevos en los archivos tocados.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseTicketsScreen.kt app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt app/src/main/kotlin/com/homepantry/app/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: tickets list with store filter, duplicate flag and delete"
```

- [ ] **Step 7: Comprobación manual en el dispositivo (la hace el usuario)**

1. En el historial, el botón «Tickets» de la barra superior abre la lista; la pantalla de historial sigue igual (secciones por supermercado con precio unitario).
2. Escanear el mismo ticket dos veces: en la lista aparecen dos tickets y el segundo lleva «Posible duplicado» con su botón «Eliminar». Al pulsarlo pide confirmación y, al aceptar, desaparece.
3. Abrir un ticket: papelera arriba (con confirmación) y papelera en cada línea. Borrar una línea muestra «Línea eliminada · Deshacer» y «Deshacer» la restaura. Con un ticket de una sola línea, su papelera pide confirmar el borrado del ticket.
4. Las compras antiguas (sin `ticketId`) también salen como tickets, agrupadas por supermercado y fecha, y se pueden borrar igual.
5. Tras borrar, el historial por supermercado y los precios unitarios se actualizan.
