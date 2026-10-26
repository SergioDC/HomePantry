# SNcount: pantalla de gasto, reparto por importes y opciones — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir la hoja de gasto de SNcount por una pantalla completa con emoji y reparto igual o por importes, y añadir al SNcount un menú ⋮ (Editar, Compartir con enlace que abre la app, Archivar y Eliminar).

**Architecture:** Los campos nuevos (`emoji`, `shares`, `archived`) se añaden a los modelos de Firestore con valores por defecto compatibles con los datos actuales. Toda la lógica nueva va en funciones puras con tests (`SplitLogic.kt`, `SplitLink.kt`, `SplitMonthHolders`). El mes abierto en `SplitViewModel` deja de ir ligado a la composición de una pantalla y pasa a ir ligado al ciclo de vida de las entradas de navegación, para que varias pantallas (SNcount, gasto, editar) compartan el mismo mes abierto.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose (rutas en string), Firestore, Firebase Hosting + Android App Links, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-23-sncount-gasto-y-opciones-design.md` (bocetos en `.superpowers/brainstorm/547-1792750326/content/pantallas.html`).

## Global Constraints

- Rama `sncount-gasto`. Un commit por tarea, en español y con prefijo convencional. Sin merge ni push sin preguntar al usuario.
- `CLAUDE.md` manda: textos en `res/values/strings.xml` (con `plurals`), comentarios y KDoc en español explicando el porqué, `CancellationException` siempre relanzada, `update(campo, valor)` para cambios parciales, modelos con valores por defecto en todos los campos.
- SNcount va sin barra inferior y con flecha atrás. Las rutas nuevas no entran en `BOTTOM_NAV_ROUTES`.
- Tema Nocturne y piezas existentes: `SpendSectionTitle`, `SpendHeaderCard`, `IconTile`, `MemberAvatar`, `ColorDot`, `memberColors`/`memberColor`, `groupColors`, `StripedCard`. El coral (`colorScheme.error`) solo para borrar y para los avisos de «Faltan/Sobran».
- Importes en céntimos (`Long`). Los gastos sin `shares` se reparten a partes iguales, exactamente como hoy.
- Sin librerías nuevas. No tocar JDK, SDKs, `abiFilters`, `localeFilters`, R8, AGP 8.x.
- Dominio del enlace: `https://home-pantry-bed42.web.app/sncount/{monthId}`.
- `firebase deploy` es una acción externa: se le pide permiso al usuario o la lanza él.

## Review Focus

1. **Gastos antiguos sin `shares`** (todos los de hoy): deben dar exactamente los mismos saldos que antes. Lo cubre el test `expenseShares falls back to equal split` y `balances are unchanged for expenses without shares` (Tarea 1).
2. **Modo por importes con alguien en blanco o a 0**: ese participante queda fuera, no es un error. Test `blank or zero share means not participating` (Tarea 1).
3. **Cambiar el total después de escribir los importes**: no se puede guardar hasta volver a cuadrar. Test `shares that no longer match the total are rejected` (Tarea 1).
4. **Ir del SNcount al gasto y volver**: los gastos del mes no se vacían ni se recargan, y salir del todo sí cierra el mes. Tests de `SplitMonthHolders` (Tarea 2).
5. **Enlaces con otra forma**: barra final, parámetros de query, otro host o ruta sin id. Tests `splitMonthIdFromLink` (Tarea 4).

---

