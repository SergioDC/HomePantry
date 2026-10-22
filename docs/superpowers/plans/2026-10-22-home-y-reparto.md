# Home y reparto de gastos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pantalla Home con tres tarjetas (Almacén, Gastos, Reparto) y una sección nueva de reparto de gastos por mes, estilo Tricount, con pestañas Gastos, Saldos y Fotos.

**Architecture:** Lógica de reparto en funciones puras (`data/SplitLogic.kt`) con tests JUnit. Tres repositorios Firestore, un `SplitViewModel` con un único `StateFlow` y pantallas Compose que solo pintan su estado. El reparto vive en `households/{code}/splitMonths` y no toca `purchases`. Home pasa a ser el destino inicial del `NavHost`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Firebase Firestore y Storage, Coil (ya incluido), JUnit 4. Sin dependencias nuevas.

**Spec:** `docs/superpowers/specs/2026-10-22-reparto-gastos-design.md`

## Global Constraints

- Sin dependencias nuevas (no engordar el APK).
- No se cambian `Purchase`, `purchases` ni las pantallas de tickets. El reparto no comparte datos con ellos.
- Importes siempre en `Long` (céntimos). Formato es-ES solo al pintar (`Locale("es", "ES")`).
- Modelos de Firestore en `data/`: `data class` con valores por defecto, `@DocumentId`, `@ServerTimestamp`. Fuera de `data/` se rompen en release sin fallo de compilación.
- Campo booleano `reimbursement`, nunca `isReimbursement` (Firestore quita el prefijo `is`).
- Cambios de un solo campo con `update(campo, valor)`, no `set` del objeto entero.
- `CancellationException` se relanza siempre.
- Textos visibles en `res/values/strings.xml` (con `plurals` cuando corresponda). Comentarios y KDoc en español explicando el porqué.
- Colores de `ui/theme/Color.kt`. El coral solo para deudas y borrar.
- `firestore.rules` y `storage.rules` ya cubren `households/{code}/{document=**}`: no se tocan.
- No hacer `git commit` sin que el usuario lo pida (regla de su CLAUDE.md global). Commits en español con prefijo (`feat:`, `docs:`...).

## Review Focus

- La suma de saldos es 0 en todos los casos, también con ex-miembros y gastos sin participantes (Task 1).
- Los sobrantes de céntimos no dependen del orden de la lista de participantes (Task 1).
- Un reembolso no cuenta en el total del mes pero sí ajusta saldos (Task 1).
- `monthKeyOf` usa la zona horaria del dispositivo: un gasto a las 00:30 del día 1 en Madrid cae en el mes nuevo (Task 1).
- `reimbursement` se lee y se escribe con el mismo nombre en Firestore (Task 2).
- Borrar un mes elimina sus subcolecciones y los objetos de Storage; Firestore no borra subcolecciones solo (Task 2).
- Con `home` de inicio, la barra inferior no parpadea al arrancar (ruta `null`) y el botón atrás de cualquier pestaña vuelve a Home (Task 5).
- Las rutas nuevas no están en `BOTTOM_NAV_ROUTES`, así que no muestran barra (Task 4 y 5).
- Probar el APK **release** en el S25: R8 rompe modelos fuera de `data/` sin avisar (Task 9).

---

### Task 1: Modelos y lógica de reparto

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitMonth.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitExpense.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitPhoto.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitLogic.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/SplitLogicTest.kt`

**Interfaces:**
- Produces:
  - `data class SplitMonth(id, name, monthKey, memberIds, createdAt)`
  - `data class SplitExpense(id, title, amountCents, payerId, participants, date, reimbursement, createdAt)`
  - `data class SplitPhoto(id, url, storagePath, note, expenseId, createdAt)`
  - `data class MemberBalance(val memberId: String, val paidCents: Long, val shareCents: Long) { val netCents: Long }`
  - `data class Settlement(val fromId: String, val toId: String, val amountCents: Long)`
  - `const val MAX_CENTS = 99_999_999L`
  - `fun splitEqually(amountCents: Long, participants: List<String>): Map<String, Long>`
  - `fun computeBalances(expenses: List<SplitExpense>, memberIds: List<String>): List<MemberBalance>`
  - `fun computeSettlements(balances: List<MemberBalance>): List<Settlement>`
  - `fun monthTotalCents(expenses: List<SplitExpense>): Long`
  - `fun monthKeyOf(date: Date, timeZone: TimeZone = TimeZone.getDefault()): String`
  - `fun isDateInMonth(date: Date, monthKey: String, timeZone: TimeZone = TimeZone.getDefault()): Boolean`
  - `fun parseCents(text: String): Long?`

- [ ] **Step 1: Crear los modelos**

`SplitMonth.kt`:

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Un mes de reparto de gastos entre los miembros de la casa (la "lista" de Tricount).
 * households/{householdCode}/splitMonths/{monthId}
 */
data class SplitMonth(
    @DocumentId
    val id: String = "",
    val name: String = "",
    /** "yyyy-MM" del mes al que pertenecen los gastos. No es único: puede haber varios del mismo mes. */
    val monthKey: String = "",
    /** Uids de los miembros que participan en este mes. */
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null
)
```

