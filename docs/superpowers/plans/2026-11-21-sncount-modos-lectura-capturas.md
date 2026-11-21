# Modos de lectura de las capturas de SNcount: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** al importar cargos en SNcount, elegir entre leer las capturas con Gemini (imagen), con OCR + Gemini (solo texto) o solo con OCR (nada sale del móvil).

**Architecture:** el OCR de ML Kit devuelve líneas con su caja; una función pura las agrupa en filas y otra (también pura) saca los movimientos de Kutxabank. El modo OCR + Gemini manda el texto de esas filas con el mismo esquema JSON. Los tres modos acaban en `List<CardMovement>` y desde ahí todo sigue como hoy (`cardImportDraft`, revisión, guardado).

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), ML Kit Text Recognition (Play Services), DataStore, Gson, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-21-sncount-modos-lectura-capturas-design.md`

## Global Constraints

- «Solo OCR» no hace ninguna llamada de red: ni Gemini ni nada.
- Ningún modo guarda gastos sin pasar por la revisión de siempre.
- Lógica nueva testeable en ficheros puros de `data/`, sin dependencias de Android (CLAUDE.md).
- Clases que pasan por Gson van en `data/` con valores por defecto en todos los campos (R8).
- Textos en `res/values/strings.xml`, en español; comentarios y KDoc en español explicando el porqué.
- `CancellationException` se relanza siempre.
- Commits en español con prefijo convencional y `Refs #3` (el último lleva `Closes #3`).
- Verificación final: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease` → 0 fallos, 0 errores de lint, release de unos 5,5 MB (si el comando único se corta sin mensaje, por partes).

## Review Focus

1. Una fila con dos importes (movimiento y saldo después del movimiento): debe tomarse el primero por la izquierda, no el saldo. → test en Task 1.
2. Fecha sin año en enero que cae en diciembre («28 dic» con hoy = 5 de enero): debe ser del año anterior, no futura. → test en Task 1.
3. Captura sin texto o sin ningún importe: la revisión se abre vacía con el aviso y «Copiar texto leído», no un error ni un cierre. → test en Task 1 (lista vacía) y comprobación manual en Task 3.
4. Concepto en una línea y el importe sola en la siguiente: se titulan con la fila anterior y esa fila no se usa dos veces. → test en Task 1.
5. Borrador guardado con la versión anterior (sin `ocrText` ni modo): se lee sin fallar y reintentar usa Gemini (imagen). → test en Task 2.

---

### Task 1: Filas de OCR y lector de movimientos de Kutxabank (puro)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/BankMovementsParsing.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/BankMovementsParsingTest.kt`

**Interfaces:**
- Consumes: `CardMovement(title, amountCents, date, charge)` de `CardChargesImport.kt`; `plausibleReceiptDate(date, today)` de `ReceiptDate.kt`.
- Produces:
  - `data class OcrLine(val text: String = "", val top: Int = 0, val bottom: Int = 0, val left: Int = 0)`
  - `fun groupOcrRows(lines: List<OcrLine>): List<String>` — filas de arriba abajo; dentro de cada fila, textos de izquierda a derecha unidos con `"  "` (dos espacios).
  - `fun ocrRowsText(screenshots: List<List<String>>): String` — `"--- Captura 1 ---\n<filas>\n--- Captura 2 ---\n…"`.
  - `fun parseBankMovements(screenshots: List<List<String>>, today: Date): List<CardMovement>`

- [ ] **Step 1: Tests en rojo**

Helper `day(y, m, d)` como en `CardChargesImportTest`; `today = day(2026, 11, 21)`.

