# Panel de cocina con cielo animado: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fondo de cielo animado según el tiempo y la fase del día detrás del panel de cocina, con tarjetas translúcidas y pequeñas animaciones de interfaz.

**Architecture:** Lógica pura (fase del día, colores, contenido de la escena, saludo) en `data/PanelSkyLogic.kt` con tests. Un Composable `PanelSky` dibuja la escena en un único `Canvas` movido por un reloj de animación; `PanelScreen` lo pone al fondo. `PanelIdleHost` publica el estado atenuado para congelar la escena.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas, `withInfiniteAnimationFrameMillis`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-22-panel-cielo-animado-design.md`. Maqueta de referencia: https://claude.ai/artifact/GGE3n2ncjZ8GqLLTV8vSSL (estilo «Cielo completo»; colores y cantidades salen de ahí).

## Global Constraints

- Sin imágenes, Lottie ni dependencias nuevas; el APK release sigue en ~5,5 MB.
- Lógica testeable en `data/`, sin Android. Textos en `res/values/strings.xml`. Comentarios en español explicando el porqué.
- Modelos de red y de `data/` con valores por defecto en todos los campos (R8/Gson).
- `CancellationException` se relanza siempre.
- Commits en español con prefijo convencional y `Refs #2`.

## Review Focus

- Snapshot sin `sunrise`/`sunset` (respuesta antigua o campo nulo): la fase usa 7:00–19:00, sin fallo. Test en la Tarea 1.
- Sin pueblo o sin datos del tiempo: el cielo es «nublado» con la fase del día, no un fondo vacío. Lo resuelve `PanelScreen` (Tarea 3), revisión manual.
- Cielo claro (nieve, niebla de día): la cabecera blanca se lee gracias al velo. Revisión manual en el S25.
- Panel atenuado o app en segundo plano: la escena deja de pedir fotogramas. Revisión manual (Tarea 3).
- Ocaso antes de las 14:00 o «atardecer» que cruza la medianoche no existen en Navarra; no se tratan. `skyPhase` sí debe aceptar `sunset` < 90 min después de `sunrise` sin lanzar (Tarea 1).

---

### Task 1: Lógica pura del cielo y amanecer/ocaso

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/PanelSkyLogic.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/Weather.kt` (pedir `sunrise,sunset`, guardarlos en el snapshot)
- Test: `app/src/test/kotlin/com/homepantry/app/data/PanelSkyLogicTest.kt`, `WeatherTest.kt`

**Interfaces:**
- Produces:
  - `enum class SkyPhase { DAY, DUSK, NIGHT }`
  - `fun skyPhase(now: LocalTime, sunriseMinute: Int?, sunsetMinute: Int?): SkyPhase`
  - `fun skyGradient(kind: WeatherKind, phase: SkyPhase): List<Long>` (3 colores ARGB, de arriba abajo)
  - `enum class Precipitation { NONE, DRIZZLE, RAIN, STORM, SNOW }`
  - `data class SceneSpec(val sun: Boolean = false, val moon: Boolean = false, val stars: Boolean = false, val clouds: Int = 0, val cloudColor: Long = 0, val precipitation: Precipitation = Precipitation.NONE, val particles: Int = 0, val lightning: Boolean = false, val fog: Boolean = false)`
  - `fun sceneSpec(kind: WeatherKind, phase: SkyPhase): SceneSpec`
  - `enum class Greeting { MORNING, AFTERNOON, NIGHT }` y `fun greeting(time: LocalTime): Greeting`
  - `OpenMeteoDaily` gana `sunrise: List<String?>? = null`, `sunset: List<String?>? = null`; `WeatherSnapshot` gana `sunriseMinute: Int? = null`, `sunsetMinute: Int? = null` (minutos desde medianoche, hora local).

- [ ] **Step 1: Tests que fallan.** En `PanelSkyLogicTest`:
  - `fase con sol`: sunrise 8:10 (490), sunset 17:50 (1070): 7:00 → NIGHT, 8:10 → DAY, 16:19 → DAY, 16:20 → DUSK, 17:49 → DUSK, 17:50 → NIGHT, 23:00 → NIGHT.
  - `fase sin datos usa 7 a 19`: nulls: 6:59 → NIGHT, 7:00 → DAY, 17:30 → DUSK, 19:00 → NIGHT.
  - `fase con dia muy corto no lanza`: sunrise 600, sunset 650: 10:20 → DUSK.
  - `escena`: CLEAR+DAY → sun, sin luna, 0 nubes; CLEAR+NIGHT → luna y estrellas, sin sol; PARTLY_CLOUDY+DUSK → sol y 3 nubes; CLOUDY → 7 nubes, sin sol; RAIN → RAIN, 90 partículas, 6 nubes; DRIZZLE → DRIZZLE, 45; STORM → STORM, 130, lightning, 7 nubes; SNOW+NIGHT → SNOW, 70, estrellas; FOG → fog, 2 nubes.
  - `degradados`: para cada `WeatherKind` × `SkyPhase`, 3 colores con alfa 0xFF; CLEAR+DAY = `[0xFF2F7FD9, 0xFF6FB4F0, 0xFFB9E0FF]`.
  - `saludo`: 13:59 → MORNING, 14:00 → AFTERNOON, 20:59 → AFTERNOON, 21:00 → NIGHT, 5:00 → NIGHT, 6:00 → MORNING.
  En `WeatherTest`: `amanecer y ocaso`: daily con `sunrise = ["2026-11-22T08:10"]`, `sunset = ["2026-11-22T17:50"]` → 490 y 1070; sin esos campos → null y null.

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.PanelSkyLogicTest" --tests "com.homepantry.app.data.WeatherTest"` → no compila / FAIL.