`SplitExpense.kt`:

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Un gasto repartido a partes iguales entre [participants]. Un reembolso es un gasto con
 * [reimbursement] = true, pagador = quien devuelve y participantes = [receptor]: para los saldos
 * es un gasto más, y solo se excluye del total del mes.
 * households/{householdCode}/splitMonths/{monthId}/expenses/{expenseId}
 *
 * El campo se llama `reimbursement` y no `isReimbursement` porque Firestore quita el prefijo
 * `is` al mapear propiedades Kotlin y el nombre guardado no coincidiría con el leído.
 */
data class SplitExpense(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val amountCents: Long = 0L,
    val payerId: String = "",
    val participants: List<String> = emptyList(),
    val date: Date? = null,
    val reimbursement: Boolean = false,
    @ServerTimestamp
    val createdAt: Date? = null
)
```

`SplitPhoto.kt`:

```kotlin
package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Foto de un mes de reparto, suelta o ligada a un gasto.
 * households/{householdCode}/splitMonths/{monthId}/photos/{photoId}
 */
data class SplitPhoto(
    @DocumentId
    val id: String = "",
    /** URL de descarga, para pintarla con Coil. */
    val url: String = "",
    /** Ruta en Storage, necesaria para borrar el objeto (la URL no sirve para eso). */
    val storagePath: String = "",
    val note: String = "",
    val expenseId: String? = null,
    @ServerTimestamp
    val createdAt: Date? = null
)
```

- [ ] **Step 2: Escribir los tests (fallan porque no existe `SplitLogic.kt`)**

Crear `SplitLogicTest.kt`:

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class SplitLogicTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")

    private fun e(payer: String, cents: Long, vararg who: String, reimbursement: Boolean = false) =
        SplitExpense(
            title = "x", amountCents = cents, payerId = payer,
            participants = who.toList(), reimbursement = reimbursement
        )

    private fun net(balances: List<MemberBalance>, id: String) =
        balances.first { it.memberId == id }.netCents

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Date =
        Calendar.getInstance(madrid).apply { clear(); set(y, m - 1, d, h, min) }.time

    private val scenario = listOf(
        e("a", 1001, "a", "b", "c"),
        e("b", 2599, "a", "b"),
        e("c", 333, "a", "b", "c"),
        e("a", 7, "b", "c"),
        e("b", 1500, "a", reimbursement = true)
    )

    // --- splitEqually ---

    @Test fun `splitEqually gives the extra cents to the first ids in alphabetical order`() {
        val r = splitEqually(100, listOf("a", "b", "c"))
        assertEquals(mapOf("a" to 34L, "b" to 33L, "c" to 33L), r)
        assertEquals(100L, r.values.sum())
    }

    @Test fun `splitEqually does not depend on the list order`() {
        assertEquals(splitEqually(100, listOf("a", "b", "c")), splitEqually(100, listOf("c", "a", "b")))
    }

    @Test fun `splitEqually one cent between two`() {
        assertEquals(mapOf("a" to 1L, "b" to 0L), splitEqually(1, listOf("b", "a")))
    }

    @Test fun `splitEqually ignores duplicates and handles empty`() {
        assertEquals(mapOf("a" to 50L, "b" to 50L), splitEqually(100, listOf("a", "a", "b")))
        assertTrue(splitEqually(100, emptyList()).isEmpty())
    }

    // --- computeBalances ---

    @Test fun `balances of one expense among everyone`() {
        val b = computeBalances(listOf(e("a", 3000, "a", "b", "c")), listOf("a", "b", "c"))
        assertEquals(2000L, net(b, "a"))
        assertEquals(-1000L, net(b, "b"))
        assertEquals(-1000L, net(b, "c"))
    }

    @Test fun `balances always add up to zero`() {
        val b = computeBalances(scenario, listOf("a", "b", "c"))
        assertEquals(0L, b.sumOf { it.netCents })
    }

    @Test fun `partial participants leave the rest untouched`() {
        val b = computeBalances(listOf(e("a", 1000, "b")), listOf("a", "b", "c"))
        assertEquals(1000L, net(b, "a"))
        assertEquals(-1000L, net(b, "b"))
        assertEquals(0L, net(b, "c"))
    }

    @Test fun `a reimbursement reduces the debt without being a new expense`() {
        val b = computeBalances(
            listOf(e("a", 3000, "a", "b", "c"), e("b", 1000, "a", reimbursement = true)),
            listOf("a", "b", "c")
        )
        assertEquals(1000L, net(b, "a"))
        assertEquals(0L, net(b, "b"))
        assertEquals(-1000L, net(b, "c"))
    }

    @Test fun `expenses without participants or amount are ignored`() {
        val b = computeBalances(listOf(e("a", 1000), e("a", 0, "a", "b")), listOf("a", "b"))
        assertTrue(b.all { it.paidCents == 0L && it.shareCents == 0L })
    }

    @Test fun `ids that are no longer members still show up and keep the sum at zero`() {
        val b = computeBalances(listOf(e("old", 900, "old", "a", "b")), listOf("a", "b"))
        assertEquals(3, b.size)
        assertEquals(0L, b.sumOf { it.netCents })
    }

    @Test fun `no expenses gives zero for every member`() {
        val b = computeBalances(emptyList(), listOf("a", "b"))
        assertEquals(2, b.size)
        assertTrue(b.all { it.netCents == 0L })
    }

    @Test fun `balances are sorted by net descending`() {
        val b = computeBalances(listOf(e("b", 3000, "a", "b", "c")), listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), b.map { it.memberId })
    }

    // --- computeSettlements ---

    @Test fun `settlements pay the creditor`() {
        val b = computeBalances(listOf(e("a", 3000, "a", "b", "c")), listOf("a", "b", "c"))
        assertEquals(
            listOf(Settlement("b", "a", 1000), Settlement("c", "a", 1000)),
            computeSettlements(b).sortedBy { it.fromId }
        )
    }

    @Test fun `one debtor pays two creditors`() {
        val b = listOf(
            MemberBalance("a", 3000, 0), MemberBalance("b", 1000, 0), MemberBalance("c", 0, 4000)
        )
        assertEquals(
            listOf(Settlement("c", "a", 3000), Settlement("c", "b", 1000)),
            computeSettlements(b)
        )
    }

    @Test fun `no settlements when everything is even`() {
        assertTrue(computeSettlements(computeBalances(emptyList(), listOf("a", "b"))).isEmpty())
    }

    @Test fun `applying the settlements leaves everyone at zero with at most n-1 payments`() {
        val members = listOf("a", "b", "c", "d")
        val expenses = scenario + e("d", 4321, "a", "b", "c", "d")
        val balances = computeBalances(expenses, members)
        val settlements = computeSettlements(balances)
        val nets = balances.associate { it.memberId to it.netCents }.toMutableMap()
        settlements.forEach {
            nets[it.fromId] = nets.getValue(it.fromId) + it.amountCents
            nets[it.toId] = nets.getValue(it.toId) - it.amountCents
        }
        assertTrue(nets.values.all { it == 0L })
        assertTrue(settlements.size <= members.size - 1)
        assertTrue(settlements.all { it.amountCents > 0 })
    }

    // --- monthTotalCents ---

    @Test fun `month total excludes reimbursements`() {
        assertEquals(1001L + 2599 + 333 + 7, monthTotalCents(scenario))
        assertEquals(0L, monthTotalCents(emptyList()))
    }

    // --- monthKeyOf / isDateInMonth ---

    @Test fun `monthKeyOf uses the given time zone`() {
        assertEquals("2026-10", monthKeyOf(at(2026, 10, 31, 23, 30), madrid))
        // 00:30 del día 1 en Madrid son las 23:30 del día anterior en UTC.
        assertEquals("2026-11", monthKeyOf(at(2026, 11, 1, 0, 30), madrid))
    }

    @Test fun `isDateInMonth compares against the key`() {
        assertTrue(isDateInMonth(at(2026, 10, 15, 12, 0), "2026-10", madrid))
        assertFalse(isDateInMonth(at(2026, 11, 1, 0, 30), "2026-10", madrid))
    }

    // --- parseCents ---

    @Test fun `parseCents accepts comma and dot decimals`() {
        assertEquals(1250L, parseCents("12,5"))
        assertEquals(1250L, parseCents("12.50"))
        assertEquals(1200L, parseCents("12"))
        assertEquals(1L, parseCents("0,01"))
        assertEquals(123456L, parseCents("1234,56"))
        assertEquals(700L, parseCents(" 7 "))
    }

    @Test fun `parseCents rejects what is not a positive amount`() {
        assertNull(parseCents(""))
        assertNull(parseCents("abc"))
        assertNull(parseCents("-5"))
        assertNull(parseCents("0"))
        assertNull(parseCents("0,00"))
        assertNull(parseCents("1,234"))
        assertNull(parseCents("1.234,56"))
        assertNull(parseCents("12,5,3"))
        assertNull(parseCents("5,"))
    }

    @Test fun `parseCents has an upper bound`() {
        assertEquals(99_999_999L, parseCents("999999,99"))
        assertNull(parseCents("1000000"))
        assertNull(parseCents("99999999999999999999"))
    }
}
```

