# Menú del comedor: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** descargar de Menuo el PDF del menú del comedor de Basauri y convertirlo en un borrador de importación para Oihan, de dos formas: con un botón manual y con una comprobación automática a final y principio de mes que avisa con una notificación.

**Architecture:** la lógica pura (cuerpo de la petición, fechas, ventana de comprobación y decisiones) va en `data/`, con tests. Una capa de red y PDF (Retrofit, `PdfRenderer`) entrega un `ParsedMenu` a través del reconocedor de Gemini que ya existe. La acción manual va en `MenuViewModel`/`MenuScreen` y la automática en un `JobService` gemelo de `OffersCheckJobService`. Las dos acaban en `MenuImportDraft` y en la revisión de siempre.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Retrofit + Gson, `android.graphics.pdf.PdfRenderer`, JobScheduler, DataStore, Firestore, JUnit 4 y `java.time`.

**Spec:** `docs/superpowers/specs/2026-10-30-menu-comedor-design.md`

## Global Constraints

- Sin dependencias nuevas. Nada de WorkManager: JobScheduler, como en ofertas.
- Las clases que serializa Gson van en `com.homepantry.app.data` (R8).
- `CancellationException` se relanza siempre en los `catch`.
- Textos visibles en `res/values/strings.xml`. Código, comentarios y commits en español (`feat:`, `fix:`…).
- Nunca se importa sin revisar: el resultado es siempre un `MenuImportDraft`.
- Ni el PDF ni el bitmap quedan en disco al terminar. Solo se guarda la huella SHA-256 en `UserPrefs`.
- Persona `"Oihan"` e idioma castellano, en `ComedorConfig`.
- Versiones del build intactas (JDK 17, minSdk 26, AGP/Gradle 8.x).
- Verificación final: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`.

## Review Focus

1. El job escribe el borrador en disco mientras la app está abierta y el ViewModel tiene `_importDraft == null`. Al entrar en Menú tiene que aparecer, y el guardado con debounce del ViewModel no debe borrarlo (Task 4, `reloadImportDraft`).
2. Hay un borrador a medio revisar (de una foto) cuando se ejecuta el job, o cuando se pulsa el botón: no se pisa (Task 4 y Task 5).
3. Menuo responde 200 con HTML o JSON, o con un error, porque ha cambiado la API: mensaje, sin crash (Task 1, tests de `isPdf`; Task 3, `NotPdf`/`Http`).
4. El PDF sigue siendo el del mes anterior durante toda la ventana: Gemini se llama una sola vez por huella (Task 5, paso 7.a y huella guardada).
5. La notificación se toca con la app cerrada o abierta: abre la pantalla de Menú con la revisión (Task 5, extra `EXTRA_OPEN_MENU` en `onCreate` y en `onNewIntent`).

---

### Task 1: Lógica pura de la petición y de las decisiones

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/ComedorMenuLogic.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/ComedorMenuLogicTest.kt`

**Interfaces:**
- Produces:
  - `object ComedorConfig { BASE_URL = "https://apimenuo.ausolan.com/"; PK_MENU; PK_CENTRO; PK_CENTRO_ENVIO; PK_DIETA; PK_SERVICIO; PK_IDIOMA_ES; PERSON = "Oihan"; ZONE: ZoneId = ZoneId.of("Europe/Madrid") }` con los valores de la tabla del spec.
  - `data class ComedorParam(val nombre: String = "", val valorObjeto: String = "")`
  - `fun comedorMonthRange(month: YearMonth, zone: ZoneId = ComedorConfig.ZONE): Pair<String, String>`
  - `fun comedorRequestBody(month: YearMonth): List<ComedorParam>`
  - `fun isPdf(contentType: String?, bytes: ByteArray): Boolean`
  - `fun pdfFingerprint(bytes: ByteArray): String` (SHA-256 en hex, minúsculas)
  - `fun hasComedorMenu(entries: List<MealEntry>, person: String = ComedorConfig.PERSON): Boolean` (alguna entrada `slot == MealSlot.LUNCH.name` con `personKey(person)` igual)
  - `fun manualComedorMonth(detected: YearMonth?, today: LocalDate): YearMonth?` (null = el PDF es de un mes anterior al actual; si no, `detected ?: YearMonth.from(today).plusMonths(1)`)
  - `fun isStaleComedorMonth(detected: YearMonth?, target: YearMonth): Boolean` (`detected != null && detected < target`)