### Task 1: Modelos y lógica pura del reparto

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/SplitExpense.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/SplitMonth.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/SplitLogic.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/SplitLogicTest.kt`

**Interfaces:**
- Produces:
  - `SplitExpense.emoji: String = ""` y `SplitExpense.shares: Map<String, Long> = emptyMap()`, con KDoc: vacío = icono automático / reparto igual entre `participants`. Si `shares` no está vacío, `participants` son sus claves con importe > 0, en el orden de los miembros del mes.
  - `SplitMonth.emoji: String = ""` y `SplitMonth.archived: Boolean = false` (nombre sin `is`, por la trampa de Firestore ya documentada).
  - `fun expenseShares(expense: SplitExpense): Map<String, Long>`
  - `fun parseShareCents(text: String): Long?`: blanco → 0; si no, `parseCents`; un `0` o `0,00` escrito → 0; inválido → null.
  - `fun parseShares(shareTexts: Map<String, String>): Map<String, Long>?`: null si algún texto es inválido; si no, solo las entradas > 0.
  - `fun sharesRemainderCents(totalCents: Long, shares: Map<String, Long>): Long` = total − suma (positivo: faltan; negativo: sobran).
  - `SplitExpenseError.SHARES` (nuevo valor del enum).
  - `validateSplitExpense(…, timeZone, shareTexts: Map<String, String>? = null)`: parámetro nuevo al final. `null` = modo igual (comportamiento actual). Si no es null: `SHARES` si `parseShares` da null o, con importe válido, si la suma ≠ total; `PARTICIPANTS` si `parseShares` no tiene ninguna entrada > 0. En modo por importes, `participants` no se mira.

- [ ] **Step 1: Escribir los tests que fallan** en `SplitLogicTest.kt` (reutilizar los helpers `e`, `net` y `october`/`madrid` que ya hay):

```kotlin
@Test fun `expenseShares falls back to equal split`() {
    assertEquals(splitEqually(1001, listOf("a", "b", "c")), expenseShares(e("a", 1001, "a", "b", "c")))
}

@Test fun `expenseShares uses explicit shares when present`() {
    val x = e("a", 6000, "a", "b").copy(shares = mapOf("a" to 4000L, "b" to 2000L))
    assertEquals(mapOf("a" to 4000L, "b" to 2000L), expenseShares(x))
}

@Test fun `balances are unchanged for expenses without shares`() {
    // Sin `shares` debe dar lo mismo que con `shares` igual a splitEqually: el reparto de siempre.
    val explicit = scenario.map { it.copy(shares = splitEqually(it.amountCents, it.participants)) }
    val members = listOf("a", "b", "c")
    assertEquals(computeBalances(explicit, members), computeBalances(scenario, members))
    assertEquals(0L, computeBalances(scenario, members).sumOf { it.netCents })
}

@Test fun `balances with mixed equal and explicit shares sum to zero`() {
    val mixed = scenario + e("c", 6000, "a", "b", "c").copy(shares = mapOf("a" to 1000L, "b" to 2000L, "c" to 3000L))
    val balances = computeBalances(mixed, listOf("a", "b", "c"))
    assertEquals(0L, balances.sumOf { it.netCents })
    val before = computeBalances(scenario, listOf("a", "b", "c"))
    assertEquals(net(before, "a") - 1000, net(balances, "a"))
    assertEquals(net(before, "c") + 3000, net(balances, "c"))
}

@Test fun `myShareCents uses explicit shares`() {
    val x = e("a", 6000, "a", "b").copy(shares = mapOf("a" to 4500L, "b" to 1500L))
    assertEquals(1500L, myShareCents(listOf(x), "b"))
}

@Test fun `blank or zero share means not participating`() {
    assertEquals(mapOf("a" to 2500L), parseShares(mapOf("a" to "25", "b" to "", "c" to "0")))
    assertEquals(0L, parseShareCents("  "))
    assertEquals(0L, parseShareCents("0,00"))
    assertNull(parseShareCents("-3"))
    assertNull(parseShares(mapOf("a" to "abc")))
}

@Test fun `sharesRemainderCents reports missing and extra`() {
    assertEquals(320L, sharesRemainderCents(6000, mapOf("a" to 2500L, "b" to 1800L, "c" to 1380L)))
    assertEquals(-100L, sharesRemainderCents(6000, mapOf("a" to 6100L)))
}

@Test fun `shares that no longer match the total are rejected`() {
    val ok = validateSplitExpense("Cena", "60", "a", emptyList(), at(2026, 10, 5, 12, 0), false, october, madrid,
        shareTexts = mapOf("a" to "40", "b" to "20"))
    assertTrue(ok.isEmpty())
    val changedTotal = validateSplitExpense("Cena", "65", "a", emptyList(), at(2026, 10, 5, 12, 0), false, october, madrid,
        shareTexts = mapOf("a" to "40", "b" to "20"))
    assertEquals(setOf(SplitExpenseError.SHARES), changedTotal)
}

