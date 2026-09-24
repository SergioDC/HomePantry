# Importar el menú mensual desde una foto (Gemini)

**Fecha:** 2026-09-21
**Estado:** Aprobado en brainstorming, pendiente de revisión del spec y plan de implementación

## Contexto y objetivo

La casa recibe un menú mensual impreso (tipo comedor): una cuadrícula de lunes a viernes con el
número del día dentro de cada recuadro y, por día, el primero, el segundo, la verdura, el postre
y, a veces, el pan. Hoy habría que teclear cada plato a mano en el calendario del Menú.

Se añade **«Importar menú desde foto»** a la pestaña Menú: se saca una foto (o se elige de la
galería), Gemini la interpreta, se revisa lo leído y, al confirmar, las entradas se añaden al
calendario y los platos que no existan se crean.

Reutiliza lo ya construido para el OCR de tickets con Gemini (2026-09-17): cliente Retrofit,
salida JSON estructurada, errores tipados, reintentos ante 503 y la API key cifrada que cada
usuario aporta en Ajustes. No cambia el modelo de datos del menú (2026-09-20).

### Decisiones tomadas en el brainstorming

| Tema | Decisión |
|---|---|
| Tipo de menú | Mensual, solo de lunes a viernes, con el número de día dentro de cada recuadro. |
| Mes destino | Se elige en la hoja de revisión (por defecto el mes visible). Si Gemini lee mes y año en la cabecera, se ofrece como sugerencia, pero decide el usuario. |
| Franja | Siempre **Comida**. |
| Qué se importa | Primero, segundo, verdura y postre. **Sin el pan.** Cada plato es una entrada aparte en la comida del día. |
| Platos | Los que no existan se crean, sin ingredientes. |
| Días que ya tienen menú | Nunca se borra nada: se añade detrás y se omiten los duplicados exactos. |
| Sin clave de Gemini | No hay respaldo con OCR clásico (no puede leer una cuadrícula): se avisa y no se hace nada. |
| Enfoque | Una sola llamada a Gemini con salida JSON estructurada. |

Descartados: OCR clásico + Gemini sobre el texto (el OCR pierde la estructura de la cuadrícula) y
campos fijos por día `primero/segundo/verdura/postre` (`MealEntry` no tiene categoría, y rompe con
menús de «plato único»).

## Flujo

1. Menú ⋮ de la pestaña Menú → **«Importar menú desde foto»**, con cámara o galería, como en tickets
   (permiso de cámara con accompanist, captura vía `createReceiptCaptureUri`).
2. Sin API key de Gemini: aviso que indica dónde configurarla (Ajustes); no se llama a nada.
3. Indicador de carga mientras Gemini procesa. Un fallo se muestra con su mensaje y no se guarda nada.
4. Se abre **`MenuImportSheet`** con:
   - selector de **mes** (por defecto el visible; con la sugerencia de la cabecera si la hay);
   - aviso de mes dudoso, si procede (ver «Comprobación del mes»);
   - los días leídos, con sus platos editables o descartables; al editar un nombre salen
     sugerencias de platos existentes (`suggestDishes`);
   - etiqueta **«nuevo»** en los platos que aún no existen.
5. Al confirmar se crean los platos nuevos y las entradas de Comida, y se muestra un resumen:
   «Añadidos 84 platos (12 nuevos). 3 ya estaban. 1 día descartado».

## Lectura con Gemini

Misma llamada `v1beta/interactions` que el ticket (`GeminiInteractionRequest`), con la foto y un
prompt propio. El prompt le indica:

- que es un menú mensual de lunes a viernes y que el número de día está dentro de cada recuadro:
  los platos se asignan **por ese número**, no por la posición en la cuadrícula;
- que devuelva por día los platos en el orden impreso (primero, segundo, verdura, postre) y
  **excluya el pan**;
- que ignore textos que no son platos («Festivo», «No lectivo») y omita los recuadros vacíos;
- que devuelva `month` y `year` solo si aparecen en la cabecera.

Esquema de salida:

```json
{
  "month": 9,
  "year": 2026,
  "days": [ { "day": 21, "dishes": ["Lentejas", "Merluza al horno", "Ensalada", "Fruta"] } ]
}
```

`month` y `year` son opcionales; `days` es obligatorio. Un `month` fuera de 1..12 se ignora.