- [ ] **Step 1: Escribir los tests que fallan**

```kotlin
@Test fun `rango de noviembre 2026 en UTC`() = assertEquals(
    "2026-10-31T23:00:00.000Z" to "2026-11-29T23:00:00.000Z", comedorMonthRange(YearMonth.of(2026, 11)))
@Test fun `rango de octubre 2026 cruza el cambio de hora`() = assertEquals(
    "2026-09-30T22:00:00.000Z" to "2026-10-30T23:00:00.000Z", comedorMonthRange(YearMonth.of(2026, 10)))
@Test fun `rango de marzo 2027 cruza el cambio de hora`() = assertEquals(
    "2027-02-28T23:00:00.000Z" to "2027-03-30T22:00:00.000Z", comedorMonthRange(YearMonth.of(2027, 3)))

@Test fun `cuerpo serializado como espera Menuo`() {
    val json = Gson().toJson(comedorRequestBody(YearMonth.of(2026, 11)))
    assertEquals(
        """[{"nombre":"FechaIni","valorObjeto":"2026-10-31T23:00:00.000Z"},""" +
        """{"nombre":"FechaFin","valorObjeto":"2026-11-29T23:00:00.000Z"},""" +
        """{"nombre":"PkMenu","valorObjeto":"fce8da488dcf48bab961b36a009fcfdc"},""" +
        """{"nombre":"PkCentro","valorObjeto":"4cf8682f16604ae1b1dcb2e400f2972e"},""" +
        """{"nombre":"PkCentroEnvio","valorObjeto":"cef83569f1ae40cc816cb3d300cf64d9"},""" +
        """{"nombre":"PkDieta","valorObjeto":"d12fe25a9bbd49239e3cd65eef62e7c5"},""" +
        """{"nombre":"PkServicio","valorObjeto":"[\"6f6096586ff248189d7d535b8509f411\"]"},""" +
        """{"nombre":"PkIdioma","valorObjeto":"00242C08E94B10101810432101271385"},""" +
        """{"nombre":"NombreArchivo","valorObjeto":"menu"},""" +
        """{"nombre":"TipoVista","valorObjeto":"Mensual"}]""",
        json)
}

@Test fun `isPdf acepta content type o cabecera %PDF`() {
    assertTrue(isPdf("application/pdf", byteArrayOf()))
    assertTrue(isPdf(null, "%PDF-1.7".toByteArray()))
    assertFalse(isPdf("text/html", "<html>".toByteArray()))
    assertFalse(isPdf("application/json", byteArrayOf()))
}
@Test fun `huella sha256`() = assertEquals(
    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", pdfFingerprint("abc".toByteArray()))

@Test fun `hasComedorMenu solo cuenta comidas de Oihan`() {
    assertTrue(hasComedorMenu(listOf(MealEntry(slot = MealSlot.LUNCH.name, person = " oihan "))))
    assertFalse(hasComedorMenu(listOf(MealEntry(slot = MealSlot.LUNCH.name, person = null))))
    assertFalse(hasComedorMenu(listOf(MealEntry(slot = MealSlot.DINNER.name, person = "Oihan"))))
}

@Test fun `manualComedorMonth`() {
    val today = LocalDate.of(2026, 10, 30)
    assertEquals(YearMonth.of(2026, 11), manualComedorMonth(YearMonth.of(2026, 11), today))
    assertEquals(YearMonth.of(2026, 10), manualComedorMonth(YearMonth.of(2026, 10), today))
    assertNull(manualComedorMonth(YearMonth.of(2026, 9), today))
    assertEquals(YearMonth.of(2026, 11), manualComedorMonth(null, today))
}
@Test fun `isStaleComedorMonth`() {
    assertTrue(isStaleComedorMonth(YearMonth.of(2026, 10), YearMonth.of(2026, 11)))
    assertFalse(isStaleComedorMonth(YearMonth.of(2026, 11), YearMonth.of(2026, 11)))
    assertFalse(isStaleComedorMonth(YearMonth.of(2026, 12), YearMonth.of(2026, 11)))
    assertFalse(isStaleComedorMonth(null, YearMonth.of(2026, 11)))
}
```