@Test fun `manual mode needs at least one positive share`() {
    val errors = validateSplitExpense("Cena", "60", "a", emptyList(), at(2026, 10, 5, 12, 0), false, october, madrid,
        shareTexts = mapOf("a" to "", "b" to "0"))
    assertTrue(SplitExpenseError.PARTICIPANTS in errors)
}
```

  Si `october` o `at` no existen con esos nombres, usar los equivalentes del fichero (mes `2026-10` con miembros `a`, `b`, `c`).

- [ ] **Step 2: Ejecutar y ver que fallan**
  Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.SplitLogicTest"`
  Expected: error de compilación (no existen `expenseShares`, `shares`, …).

- [ ] **Step 3: Implementar** los campos y funciones de *Interfaces*. `computeBalances` y `myShareCents` llaman a `expenseShares` en lugar de a `splitEqually`. En `computeBalances`, un gasto se ignora si `expenseShares(expense)` está vacío o su importe no es positivo. Así se mantiene la suma 0 aunque `participants` venga vacío y `shares` no.

- [ ] **Step 4: Ejecutar y ver que pasan**
  Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.SplitLogicTest"`
  Expected: BUILD SUCCESSFUL, 0 fallos.

- [ ] **Step 5: Commit** — `feat: reparto por importes y emoji en el modelo de SNcount`

---

### Task 2: ViewModel, repositorios y mes abierto compartido

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/SplitMonthHolders.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/SplitViewModel.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/SplitExpensesRepository.kt` (`update`)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/SplitMonthsRepository.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt` (quitar el `DisposableEffect` de abrir/cerrar)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (ruta `splitMonth/{monthId}`)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/HomeScreen.kt:60` (contar solo activos)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitExpenseSheet.kt` (solo adaptar la llamada a `saveExpense`; la hoja desaparece en la Tarea 3)
- Test: `app/src/test/kotlin/com/homepantry/app/ui/SplitMonthHoldersTest.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/ui/SplitUiStateTest.kt`

**Interfaces:**
- Consumes: Tarea 1 (`parseShares`, `validateSplitExpense(…, shareTexts)`, campos nuevos).
- Produces:
  - `class SplitMonthHolders` (pura, sin Android): `fun retain(holderId: String, monthId: String): String?`, `fun release(holderId: String): String?` y `val openMonthId: String?`. Las dos funciones devuelven el `openMonthId` resultante. Retener dos veces con el mismo `holderId` no cuenta doble, y liberar un holder desconocido no hace nada. El mes abierto es el del holder vivo retenido más recientemente; si no queda ninguno, null.
  - `SplitViewModel.retainMonth(holderId: String, monthId: String)` y `SplitViewModel.releaseMonth(holderId: String)` sustituyen a `openMonth`/`closeMonth`. Al cambiar el mes abierto se vacían gastos y fotos y `expensesLoaded` pasa a false, como hacía `openMonth`.
  - Composable `RetainSplitMonth(viewModel: SplitViewModel, entry: NavBackStackEntry, monthId: String)` en `SplitViewModel.kt` o en `ui/components/SplitCommon.kt`. Llama a `retainMonth(entry.id, monthId)` y añade al `entry.lifecycle` un `LifecycleEventObserver` que llama a `releaseMonth(entry.id)` en `ON_DESTROY`. No quita el observer en `onDispose`: la entrada puede destruirse cuando ya no está en composición (por ejemplo, con dos `popBackStack` seguidos), y retener y liberar son idempotentes.
  - `SplitUiState.expensesLoaded: Boolean = false`, que pasa a true con la primera emisión de gastos del mes abierto.
  - `SplitUiState.activeMonths: List<SplitMonth>` y `SplitUiState.archivedMonths: List<SplitMonth>` (perezosas, en el orden de `months`).
  - `SplitViewModel.saveExpense(expenseId: String?, title: String, amountText: String, payerId: String, participants: List<String>, date: Date?, reimbursement: Boolean, emoji: String, shareTexts: Map<String, String>?): Set<SplitExpenseError>`. Con `shareTexts` distinto de null guarda `shares = parseShares(...)` y `participants` = los de la lista recibida (en su orden) con importe > 0. Con null guarda `shares = emptyMap()`.
  - `SplitExpensesRepository.update`: añade `"emoji"` y `"shares"` al mapa. Un `shares` vacío se escribe como mapa vacío para borrar el reparto anterior.
  - `SplitMonthsRepository.setArchived(monthId: String, archived: Boolean)` y `SplitMonthsRepository.updateMonth(monthId: String, name: String, monthKey: String, emoji: String, addedMemberIds: List<String>)`. `updateMonth` usa `update` con `name`, `monthKey` y `emoji`, y con `memberIds` mediante `FieldValue.arrayUnion(*addedMemberIds)` solo si la lista no está vacía.
  - `SplitViewModel.setArchived(monthId: String, archived: Boolean)`, `SplitViewModel.editMonth(monthId: String, name: String, monthKey: String, emoji: String, addedMemberIds: List<String>)` y `deleteMonth(monthId)`. Este último ya no llama a `closeMonth`: el mes se cierra al destruirse la entrada.

