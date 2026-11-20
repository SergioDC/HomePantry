# SNcount: importar gastos desde capturas del banco. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desde un grupo de SNcount, leer con Gemini una o varias capturas de cargos de tarjeta, revisarlas (desmarcando lo personal) y crear los gastos en bloque.

**Architecture:** Lógica pura y llamada a Gemini en `data/CardChargesImport.kt`, que reutiliza el cliente REST, los errores y el formato de imagen de los tickets. El borrador vive en un `CardImportViewModel` de nivel actividad (como `ReceiptScanViewModel`) y se guarda en `filesDir`. La revisión es una pantalla de ruta propia, `splitImport/{monthId}`, y los gastos se escriben con un batch nuevo en `SplitExpensesRepository`.

**Tech Stack:** Kotlin, Jetpack Compose M3, Firestore, Gemini REST (`v1beta/interactions`), Gson, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-21-sncount-importar-capturas-design.md`

## Global Constraints

- Lógica testeable fuera de Composables y repositorios, en `data/`. Tests JVM en `app/src/test/kotlin/com/homepantry/app/data/`.
- Las clases que serializa Gson van en `com.homepantry.app.data` y tienen valores por defecto en todos los campos (R8 y `proguard-rules.pro`).
- Importes en céntimos (`Long`). `emoji = ""` (icono automático según el concepto).
- Textos en `res/values/strings.xml`, en español. Comentarios en español explicando el porqué.
- Al capturar excepciones en corrutinas, `CancellationException` se relanza siempre.
- Navegación: pantalla sin barra inferior, con flecha atrás.
- Sin dependencias nuevas.
- Commits en español con prefijo convencional, terminados en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. El último lleva `Closes #1`.

## Review Focus

1. Capturas solapadas: el mismo cargo aparece en dos imágenes. El segundo sale desmarcado como duplicado (test en Task 1).
2. Cargo sin fecha legible: sale desmarcado con motivo «sin fecha» y no se puede añadir hasta que se le ponga una (test en Task 1).
3. Gemini devuelve el importe con signo («-12,50»): se toma el valor absoluto y lo que decide si es cargo es el campo `charge` (test en Task 1).
4. El móvil que importa no es miembro del grupo: el pagador por defecto es el primer miembro (test en Task 1).
5. Más de 400 cargos marcados: el batch se trocea (Task 2, por revisión: Firestore admite 500 operaciones por batch).

---