Comprueba los nombres reales de `MealSlot` (`LUNCH` y el de la cena) y los parámetros de `MealEntry` antes de escribir el test.

- [ ] **Step 2: Ejecutar y ver que fallan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ComedorMenuLogicTest"`
Expected: FAIL de compilación (símbolos sin definir).

- [ ] **Step 3: Implementar `ComedorMenuLogic.kt`**

Fechas: `month.atDay(1)` y `month.atEndOfMonth()` con `atStartOfDay(zone).toInstant()`, formateadas con `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)`. `PkServicio` lleva el texto `["<pk>"]` (escrito a mano o con `Gson().toJson(listOf(pk))`). Huella con `MessageDigest.getInstance("SHA-256")`. KDoc que explique por qué se envían fechas aunque Menuo las ignore.

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ComedorMenuLogicTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/ComedorMenuLogic.kt app/src/test/kotlin/com/homepantry/app/data/ComedorMenuLogicTest.kt
git commit -m "feat: lógica de la petición del menú del comedor"
```

### Task 2: Ventana de comprobación y próxima ejecución

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/ComedorSchedule.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/ComedorScheduleTest.kt`

**Interfaces:**
- Produces:
  - `const val COMEDOR_CHECK_HOUR = 10`
  - `fun comedorTargetMonth(today: LocalDate): YearMonth?`: en los 3 últimos días del mes (`day > lengthOfMonth - 3`), el mes siguiente; del 1 al 5, el mes actual; si no, `null`.
  - `fun nextComedorCheck(now: ZonedDateTime, gotTarget: Boolean): ZonedDateTime`: el primer día `d > now.toLocalDate()` con `comedorTargetMonth(d) != null` y, si `gotTarget`, además `comedorTargetMonth(d) != comedorTargetMonth(now.toLocalDate())`, a las `COMEDOR_CHECK_HOUR:00` en `now.zone`. Búsqueda día a día con un tope de 62 días.
  - `fun initialComedorCheck(now: ZonedDateTime): ZonedDateTime`: `now` si hoy está en la ventana; si no, `nextComedorCheck(now, false)`.

- [ ] **Step 1: Escribir los tests que fallan**

```kotlin
private val madrid = ZoneId.of("Europe/Madrid")
private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, madrid)

@Test fun `ventana`() {
    assertNull(comedorTargetMonth(LocalDate.of(2026, 10, 28)))
    assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 10, 29)))
    assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 10, 31)))
    assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 11, 1)))
    assertEquals(YearMonth.of(2026, 11), comedorTargetMonth(LocalDate.of(2026, 11, 5)))
    assertNull(comedorTargetMonth(LocalDate.of(2026, 11, 6)))
    assertNull(comedorTargetMonth(LocalDate.of(2027, 2, 25)))
    assertEquals(YearMonth.of(2027, 3), comedorTargetMonth(LocalDate.of(2027, 2, 26)))
    assertEquals(YearMonth.of(2027, 1), comedorTargetMonth(LocalDate.of(2026, 12, 30)))
}

@Test fun `sin menú: mañana dentro de la ventana`() {
    assertEquals(at(2026, 10, 31, 10), nextComedorCheck(at(2026, 10, 30), false))
    assertEquals(at(2026, 11, 1, 10), nextComedorCheck(at(2026, 10, 31), false))
}
@Test fun `sin menú el día 5: siguiente ventana`() =
    assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 11, 5), false))
@Test fun `con menú: siguiente ventana aunque queden días`() {
    assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 10, 30), true))
    assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 11, 2), true))
}
@Test fun `fuera de la ventana`() =
    assertEquals(at(2026, 11, 28, 10), nextComedorCheck(at(2026, 11, 15), false))
@Test fun `primera programación`() {
    assertEquals(at(2026, 10, 30), initialComedorCheck(at(2026, 10, 30)))
    assertEquals(at(2026, 11, 28, 10), initialComedorCheck(at(2026, 11, 15)))
}
```