- [ ] **Step 1: Escribir los tests que fallan.** `SplitMonthHoldersTest`:

```kotlin
@Test fun `month stays open while a screen above holds it`() {
    val h = SplitMonthHolders()
    assertEquals("m1", h.retain("monthEntry", "m1"))
    assertEquals("m1", h.retain("expenseEntry", "m1"))
    assertEquals("m1", h.release("expenseEntry"))
    assertEquals("m1", h.retain("monthEntry", "m1")) // volver a componer no cuenta doble
    assertNull(h.release("monthEntry"))
}

@Test fun `releasing unknown or twice is harmless`() {
    val h = SplitMonthHolders()
    h.retain("a", "m1")
    assertEquals("m1", h.release("zzz"))
    assertNull(h.release("a"))
    assertNull(h.release("a"))
}

@Test fun `latest live holder decides the open month`() {
    val h = SplitMonthHolders()
    h.retain("list-edit", "m1")
    assertEquals("m2", h.retain("other", "m2"))
    assertEquals("m1", h.release("other"))
}
```

  En `SplitUiStateTest`: `active and archived months keep order` (meses `m1` activo, `m2` archivado, `m3` activo → `activeMonths` = `[m1, m3]`, `archivedMonths` = `[m2]`).

- [ ] **Step 2: Ejecutar y ver que fallan**
  Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.ui.*"`
  Expected: error de compilación.

- [ ] **Step 3: Implementar** lo descrito en *Interfaces*. En `MainActivity`, la ruta `splitMonth/{monthId}` llama a `RetainSplitMonth(splitViewModel, backStackEntry, monthId)` antes de `SplitMonthScreen`, y la pantalla deja de abrir y cerrar el mes por su cuenta. `HomeScreen` cuenta `splitState.activeMonths.size`. `groupColors` sigue recibiendo `state.months` (todos) para que el color de un SNcount no cambie al archivarlo. `SplitMonthsScreen` lista `activeMonths`; la fila de archivados llega en la Tarea 5. La hoja actual llama a `saveExpense(..., emoji = expense?.emoji.orEmpty(), shareTexts = null)`.

- [ ] **Step 4: Ejecutar tests y build**
  Run: `./gradlew testDebugUnitTest assembleDebug`
  Expected: BUILD SUCCESSFUL, 0 fallos.

- [ ] **Step 5: Commit** — `feat: mes abierto compartido entre pantallas, archivar y editar SNcount en el ViewModel`

---