- [ ] **Step 3: Ejecutar los tests y comprobar que fallan**

`./gradlew testDebugUnitTest --tests "com.homepantry.app.data.SplitLogicTest"` → error de compilación (funciones sin definir).

- [ ] **Step 4: Implementar `SplitLogic.kt`**

```kotlin
package com.homepantry.app.data

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Importe máximo aceptado: 999.999,99 €. Evita desbordes y typos absurdos. */
const val MAX_CENTS = 99_999_999L

/** Lo pagado y la parte que le toca a un miembro. Positivo = le deben, negativo = debe. */
data class MemberBalance(val memberId: String, val paidCents: Long, val shareCents: Long) {
    val netCents: Long get() = paidCents - shareCents
}

/** [fromId] paga [amountCents] a [toId]. */
data class Settlement(val fromId: String, val toId: String, val amountCents: Long)

/**
 * Reparte [amountCents] a partes iguales. Los céntimos sobrantes van de uno en uno a los
 * primeros ids en orden alfabético: así el resultado es determinista, no depende del orden en
 * que se guardó la lista y siempre suma exactamente [amountCents]. Espera un importe >= 0.
 */
fun splitEqually(amountCents: Long, participants: List<String>): Map<String, Long> {
    val ids = participants.distinct().sorted()
    if (ids.isEmpty()) return emptyMap()
    val base = amountCents / ids.size
    val remainder = (amountCents % ids.size).toInt()
    return ids.withIndex().associate { (index, id) -> id to base + if (index < remainder) 1 else 0 }
}

/**
 * Saldo de cada miembro. Un reembolso no necesita rama propia: quien devuelve suma lo pagado y
 * quien recibe suma su parte, igual que en un gasto normal con un único participante.
 *
 * Incluye a los miembros del mes y a cualquier uid que aparezca en un gasto (por ejemplo, un
 * dispositivo reinstalado): si se omitiera, la suma de saldos dejaría de ser 0.
 * Los gastos sin participantes o sin importe positivo se ignoran enteros, por la misma razón.
 */
fun computeBalances(expenses: List<SplitExpense>, memberIds: List<String>): List<MemberBalance> {
    val paid = HashMap<String, Long>()
    val share = HashMap<String, Long>()
    for (expense in expenses) {
        if (expense.participants.isEmpty() || expense.amountCents <= 0) continue
        paid[expense.payerId] = (paid[expense.payerId] ?: 0L) + expense.amountCents
        splitEqually(expense.amountCents, expense.participants).forEach { (id, cents) ->
            share[id] = (share[id] ?: 0L) + cents
        }
    }
    val ids = (memberIds + paid.keys + share.keys).distinct()
    return ids
        .map { MemberBalance(it, paid[it] ?: 0L, share[it] ?: 0L) }
        .sortedWith(compareByDescending<MemberBalance> { it.netCents }.thenBy { it.memberId })
}

/**
 * Pagos para dejar todos los saldos a 0: empareja el mayor deudor con el mayor acreedor. Da como
 * mucho n-1 pagos. No siempre es el mínimo absoluto (es un problema NP-difícil), pero para un
 * grupo doméstico es más que suficiente y el resultado es determinista.
 */
fun computeSettlements(balances: List<MemberBalance>): List<Settlement> {
    val order = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
    val debtors = balances.filter { it.netCents < 0 }.map { it.memberId to -it.netCents }.toMutableList()
    val creditors = balances.filter { it.netCents > 0 }.map { it.memberId to it.netCents }.toMutableList()
    val result = mutableListOf<Settlement>()
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        debtors.sortWith(order)
        creditors.sortWith(order)
        val (debtorId, debt) = debtors.first()
        val (creditorId, credit) = creditors.first()
        val amount = minOf(debt, credit)
        result += Settlement(debtorId, creditorId, amount)
        if (debt == amount) debtors.removeAt(0) else debtors[0] = debtorId to debt - amount
        if (credit == amount) creditors.removeAt(0) else creditors[0] = creditorId to credit - amount
    }
    return result
}

/** Total gastado en el mes. Los reembolsos mueven dinero entre miembros, no son gasto. */
fun monthTotalCents(expenses: List<SplitExpense>): Long =
    expenses.filter { !it.reimbursement }.sumOf { it.amountCents }

/** "yyyy-MM" de [date] en [timeZone]. La zona se inyecta para que los tests no dependan del equipo. */
fun monthKeyOf(date: Date, timeZone: TimeZone = TimeZone.getDefault()): String {
    val calendar = Calendar.getInstance(timeZone).apply { time = date }
    return String.format(Locale.ROOT, "%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

fun isDateInMonth(date: Date, monthKey: String, timeZone: TimeZone = TimeZone.getDefault()): Boolean =
    monthKeyOf(date, timeZone) == monthKey

private val CENTS_REGEX = Regex("^(\\d{1,7})(?:[.,](\\d{1,2}))?$")

/**
 * Importe escrito por el usuario a céntimos. Acepta `12`, `12,5` y `12.50`. Rechaza vacío,
 * negativos, cero, más de dos decimales y separador de miles ("1.234,56" es ambiguo), y lo que
 * pase de [MAX_CENTS].
 */
fun parseCents(text: String): Long? {
    val match = CENTS_REGEX.matchEntire(text.trim()) ?: return null
    val euros = match.groupValues[1].toLong()
    val fraction = match.groupValues[2].padEnd(2, '0').toLong()
    val cents = euros * 100 + fraction
    return if (cents in 1..MAX_CENTS) cents else null
}
```