### Task 1: Lógica pura y lectura con Gemini

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/CardChargesImport.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/CardChargesImportTest.kt`

**Interfaces:**
- Consumes: `GeminiInteractionRequest`, `GeminiInputPart`, `GeminiResponseFormat`, `callGemini`, `extractOutputText`, `encodePhotoForGemini`, `GeminiResponseException`, `parseIsoReceiptDate`, `plausibleReceiptDate`, `isDateInMonth`, `parseCents`, `SplitExpense`, `SplitMonth`.
- Produces:
  - `data class CardMovement(val title: String, val amountCents: Long, val date: Date?, val charge: Boolean)`
  - `enum class CardRowFlag { NONE, DUPLICATE, OUT_OF_MONTH, NO_DATE }`
  - `data class CardImportRow(val title: String = "", val amountText: String = "", val dateMillis: Long? = null, val selected: Boolean = true, val flag: CardRowFlag = CardRowFlag.NONE)`, con `fun isValid(): Boolean` (título no vacío, `parseCents(amountText) != null` y `dateMillis != null`)
  - `data class CardImportDraft(val monthId: String = "", val payerId: String = "", val rows: List<CardImportRow> = emptyList(), val imageUris: List<String> = emptyList())`
  - `fun mapCardMovementsOutput(outputText: String, today: Date = Date()): List<CardMovement>`
  - `fun cardImportDraft(movements: List<CardMovement>, month: SplitMonth, existing: List<SplitExpense>, currentUid: String, imageUris: List<String>): CardImportDraft`
  - `fun CardImportDraft.toExpenses(month: SplitMonth): List<SplitExpense>`
  - `fun cardImportDraftToJson(draft: CardImportDraft): String` / `fun cardImportDraftFromJson(json: String): CardImportDraft?`
  - `suspend fun recognizeCardChargesWithGemini(context: Context, imageUris: List<Uri>, apiKey: String, model: String, month: SplitMonth, today: Date = Date()): List<CardMovement>`

- [ ] **Step 1: Write the failing tests** in `CardChargesImportTest`. Fechas con un helper `day(y, m, d)` a medianoche local, y un grupo `SplitMonth(id = "m1", monthKey = "2026-11", memberIds = listOf("a", "b"))`:
  - `mapea cargos e ingresos`: con `{"movements":[{"title":"MERCADONA","amount":-23.45,"date":"2026-11-10","charge":true},{"title":"Bizum Ana","amount":10,"date":"2026-11-11","charge":false}]}` salen 2 movimientos; el primero con `amountCents == 2345`, `charge == true` y la fecha `day(2026,11,10)`.
  - `descarta importes cero o sin titulo`: `amount 0` o `title ""` no salen.
  - `fecha futura o ilegible queda null`: `"date":"2099-01-01"` y `"date":"10/11"` dan `date == null`.
  - `JSON invalido lanza GeminiResponseException`: `assertThrows` con `"no es json"` y con `{}` (falta `movements`).
  - `el borrador quita los ingresos`: de 1 cargo y 1 ingreso sale 1 fila.
  - `duplicado de un gasto existente`: con un `SplitExpense(amountCents = 2345, date = day(2026,11,10) + 15 h)` en `existing`, la fila de 23,45 € del 10 sale con `selected == false` y `flag == DUPLICATE`; otra de 23,45 € del día 11 sale `NONE` y marcada.
  - `duplicado entre capturas`: dos movimientos iguales (título, importe y día): el primero se queda `NONE` y marcado, el segundo sale `DUPLICATE` y desmarcado.
  - `fuera del mes solo en grupos de tipo mes`: un cargo del 2026-10-31 sale `OUT_OF_MONTH` y desmarcado; con `month.copy(activity = true)` sale `NONE` y marcado.
  - `sin fecha`: `date == null` da `NO_DATE`, desmarcado y `isValid() == false`.
  - `pagador por defecto`: con `currentUid = "b"` el pagador es `"b"`; con `"z"` (no es miembro) es `"a"`.
  - `amountText con coma`: 2345 céntimos dan `"23,45"`.
  - `toExpenses solo marcadas y validas`: 3 filas (marcada y válida, desmarcada, marcada sin fecha) dan 1 gasto con `payerId` del borrador, `participants == month.memberIds`, `amountCents` de `parseCents`, `title` sin espacios a los lados, `emoji == ""`, `reimbursement == false` y la fecha de la fila.
  - `json ida y vuelta`: un borrador con 2 filas sobrevive a `toJson`/`fromJson`; `cardImportDraftFromJson("{")` da `null`, y `"{}"` da un borrador con listas vacías, no nulas.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.CardChargesImportTest"`
Expected: FAIL de compilación (no existen las funciones).

