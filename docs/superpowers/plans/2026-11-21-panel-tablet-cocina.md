# Panel de la tablet de la cocina: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** un «modo panel» de SNHome para la tablet de la cocina: tiempo, menú de hoy y lista de la compra, con tachar y «Se ha acabado…», siempre encendido.

**Architecture:** lógica pura nueva en `data/` (menú de hoy, cuadrícula de «Se ha acabado», tiempo de Open-Meteo, inactividad). Un `PanelViewModel` para el menú de hoy y el tiempo; los productos y zonas salen del `AppViewModel` existente (que ya tiene `setDone` y lo usa `AddItemSheet`). Dos rutas nuevas, `panel` y `panelRestock`; el modo se guarda en `UserPrefs` y decide el destino inicial.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose, Retrofit + Gson (como `OpenFoodFactsApi`), DataStore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-21-panel-tablet-cocina-design.md`

## Global Constraints

- Mismo APK y mismos datos de Firestore; sin backend nuevo.
- Open-Meteo sin clave: `https://api.open-meteo.com/` (previsión) y `https://geocoding-api.open-meteo.com/` (buscar pueblo). Solo se envía la ubicación.
- Modelos de red y de `UserPrefs` en `data/` con valores por defecto en todos los campos (R8, `proguard-rules.pro` conserva `com.homepantry.app.data.**`).
- Lógica testeable en ficheros puros de `data/`, sin Android; tests JUnit 4 en `app/src/test`.
- Textos en `res/values/strings.xml`, en español; tema oscuro y paleta de `ui/theme/Color.kt` (coral solo para alertas y borrar).
- Cambios de un campo con `update`, nunca `set` del objeto entero (ya lo hace `AppViewModel.setDone`).
- `CancellationException` se relanza siempre.
- Sin cambiar `abiFilters`, `minSdk`, `targetSdk` ni AGP.
- Commits en español con prefijo convencional y `Refs #2`; el último lleva `Closes #2`.
- Verificación final: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease` (por partes si se corta) → 0 fallos, 0 errores de lint, release de unos 5,5 MB.

## Review Focus

1. Medianoche con la tablet encendida: la fecha, el menú y el «hoy» del tiempo pasan al día nuevo sin reiniciar. → test de `todayMenuSections` con dos fechas en Task 1 y `PanelViewModel` con la fecha como flujo en Task 3.
2. Producto de una subzona o de una zona borrada en «Se ha acabado»: aparece bajo su zona raíz, o en «Sin zona» al final; nunca desaparece. → tests en Task 1.
3. Respuesta de Open-Meteo incompleta o sin red: no rompe; se ve el último dato con su hora, o «Sin datos del tiempo». → tests de `weatherSnapshot` con campos ausentes en Task 2.
4. Tocar un producto tachado otra vez en los 3 segundos lo deja en la lista; si otro miembro lo cambia mientras tanto, no se pisa. → comprobación manual en Task 3 (el estado del tachado se guarda por id y solo se escribe `done = true`).
5. Tablet sin tocar en otra pantalla (p. ej. Ajustes) con el modo panel activado: vuelve al panel a los 2 minutos; en el móvil (modo apagado) nunca. → tests de `idleAction` en Task 4.

---

### Task 1: Menú de hoy y cuadrícula de «Se ha acabado» (puro)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/PanelLogic.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/PanelLogicTest.kt`

**Interfaces:**
- Consumes: `MealEntry`, `MealSlot`, `Item`, `Zone` (existentes); `normalizeRecipeText(text)` de `RecipeLogic.kt`.
- Produces:
  - `data class MenuLine(val person: String? = null, val name: String = "")` — `person` null para la familia (entrada sin persona o con `"Familia"`).
  - `data class MenuSlotSection(val slot: MealSlot = MealSlot.LUNCH, val lines: List<MenuLine> = emptyList())`
  - `fun todayMenuSections(entries: List<MealEntry>, today: LocalDate): List<MenuSlotSection>`
  - `data class RestockSection(val zoneId: String? = null, val zoneName: String = "", val items: List<Item> = emptyList())` — `zoneId` null = «Sin zona».
  - `fun restockSections(items: List<Item>, zones: List<Zone>, query: String, noZoneName: String): List<RestockSection>`

