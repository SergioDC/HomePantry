# Menú del comedor (Ausolan / Menuo): diseño

Fecha: 2026-10-30. Retoma la idea aparcada el 2026-10-28 de importar el menú desde la web.

## Intención

Oihan come en el comedor del Colegio Basauri, que gestiona Ausolan y publica el menú mensual en
Menuo. Hoy hay que hacer una foto del menú e importarla. Queremos que el menú del mes siguiente
llegue solo, a final de mes, listo para revisar, gastando la menor cuota de Gemini posible y sin
dejar ficheros en el móvil.

## Objetivos

1. Acción manual «Importar menú del comedor» en la pantalla de Menú.
2. Comprobación automática en segundo plano a final y principio de mes que deja el menú como
   borrador y avisa con una notificación.
3. Las dos reutilizan el flujo de importación desde foto: Gemini → `ParsedMenu` →
   `MenuImportDraft` → `MenuImportDialog` → `importMenu`.

## No objetivos

- Endpoints JSON de Menuo (`GetMenu`, `GetPlanificacion`, `GetFestivos`).
- Menú en euskera u otros centros, dietas o servicios. Los ids quedan en constantes, sin UI de
  configuración.
- WorkManager.

## Anti-objetivos (sería un fallo aunque funcione)

- Importar sin que el usuario revise el borrador.
- Pisar o borrar entradas del menú existentes, o pisar un borrador a medio revisar.
- Llamar a Gemini más de una vez por cada PDF distinto.
- Guardar el PDF o la imagen en disco (ni en `cacheDir`).
- Que un cambio de la API de Menuo provoque un crash.

## Fuente de datos

`POST https://apimenuo.ausolan.com/plapi/Report/GeneratePdfFigma`, `Content-Type: application/json`,
sin autenticación. Cuerpo: un array de `{nombre, valorObjeto}` con todos los valores como string:

| nombre | valorObjeto |
|---|---|
| FechaIni | primer día del mes a las 00:00 de Europe/Madrid, en UTC ISO con milisegundos (`2026-10-31T23:00:00.000Z`) |
| FechaFin | último día del mes, mismo formato |
| PkMenu | `fce8da488dcf48bab961b36a009fcfdc` |
| PkCentro | `4cf8682f16604ae1b1dcb2e400f2972e` |
| PkCentroEnvio | `cef83569f1ae40cc816cb3d300cf64d9` |
| PkDieta | `d12fe25a9bbd49239e3cd65eef62e7c5` (Basal) |
| PkServicio | `["6f6096586ff248189d7d535b8509f411"]` (array JSON serializado como string) |
| PkIdioma | `00242C08E94B10101810432101271385` (castellano) |
| NombreArchivo | `menu` |
| TipoVista | `Mensual` |

La respuesta es un PDF (`application/pdf`, unos 224 KB) de una página apaisada que es solo imagen.
Menuo ignora las fechas y devuelve el mes publicado en ese momento. Aun así se envían las del mes
objetivo, por si lo corrigen. El mes real se lee de la cabecera de la imagen («Noviembre 2026»), y
eso ya lo hace el prompt de Gemini (`ParsedMenu.month`).

Nota: la web envió `FechaIni = 2026-11-01T23:00:00.000Z` (el lunes 2 a medianoche en Madrid, no el
día 1). Como el servidor ignora las fechas, aquí se usa el día 1 del mes, que es lo coherente. Es una
decisión mía.

## Decisiones del usuario

- Menú en castellano, asignado a **Oihan**.
- Automático, pero con notificación y revisión. Nunca se importa directamente.
- La comprobación no es diaria todo el mes: solo de 3 días antes de fin de mes al día 5 del
  siguiente, una vez al día, y para en cuanto llega el menú del mes nuevo.
- Si el menú del mes objetivo ya está en la app, no se descarga nada.
- No dejar ficheros en el móvil.

## Diseño

### Lógica pura (`data/ComedorMenuLogic.kt`, con tests JUnit)