- [ ] **Step 5: Ejecutar los tests y comprobar que pasan**

`./gradlew testDebugUnitTest --tests "com.homepantry.app.data.SplitLogicTest"` → 0 fallos. Si `monthKeyOf` falla en un equipo con otra zona horaria, es un bug: el test pasa la zona explícita.

---

### Task 2: Repositorios y Storage

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitMonthsRepository.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitExpensesRepository.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitPhotosRepository.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/StorageRepository.kt`

**Interfaces:**
- Consumes: `SplitMonth`, `SplitExpense`, `SplitPhoto` (Task 1), `StorageRepository`.
- Produces:
  - `SplitMonthsRepository(firestore, householdCode)`: `observeMonths(): Flow<List<SplitMonth>>`, `createMonth(name, monthKey, memberIds): String`, `rename(monthId, name)`, `delete(monthId, photoPaths: List<String>)`.
  - `SplitExpensesRepository(firestore, householdCode)`: `observeExpenses(monthId): Flow<List<SplitExpense>>`, `add(monthId, expense)`, `update(monthId, expense)`, `delete(monthId, expenseId)`.
  - `SplitPhotosRepository(firestore, householdCode)`: `observePhotos(monthId): Flow<List<SplitPhoto>>`, `add(monthId, photo): String`, `delete(monthId, photoId)`, `newPhotoId(monthId): String`.
  - `StorageRepository.uploadSplitPhoto(monthId, photoId, localUri): SplitUpload` con `data class SplitUpload(val url: String, val storagePath: String)`, y `deleteSplitPhoto(storagePath)`.

- [ ] **Step 1: Repositorios con el patrón de `MembersRepository`/`PeopleRepository`**

`collection()` privado con `households/{code}/splitMonths` (y la subcolección con `monthId`). Lecturas con `callbackFlow` + `addSnapshotListener` + `awaitClose`. Escrituras `suspend` con `.await()`. Las lecturas ordenan en memoria (meses por `monthKey` descendente, gastos por `date` descendente, fotos por `createdAt`), sin índices compuestos.

- [ ] **Step 2: `update` de gasto**

Usar `update(mapOf("title" to ..., "amountCents" to ..., "payerId" to ..., "participants" to ..., "date" to ..., "reimbursement" to ...))` sobre el documento. No `set` del objeto: no pisa `createdAt` ni cambios de otro miembro en campos no editados.

- [ ] **Step 3: Borrado de un mes**

`delete(monthId, photoPaths)`: leer `expenses` y `photos` del mes, borrarlos en batches de 400 como el resto de operaciones masivas del proyecto, borrar el documento del mes al final y, fuera del batch, los objetos de Storage de `photoPaths` (el ViewModel los pasa desde su estado; el repositorio no depende de Storage). Si un objeto de Storage ya no existe, se ignora.

- [ ] **Step 4: Storage**

En `StorageRepository`:
- `uploadSplitPhoto(monthId, photoId, localUri)`: reusa `compressOffMainThread`, sube a `households/$householdCode/splitMonths/$monthId/$photoId.jpg` y devuelve `SplitUpload(url, path)`.
- `deleteSplitPhoto(storagePath)`: `storage.reference.child(path).delete().await()`, capturando `StorageException` con `errorCode == StorageException.ERROR_OBJECT_NOT_FOUND` para ignorarlo. `CancellationException` se relanza.

- [ ] **Step 5: Compilar**

`./gradlew assembleDebug` sin errores. Sin tests nuevos: los repositorios no se testean (sin Robolectric), como el resto.

---

### Task 3: SplitViewModel

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/SplitViewModel.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/ui/SplitUiStateTest.kt` (solo si las derivadas se pueden probar sin Android; ver Step 3)