- [ ] **Step 1: Tests en rojo**

`todayMenuSections`:
- `solo las entradas de hoy`: entradas del 2026-11-21 y del 2026-11-22; con `today = 2026-11-22` solo salen las del 22, y con `2026-11-21` las del 21.
- `franjas en orden y sin vacias`: CENA, COMIDA (sin desayuno) → `[LUNCH, DINNER]`; con una de BREAKFAST → primero BREAKFAST.
- `familia primero y luego personas`: en LUNCH, `("Oihan", "Arroz a la cubana", order 0)`, `(null, "Lentejas", order 1)`, `("Familia", "Ensalada", order 2)` → `[MenuLine(null,"Lentejas"), MenuLine(null,"Ensalada"), MenuLine("Oihan","Arroz a la cubana")]` (familia por `order`, después personas por nombre y `order`).
- `sin entradas`: lista vacía.

`restockSections` (`noZoneName = "Sin zona"`):
- `solo lo que hay en casa`: un item `done = false` no sale.
- `zonas raiz en orden`: zonas Despensa (order 1) y Nevera (order 0) → secciones `["Nevera", "Despensa"]`; las vacías no salen.
- `subzona dentro de su raiz` (Review Focus 2): item en una subzona («Cajón», `parentZoneId` = Nevera) → en la sección «Nevera».
- `zona borrada al final` (Review Focus 2): item con `zone = "zz"` inexistente → última sección `RestockSection(null, "Sin zona", …)`.
- `orden por nombre sin tildes`: «Átun», «aceite», «Café» → `aceite, Átun, Café`.
- `buscador por palabras sin tildes`: consulta `"cafe mol"` encuentra «Café molido» y no «Café en grano»; consulta en blanco no filtra.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PanelLogicTest"`
Expected: FAIL de compilación (`Unresolved reference`).

- [ ] **Step 3: Implementar `PanelLogic.kt`**

Fecha de `MealEntry.date` como `"yyyy-MM-dd"` comparada con `today.toString()`. Franja desconocida en `slot` → se ignora. Las subzonas se resuelven subiendo por `parentZoneId` hasta la raíz.

- [ ] **Step 4: Comprobar que pasan**