- [ ] **Step 3: Implement `CardChargesImport.kt`**
  - Esquema `CARD_MOVEMENTS_JSON_SCHEMA`: objeto con `movements`, un array de `{title: string, amount: number, date: string, charge: boolean}` en el que `title`, `amount` y `charge` son obligatorios. `movements` también es obligatorio.
  - Mapeo: `amountCents = Math.round(abs(amount) * 100)`. Si el `title` queda vacío tras `trim` o los céntimos son ≤ 0, el movimiento se descarta. `date = plausibleReceiptDate(parseIsoReceiptDate(date), today)`. Un JSON ilegible o sin `movements` lanza `GeminiResponseException`. Usar un payload `private data class` con campos anulables, como en `GeminiReceiptRecognizer`.
  - Duplicados: misma clave `(amountCents, día local)` que un gasto de `existing` (con `date` no nula), o que una fila anterior del mismo borrador con `(título en minúsculas, amountCents, día)`. Prioridad de la marca: `NO_DATE` > `DUPLICATE` > `OUT_OF_MONTH`. Solo las filas `NONE` empiezan marcadas.
  - `amountText` con `String.format(Locale.forLanguageTag("es-ES"), "%.2f", cents / 100.0)`.
  - `fromJson`, como `receiptScanDraftFromJson`: `try`/`catch` que devuelve null y rellena con valores por defecto las listas y cadenas nulas que deja Gson.
  - Prompt `CARD_CHARGES_PROMPT`: «capturas de la lista de movimientos de la app de un banco español; una entrada por movimiento en todas las imágenes; title = comercio o concepto tal como aparece; amount = importe en euros; charge = true si es un cargo o compra y false si es un ingreso, devolución, Bizum recibido o transferencia recibida; date = YYYY-MM-DD. Si la captura no muestra el año, el del mes del grupo (`month.monthKey`) o, si no puede ser, el de hoy (`today`). Si una misma fila aparece en dos capturas, devuélvela una sola vez. Ignora saldos, totales y cabeceras». El prompt incluye la fecha de hoy y el `monthKey`.
  - `recognizeCardChargesWithGemini`: un `GeminiInputPart` de texto y uno `image` por Uri (con `encodePhotoForGemini(context, uri, "de la captura")`), `GeminiResponseFormat(schema = CARD_MOVEMENTS_JSON_SCHEMA)`, `callGemini`, `extractOutputText` y `mapCardMovementsOutput`.

- [ ] **Step 4: Run tests to verify they pass.** El mismo comando. Expected: PASS.

- [ ] **Step 5: Commit** `feat: lectura de cargos de tarjeta con Gemini para SNcount`

### Task 2: Batch de gastos, ViewModel y borrador en disco

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/SplitExpensesRepository.kt` (añadir `addAll`)
- Create: `app/src/main/kotlin/com/homepantry/app/data/CardImportDraftStore.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/CardImportViewModel.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (factory junto a `receiptScanViewModel`, `key = "cardImport-$code"`)

**Interfaces:**
- Consumes: todo lo de Task 1, `GeminiApiKeyStore`.
- Produces:
  - `suspend fun SplitExpensesRepository.addAll(monthId: String, expenses: List<SplitExpense>)`: batches de 400 como máximo, cada documento con `collection(monthId).document()`.
  - `fun cardImportDraftFile(filesDir: File, householdCode: String): File`, que apunta a `card_import_draft_<code>.json`.
  - `class CardImportDraftStore(file: File)` con `suspend fun load(): CardImportDraft?` (nunca lanza) y `suspend fun save(draft: CardImportDraft?)` (null lo borra; escribe en un `.tmp` y lo renombra). Copiar el patrón de `ReceiptScanDraftStore`.
  - `class CardImportViewModel(draftStore: CardImportDraftStore) : ViewModel()` con:
    - `val reading: StateFlow<Boolean>`, `val draft: StateFlow<CardImportDraft?>`, `val error: StateFlow<String?>`, `val saving: StateFlow<Boolean>`
    - `fun read(context: Context, uris: List<Uri>, month: SplitMonth, existing: List<SplitExpense>, currentUid: String, apiKeyStore: GeminiApiKeyStore, noKeyMessage: String)`: no hace nada si ya hay una lectura en marcha o un borrador. Sin clave pone `error = noKeyMessage`. Si falla, `error = e.message` y guarda los Uris para reintentar.
    - `fun retry(context: Context, month: SplitMonth, existing: List<SplitExpense>, currentUid: String, apiKeyStore: GeminiApiKeyStore, noKeyMessage: String)`: vuelve a leer los últimos Uris.
    - `fun updateDraft(draft: CardImportDraft)`
    - `fun save(month: SplitMonth, write: suspend (List<SplitExpense>) -> Unit)`: si va bien descarta el borrador; si falla, lo conserva y pone `error`.
    - `fun discard()`: borra el borrador y el error.
  - Carga y guarda en disco con `debounce(300)`, igual que en el `init` de `ReceiptScanViewModel`.

- [ ] **Step 1: Implement** los cuatro ficheros siguiendo el patrón de `ReceiptScanViewModel`. Solo `applicationContext` en el ViewModel.
- [ ] **Step 2: Verify:** `./gradlew testDebugUnitTest assembleDebug`. Expected: BUILD SUCCESSFUL y todos los tests en verde.
- [ ] **Step 3: Commit** `feat: borrador y guardado en bloque de los cargos importados`