- [ ] **Step 3: Implementar.** Valores de la maqueta:
  - Fase: DUSK = `[sunset-90, sunset)`, acotado a no empezar antes de sunrise. NIGHT antes de las 6:00 para el saludo (6:00–13:59 MORNING).
  - Degradados (DAY / DUSK / NIGHT), por `CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM`:
    - DAY: `2f7fd9,6fb4f0,b9e0ff` · `3b82c9,7fb3e0,c3dcf0` · `5d6f84,8797a8,b3bfcb` · `8e979f,b4bbc1,d5d9dc` · `56677a,7b8b9c,a4b0bc` · `3d4a5c,5d6b7d,808d9c` · `7f93a8,b3c2d0,e4ebf1` · `1f2433,343b4f,4b5367`
    - DUSK: `3c3f8f,c0629a,ffad6b` · `45488f,b56c95,f3a877` · `5a5872,8d7f8f,c09a8f` · `8a8590,b3a9a8,d8c8bd` · `4f5068,776f80,a48e8e` · `383c52,585a6e,7a7482` · `7c7f9e,b3b0c4,e6d9d8` · `1d1f2e,33334a,4d4658`
    - NIGHT: `070a1f,121a3d,24305e` · `0a0d24,18204a,2a3460` · `14161f,232633,33374a` · `1d2028,2e323c,454a55` · `12151f,1f2431,2f3545` · `0e1119,1a1f2b,283040` · `1c2233,2e3750,4a5570` · `08090f,141622,202435`
  - Nubes por tipo: CLEAR 0, PARTLY 3, CLOUDY 7, FOG 2, DRIZZLE 5, RAIN 6, SNOW 4, STORM 7. Color de día: `f4f7fb, f4f7fb, d5dde6, e2e6ea, b9c3ce, 9aa6b4, e9eef3, 5c6476`; DUSK con CLEAR/PARTLY `f6d6cf`; NIGHT `454b63` (STORM `2a2e3d`).
  - Sol: CLEAR/PARTLY y fase ≠ NIGHT. Luna: CLEAR/PARTLY y NIGHT. Estrellas: NIGHT y (CLEAR, PARTLY o SNOW).
  - Weather.kt: añadir `sunrise,sunset` al `daily` por defecto de `forecast`; en `weatherSnapshot`, `hh*60+mm` de `sunrise/sunset.firstOrNull()` (la parte tras `T`), null si falta o no se parsea.

- [ ] **Step 4:** mismo comando → PASS.
- [ ] **Step 5: Commit** `feat: lógica del cielo del panel y amanecer/ocaso de Open-Meteo (Refs #2)`.