**Interfaces:**
- Consumes: repositorios de Task 2, `MembersRepository`, `StorageRepository`, `SplitLogic`.
- Produces:
  - `data class SplitUiState(months, members, openMonthId, expenses, photos, loading, error)` con derivadas `openMonth`, `balances`, `settlements`, `monthTotalCents`, `memberName(id)`.
  - `class SplitViewModel(monthsRepository, expensesRepository, photosRepository, membersRepository, storageRepository, userName, uid)`.
  - Acciones: `openMonth(id)`, `closeMonth()`, `createMonth(name, monthKey, memberIds)`, `renameMonth`, `deleteMonth`, `saveExpense(...)`, `deleteExpense`, `settle(settlement)`, `addPhoto(uri, note, expenseId)`, `deletePhoto`, `clearError()`.

- [ ] **Step 1: Estado y observación**

Un único `MutableStateFlow<SplitUiState>`. Meses y miembros se observan siempre. Gastos y fotos del mes abierto con `flatMapLatest` sobre un `MutableStateFlow<String?>` (`null` → lista vacía). Errores de los flows a `error`, sin tumbar la app.

- [ ] **Step 2: Acciones con validación**

`saveExpense` devuelve un resultado (éxito o motivo) para que el sheet pinte el error. Valida: título no vacío, `parseCents(text) != null`, al menos un participante, pagador entre los miembros del mes y, si no es reembolso, `isDateInMonth(date, month.monthKey)`. `settle` crea un `SplitExpense(title = "Reembolso", amountCents, payerId = from, participants = listOf(to), reimbursement = true, date = Date())`. `addPhoto` genera el id con `newPhotoId`, sube, y solo entonces crea el documento. Si falla la subida, no queda documento huérfano. `deleteMonth` pasa al repositorio los `storagePath` de las fotos del mes.