Run: el mismo comando. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/PanelLogic.kt app/src/test/kotlin/com/homepantry/app/data/PanelLogicTest.kt
git commit -m "feat: menú de hoy y productos en casa para el panel de cocina (Refs #2)"
```

---

### Task 2: Tiempo de Open-Meteo, ubicación y modo panel en `UserPrefs`

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/Weather.kt` (modelos, API, cliente y mapeo puro)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/UserPrefs.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/WeatherTest.kt`

**Interfaces:**
- Produces:
  - Modelos Gson (todos con valores por defecto, listas anulables):
    `OpenMeteoForecast(current: OpenMeteoCurrent?, hourly: OpenMeteoHourly?, daily: OpenMeteoDaily?)`,
    `OpenMeteoCurrent(time: String?, temperature_2m: Double?, weather_code: Int?)`,
    `OpenMeteoHourly(time: List<String>?, temperature_2m: List<Double?>?, weather_code: List<Int?>?, precipitation_probability: List<Int?>?)`,
    `OpenMeteoDaily(time: List<String>?, temperature_2m_max: List<Double?>?, temperature_2m_min: List<Double?>?, precipitation_probability_max: List<Int?>?)`,
    `OpenMeteoGeocoding(results: List<WeatherPlace>?)`. Nombres de campo con `@SerializedName` si se usan nombres Kotlin en camelCase.
  - `data class WeatherPlace(val name: String = "", val admin1: String = "", val latitude: Double = 0.0, val longitude: Double = 0.0)`
  - `interface OpenMeteoApi { @GET("v1/forecast") suspend fun forecast(...): OpenMeteoForecast }` con `latitude`, `longitude`, `current=temperature_2m,weather_code`, `hourly=temperature_2m,weather_code,precipitation_probability`, `daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max`, `timezone=auto`, `forecast_days=1`.
  - `interface OpenMeteoGeocodingApi { @GET("v1/search") suspend fun search(@Query("name") name: String, @Query("count") count: Int = 5, @Query("language") language: String = "es"): OpenMeteoGeocoding }`
  - `object OpenMeteoClient { fun forecast(): OpenMeteoApi; fun geocoding(): OpenMeteoGeocodingApi }`
  - `enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }`; `fun weatherKind(code: Int): WeatherKind`
  - `data class HourWeather(val hour: Int = 0, val temp: Int = 0, val kind: WeatherKind = WeatherKind.CLEAR)`
  - `data class WeatherSnapshot(val currentTemp: Int = 0, val kind: WeatherKind = WeatherKind.CLEAR, val maxTemp: Int = 0, val minTemp: Int = 0, val rainProbability: Int = 0, val hours: List<HourWeather> = emptyList(), val fetchedAtMillis: Long = 0)`
  - `fun weatherSnapshot(forecast: OpenMeteoForecast, fetchedAtMillis: Long): WeatherSnapshot?`
  - `fun isMorning(time: LocalTime): Boolean`
  - `UserPrefs`: `val panelMode: Flow<Boolean>`, `suspend fun setPanelMode(enabled: Boolean)`, `val weatherPlace: Flow<WeatherPlace?>`, `suspend fun saveWeatherPlace(place: WeatherPlace)` (JSON con Gson; JSON ilegible → null).

- [ ] **Step 1: Tests en rojo**

- `codigos WMO`: 0→CLEAR; 1, 2→PARTLY_CLOUDY; 3→CLOUDY; 45, 48→FOG; 51, 55, 57→DRIZZLE; 61, 65, 67, 80, 82→RAIN; 71, 75, 77, 85, 86→SNOW; 95, 96, 99→STORM; desconocido (42)→CLOUDY.
- `instantanea completa`: previsión con `current.temperature_2m = 13.6`, `weather_code = 2`; `daily` máx 18.4, mín 6.5, lluvia 30; `hourly` de 00:00 a 23:00 del 2026-11-22 → `currentTemp 14`, `kind PARTLY_CLOUDY`, `maxTemp 18`, `minTemp 7`, `rainProbability 30`, `hours.map { it.hour } == (8..22).toList()`.
- `horas solo del dia de hoy`: `hourly` con 48 horas (dos días) → solo las de 8 a 22 del primer día de `daily.time`.
- `sin current o sin daily devuelve null` (Review Focus 3): `current = null` → null; `daily.temperature_2m_max = null` → null.
- `huecos en las horas` (Review Focus 3): una hora con `temperature_2m` null → esa hora se omite, el resto sale.
- `lluvia ausente es 0`: `precipitation_probability_max = null` → `rainProbability 0`.
- `manana hasta las 12`: `isMorning(11:59)` true, `isMorning(12:00)` false.
- `gson de la respuesta real`: un JSON con la forma real de Open-Meteo (`{"current":{"time":"2026-11-22T08:15","temperature_2m":13.6,"weather_code":2},"daily":{…},"hourly":{…}}`) se parsea con `Gson().fromJson(json, OpenMeteoForecast::class.java)` y da la misma instantánea.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.WeatherTest"`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar `Weather.kt` y las claves de `UserPrefs`**