La foto se prepara con el mismo cargador que el ticket (lado máximo `MAX_OCR_SIDE` = 2000 px y
orientación EXIF física). Una cuadrícula mensual es densa: si 2000 px se queda corto al probar con
una foto real, se sube el límite para esta llamada.

## Lógica pura (con tests unitarios)

`data/MenuImport.kt`, sin dependencias de Android:

```kotlin
data class ParsedMenuDay(val day: Int, val dishes: List<String>)
data class ParsedMenu(val month: YearMonth?, val days: List<ParsedMenuDay>)   // ya limpio
data class MenuImportPlan(val newDishNames: List<String>, val entries: List<PlannedEntry>, val skipped: SkipCounts)

fun cleanParsedMenu(month: YearMonth?, days: List<ParsedMenuDay>): ParsedMenu
fun planMenuImport(menu: ParsedMenu, month: YearMonth, dishes: List<Dish>, existing: List<MealEntry>): MenuImportPlan
fun weekendDayCount(menu: ParsedMenu, month: YearMonth): Int
```

### Limpieza (`cleanParsedMenu`)

- Se recortan espacios y se descartan los platos en blanco.
- Se descartan los días fuera de 1..31 y los días sin platos.
- Los platos repetidos dentro de un día (tras `normalizeProductName`) quedan en uno.
- Si un día aparece dos veces se fusionan.
- Un plato que sea exactamente «pan» (normalizado) se descarta: red de seguridad por si el prompt falla.
- Un nombre entero en MAYÚSCULAS pasa a «Mayúscula inicial» («LENTEJAS CON VERDURAS» → «Lentejas con
  verduras»), porque los menús impresos suelen ir así.

### Plan (`planMenuImport`)

- Coincidencia con platos existentes: `matchDish` (nombre normalizado idéntico). No hay coincidencia
  difusa: «Lentejas» y «Lentejas estofadas» son platos distintos. La corrección, si hace falta, se
  hace en la revisión con las sugerencias.
- Platos nuevos: uno por nombre normalizado, aunque salga en muchos días; todas las entradas de
  ese plato apuntan a él.
- Los días que no existen en el mes elegido (un «31» en un mes de 30) se descartan y se cuentan.
- Las entradas van a `MealSlot.LUNCH`, con `order` siguiente al de lo que ya haya esa comida.
- Se omite un plato si esa comida ya tiene una entrada con el mismo nombre normalizado, así
  reimportar la misma foto no duplica nada. Nunca se borra nada.
- `skipped` cuenta los omitidos por ya existentes y los días descartados, para el resumen.

### Comprobación del mes (`weekendDayCount`)

El menú es solo de lunes a viernes, así que un día leído que cae en sábado o domingo del mes
elegido indica un mes o año equivocado. Si el conteo es mayor que 0, la hoja de revisión muestra un
aviso («5 días caen en fin de semana en septiembre 2026: ¿es el mes correcto?»). El aviso no
descarta nada; decide el usuario.

## Escritura

- Escritura en lote de Firestore: primero los platos nuevos (ids generados en el cliente con
  `collection.document()`), después las entradas con su `dishId`. Así no quedan entradas apuntando a
  platos que no se llegaron a crear. Los lotes son de como mucho 500 operaciones, como en duplicar
  semana.
- Los platos nuevos se crean con `ingredients = emptyList()`, `note = null` y `addedBy` = usuario.
- Las entradas usan `addedBy` = usuario y `date` en «yyyy-MM-dd».
- Las entradas existentes del mes se leen con `MealEntriesRepository.getRange` en el momento de
  confirmar, no de la caché de la pantalla: el mes elegido puede no estar entre los cargados.
- La escritura corre en el scope del `MenuViewModel` (como `duplicateWeek`): cerrar la hoja no la
  deja a medias. Si falla, se publica en `error`.
- Cambios en `Dish` y `firestore.rules`: ninguno (`MealEntry` gana `person` en la ampliación del final).

## Errores

Se reutiliza la jerarquía `GeminiReceiptException`; sus mensajes ya están en español.

| Situación | Qué pasa |
|---|---|
| Sin API key | No se llama a nada; aviso con la indicación de Ajustes. |
| Clave inválida, cuota, modelo, red o respuesta ilegible | Mensaje con el motivo; no se guarda nada. Los 5xx transitorios se reintentan solos. |
| Gemini no devuelve ningún plato | «No he encontrado platos en la foto»; no se abre la hoja. |
| Se pierde la URI de la captura (recreación de la actividad) | Mismo aviso que en tickets. |
| Falla el guardado | Se publica en `error` del `MenuViewModel`; el lote evita el estado a medias. |