- `agrupa en filas por solape vertical`: `[OcrLine("-12,34 €", 102, 120, 800), OcrLine("MERCADONA", 100, 118, 50), OcrLine("Hoy", 40, 58, 50)]` → `["Hoy", "MERCADONA  -12,34 €"]`. Solapan si la intersección vertical es al menos la mitad de la altura de la más baja.
- `cargo con signo menos y cabecera Hoy`: filas `["Hoy", "MERCADONA  -12,34 €"]` → un `CardMovement("MERCADONA", 1234, day(2026,11,21), charge = true)`.
- `ingreso con signo mas`: `["20/11", "Bizum de Ana  +15,00 €", "LIDL  -8,10 €"]` → Bizum con `charge = false`, LIDL con `charge = true`, ambos con `day(2026,11,20)`.
- `sin ningun menos todo son cargos`: `["Ayer", "LIDL  8,10 €", "EROSKI  3,00"]` → los dos con `charge = true` y fecha `day(2026,11,20)`.
- `miles con punto`: `"Alquiler  -1.234,56 €"` → `123456`.
- `formatos de fecha`: cabeceras `"20 nov"`, `"20 de noviembre"`, `"20/11/2026"`, `"20 NOV. 2026"` → `day(2026,11,20)`; una fecha dentro de la fila (`"19/11  CEPSA  -40,00 €"`) manda sobre la cabecera y no entra en el título (`"CEPSA"`).
- `fecha sin anio en enero` (Review Focus 2): `today = day(2027,1,5)`, `["28 dic", "IKEA  -50,00 €"]` → `day(2026,12,28)`.
- `concepto en la fila anterior` (Review Focus 4): `["Hoy", "AMAZON EU SARL", "-23,99 €", "DIA  -2,00 €"]` → `AMAZON EU SARL` 2399 y `DIA` 200; dos movimientos.
- `dos importes toma el primero` (Review Focus 1): `"MERCADONA  -12,34 €  1.200,00 €"` → 1234.
- `ignora saldo y filas sin concepto`: `["Saldo disponible  1.500,00 €", "Hoy", "-5,00 €"]` (el importe suelto tiene encima una fecha, no un concepto) → lista vacía.
- `sin importes devuelve vacio` (Review Focus 3): `listOf(emptyList())` y `listOf(listOf("Movimientos", "Hoy"))` → vacía.
- `sin fecha queda null`: `["LIDL  -8,10 €"]` sin cabecera → `date == null`.
- `varias capturas`: la fecha de la cabecera de la primera captura no pasa a la segunda.
- `ocrRowsText` con dos capturas → texto exacto con los separadores de arriba.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.BankMovementsParsingTest"`
Expected: FAIL de compilación (`Unresolved reference`).

- [ ] **Step 3: Implementar en `BankMovementsParsing.kt`**

Reglas (spec, «Solo OCR»):
- Importe: `(?<![\d.,])([-−+])?\s?(\d{1,3}(?:\.\d{3})*|\d+),(\d{2})(?!\d)\s?€?`. Se usa la primera coincidencia de la fila; `−` cuenta como menos.
- Filas que contienen `saldo` o `disponible` (sin tildes ni mayúsculas) se ignoran por completo.
- Fecha: `hoy`, `ayer`, `d/m`, `d/m/aaaa` (o `aa`), `d mes`, `d de mes`, con o sin año y con o sin punto tras la abreviatura (`ene…dic` y nombres completos). Sin año: el de `today`, y si queda en el futuro, el anterior; después `plausibleReceiptDate`. Una fila que tras quitar la fecha queda vacía es cabecera: fija la fecha actual (que se reinicia a null al empezar cada captura).
- Título: la fila sin importe(s) ni fecha, con espacios colapsados. Si queda vacío, la fila anterior de la misma captura si no es cabecera de fecha, no tiene importe y no se ha usado ya como título; si tampoco, se descarta.
- Si ningún importe de ninguna captura lleva signo menos, todos son cargos; si alguno lo lleva, menos → cargo y el resto → ingreso.

- [ ] **Step 4: Comprobar que pasan**