- [ ] **Step 2: Ejecutar y ver que fallan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ComedorScheduleTest"`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar `ComedorSchedule.kt`** con las firmas de arriba.

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ComedorScheduleTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/ComedorSchedule.kt app/src/test/kotlin/com/homepantry/app/data/ComedorScheduleTest.kt
git commit -m "feat: ventana de comprobación del menú del comedor"
```

### Task 3: Descarga, PDF y lectura con Gemini

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/ComedorMenuApi.kt` (Retrofit, excepciones y `downloadComedorPdf`)
- Create: `app/src/main/kotlin/com/homepantry/app/data/ComedorMenuReader.kt` (`renderPdfFirstPage`, `fetchComedorMenu`)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/GeminiMenuRecognizer.kt` (separar la llamada común)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptRecognizer.kt` (`encodeBitmapForGemini`)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/UserPrefs.kt` (huella)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/MenuImportDraft.kt` (`menuImportDraftFile`)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt:279-281` (usar `menuImportDraftFile`)

**Interfaces:**
- Consumes: `comedorRequestBody`, `isPdf`, `pdfFingerprint`, `ComedorConfig` (Task 1).
- Produces:
  - `interface ComedorMenuApi { @POST("plapi/Report/GeneratePdfFigma") suspend fun generatePdf(@Body body: List<ComedorParam>): Response<ResponseBody> }` y `object ComedorMenuClient { fun api(): ComedorMenuApi }`, como `OpenFoodFactsClient`.
  - `sealed class ComedorMenuException : Exception() { class Network(cause: Throwable); class Http(val code: Int); object NotPdf }`. Sin texto visible: la UI elige el string.
  - `suspend fun downloadComedorPdf(month: YearMonth): ByteArray`: `IOException` → `Network`, `!isSuccessful` → `Http`, `!isPdf` → `NotPdf`.
  - `suspend fun renderPdfFirstPage(context: Context, pdf: ByteArray): Bitmap` en `Dispatchers.IO`: fichero temporal `File.createTempFile("comedor", ".pdf", context.cacheDir)` borrado en `finally`. Se escala para que el lado mayor mida `MAX_OCR_SIDE`, con fondo blanco (`eraseColor(Color.WHITE)`) antes de `page.render(..., RENDER_MODE_FOR_DISPLAY)`. Si falla `PdfRenderer` (`IOException`/`SecurityException`) → `ComedorMenuException.NotPdf`.
  - `internal suspend fun encodeBitmapForGemini(bitmap: Bitmap): String`: JPEG 85 + Base64 `NO_WRAP` en `Dispatchers.Default`, reutilizado por `loadPhotoBytesForGemini` donde encaje.
  - `suspend fun recognizeMenuBitmapWithGemini(bitmap: Bitmap, apiKey: String, model: String): ParsedMenu`. Mismo prompt y esquema: `recognizeMenuWithGemini` y esta función comparten una `private suspend fun recognizeMenuJpeg(base64: String, apiKey, model)`.
  - `sealed interface ComedorFetch { object Unchanged; data class Read(val menu: ParsedMenu, val fingerprint: String) }`
  - `suspend fun fetchComedorMenu(context: Context, month: YearMonth, apiKey: String, model: String, skipIfFingerprint: String?): ComedorFetch`: descarga → si la huella coincide con `skipIfFingerprint`, `Unchanged` → render → Gemini (`bitmap.recycle()` en `finally`) → `Read`.
  - `UserPrefs.comedorFingerprint: Flow<String?>` y `suspend fun saveComedorFingerprint(value: String)` (clave `comedor_pdf_fingerprint`).
  - `fun menuImportDraftFile(context: Context, householdCode: String): File` = `File(context.applicationContext.filesDir, "menu_import_draft_$householdCode.json")`.

- [ ] **Step 1: Implementar los ficheros de arriba.** `CancellationException` se relanza antes del `catch (e: IOException)` cuando haya un `catch` genérico.

- [ ] **Step 2: Comprobar que compila y que los tests existentes siguen pasando**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, con `GeminiMenuRecognizerTest` en verde.

- [ ] **Step 3: Commit**

```bash
git add -A app/src/main/kotlin
git commit -m "feat: descarga y lectura del PDF del menú del comedor"
```

### Task 4: Acción manual en la pantalla de Menú

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/MenuViewModel.kt` (junto a `readMenuPhoto`, ~l. 284-333)
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/MenuScreen.kt` (selector `showImportSource`, ~l. 192-215)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `fetchComedorMenu`, `ComedorMenuException` (Task 3); `manualComedorMonth`, `ComedorConfig.PERSON` (Task 1).
- Produces:
  - `fun MenuViewModel.readComedorMenu(context: Context, apiKeyStore: GeminiApiKeyStore, today: LocalDate, messages: ComedorMessages)` con `data class ComedorMessages(val noKey: String, val empty: String, val network: String, val changed: String, val stale: (YearMonth) -> String)` en `ui/`. Mismo esqueleto que `readMenuPhoto`: ignora la llamada si `_readingMenu` o si ya hay `_importDraft`; `skipIfFingerprint = null`. `Network` → `network`; `NotPdf` y `Http` → `changed`; `manualComedorMonth == null` → `stale(detected)`; sin días → `empty`. Si no, `MenuImportDraft(menu.month, month, ComedorConfig.PERSON, menu.days)`. Con éxito guarda la huella en `UserPrefs` (para que el job no vuelva a leer ese PDF).
  - `fun MenuViewModel.reloadImportDraft()`: si `_importDraft.value == null && !_readingMenu.value`, carga `importDraftStore.load()` y lo publica.

Strings nuevos:

```xml
<string name="menu_import_dialog_comedor">Menú del comedor</string>
<string name="menu_import_comedor_network">No se pudo descargar el menú del comedor. Comprueba la conexión.</string>
<string name="menu_import_comedor_changed">La web del comedor ha cambiado y no se puede leer el menú.</string>
<string name="menu_import_comedor_stale">El comedor aún no ha publicado el menú nuevo (sigue el de %1$s).</string>
```

`menu_import_no_key` pasa a decir «Para importar el menú hace falta tu clave de Gemini…» (ya no solo desde una foto). El mes de `stale` se formatea con `LLLL yyyy` en `es`, como en `PurchaseHistoryScreen`.

- [ ] **Step 1: Implementar `readComedorMenu` y `reloadImportDraft`.**
- [ ] **Step 2: Añadir la tercera opción al selector de `MenuScreen`.** Llama a `readComedorMenu` con `LocalDate.now()`. Además, `LaunchedEffect(Unit) { menuViewModel.reloadImportDraft() }`, para que aparezca el borrador que dejó el job.
- [ ] **Step 3: Compilar y pasar los tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Prueba manual en el S25 (debug).** Menú → Importar → «Menú del comedor» → se abre la revisión de noviembre 2026 con «Para quién: Oihan». Sin red: aparece el mensaje de red.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main
git commit -m "feat: importar el menú del comedor desde la pantalla de Menú"
```