### Task 3: Pantalla «Nuevo gasto» / «Editar gasto» con emoji y reparto

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitExpenseScreen.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/components/SplitEmoji.kt`
- Delete: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitExpenseSheet.kt` (lo reutilizable se mueve: `MemberChip`, `DateRow`, `SplitDatePickerDialog`, `FieldError`, `monthBounds`, `defaultDate`)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/components/SplitCommon.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt` (añadir y abrir gastos navegando)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitExpensesTab.kt:160`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Tarea 1 (`parseShareCents`, `parseShares`, `sharesRemainderCents`, `splitEqually`, `SplitExpenseError.SHARES`) y Tarea 2 (`saveExpense` con firma nueva, `RetainSplitMonth`, `expensesLoaded`).
- Produces:
  - `val SPLIT_EMOJIS: List<String>` en `SplitEmoji.kt`: unos 40 emojis de gasto y de grupo (comida, bebida, súper, transporte, viaje, casa, suministros, ocio, regalos, salud, mascotas, ropa, deporte, fiesta, playa, montaña…).
  - `@Composable fun EmojiPickerSheet(automaticIcon: ImageVector, automaticColor: Color, onPick: (emoji: String) -> Unit, onDismiss: () -> Unit)` en `SplitEmoji.kt`. Una `ModalBottomSheet` con una fila «Automático (según el concepto)» que llama a `onPick("")`, y debajo una cuadrícula (`LazyVerticalGrid` o `FlowRow`) de `SPLIT_EMOJIS`. La usará también la Tarea 5.
  - `@Composable fun SplitIcon(emoji: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp)` en `SplitCommon.kt`: si `emoji` no está en blanco, el mismo fondo que `IconTile` con el emoji como texto (alrededor del 55 % de `size`); si está en blanco, `IconTile`.
  - Ruta `splitExpense/{monthId}?expenseId={expenseId}` (argumento opcional, null = alta) con `RetainSplitMonth` y `@Composable fun SplitExpenseScreen(viewModel: SplitViewModel, monthId: String, expenseId: String?, onBack: () -> Unit)`.
  - En `SplitMonthScreen`, `onNavigateToExpense: (expenseId: String?) -> Unit` sustituye a `ExpenseSheetTarget`.

Comportamiento (del spec y del boceto, sección 1):
- `Scaffold` con `TopAppBar`: flecha atrás, título `split_expense_new_title` o `split_expense_edit_title`, una papelera en coral (solo al editar y nunca en un reembolso nuevo) con el diálogo de borrado actual, y la acción `TextButton` «Guardar». Guardar llama a `saveExpense`; si no hay errores, `onBack()`.
- Contenido con scroll, `imePadding`, en este orden:
  1. Fila con el concepto (`OutlinedTextField`, `weight(1f)`) y **a su derecha** `SplitIcon` de 52 dp con un distintivo de lápiz. El icono es el emoji elegido o, en automático, `expenseCategoryOf(title).icon()` con su color. Pulsarlo abre `EmojiPickerSheet`. En un reembolso se muestra el icono de intercambio y no se puede pulsar.
  2. Importe grande, como en la hoja actual.
  3. «Pagado por» con `MemberChip`.
  4. La fecha, con `DateRow` y `SplitDatePickerDialog`, con los mismos límites de hoy.
  5. **Abajo del todo**, `SpendSectionTitle` «Reparto» y una tarjeta. En un reembolso no hay interruptor: solo el chip del receptor, como ahora. En los demás gastos:
     - `Switch` «Dividir a partes iguales». Encendido por defecto en un gasto nuevo; apagado si el gasto que se edita tiene `shares`.
     - **Encendido:** una fila por cada opción de participante (miembros del mes más los participantes antiguos del gasto, sin repetir) con `Checkbox`, `MemberAvatar`, nombre y su parte de `splitEqually` (o «—» si el importe aún no es válido).
     - **Apagado:** las mismas filas con un campo de importe pequeño (teclado decimal) en vez de la casilla. Debajo, el aviso en vivo con `sharesRemainderCents`: «Faltan %1$s para cuadrar con %2$s» o «Sobran %1$s» en coral, o «Cuadra» en primario. Si el total no es válido, no hay aviso.
     - Al **apagar**, los campos se rellenan con el reparto igual de los marcados (formato `%d,%02d`, a 0 los no marcados). Al **encender**, se descartan los campos y se conservan las casillas.
- Errores bajo cada campo, como ahora. `SHARES` se muestra bajo la tarjeta de reparto con `split_error_shares`.
- Si `expensesLoaded` y `expenseId` no está en `state.expenses` (otro miembro lo borró), o si el mes ya no existe, `onBack()`.
- En `SplitExpensesTab`, `IconTile(expense.icon(), color)` pasa a `SplitIcon(expense.emoji.takeUnless { expense.reimbursement }.orEmpty(), expense.icon(), color)`.
- Strings nuevos: `split_expense_split_section` («Reparto»), `split_expense_split_equally` («Dividir a partes iguales»), `split_shares_missing` («Faltan %1$s para cuadrar con %2$s»), `split_shares_extra` («Sobran %1$s»), `split_shares_ok` («Cuadra»), `split_error_shares` («Los importes tienen que sumar el total»), `split_emoji_title` («Icono»), `split_emoji_automatic` («Automático (según el concepto)»), `split_emoji_cd` («Cambiar icono»), `split_expense_delete_cd` («Borrar gasto»). Borrar los strings que dejen de usarse.