Run: el mismo comando. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/BankMovementsParsing.kt app/src/test/kotlin/com/homepantry/app/data/BankMovementsParsingTest.kt
git commit -m "feat: lector de movimientos de Kutxabank a partir del OCR (Refs #3)"
```

---

### Task 2: Modos de lectura en el borrador, el OCR y el ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/CardChargesImport.kt` (modo, `ocrText`, lectura por texto)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/ReceiptTextRecognizer.kt` (líneas con caja)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/UserPrefs.kt` (último modo)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/CardImportViewModel.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/CardChargesImportTest.kt`

**Interfaces:**
- Consumes: Task 1 (`OcrLine`, `groupOcrRows`, `ocrRowsText`, `parseBankMovements`).
- Produces:
  - `enum class CardReadMode { GEMINI_IMAGE, OCR_GEMINI_TEXT, OCR_ONLY }` en `CardChargesImport.kt`; `val CardReadMode.needsApiKey: Boolean` (true salvo `OCR_ONLY`).
  - `CardImportDraft` gana `val ocrText: String = ""` y `val readMode: CardReadMode = CardReadMode.GEMINI_IMAGE`; `cardImportDraft(...)` gana los parámetros `ocrText: String = ""` y `readMode: CardReadMode = CardReadMode.GEMINI_IMAGE`.
  - `suspend fun recognizeOcrLines(context: Context, imageUri: Uri): List<OcrLine>` en `ReceiptTextRecognizer.kt` (misma decodificación reducida que `recognizeReceiptTextLines`; caja de `line.boundingBox`, líneas sin caja se omiten).
  - `suspend fun recognizeCardChargesFromText(ocrText: String, apiKey: String, model: String, month: SplitMonth, today: Date = Date()): List<CardMovement>`
  - `UserPrefs.cardReadMode: Flow<CardReadMode?>` y `suspend fun saveCardReadMode(mode: CardReadMode)` (clave `card_read_mode`, guarda el `name`; un valor desconocido se lee como null).
  - `CardImportViewModel.read(context, uris, month, existing, currentUid, apiKeyStore, noKeyMessage, mode: CardReadMode)` y `retry(...)` reutiliza el modo de la lectura fallida o el `readMode` del borrador.

- [ ] **Step 1: Tests en rojo** (en `CardChargesImportTest`)

- `json con ocrText y modo ida y vuelta`: borrador con `ocrText = "--- Captura 1 ---\nLIDL  -8,10 €"` y `readMode = OCR_ONLY` sobrevive a `cardImportDraftToJson` → `cardImportDraftFromJson`.
- `borrador antiguo sin modo` (Review Focus 5): el JSON del test `un borrador sin ids recibe uno estable al leerlo` → `ocrText == ""` y `readMode == GEMINI_IMAGE`.
- `modo desconocido en el json`: `"readMode":"OTRO"` → `GEMINI_IMAGE`.
- `cardImportDraft guarda ocrText y modo`.
- `needsApiKey`: false solo en `OCR_ONLY`.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.CardChargesImportTest"`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

- `cardImportDraftFromJson` rellena `ocrText` nulo con `""` y `readMode` nulo con `GEMINI_IMAGE` (Gson deja null un enum desconocido).
- `recognizeCardChargesFromText`: una sola petición con un `GeminiInputPart` de texto: el prompt de `cardChargesPrompt` adaptado («texto de capturas leído con OCR, una fila por línea; `--- Captura N ---` separa capturas») seguido de `ocrText`; mismo `CARD_MOVEMENTS_JSON_SCHEMA` y `mapCardMovementsOutput`.
- `ReceiptTextRecognizer.kt`: sacar el paso de ML Kit a una función privada común para que `recognizeReceiptTextLines` y `recognizeOcrLines` no dupliquen la decodificación.
- `CardImportViewModel.read`: guarda el modo junto a `failedUris` para `retry`. Pide la clave solo si `mode.needsApiKey`. Según el modo:
  - `GEMINI_IMAGE`: como hoy.
  - `OCR_GEMINI_TEXT` y `OCR_ONLY`: `recognizeOcrLines` por captura → `groupOcrRows` → `ocrText = ocrRowsText(filas)`; luego `recognizeCardChargesFromText(ocrText, …)` o `parseBankMovements(filas, Date())`.
  - Borrador con `ocrText` y `readMode` del modo usado. Un resultado sin movimientos deja igualmente un borrador (vacío) para poder copiar el texto.