- `ComedorConfig`: objeto con las constantes de la tabla, la URL base, el idioma y la persona
  (`"Oihan"`).
- `comedorRequestBody(month: YearMonth): List<ComedorParam>`: el cuerpo de la petición.
  `ComedorParam(nombre, valorObjeto)` va en `data/` por R8.
- `comedorMonthRange(month, zone = Europe/Madrid)`: `FechaIni`/`FechaFin` en UTC ISO con
  milisegundos y `Z`. Tests de los cambios de horario (marzo y octubre).
- `comedorTargetMonth(today): YearMonth?`: dentro de la ventana devuelve el mes objetivo (el
  siguiente si estamos en los 3 últimos días del mes, el actual si estamos del día 1 al 5); fuera
  de ella, `null`.
- `nextComedorCheck(now, gotTarget: Boolean): ZonedDateTime`: si `gotTarget` es true o estamos
  fuera de la ventana, la próxima vez es el inicio de la siguiente ventana; si no, mañana. La hora
  fija la decide quien implemente (por ejemplo, 10:00).
- `pdfFingerprint(bytes)`: SHA-256 en hex.
- `isPdf(contentType, bytes)`: el content type es `application/pdf` o los bytes empiezan por `%PDF`.

### Red (`data/ComedorMenuApi.kt`)

Retrofit con Gson, como `OpenFoodFactsApi`: `@POST("plapi/Report/GeneratePdfFigma")` con
`@Body List<ComedorParam>` y respuesta `ResponseBody`. `downloadComedorPdf(month): ByteArray`
lanza `ComedorMenuException` (una clase sellada: `Network`, `NotPdf`, `Http(code)`) con mensajes
en `strings.xml`. Los bytes se quedan en memoria.

### Reconocimiento

- `renderPdfFirstPage(context, bytes): Bitmap` con `PdfRenderer`. `PdfRenderer` necesita un
  `ParcelFileDescriptor`, así que se usa un fichero anónimo en memoria
  (`ParcelFileDescriptor.createPipe` no vale porque necesita seek; usar
  `MemoryFile`/`SharedMemory`) o, si no fuera viable, un fichero temporal en `cacheDir` borrado en
  `finally`. Lo elige quien implemente, sin dejar nada en disco al terminar.
- Ancho del bitmap: similar al de las fotos que ya se mandan (lo que use `encodePhotoForGemini`).
  Fondo blanco antes de `render`, que los PDF pueden ser transparentes.
- `GeminiMenuRecognizer`: se separa la parte común de `recognizeMenuWithGemini` para tener también
  `recognizeMenuBitmapWithGemini(bitmap, apiKey, model)`. El prompt y el esquema no cambian.

### Caso común (`data/ComedorMenuImporter.kt` o similar, sin Compose)

`fetchComedorMenu(context, target, apiKey, model, lastFingerprint): ComedorResult`:
descarga → si la huella es igual a `lastFingerprint`, `Unchanged` (no se llama a Gemini) →
renderiza → Gemini → `Read(menu, fingerprint)`. Los errores son subclases de
`ComedorMenuException` o las `GeminiReceiptException` de siempre. `CancellationException` se
relanza.

La huella del último PDF leído se guarda en `UserPrefs` (DataStore, un string). No es un fichero
del PDF.

### Acción manual (pantalla de Menú)

- Nueva opción «Importar menú del comedor» en el selector de origen que ya existe
  (`showImportSource`: cámara / galería), junto a las otras dos.
- `MenuViewModel.readComedorMenu(...)`: mismo esquema que `readMenuPhoto` (comparte el indicador
  `readingMenu` y el `error`). En manual se ignora la huella: siempre se lee, porque lo ha pedido
  el usuario.
- Resultado:
  - Gemini leyó un mes anterior al actual → error «El comedor aún no ha publicado el menú nuevo
    (sigue el de {mes})», sin borrador.
  - Sin días → el `menu_import_empty` de siempre.
  - Si no, borrador `MenuImportDraft(detectedMonth, month = detectedMonth ?: mes siguiente,
    personText = "Oihan", days)` y se abre `MenuImportDialog`, que ya avisa de fines de semana y
    deja cambiar el mes.