- [ ] **Step 3: Derivadas**

`balances = computeBalances(expenses, openMonth.memberIds)`, `settlements = computeSettlements(balances)`, `monthTotalCents = monthTotalCents(expenses)`. Si `SplitUiState` no arrastra dependencias de Android, añadir `SplitUiStateTest` con un mes y tres gastos que compruebe que las derivadas coinciden con `SplitLogic`. Si las arrastra, omitir el test: la lógica ya está cubierta en Task 1.

- [ ] **Step 4: Compilar**

`./gradlew assembleDebug`.

---

### Task 4: Lista de meses y detalle (esqueleto)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthsScreen.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthFormSheet.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SplitViewModel` (Task 3).
- Produces: rutas `splitMonths` y `splitMonth/{monthId}`.

- [ ] **Step 1: Crear el `SplitViewModel` en `ListaDeLaCasaApp`**

Mismo patrón que `menuViewModel`: `viewModel(key = "split-$code", factory = remember(code) { ... })` con repositorios de `(firestore, code)`, `MembersRepository`, el `StorageRepository` ya creado y `name`/`uid`. Llamar a `updateUserName(name)` en el `LaunchedEffect` existente si el ViewModel lo expone.

- [ ] **Step 2: `SplitMonthsScreen`**

`Scaffold` con `TopAppBar` (título "Reparto", botón atrás) y FAB para crear. Lista de meses con nombre y nombres de participantes. Menú por mes con Renombrar y Borrar (diálogo de confirmación, coral). Estado vacío con texto y botón. Mismo estilo de tarjeta que `ZoneCard`/`PurchaseMonthsTab`.

- [ ] **Step 3: `SplitMonthFormSheet`**

Bottom sheet de alta: nombre (por defecto "Mes Año" en es-ES del mes actual, p. ej. "Octubre 2026"), selector de mes y año, y casillas de participantes (todos marcados por defecto; usar el patrón de `PersonPicker` si encaja). Mínimo un participante. El nombre por defecto se genera en la UI con `SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag("es-ES"))`, sin test (depende de los datos de idioma del JDK).

- [ ] **Step 4: `SplitMonthScreen` con tres pestañas vacías**

`TopAppBar` con el nombre del mes y atrás, `TabRow` con Gastos, Saldos y Fotos, y un `Text` de marcador en cada una. Las pestañas se rellenan en Task 6, 7 y 8. Al entrar llama a `openMonth(monthId)`; al salir, `closeMonth()`.