- Guardar el modo en `UserPrefs` lo hace quien llama (Task 3), no el ViewModel.

- [ ] **Step 4: Comprobar que pasan y que compila**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. (`MainActivity` debe pasar ya `mode = CardReadMode.GEMINI_IMAGE` en la llamada actual a `read` para compilar; el diálogo llega en la Task 3.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data app/src/main/kotlin/com/homepantry/app/ui/CardImportViewModel.kt app/src/main/kotlin/com/homepantry/app/MainActivity.kt app/src/test
git commit -m "feat: lectura de capturas por OCR y por texto con Gemini en SNcount (Refs #3)"
```

---

### Task 3: Diálogo del modo y «Copiar texto leído»

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/SplitMonthScreen.kt` (diálogo antes del selector de imágenes)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (pasa el modo a `read` y lo guarda en `UserPrefs`)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/CardImportScreen.kt` (menú ⋮ y vacío)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 2 (`CardReadMode`, `needsApiKey`, `UserPrefs.cardReadMode`/`saveCardReadMode`, `read(..., mode)`, `CardImportDraft.ocrText`).
- Produces: `SplitMonthScreen` cambia `onImport: (List<Uri>) -> Unit` por `onImport: (List<Uri>, CardReadMode) -> Unit` y gana `hasApiKey: Boolean` e `initialReadMode: CardReadMode?`.

- [ ] **Step 1: Diálogo del modo en `SplitMonthScreen`**

Al pulsar el icono de importar (si no hay importación pendiente) se abre un `AlertDialog` con tres opciones (radio), cada una con título y una línea debajo:
- «Gemini (imagen)» — «Más preciso. Las capturas salen del móvil.»
- «OCR + Gemini (texto)» — «Solo sale del móvil el texto leído.»
- «Solo OCR» — «No sale nada del móvil. Solo Kutxabank.»

Sin clave, las dos primeras deshabilitadas con «Necesita clave de Gemini». Marcada al abrir: `initialReadMode` si se puede usar; si no, `GEMINI_IMAGE` con clave u `OCR_ONLY` sin ella. «Elegir capturas» lanza el selector de imágenes y guarda el modo elegido (`rememberSaveable`) para pasárselo a `onImport` cuando vuelvan las Uris.

- [ ] **Step 2: Conectar en `MainActivity`**

`hasApiKey` desde `geminiApiKeyStore` (leído en un `LaunchedEffect`/`produceState`, la lectura nunca lanza), `initialReadMode` desde `userPrefs.cardReadMode`. `onImport(uris, mode)` guarda el modo con `saveCardReadMode` y llama a `read(..., mode)`.

- [ ] **Step 3: «Copiar texto leído» en `CardImportScreen`**

Menú ⋮ en la barra superior, visible solo si `ownDraft?.ocrText` no está vacío, con «Copiar texto leído»: copia `ocrText` con `LocalClipboard`/`ClipboardManager` y muestra «Texto copiado» (Snackbar o Toast). Con el borrador vacío y `readMode == OCR_ONLY`, el texto del vacío pasa a «No se ha encontrado ningún cargo. Puedes copiar el texto leído desde ⋮.» (en los otros modos se queda `split_import_empty`).

- [ ] **Step 4: Verificación completa**

Run: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`
Expected: 0 fallos de test, `0 errors` en `app/build/reports/lint-results-debug.txt`, `app-release.apk` de unos 5,5 MB.

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat: elegir cómo se leen las capturas del banco en SNcount (Closes #3)"
```

- [ ] **Step 6: Prueba en el S25 (con el usuario)**

APK release en el apk-site (`\\192.168.1.54\docker\apk-site\apks\SNHome-1.0-<hash>.apk`). Probar con las mismas capturas de Kutxabank en los tres modos; en «Solo OCR», copiar el texto leído para ajustar el lector si hace falta. Sin push ni merge hasta que el usuario lo confirme.