### Task 5: Comprobación automática con notificación

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ComedorCheckJobService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (servicio, igual que `OffersCheckJobService`)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (programar junto a ofertas, ~l. 333-337, y `EXTRA_OPEN_MENU` en `onCreate`/`onNewIntent` como `pendingNotificationId`)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Tasks 1-3. `MealEntriesRepository.getRange(from, to)`, `MenuImportDraftStore`, `menuImportDraftFile`, `GeminiApiKeyStore`, `UserPrefs`.
- Produces: `fun scheduleComedorCheck(context: Context, at: ZonedDateTime, replace: Boolean)`, `fun ensureComedorChannel(context: Context)`, `const val EXTRA_OPEN_MENU = "com.homepantry.app.OPEN_MENU"`. Id del job: `4_202`. Canal: `"comedor"`.

`check()` devuelve el siguiente `ZonedDateTime` (o null si no hay casa) y sigue el spec, §Comprobación automática:

1. Sin casa → null.
2. `target = comedorTargetMonth(hoy)` null → `nextComedorCheck(now, false)`.
3. Login anónimo si hace falta (como en ofertas). `hasComedorMenu(getRange(target.atDay(1), target.atEndOfMonth()))` → `nextComedorCheck(now, true)`, sin descargar.
4. `MenuImportDraftStore(menuImportDraftFile(...)).load() != null` → `nextComedorCheck(now, false)`.
5. Sin clave → `nextComedorCheck(now, false)`.
6. `fetchComedorMenu(..., skipIfFingerprint = prefs.comedorFingerprint.first())`. `Unchanged` → mañana.
7. `Read`: se guarda siempre la huella. a) `isStaleComedorMonth(menu.month, target)` o sin días → mañana. b) Si no, se guarda `MenuImportDraft(menu.month, menu.month ?: target, ComedorConfig.PERSON, menu.days)`, se notifica y `nextComedorCheck(now, true)`.
8. Cualquier excepción (salvo cancelación) → `nextComedorCheck(now, false)`. `onStopJob` → reintento en 1 h dentro de la ventana (`now.plusHours(1)`).