- [ ] **Step 5: Rutas**

Añadir `composable("splitMonths")` y `composable("splitMonth/{monthId}")` al `NavHost`. No añadirlas a `BOTTOM_NAV_ROUTES`. Textos nuevos en `strings.xml` (con `plurals` para el número de participantes).

- [ ] **Step 6: Verificar**

`./gradlew assembleDebug`. Abrir `splitMonths` a mano (aún sin Home) y comprobar: crear mes, aparece en la lista, abrirlo, renombrar, borrar.

---

### Task 5: Pantalla Home y navegación

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/HomeScreen.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/components/HomeCard.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AppViewModel.state` (productos pendientes, `purchases`), `monthSummary(...)`, `SplitViewModel.state.months`.
- Produces: ruta `home` como `startDestination`.

- [ ] **Step 1: `HomeCard`**

Composable reutilizable: `Surface` `NocturneSurface` con esquinas redondeadas, `Row` con icono en círculo `primaryContainer` (tinte `primary`), columna con título (`titleMedium`) y resumen (`bodyMedium`, `onSurfaceVariant`), y chevron. Toda la tarjeta es clicable. Parámetros: `icon`, `title`, `summary`, `onClick`.

- [ ] **Step 2: `HomeScreen`**

`Scaffold` con `TopAppBar` y acción de ajustes (`Icons.Filled.Settings`, descripción `main_settings_cd` ya existente) hacia `manageZones`. Debajo, saludo y fecha con los mismos recursos y formato que `ZonesDashboardScreen` (`dashboard_greeting`), y tres `HomeCard` apilados con separación de 12 dp:
- Almacén (`Inventory2`): `pluralStringResource` con los productos pendientes, o "Todo al día" si es 0. Destino `zonesDashboard`.
- Gastos (`Receipt`): total del mes con `monthSummary(state.purchases).total` formateado es-ES, o "Sin tickets este mes" si `ticketCount == 0`. Destino `purchaseHistory`.
- Reparto (`Groups`): número de meses (`plurals`), o "Sin meses todavía". Destino `splitMonths`.

Contenido con scroll por si la fuente del sistema es grande.

- [ ] **Step 3: Cambios en `MainActivity.kt`**

- `startDestination = "home"` y `composable("home") { HomeScreen(...) }`.
- Añadir `BottomNavItem("home", stringResource(R.string.nav_inicio), Icons.Filled.Home)` como **primer** elemento de la barra.
- `showBottomBar` exige `currentRoute != null && (BOTTOM_NAV_ROUTES.contains(currentRoute) || currentRoute.startsWith("zoneDetail/"))`. `home` no entra en `BOTTOM_NAV_ROUTES`. Sin el `!= null`, la barra aparece un fotograma al arrancar.
- `onSelect` de la barra no cambia: `popUpTo(startDestination)` ahora apunta a `home`, así que el botón atrás desde cualquier pestaña vuelve a Home.
- Tarjeta Almacén: `navController.navigate("zonesDashboard")`.

- [ ] **Step 4: Verificar**

`./gradlew assembleDebug`. Comprobar a mano:
1. Arranque en Home, sin barra inferior, sin parpadeo.
2. Cada tarjeta abre su destino; atrás vuelve a Home.
3. En Almacén, la barra tiene cinco destinos; Inicio lleva a Home; cambiar de pestaña y pulsar atrás vuelve a Home.
4. Ajustes desde Home abre `manageZones` y atrás vuelve a Home.
5. Atrás en Home cierra la app.

---

### Task 6: Pestaña Gastos y alta de gasto

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitExpensesTab.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitExpenseSheet.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SplitViewModel.saveExpense`, `deleteExpense`, `SplitUiState`.

- [ ] **Step 1: `SplitExpensesTab`**

Cabecera con el total del mes (`monthTotalCents`, formato es-ES). Lista ordenada por fecha descendente: título, "Pagó X · entre N", fecha e importe. Los reembolsos con una etiqueta propia y sin sumar al total. FAB para añadir. Pulsar un gasto abre el sheet de edición. Estado vacío con texto.

- [ ] **Step 2: `SplitExpenseSheet`**

Bottom sheet con estado local, como `AddItemSheet`. Campos: título, importe (teclado decimal, validado con `parseCents`), pagador (por defecto el usuario actual si es miembro del mes), "repartido entre" (casillas, todos por defecto, mínimo uno) y fecha (`DatePicker` limitado al mes). En edición, botón Borrar en coral con confirmación. Errores de validación bajo cada campo, con los textos de `strings.xml`.