Temperaturas con `roundToInt()`. Claves de DataStore: `panel_mode` (`booleanPreferencesKey`) y `weather_place` (JSON).

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data app/src/test/kotlin/com/homepantry/app/data/WeatherTest.kt
git commit -m "feat: tiempo de Open-Meteo y preferencias del panel de cocina (Refs #2)"
```

---

### Task 3: Pantalla del panel, `PanelViewModel` y activación en Ajustes

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/PanelViewModel.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PanelScreen.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/WeatherPlaceDialog.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/ManageZonesScreen.kt` (sección «Panel de cocina»)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (ViewModel, ruta `panel`, destino inicial)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1 (`todayMenuSections`, `MenuSlotSection`), Task 2 (`OpenMeteoClient`, `weatherSnapshot`, `WeatherSnapshot`, `WeatherKind`, `isMorning`, `WeatherPlace`, `UserPrefs.panelMode/setPanelMode/weatherPlace/saveWeatherPlace`); `MealEntriesRepository.observeRange(from, to)`; `AppViewModel.state` (items, zones) y `AppViewModel.setDone(item, done)`; `flattenAndSort(items, zones)`.
- Produces:
  - `data class WeatherState(val place: WeatherPlace? = null, val snapshot: WeatherSnapshot? = null, val failed: Boolean = false)`
  - `class PanelViewModel(entriesRepository: MealEntriesRepository, userPrefs: UserPrefs) : ViewModel()` con `val now: StateFlow<LocalDateTime>` (tic cada minuto), `val menu: StateFlow<List<MenuSlotSection>>` (`flatMapLatest` sobre la fecha de `now`, Review Focus 1), `val weather: StateFlow<WeatherState>` (refresco al cambiar el lugar y cada 30 minutos; un fallo deja el último `snapshot` y pone `failed = true`), `fun refreshWeather()` y `suspend fun searchPlaces(query: String): List<WeatherPlace>`.
  - Ruta `panel` (y `panelRestock`, que se crea en la Task 4; aquí el botón ya navega a ella).
  - `@Composable fun PanelScreen(appViewModel: AppViewModel, panelViewModel: PanelViewModel, onOpenRestock: () -> Unit, onExitPanel: () -> Unit, onConfigureWeather: () -> Unit)`

- [ ] **Step 1: `PanelViewModel`**

Se crea en `MainActivity` como los demás (Factory anónima, clave `"panel-$code"`), con `MealEntriesRepository(firestore, code)` y `userPrefs`.

- [ ] **Step 2: `PanelScreen`** (boceto del spec)

- Cabecera: día de la semana, fecha y hora de `now`; pulsación larga → `onExitPanel`. Tiempo a la derecha: si `isMorning`, tarjeta grande con la fila de horas 8–22 (icono y temperatura); si no, una línea (icono, actual, ↑máx ↓mín, lluvia %). Sin lugar → «Elige tu pueblo para ver el tiempo» (toca → `onConfigureWeather`). Sin datos → «Sin datos del tiempo»; con `failed` y un `snapshot`, se ve el último con «Actualizado a las HH:mm». Iconos de Material según `WeatherKind`.
- Columna izquierda «Menú de hoy»: secciones con el nombre de la franja (`MealSlot.label`) y las líneas; las de persona con «Nombre: plato». Vacío → «Sin menú para hoy».
- Columna derecha «Lista de la compra (N)»: `flattenAndSort` de los `done = false`, letra grande. Tocar → tachado; un mapa `id → Job` en `remember` lanza a los 3 s `appViewModel.setDone(item, true)`; tocar otra vez cancela el `Job` (Review Focus 4). Botón «+ Se ha acabado…» → `onOpenRestock`.
- Horizontal: dos columnas; vertical (`maxWidth < maxHeight` con `BoxWithConstraints`): apiladas.

- [ ] **Step 3: Ajustes y diálogo del lugar**

Sección «Panel de cocina» en `ManageZonesScreen`: interruptor «Modo panel de cocina» (`setPanelMode`) con la línea «La app arranca en el panel. Para salir, mantén pulsada la fecha.», y «Ubicación del tiempo: <pueblo>» que abre `WeatherPlaceDialog` (campo de texto, buscar, lista de resultados «name, admin1»; elegir → `saveWeatherPlace`). La búsqueda la hace quien abre el diálogo con `PanelViewModel.searchPlaces` (pasada como lambda), con error visible si falla.

- [ ] **Step 4: Destino inicial y ruta**

`panelMode` con `collectAsState(initial = null)`: hasta que no se lee no se monta el `NavHost`; `startDestination = if (panelMode == true) "panel" else "home"`. `onExitPanel` → `navigate("home")`. `onConfigureWeather` → `navigate("manageZones")`. El panel va sin barra inferior.

- [ ] **Step 5: Compilar y tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main
git commit -m "feat: panel de cocina con tiempo, menú de hoy y lista de la compra (Refs #2)"
```

---

### Task 4: «Se ha acabado…», pantalla siempre encendida y vuelta al panel

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/PanelIdle.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PanelRestockScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (ruta `panelRestock`, inactividad)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/homepantry/app/data/PanelIdleTest.kt`