Notificación: sin permiso no se lanza (como en ofertas). Intent a `MainActivity` con `EXTRA_OPEN_MENU = true` y las mismas flags. `MainActivity` navega a `menu` cuando `navReady` y `MenuScreen` recarga el borrador (Task 4).

Strings:

```xml
<string name="comedor_channel_name">Menú del comedor</string>
<string name="comedor_channel_description">Aviso cuando se publica el menú del comedor del mes</string>
<string name="comedor_notification_title">Menú del comedor de %1$s listo para revisar</string>
<string name="comedor_notification_text">Tócalo para revisarlo e importarlo.</string>
```

- [ ] **Step 1: Implementar el servicio, el manifest y la programación en `MainActivity`** con `initialComedorCheck(ZonedDateTime.now())` y `replace = false`.
- [ ] **Step 2: Compilar y pasar los tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Prueba en el S25 (debug).** Sin menú de Oihan en noviembre y sin borrador:
  1. Fuerza el job con `adb shell cmd jobscheduler run -f com.homepantry.app 4202`. Como la prueba de la Task 4 guardó la huella, no debe llegar ninguna notificación (`Unchanged`, sin llamar a Gemini).
  2. `adb shell pm clear com.homepantry.app`, vuelve a entrar con el código de casa y el nombre, vuelve a poner la clave de Gemini en Ajustes (también se borra) y fuerza el job otra vez. Debe llegar la notificación y, al tocarla, abrirse Menú con la revisión de noviembre para Oihan.
  3. Fuérzalo una tercera vez: ni notificación ni llamada a Gemini (hay un borrador).

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main
git commit -m "feat: comprobación automática del menú del comedor con aviso"
```

### Task 6: Verificación final

- [ ] **Step 1:** Run: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`. Expected: 0 fallos de test, 0 errores de lint y `app-release.apk` de unos 4,5 MB.
- [ ] **Step 2:** Instalar el APK release en el S25 y repetir la prueba manual de la Task 4 y el job de la Task 5 (pasa por R8: `ComedorParam` debe serializarse bien).
- [ ] **Step 3:** Actualizar la memoria «Menú desde URL» como hecha y fusionar la rama siguiendo superpowers:finishing-a-development-branch.