- [ ] **Step 3: Pie con el reparto**

Bajo el importe, texto "Cada uno: X €" con `splitEqually` cuando hay importe y participantes válidos, mostrando el sobrante si el reparto no es exacto (p. ej. "33,34 € a uno, 33,33 € al resto"). Sirve de comprobación visual de los céntimos.

- [ ] **Step 4: Verificar**

Crear gastos con 2 y 3 participantes, importes con coma y con punto, y uno de 100,00 € entre 3. Editar y borrar. Comprobar que los datos aparecen en un segundo móvil (o en la consola de Firebase) y que la fecha fuera del mes se rechaza.

---

### Task 7: Pestaña Saldos

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitBalancesTab.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SplitUiState.balances`, `settlements`, `SplitViewModel.settle`.

- [ ] **Step 1: Saldos por miembro**

Una fila por miembro, en el orden de `balances` (saldo descendente: quien más ha adelantado arriba). Muestra nombre (`memberName`), "Pagó X · Le toca Y" y el saldo neto: positivo con `primary` ("le deben"), negativo en coral ("debe"), cero en gris. Si el uid ya no está en `members`, mostrar un nombre genérico ("Miembro anterior").

- [ ] **Step 2: Quién debe a quién**

Debajo, una fila por `Settlement`: "A paga X € a B", con botón **Saldar**. Saldar pide confirmación y llama a `settle`; el saldo se recalcula solo por el listener. Sin pagos pendientes, un texto "Todo saldado". Si el usuario actual es el deudor, resaltar su fila.

- [ ] **Step 3: Verificar**

Con tres miembros y cuatro gastos, comprobar a mano: la suma de saldos es 0, los pagos sugeridos dejan todo a 0 y, tras pulsar Saldar en todos, la pantalla muestra "Todo saldado" y el total del mes no ha cambiado.

---

### Task 8: Pestaña Fotos

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitPhotosTab.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SplitUiState.photos`, `SplitViewModel.addPhoto`, `deletePhoto`, `createReceiptCaptureUri`.

- [ ] **Step 1: Galería**

`LazyVerticalGrid` de 3 columnas con `AsyncImage` de Coil (ya se usa en `AddItemSheet`) y `ContentScale.Crop`. Pulsar una foto abre una vista ampliada con la nota, el gasto ligado (si hay) y un botón Borrar en coral con confirmación. Estado vacío con texto.

- [ ] **Step 2: Subida**

FAB con dos opciones, cámara y galería, usando los mismos lanzadores y permisos que `PurchaseHistoryScreen` (`ActivityResultContracts.TakePicture` con `createReceiptCaptureUri` y `PickVisualMedia`). Tras elegir la imagen, un sheet pide una nota opcional y, opcionalmente, un gasto del mes al que ligarla. Indicador de progreso mientras sube y mensaje de error si falla.

- [ ] **Step 3: Verificar**

Subir una foto desde cámara y otra desde galería, ligar una a un gasto, borrar una y comprobar en la consola de Firebase que el documento y el objeto de Storage desaparecen. Probar sin red: el error se muestra y no queda documento huérfano.

---

### Task 9: Verificación completa

**Files:** ninguno (solo comprobaciones).

- [ ] **Step 1: Build completo**

`./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`. Debe acabar con 0 fallos de test, 0 errores de lint y un `app-release.apk` de tamaño parecido al actual (unos 4,5 MB; no debería crecer de forma apreciable).

- [ ] **Step 2: Prueba del APK release en el S25**

R8 solo se ejercita en release. Repetir los recorridos de Task 4 a 8 con el APK release y comprobar en concreto que los gastos se guardan y se leen con el campo `reimbursement` (hacer un reembolso con Saldar) y que las fotos aparecen.

- [ ] **Step 3: Regresión**

Comprobar que sigue funcionando: lista de la compra, zonas, menú, búsqueda, escaneo de tickets y estadísticas. El único cambio visible fuera del reparto es la entrada por Home y la pestaña Inicio de la barra.

- [ ] **Step 4: Documentación**

Actualizar `CLAUDE.md`: en "Arquitectura" mencionar `home` como destino inicial y las colecciones `splitMonths` (con `expenses` y `photos`), y en "Lógica pura" añadir `SplitLogic`. Añadir `SplitViewModel` junto a los otros dos ViewModels.

---

## Fase posterior (no incluida)

Captura de cargos con Gemini: un `GeminiExpenseRecognizer` calcado de `GeminiReceiptRecognizer`, con pantalla de revisión (nada se guarda sin confirmar), deduplicación por fecha + importe + comercio y asignación de cada cargo al mes según su fecha, creándolo si no existe. Requiere su propio spec.