## Estructura de archivos

**Nuevos** (`app/src/main/kotlin/com/homepantry/app/`):
- `data/GeminiMenuRecognizer.kt`: prompt, esquema, petición y conversión del JSON a `ParsedMenu`.
- `data/MenuImport.kt`: lógica pura de arriba.
- `ui/screens/MenuImportSheet.kt`: hoja de revisión.

**Existentes que cambian:**
- `data/GeminiReceiptRecognizer.kt`: `callGemini` y el cargador de imagen pasan de `private` a
  `internal`, y el cargador se renombra a un nombre genérico (su mensaje de error dice «foto del
  ticket»). Cambio mínimo para no duplicar código.
- `data/DishesRepository.kt` y `data/MealEntriesRepository.kt`: escritura en lote de platos y
  entradas con ids del cliente (se comprueba en el plan cómo encaja con `addDish` y `applyBatch`).
- `ui/MenuViewModel.kt`: `importMenu(plan)`.
- `ui/screens/MenuScreen.kt` y `MenuCalendarTab.kt`: entrada en el ⋮, lanzadores de cámara y
  galería, permiso de cámara y estado de la hoja.
- `res/values/strings.xml`: textos nuevos.

La llamada a Gemini va en la pantalla, como en `PurchaseHistoryScreen`, no en el ViewModel (que no
tiene `Context`).

## Tests

JUnit 4, sin dependencias nuevas, en `app/src/test/kotlin/com/homepantry/app/data/`:

- `MenuImportTest`: limpieza (vacíos, repetidos, día fuera de rango, día duplicado fusionado,
  «pan» descartado, MAYÚSCULAS), platos nuevos deduplicados, coincidencia con existentes, `order`
  detrás de lo que ya hay, omitir duplicados al reimportar, días que no existen en el mes, y
  `weekendDayCount` (septiembre de 2026: el día 5 es sábado).
- `GeminiMenuRecognizerTest`, al estilo de `GeminiReceiptRecognizerTest`: JSON válido, JSON roto,
  falta `days`, `month` fuera de 1..12 ignorado.

La UI de Compose no lleva tests, como el resto de pantallas del proyecto; se comprueba con
`./gradlew assembleDebug` y a mano en el móvil con una foto real del menú.

## Fuera del alcance de la primera versión

- Varias fotos o páginas en una misma importación, y PDF.
- Coincidencia difusa de platos.
- Desayunos y cenas (todo va a Comida).
- Importar ingredientes.
- Fines de semana (el menú no los trae; los días leídos que caigan en uno solo generan el aviso).
- Una alternativa sin API key (OCR clásico).

## Ampliación: asignar el menú importado a una persona

Decidido en un segundo brainstorming, sobre lo anterior:

| Tema | Decisión |
|---|---|
| Quién | Nombres libres, sin lista que mantener. (`Familia` dejó de ser «sin persona»: ver la corrección del final.) |
| Alcance | Una persona para toda la importación (un selector en la hoja de revisión), no por día ni por plato. |
| Modelo | `MealEntry.person: String? = null` (`null` = sin asignar). Las entradas anteriores siguen valiendo sin migrar; `Dish` no cambia. |
| Duplicados | La clave para omitir lo ya presente pasa a ser día + comida + plato normalizado + persona normalizada: el mismo plato para otra persona el mismo día no es un duplicado. Los platos siguen compartidos entre personas. |
| Nombres | En blanco o «familia» = Familia. Un nombre que coincide con uno ya usado (sin mayúsculas ni acentos) reutiliza su grafía. Máximo 30 caracteres. La clave de comparación no quita la «s» final (Marco y Marcos son distintos). |
| Atajos | Los chips de la hoja de revisión salen de las entradas que el calendario tiene cargadas; si la persona no está ahí se escribe una vez. |
| Fuera de alcance | Elegir persona al añadir a mano en la hoja del día, y cambiar la persona de una entrada ya guardada. |

**Cómo se ve.** Los platos de una franja se agrupan por persona (`groupByPerson`: primero la familia y
luego cada persona por orden de aparición) y el nombre sale **una sola vez** por grupo, en verde menta:

- Semana: dentro del mismo `FlowRow`, delante de sus chips (`Comida  Pepe [Lentejas] [Merluza]…`).
- Hoja del día: como subtítulo sobre sus platos.
- Imagen de compartir: `Pepe: Lentejas, Merluza, …`, y cada grupo en su línea si hay más de uno.
- Vista mes: sin cambios.

Menta clara (`Mint400`) en la app, de tema oscuro; menta oscura (`MintPrimary`) en la imagen de
compartir, de fondo blanco, donde la clara casi no se leería.

**Archivos:** `MealEntry.kt`, `MealEntriesLogic.kt` (`groupByPerson`, `normalizePerson`, `knownPeople`),
`MenuImport.kt` (parámetro `person`), `MenuViewModel.kt`, `MenuImportSheet.kt`, `MenuScreen.kt`,
`MenuCalendarTab.kt`, `DayMealsSheet.kt`, `WeekShare.kt`, `ui/components/PersonLabel.kt`, `strings.xml`.
Tests en `MealEntryPersonTest` y `MenuImportTest`.

## Ampliación: reasignar lo ya metido y colores por persona

Decidido en un tercer brainstorming, sobre la ampliación anterior (que dejaba fuera las dos cosas):

| Tema | Decisión |
|---|---|
| Reasignar | Por plato (selector «Para quién» al añadir o editar en la hoja del día), por día («Asignar el día a…» en esa hoja) y por mes («Asignar el mes a…» en el ⋮). |
| Diálogo de día y mes | Selector de persona, casilla «Solo los que aún no tienen persona» (marcada por defecto, para no pisar lo que ya es de otra) y el número de platos que cambiarán, leído en el momento. Se guarda en lotes de 500. |
| Personas | Colección `households/{code}/people/{personDocId}` con `name` y `color`. Las reglas ya la cubren. El documento se crea la primera vez que se asigna a alguien; los atajos salen de ahí (unión con lo que traigan las entradas cargadas), así que ya no dependen del rango visible del calendario. |
| Colores | Paleta fija de 8 (`PersonColor`: menta por defecto, cielo, lila, rosa, coral, ámbar, lima, gris), compartida por toda la casa. Se eligen en «Colores de las personas» (⋮), Familia incluida. |
| Tonos | Cada color tiene un tono claro para la app oscura y otro oscuro para la imagen blanca de compartir. `PersonColorTest` exige contraste WCAG ≥ 4,5:1 de cada tono contra sus fondos. |
| Dónde se ve | El color tiñe el nombre de la persona y también sus chips, así Familia se distingue por el tono de sus chips aunque no lleve etiqueta. Sin color elegido todo se ve como antes (nombre en menta, chips normales). |
| Fuera de alcance | Renombrar o borrar personas, y quitar un color ya elegido (se cambia por otro). |

**Lógica pura con tests:** `personDocId`, `colorFor`, `knownPeople(entries, people)`, `assignableEntries`
(`PeopleLogicTest`) y la paleta (`PersonColorTest`).
**Archivos nuevos:** `Person.kt`, `PersonColor.kt`, `PeopleRepository.kt`, `PersonPicker.kt`,
`AssignPersonDialog.kt`, `PeopleColorsSheet.kt`. `PersonLabel` recibe ahora las personas para colorear.

## Corrección: Familia es una persona, no «sin asignar»

En las ampliaciones anteriores `null` significaba «toda la familia». Eso confundía dos cosas: una
entrada sin asignar y una asignada a Familia se guardaban igual, así que asignar a Familia no
cambiaba nada y Familia nunca salía en el calendario.

- **`null` = sin asignar** (sin etiqueta ni tinte, como cualquier plato sin persona).
- **Familia es una persona más**: se guarda como `"Familia"` en la entrada, tiene su documento y su
  color, y sale con etiqueta y chips teñidos en el calendario, la hoja del día y la imagen.
- `normalizePerson("familia")` devuelve siempre `"Familia"`; en blanco devuelve `null`.
- `knownPeople` ofrece Familia siempre, la primera, aunque nadie se haya asignado aún.
- `colorFor(null)` es `null`: lo sin asignar no tiene color.
- El selector «Para quién» tiene un chip **«Sin asignar»** (el de por defecto) y Familia sale entre
  las personas.
- Las entradas que ya existían siguen «sin asignar»: para que salgan como Familia hay que
  asignarlas («Asignar el mes a…» → Familia).