- [ ] **Step 1: Implementar** los componentes, la pantalla, la ruta y los cambios en `SplitMonthScreen` y `SplitExpensesTab`, y borrar `SplitExpenseSheet.kt`.
- [ ] **Step 2: Compilar y pasar tests y lint**
  Run: `./gradlew testDebugUnitTest assembleDebug lintDebug`
  Expected: BUILD SUCCESSFUL, 0 fallos y 0 errores de lint (`app/build/reports/lint-results-debug.txt`).
- [ ] **Step 3: Commit** — `feat: pantalla de gasto de SNcount con emoji y reparto por importes`

---

### Task 4: Enlace de SNcount: compartir y abrir la app

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/SplitLink.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/SplitLinkTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `firebase.json`
- Create: `hosting/sncount.html`, `hosting/index.html`, `hosting/.well-known/assetlinks.json`

**Interfaces:**
- Produces:
  - `const val SPLIT_LINK_HOST = "home-pantry-bed42.web.app"`
  - `fun splitShareUrl(monthId: String): String` = `"https://$SPLIT_LINK_HOST/sncount/$monthId"`
  - `fun splitMonthIdFromLink(link: String?): String?`: acepta `https` y ese host, y la ruta `/sncount/{id}` con barra final opcional, ignorando query y fragmento. El id no puede estar vacío ni contener `/`. Cualquier otra cosa da null. Parseo de strings con `java.net.URI` (sin `android.net.Uri`, para que se pueda testear en la JVM).
  - `fun shareSplitMonth(context: Context, text: String, chooserTitle: String)`: `ACTION_SEND` con `text/plain` y `EXTRA_TEXT`, como `WeekShare.share`.
  - `ListaDeLaCasaApp(pendingSplitMonthId: String?, onSplitLinkHandled: () -> Unit)`: parámetros nuevos.
  - Strings: `split_share_text` («He creado el SNcount «%1$s» en SNHome. Ábrelo aquí: %2$s») y `split_share_chooser` («Compartir SNcount»).

- [ ] **Step 1: Escribir el test que falla** (`SplitLinkTest`):

```kotlin
@Test fun `share url round trips`() {
    assertEquals("AbC123", splitMonthIdFromLink(splitShareUrl("AbC123")))
}
@Test fun `trailing slash query and fragment are ignored`() {
    assertEquals("AbC123", splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/AbC123/"))
    assertEquals("AbC123", splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/AbC123?utm=wa#x"))
}
@Test fun `other links are rejected`() {
    assertNull(splitMonthIdFromLink(null))
    assertNull(splitMonthIdFromLink("https://example.com/sncount/AbC123"))
    assertNull(splitMonthIdFromLink("http://home-pantry-bed42.web.app/sncount/AbC123"))
    assertNull(splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/"))
    assertNull(splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/a/b"))
    assertNull(splitMonthIdFromLink("no es una url"))
}
```

- [ ] **Step 2: Ejecutar y ver que falla**
  Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.SplitLinkTest"`
  Expected: error de compilación.

- [ ] **Step 3: Implementar `SplitLink.kt`** y volver a ejecutar el Step 2. Expected: PASS.

- [ ] **Step 4: Manifest y MainActivity.**
  - En la actividad: `android:launchMode="singleTop"` y un segundo `intent-filter android:autoVerify="true"` con `VIEW`, `DEFAULT`, `BROWSABLE` y `<data android:scheme="https" android:host="home-pantry-bed42.web.app" android:pathPrefix="/sncount/" />`.
  - `MainActivity` guarda `private val pendingSplitMonthId = mutableStateOf<String?>(null)`. Se rellena con `splitMonthIdFromLink(intent?.dataString)` en `onCreate` (solo si `savedInstanceState == null`, para no repetirlo al girar) y en `onNewIntent`, y se pasa a `ListaDeLaCasaApp`.
  - En `ListaDeLaCasaApp`, dentro de la rama que ya tiene casa y `NavHost`: `LaunchedEffect(pendingSplitMonthId)`. Si no es null, navega a `splitMonths` (con `popUpTo("home")` y `launchSingleTop`), después a `splitMonth/$id`, y llama a `onSplitLinkHandled()`. Si el id no existe en la casa, `SplitMonthScreen` ya vuelve a la lista. Un móvil sin casa conserva el enlace pendiente hasta que se une, y entonces acaba en la lista. Es inofensivo y no necesita lógica extra.