**Interfaces:**
- Consumes: Task 1 (`restockSections`, `RestockSection`), Task 2 (`UserPrefs.panelMode`), Task 3 (ruta `panel`); `AddItemSheet(viewModel, initialZoneId, onDismiss = …)`; `AppViewModel.setDone`.
- Produces:
  - `enum class IdleAction { NONE, DIM, RETURN_TO_PANEL }`
  - `const val PANEL_IDLE_MILLIS = 120_000L`
  - `val PANEL_ROUTES = setOf("panel", "panelRestock")`
  - `fun idleAction(panelMode: Boolean, route: String?, idleMillis: Long): IdleAction`

- [ ] **Step 1: Tests en rojo** (`PanelIdleTest`, Review Focus 5)

- `modo apagado nunca hace nada`: `idleAction(false, "panel", 10 * 60_000)` y `(false, "manageZones", 10 * 60_000)` → NONE.
- `antes de 2 minutos nada`: `(true, "panel", 119_999)` → NONE; `(true, "manageZones", 119_999)` → NONE.
- `en el panel se atenua`: `(true, "panel", 120_000)` y `(true, "panelRestock", 120_000)` → DIM.
- `fuera del panel se vuelve`: `(true, "manageZones", 120_000)` y `(true, null, 120_000)` → RETURN_TO_PANEL.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PanelIdleTest"`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar `PanelIdle.kt`**

- [ ] **Step 4: Comprobar que pasan**

Run: el mismo comando. Expected: PASS.

- [ ] **Step 5: `PanelRestockScreen` y ruta `panelRestock`**

`@Composable fun PanelRestockScreen(appViewModel: AppViewModel, onBack: () -> Unit)`: barra con flecha atrás, título «Se ha acabado…», buscador y botón «Nuevo» (abre `AddItemSheet(appViewModel, initialZoneId = null, onDismiss = …)`). Debajo, `restockSections(items, zones, query, "Sin zona")` como cabeceras de zona y botones grandes en `FlowRow`. Tocar → `appViewModel.setDone(item, false)`; el botón se queda marcado «en la lista» (un `Set` de ids en `remember`, porque el producto deja de estar en casa y desaparecería de la cuadrícula) hasta salir de la pantalla.

- [ ] **Step 6: Pantalla encendida, atenuar y volver**

En `MainActivity`, alrededor del `NavHost`, un `Box` con `pointerInput` en `PointerEventPass.Initial` que apunta la hora del último toque sin consumirlo. Un bucle (`LaunchedEffect`, cada 5 s) calcula `idleAction(panelMode, rutaActual, ahora − últimoToque)`:
- DIM → `window.attributes.screenBrightness = 0.05f`; el primer toque con la pantalla atenuada restaura el brillo (`BRIGHTNESS_OVERRIDE_NONE`) y se consume, para no actuar sobre lo de debajo.
- RETURN_TO_PANEL → `navigate("panel") { popUpTo("panel") { inclusive = false }; launchSingleTop = true }`, o con `popUpTo(0)` si `panel` no está en la pila.
- En las rutas de `PANEL_ROUTES` con el modo activado, `FLAG_KEEP_SCREEN_ON` (`DisposableEffect`, se quita al salir).

- [ ] **Step 7: Verificación completa**

Run: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`
Expected: 0 fallos, `0 errors` en `app/build/reports/lint-results-debug.txt`, `app-release.apk` de unos 5,5 MB.

- [ ] **Step 8: Commit**

```bash
git add app/src
git commit -m "feat: «Se ha acabado» y pantalla siempre encendida en el panel de cocina (Closes #2)"
```

- [ ] **Step 9: Prueba en la tablet (con el usuario)**

APK release en el apk-site. Antes, confirmar con el usuario el modelo de la tablet: si no es arm64, parar y preguntar antes de tocar `abiFilters`. En la tablet: unirse a la casa con el nombre «Cocina», activar el modo panel, elegir el pueblo y probar tachar, deshacer, «Se ha acabado…», la atenuación y la vuelta al panel. Sin push ni merge hasta que el usuario lo confirme.