### Task 2: Composable `PanelSky` y atenuado compartido

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/PanelSky.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/PanelIdleHost.kt`

**Interfaces:**
- Consumes: Tarea 1 (`SceneSpec`, `skyGradient`, `sceneSpec`, `SkyPhase`).
- Produces:
  - `val LocalPanelDimmed = staticCompositionLocalOf { false }` en `PanelIdleHost.kt`, provisto alrededor de `content()` con el estado `dimmed`.
  - `@Composable fun PanelSky(kind: WeatherKind, phase: SkyPhase, animate: Boolean, modifier: Modifier = Modifier)`

- [ ] **Step 1: `LocalPanelDimmed`.** `CompositionLocalProvider(LocalPanelDimmed provides dimmed) { content() }` dentro del `Box` actual (usar `compositionLocalOf`, no `static`, porque cambia).
- [ ] **Step 2: `PanelSky`.**
  - Reloj: `var t by remember { mutableFloatStateOf(0f) }` avanzado con `withInfiniteAnimationFrameMillis` en un `LaunchedEffect(animate)` solo cuando `animate` es true; al pararse conserva el último valor (escena congelada). Respetar animaciones desactivadas del sistema: leer `Settings.Global.ANIMATOR_DURATION_SCALE` == 0f y tratarlo como `animate = false`.
  - Partículas: `remember` con `kotlin.random.Random(seed fijo)`: x (0–1), longitud/tamaño, periodo y desfase, como en la maqueta (lluvia 0,55–0,9 s y 14–32 dp; llovizna 1,1–1,7 s y 8–16 dp; nieve 6–14 s, 3–9 dp, vaivén ±28 dp; estrellas 2–6 s, en el 60 % superior; nubes 70–140 s, escala 0,6–1,4, altura 2–36 %). La posición se calcula como `((t / periodo + desfase) % 1)`.
  - Dibujo en un solo `Canvas(Modifier.fillMaxSize())`, en este orden: degradado vertical; estrellas; sol (halo radial que late ±5 % cada 5 s, rayos girando 1 vuelta/80 s; al DUSK más bajo y naranja `ffe9c2→ffab5c→f2713a`) o luna creciente (`f4f1de`, círculo menos otro desplazado con `Path.op`); niebla (4 óvalos con degradado radial blanco 60 %, vaivén ±12 % en 16–31 s); nubes (3 círculos + cápsula, color de `SceneSpec`); lluvia inclinada 9°; nieve; relámpago (velo `e8ecff` con alfa según la curva de la maqueta: picos 0,8 / 0,6 en el 87–92 % de un ciclo de 7 s); velo superior `0A0C18` del 50 % a 0 en los primeros 340 dp.
- [ ] **Step 3:** `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `feat: cielo animado del panel de cocina (Refs #2)`.

### Task 3: Panel sobre el cielo

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PanelScreen.kt`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PanelSky`, `LocalPanelDimmed`, `skyPhase`, `greeting`, `WeatherSnapshot.sunriseMinute/sunsetMinute`.

- [ ] **Step 1: Fondo.** `Scaffold(containerColor = Color.Transparent)` dentro de un `Box`; detrás, `PanelSky(kind, phase, animate)` con `kind = snapshot?.kind ?: CLOUDY`, `phase = skyPhase(now.toLocalTime(), snapshot?.sunriseMinute, snapshot?.sunsetMinute)` y `animate = !LocalPanelDimmed.current && lifecycle al menos RESUMED` (`LocalLifecycleOwner.current.lifecycle.currentStateAsState()`).
- [ ] **Step 2: Cabecera.** Textos en blanco con `Shadow(Color(0x59000000), blurRadius = 12f)`. Columna izquierda: saludo (`panel_greeting_morning` «Buenos días», `panel_greeting_afternoon` «Buenas tardes», `panel_greeting_night` «Buenas noches», 18 sp), fecha (30 sp, negrita; sigue siendo la zona de pulsación larga para salir), reloj 64 sp `Light` con los dos puntos parpadeando (alfa 1 ↔ 0,25 cada segundo, `rememberInfiniteTransition`, quieto si atenuado). Derecha: nombre del estado (`panel_weather_kind_*`, 8 textos: Despejado, Poco nuboso, Nublado, Niebla, Llovizna, Lluvia, Nieve, Tormenta) + temperatura 76 sp `Light`; debajo el rango y el pueblo. Se quita el icono grande y el `HorizontalDivider`. Los estados sin pueblo/cargando/error siguen como están, en blanco.
- [ ] **Step 3: Previsión por horas** en píldoras: fondo blanco al 14 %, radio 16 dp, hora · icono (tintes: sol `FFD66B`, lluvia `9CCBFF`, resto blanco) · temperatura.
- [ ] **Step 4: Tarjetas.** Menú y lista en `Surface` con `NocturneSurface` al 55 % de alfa, borde blanco al 12 %, radio 24 dp, relleno 24×28 dp; entrada con fundido y desplazamiento de 16 dp (0,7 s; la lista con 120 ms de retraso). Títulos «Comida»/«Cena» en `B3A9F0`. Casilla de la lista: `animateColorAsState` y escala 1 → 1,08 al tachar. Botón «Se ha acabado…» con `NocturnePrimary`, alto 60 dp, radio 18 dp.
- [ ] **Step 5: Verificación completa.** `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease` → 0 fallos, 0 errores de lint, `app-release.apk` ~5,5 MB.
- [ ] **Step 6: Commit** `feat: panel de cocina sobre el cielo animado (Refs #2)`.
- [ ] **Step 7:** revisión con Sonnet de la rama; APK release al apk-site para probar en la tablet/S25. No fusionar hasta que el usuario confirme.