- [ ] **Step 5: Hosting.**
  - En `firebase.json`, añadir `"hosting": { "public": "hosting", "ignore": ["**/node_modules/**"], "rewrites": [{ "source": "/sncount/**", "destination": "/sncount.html" }], "headers": [{ "source": "/.well-known/assetlinks.json", "headers": [{ "key": "Content-Type", "value": "application/json" }] }] }`. El `ignore` explícito evita el `**/.*` por defecto, que dejaría fuera `.well-known`.
  - `hosting/.well-known/assetlinks.json`: relación `delegate_permission/common.handle_all_urls`, paquete `com.homepantry.app` y huella SHA-256 sacada con `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`. Es la clave que firma la release (`signingConfigs.getByName("debug")`).
  - `hosting/sncount.html`: página mínima en español con el tema oscuro (fondo `#161826`, texto `#E9E9ED`, botón `#9184D9`). Dice «Abre este enlace desde el móvil donde tienes SNHome» y tiene un botón «Abrir en SNHome» cuyo `href` se construye en JS: `intent://home-pantry-bed42.web.app` + `location.pathname` + `#Intent;scheme=https;package=com.homepantry.app;end`.
  - `hosting/index.html`: lo mismo, sin botón.

- [ ] **Step 6: Tests, build y lint**
  Run: `./gradlew testDebugUnitTest assembleDebug lintDebug`
  Expected: BUILD SUCCESSFUL, 0 fallos y 0 errores de lint.

- [ ] **Step 7: Commit** — `feat: enlace de SNcount que abre la app (App Links + Firebase Hosting)`. **No desplegar.** El despliegue va en la Tarea 6, con permiso.

---

### Task 5: Opciones del SNcount: menú, Editar y Archivados

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthEditScreen.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitArchivedScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/components/SplitCommon.kt` (menú compartido)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthsScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthFormSheet.kt` (hacer `internal` `MonthFields` y lo necesario para reutilizarlo)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Tarea 2 (`setArchived`, `editMonth`, `deleteMonth`, `activeMonths`, `archivedMonths`, `expensesLoaded`, `RetainSplitMonth`), Tarea 3 (`SplitIcon`, `EmojiPickerSheet`) y Tarea 4 (`splitShareUrl`, `shareSplitMonth`).
- Produces:
  - `@Composable fun SplitMonthMenu(archived: Boolean, onEdit: () -> Unit, onShare: () -> Unit, onToggleArchive: () -> Unit, onDelete: () -> Unit)`: el `IconButton` ⋮ con su `DropdownMenu` y las opciones Editar, Compartir, Archivar o Desarchivar, y Eliminar (en coral). Se usa en la barra de `SplitMonthScreen` y en `SplitMonthCard`, que pierde «Renombrar» y su diálogo.
  - Ruta `splitMonthEdit/{monthId}` con `RetainSplitMonth` y `@Composable fun SplitMonthEditScreen(viewModel: SplitViewModel, monthId: String, onBack: () -> Unit)`.
  - Ruta `splitArchived` y `@Composable fun SplitArchivedScreen(viewModel: SplitViewModel, onBack: () -> Unit, onOpenMonth: (String) -> Unit, onEditMonth: (String) -> Unit)`.
  - `SplitMonthsScreen` recibe `onEditMonth: (String) -> Unit` y `onOpenArchived: () -> Unit`.