### Task 3: Pantalla de revisión y entrada desde el grupo

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/CardImportScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt` (acción en la barra superior)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (ruta `splitImport/{monthId}` con `RetainSplitMonth`)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `CardImportViewModel` (Task 2), `SplitViewModel.state` y `currentUid`, `SplitExpensesRepository.addAll` (el repositorio se crea en `MainActivity` como ya se hace para el `SplitViewModel`), `GeminiReadingExitDialog`.
- Produces: `@Composable fun CardImportScreen(importViewModel: CardImportViewModel, splitViewModel: SplitViewModel, monthId: String, apiKeyStore: GeminiApiKeyStore, saveExpenses: suspend (String, List<SplitExpense>) -> Unit, onBack: () -> Unit)` y el parámetro nuevo `onImport: (List<Uri>) -> Unit` en `SplitMonthScreen`.

- [ ] **Step 1: Entrada.** En `SplitMonthScreen`, un `IconButton` con un icono de imagen o documento antes de `SplitMonthMenu`, con contentDescription «Importar de capturas del banco». Abre `PickMultipleVisualMedia(maxItems = 10)` solo de imágenes. Con alguna imagen elegida, `onImport(uris)`. En `MainActivity`, `onImport` llama a `cardImportViewModel.read(...)` (con el `month` y los `expenses` del estado de `splitViewModel`) y navega a `splitImport/$monthId`. Si ya hay un borrador de otro grupo (`draft.monthId != monthId`), un Snackbar dice que primero hay que terminar o descartar esa importación, con una acción «Abrir» que lleva a `splitImport/{draft.monthId}`.
- [ ] **Step 2: Pantalla.** `TopAppBar` con flecha atrás y título «Importar cargos». El cuerpo depende del estado:
  - leyendo: indicador y texto «Leyendo N capturas…»;
  - error: el mensaje, «Reintentar» (`retry`) y «Descartar»;
  - borrador: arriba el pagador, un selector con los miembros del grupo (`memberName`); después una `LazyColumn` con una fila por cargo: `Checkbox`, título, fecha (`d MMM`), importe y, si `flag != NONE`, una línea en `onSurfaceVariant` con el motivo («Posible duplicado», «Fuera de este mes», «Sin fecha»). Al tocar la fila se abre un diálogo para editar título, importe y fecha (reutilizar `SplitDatePickerDialog` si es accesible; si no, `DatePickerDialog` con `localDayToUtcMillis`/`utcMillisToLocalDay`). Editar no cambia la marca de duplicado. Una fila no válida no se puede marcar.
  - Pie fijo: botón «Añadir N gastos» (plural), desactivado con N = 0 o mientras `saving`. Llama a `save(month) { saveExpenses(monthId, it) }` y, si el borrador se descarta, `onBack()`.
  - Atrás: mientras lee, `GeminiReadingExitDialog`; con borrador, un diálogo «¿Descartar los cargos leídos?» con «Descartar» (llama a `discard()` y sale) y «Seguir revisando».
- [ ] **Step 3: Strings** en `strings.xml` con prefijo `split_import_`, `plurals` para «Añadir %d gasto(s)» y «Leyendo %d captura(s)», y un `split_import_no_key` como `menu_import_no_key` pero sobre las capturas.
- [ ] **Step 4: Verify** la verificación completa por partes: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug`, después `./gradlew --no-daemon lintDebug` y `./gradlew --no-daemon assembleRelease`. Expected: 0 fallos, 0 errores de lint y `app-release.apk` de unos 5,5 MB.
- [ ] **Step 5: Commit** `feat: importar gastos de SNcount desde capturas del banco`, con `Closes #1`.
- [ ] **Step 6: Prueba en el S25** con el APK release (lo hace el usuario): importar 2 capturas solapadas de su banco, comprobar que salen desmarcados los duplicados y los ingresos, desmarcar uno personal, añadir y ver los gastos y saldos en el grupo.