- Si ya hay un borrador, la opción no se ofrece o avisa (como con las fotos).

### Comprobación automática (`ComedorCheckJobService`)

Copia el patrón de `OffersCheckJobService`: JobScheduler (no WorkManager), con su propio id de job
y su propio canal de notificaciones, `setPersisted(true)` y red requerida. Se programa al entrar en
la app con `replace = false` y se reprograma sola con `nextComedorCheck`.

Cada ejecución:

1. Sin casa → no reprograma (MainActivity lo hará).
2. `target = comedorTargetMonth(hoy)`. `null` → reprogramar al inicio de la ventana.
3. **¿Ya está?** Si hay entradas de comida (`LUNCH`) de Oihan en cualquier día de `target` (por
   `MealEntriesRepository.getRange` y `personKey`), no se descarga nada. Se reprograma a la
   siguiente ventana.
4. Si existe un borrador en `MenuImportDraftStore` (de foto o del comedor), no se toca: mañana.
5. Sin clave de Gemini → mañana, sin notificar (mi propuesta: no insistir).
6. `fetchComedorMenu` con la huella guardada. `Unchanged` → mañana.
7. Leído:
   - mes detectado anterior a `target` → se guarda la huella (para no volver a leer ese PDF) y
     mañana;
   - mes detectado igual o posterior a `target`, o null (mi propuesta: tratarlo como `target`), y con días →
     se escribe el borrador en `MenuImportDraftStore`, se guarda la huella, se notifica
     «Menú del comedor de {mes} listo para revisar» y se reprograma a la siguiente ventana;
   - sin días → se guarda la huella y mañana.
8. Errores de red, de PDF o de Gemini → mañana. `onStopJob` → reintento, como en ofertas.

El `MenuViewModel` ya carga el borrador guardado al arrancar (`importDraftStore.load()`), pero solo
una vez. El job escribe con la app cerrada en el caso normal. Al tocar la notificación se abre
MainActivity con un extra que navega a `menu`, y la pantalla abre la revisión. Si la app ya estaba
abierta con el ViewModel vivo, al recibir el intent se vuelve a cargar el borrador del disco (detalle
para quien implemente: un `reloadDraftFromDisk()` que no pise un borrador en memoria).

Sin permiso de notificaciones, el borrador queda guardado igualmente y se ve al abrir Menú.

### Textos

Todo en `res/values/strings.xml`: la opción del selector, el progreso («Descargando menú del
comedor…»), los errores (sin red, la web del comedor ha cambiado, aún no se ha publicado el menú
nuevo) y el canal y el texto de la notificación.

## Restricciones

- Reglas de CLAUDE.md: lógica pura con tests, modelos serializables en `data/`,
  `CancellationException` se relanza, código y commits en español, sin tocar versiones del build.
- La API de Menuo no está documentada: cualquier respuesta rara acaba en un mensaje, nunca en un
  crash.
- Verificación: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`
  y prueba del APK release en el S25 (acción manual y, para el job, forzarlo con
  `adb shell cmd jobscheduler run -f com.homepantry.app <id>`).

## Lo que decide quien implemente

- Hora de la comprobación diaria y si el ancho del bitmap coincide exactamente con el de las fotos.
- Cómo pasar los bytes a `PdfRenderer` sin dejar nada en disco.
- Nombres exactos de ficheros y clases.

## Criterios de aceptación

- La acción manual descarga el PDF sin credenciales y abre la revisión del mes correcto para Oihan.
- En la ventana, el job deja el borrador y notifica una vez. Fuera de la ventana, o si el mes ya
  tiene menú de Oihan, no descarga nada.
- Gemini no se llama dos veces con el mismo PDF en automático.
- Tests del cuerpo, de las fechas del mes (con DST), de la ventana y de la próxima comprobación.
- 0 fallos de test, 0 errores de lint y el release funciona en el S25.