Comportamiento:
- **Compartir** (en los dos menús): `shareSplitMonth(context, getString(split_share_text, month.name, splitShareUrl(month.id)), getString(split_share_chooser))`.
- **Archivar/Desarchivar:** `viewModel.setArchived(id, !month.archived)` y un snackbar `split_archived_done` («Archivado») o `split_unarchived_done` («Ya no está archivado»). En la pantalla del SNcount, te quedas en ella.
- **Eliminar:** el diálogo actual (`split_month_delete_title`/`_message`). Al confirmar desde la pantalla del SNcount, `deleteMonth` y `onBack()`.
- **Cabecera** de `SplitMonthScreen` y tarjetas: `SplitIcon(month.emoji, month.icon(), color, size = …)`.
- **Lista principal:** muestra `activeMonths`. Debajo del último, si `archivedMonths` no está vacía, una fila con borde discontinuo o una `Card` sobria: «SNcounts archivados» y el número a la derecha (`split_archived_row`), que llama a `onOpenArchived`. El estado vacío se calcula con `activeMonths`, pero si solo hay archivados la fila sigue apareciendo.
- **SplitArchivedScreen:** `TopAppBar` con atrás y título `split_archived_title` («SNcounts archivados»), y la misma `SplitMonthCard` con `SplitMonthMenu` (que muestra «Desarchivar»). Si se queda vacía, `onBack()`.
- **SplitMonthEditScreen** (boceto 2, centro):
  - `TopAppBar` con atrás, `split_month_edit_title` («Editar SNcount») y «Guardar». Guardar se desactiva con el nombre en blanco; llama a `editMonth` y después a `onBack()`.
  - Nombre (`OutlinedTextField`) con **a su derecha** `SplitIcon` de 52 dp que abre `EmojiPickerSheet` (el automático es `month.icon()`).
  - En un SNcount de tipo mes, encima, `MonthFields` como al crearlo. El selector de mes solo está activo si `expensesLoaded && state.expenses.isEmpty()`; si no, desactivado y con la nota `split_month_change_locked` («No se puede cambiar el mes: ya tiene gastos»). Al cambiar el mes, el nombre sigue a `splitMonthDisplayName` mientras no se haya editado a mano, igual que en `SplitMonthFormSheet`.
  - «Participantes»: los actuales con casilla marcada y desactivada. Los miembros de la casa que no están, con casilla libre. `addedMemberIds` = los marcados que no estaban.
  - Si el SNcount desaparece, `onBack()`.

- [ ] **Step 1: Implementar** el menú, las dos pantallas, las rutas en `MainActivity` (`splitMonths` pasa `onEditMonth = { navigate("splitMonthEdit/$it") }` y `onOpenArchived = { navigate("splitArchived") }`), los cambios de lista y cabecera, y los strings. Borrar `split_month_rename*` si quedan sin usar.
- [ ] **Step 2: Tests, build y lint**
  Run: `./gradlew testDebugUnitTest assembleDebug lintDebug`
  Expected: BUILD SUCCESSFUL, 0 fallos y 0 errores de lint.
- [ ] **Step 3: Commit** — `feat: menú de opciones de SNcount con editar, compartir, archivar y eliminar`

---

### Task 6: Verificación, despliegue del enlace y prueba en el S25

- [ ] **Step 1: Verificación completa por partes** (en un solo comando se corta con exit 127):
  Run: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug`, luego `./gradlew --no-daemon lintDebug` y después `./gradlew --no-daemon assembleRelease`.
  Expected: 0 fallos de test, 0 errores de lint y `app/build/outputs/apk/release/app-release.apk` de unos 5,2 MB.
- [ ] **Step 2: Pedir permiso al usuario** y desplegar con `firebase deploy --only hosting`, o pedirle que lo lance con `! firebase deploy --only hosting`. Comprobar que `https://home-pantry-bed42.web.app/.well-known/assetlinks.json` devuelve el JSON.
- [ ] **Step 3: Recordar al usuario** que todos los móviles de casa tienen que actualizarse antes de usar el reparto por importes (spec, *Restricciones*).
- [ ] **Step 4: Prueba en el S25** con el APK release (`adb install -r`, sin desinstalar):
  - Alta con reparto igual y por importes (aviso Faltan/Sobran y bloqueo al guardar).
  - Editar un gasto antiguo: sigue repartido igual.
  - Elegir un emoji y volver a «Automático».
  - Ir y volver entre SNcount y gasto sin que la lista parpadee.
  - Editar (nombre, emoji, añadir participante, mes bloqueado con gastos).
  - Archivar, desarchivar y eliminar.
  - Compartir por WhatsApp y abrir el enlace con la app cerrada y con la app abierta.
  - `adb shell pm get-app-links com.homepantry.app` debe dar `verified`.
