# Avisos de ofertas de Lidl y Aldi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada lunes un script de GitHub Actions cruza las ofertas de Lidl y Aldi con los productos habituales de la casa y escribe un aviso en Firestore. La app lo muestra con una campana en Home, una pantalla de avisos, un detalle que permite añadir ofertas a la lista y una notificación local programada con WorkManager.

**Architecture:** Hay dos partes independientes que se comunican solo por el documento `households/{code}/notifications/{id}`. El script Node (`tools/offers/`) tiene funciones puras probadas con `node:test`, y un `index.mjs` que hace la E/S (tiendas, Gemini y Firestore con el Admin SDK). La app sigue el patrón del proyecto: modelos de Firestore en `data/`, lógica pura en `data/OffersLogic.kt` probada con JUnit, un repositorio con `callbackFlow`, un `NotificationsViewModel` con un único `StateFlow`, dos pantallas Compose y un `CoroutineWorker`.

**Tech Stack:** Node 22 (ES modules, `fetch` nativo y `node:test`), `firebase-admin`, Gemini REST, GitHub Actions, Kotlin, Jetpack Compose (Material 3), Firestore, WorkManager (`androidx.work:work-runtime-ktx`, la única dependencia nueva de la app), JUnit 4 y `java.time`.

**Spec:** `docs/superpowers/specs/2026-10-26-avisos-ofertas-folletos-design.md`

## Global Constraints

- El repo `SergioDC/HomePantry` es **público**. En Actions, los logs del script solo pueden mostrar recuentos y nombres de tienda. Nunca el código de casa, nombres de productos, ofertas, respuestas de Gemini ni URLs con query.
- La API key de Gemini va en la cabecera `x-goog-api-key`, nunca en la URL.
- Un producto es habitual si aparece en **≥ 2 líneas de `purchases` en los últimos 90 días**, agrupando por `normalizedName` y descartando las líneas con `normalizedName` vacío.
- Todas las fechas ("hoy", `weekStart` y la ventana del worker) se calculan en **Europe/Madrid**.
- Si no hay coincidencias, no se escribe ningún aviso. Los avisos de más de 28 días se borran.
- App: JDK 17, `minSdk 26`, `compileSdk`/`targetSdk 36`, AGP/Gradle 8.x, R8 en release. El APK sigue rondando 4,5 MB.
- Los modelos de Firestore van en `data/`: `data class` con valores por defecto, `@DocumentId` y `@ServerTimestamp`.
- Los cambios de un solo campo se hacen con `update(...)` y `FieldValue.arrayUnion`, nunca con `set` del objeto entero.
- `CancellationException` se relanza siempre.
- Los textos van en `res/values/strings.xml` (con `plurals`), y los comentarios y la KDoc en español. Se usan los colores de `ui/theme`. Los no leídos se marcan con el color primario, **no con coral**.
- Navegación: `notifications` y `notification/{id}` van sin barra inferior y con flecha atrás. No se añaden a `BOTTOM_NAV_ROUTES`.
- `firestore.rules` no se toca, porque ya cubre `households/{code}/{document=**}`.
- La rama `sncount-gasto` (sin fusionar) también toca `MainActivity.kt` con App Links. Los cambios en `MainActivity` deben ser mínimos y no tocar el manejo de enlaces de SNcount.
- Los commits van en español y con prefijo (`feat:`, `build:`, `docs:`, `test:`).

## Review Focus

- Si se relanza el workflow la misma semana, se reutiliza el aviso de esa semana y se conservan `readBy` y `addedKeys` (Task 2: `mergeWeekNotification`).
- Gemini devuelve un habitual que no existe, un índice fuera de rango, una página inexistente o duplicados: se descartan sin romper nada (Task 2).
- Ejecución en el cambio de día: el domingo a las 23:30 UTC ya es lunes a las 00:30 en Madrid, así que `weekStart` es ese lunes (Task 2). Para el worker, el martes a las 22:00 más 3 h se sale de la ventana y pasa al lunes siguiente (Task 4).
- Una oferta cuyo habitual ya está en la lista **sin comprar** sale como "ya en la lista". Si en la lista está marcado como comprado (`done = true`), la oferta sale como nueva (Task 4).
- Aldi: un producto sin precio tachado ni promo no es oferta, y si un producto aparece repetido en `__NEXT_DATA__` se cuenta una sola vez (Task 1).

---

## Parte 1: script (`tools/offers/`)

### Task 1: Fuentes de Lidl y Aldi

**Files:**
- Create: `tools/offers/package.json` (`"type": "module"`, `"engines": {"node": ">=22"}`, `"scripts": {"test": "node --test test/"}` y dependencia `firebase-admin`, que se usa en la Task 3)
- Create: `tools/offers/src/dates.mjs`
- Create: `tools/offers/src/lidl.mjs`
- Create: `tools/offers/src/aldi.mjs`
- Create: `tools/offers/test/fixtures/` (fixtures **pequeños hechos a mano** con la estructura descrita en la spec, no las páginas reales de 600 KB)
- Test: `tools/offers/test/sources.test.mjs`
- Modify: `.gitignore` (añadir `tools/offers/node_modules/`)

La referencia es el spike validado el 2026-10-26: `C:\Users\Sergio\AppData\Local\Temp\claude\C--Programacion-Proyectos-HomePantry\9b42f461-f1ac-49bf-884f-545ebf04d9fa\scratchpad\spike\folletos.mjs`. Las URLs, las cabeceras y la extracción de `__NEXT_DATA__` se copian de ahí.

**Interfaces:**
- Produces:
  - `dates.mjs`: `todayMadrid(now: Date): string` ("YYYY-MM-DD"), `weekStartMadrid(now: Date): string` (el lunes de esa semana en Madrid, "YYYY-MM-DD"), `unixToMadridDay(seconds: number): string`.
  - `lidl.mjs`: `lidlFlyerSlugs(html: string): string[]` (slugs únicos que empiezan por `folleto-alimentacion`), `pickCurrentFlyer(flyers: object[], today: string): object` (el vigente; si no hay, el de `offerStartDate` más antiguo), `lidlPages(flyer): {page: number, text: string, description: string}[]`, `fetchLidl(now: Date): Promise<LidlSource>`, con `LidlSource = {store: "Lidl", name, url, validFrom, validUntil, pages}`.
  - `aldi.mjs`: `parseAldiOffers(html: string): AldiOffer[]`, con `AldiOffer = {id: string /*objectID*/, name, brand: string|null, unit: string|null, price: number, oldPrice: number|null, promo: string|null, validFrom: string, validUntil: string}`. Solo devuelve productos con `strikePrice` o `promoText1`, sin repetir `objectID`, y quita el `®` de la marca. `fetchAldi(): Promise<AldiSource>`, con `AldiSource = {store: "Aldi", name: "Ofertas ALDI", url: "https://www.aldi.es/ofertas.html", validFrom, validUntil /* mín./máx. de las ofertas */, offers}`.
  - Las funciones `fetch*` lanzan un `Error` con estado y host (sin query) si la respuesta HTTP no es ok, o si no encuentran slugs o `__NEXT_DATA__`.

- [ ] **Step 1: Escribir los tests que fallan** en `test/sources.test.mjs`:
  - `weekStartMadrid(new Date("2026-11-01T23:30:00Z"))` → `"2026-11-02"` (en Madrid ya es lunes); `weekStartMadrid(new Date("2026-10-28T10:00:00Z"))` → `"2026-10-26"`; `todayMadrid(new Date("2026-10-25T22:30:00Z"))` → `"2026-10-25"` (último domingo de octubre, cambio de hora: 23:30 en Madrid).
  - `lidlFlyerSlugs` sobre un HTML con enlaces `/l/folletos/folleto-alimentacion-a/ar/0` (dos veces), `/l/folletos/folleto-bazar-b/ar/0` y `/l/folletos/folleto-alimentacion-c/ar/0` → `["folleto-alimentacion-a", "folleto-alimentacion-c"]`.
  - `pickCurrentFlyer` con los folletos A (26/10 → 1/11) y B (2/11 → 8/11): con `today="2026-10-28"` → A; con `today="2026-10-25"` → A (no hay vigente y A empieza antes).
  - `lidlPages` convierte `keyWords: null` en `""` y recorta espacios.
  - `parseAldiOffers` sobre un fixture con `__NEXT_DATA__` cuyo `apiData` contiene un string JSON con: P1 (con `strikePrice`), P2 (solo `promoText1`), P3 (sin ninguno de los dos) y P1 repetido → devuelve P1 y P2, con `brand` sin `®` y `validFrom` `"2026-10-25"` a partir de segundos Unix.
  - `parseAldiOffers("<html></html>")` lanza un error.

- [ ] **Step 2: Ejecutar los tests y ver que fallan**
  Run: `cd tools/offers && npm install && npm test`
  Expected: FAIL (no existen los módulos)

- [ ] **Step 3: Implementar `dates.mjs`, `lidl.mjs` y `aldi.mjs`** con las firmas de arriba. `dates.mjs` usa `Intl.DateTimeFormat("en-CA", {timeZone: "Europe/Madrid"})`, sin dependencias.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**
  Run: `cd tools/offers && npm test`
  Expected: PASS

- [ ] **Step 5: Commit**
  ```bash
  git add tools/offers .gitignore
  git commit -m "feat: fuentes de ofertas de Lidl y Aldi para el script de avisos"
  ```

### Task 2: Habituales, cruce con Gemini y composición del aviso

**Files:**
- Create: `tools/offers/src/habitual.mjs`
- Create: `tools/offers/src/match.mjs`
- Create: `tools/offers/src/notification.mjs`
- Test: `tools/offers/test/logic.test.mjs`

**Interfaces:**
- Consumes: `AldiOffer`, `LidlSource`, `AldiSource` y `weekStartMadrid` (Task 1).
- Produces:
  - `habitualProducts(purchases: {normalizedName: string, rawName: string, date: Date}[], now: Date): {normalizedName: string, example: string}[]`. El umbral es ≥ 2 líneas con `date` dentro de los 90 días anteriores a `now`. `example` es el `rawName` de la línea más reciente. Ordenado por `normalizedName`.
  - `match.mjs`:
    - `aldiPrompt(habituals, offers): string` y `lidlPrompt(habituals, pages): string`. El criterio es el de la spec: **mismo tipo de producto aunque cambien la marca o la variante, y ante la duda no hay coincidencia**. Se pide además `habitualLabel`: un nombre genérico corto en español con tildes y mayúscula inicial (por ejemplo "Plátanos"), porque `normalizedName` va sin tildes y no sirve para mostrarlo.
    - `ALDI_SCHEMA` / `LIDL_SCHEMA` (`responseSchema` de Gemini). Para Aldi, un array de `{index: integer, habitual: string, habitualLabel: string}`. Para Lidl, un array de `{page: integer, habitual: string, habitualLabel: string, product: string, price: number|null, oldPrice: number|null}`.
    - `aldiMatches(raw: object[], offers: AldiOffer[], habituals): OfferMatch[]`. Descarta los `index` fuera de rango y los `habitual` que no están en la lista. Copia `product`, `price`, `oldPrice`, `promo`, `validFrom` y `validUntil` **de `offers[index]`** y pone `key = "aldi-" + id`.
    - `lidlMatches(raw: object[], pages, habituals): OfferMatch[]`. Descarta las páginas que no existen y los `habitual` desconocidos. Pone `key = "lidl-p{page}-{habitual}"`. Los precios no numéricos pasan a `null`.
    - Las dos funciones quitan los duplicados por `key` y se quedan con el primero.
    - `callGemini({apiKey, model, prompt, schema}): Promise<object[]>`. Hace un POST a `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` con la cabecera `x-goog-api-key`, `generationConfig: {responseMimeType: "application/json", responseSchema}` y hasta 3 intentos ante 429/5xx (espera inicial de 2 s, que se duplica en cada intento). El modelo es `process.env.GEMINI_MODEL || "gemini-3.8-flash"`.
    - `OfferMatch = {key, habitual, habitualLabel, product, price: number|null, oldPrice: number|null, promo: string|null, page: number|null, validFrom: string|null, validUntil: string|null}`.
  - `notification.mjs`:
    - `offersTitle(n: number): string` → `"1 oferta en cosas que compráis"` / `"3 ofertas en cosas que compráis"`.
    - `buildNotification({stores: {source: LidlSource|AldiSource, offers: OfferMatch[]}[], now: Date}): object|null`. Devuelve `null` si no hay ninguna oferta. Si las hay, devuelve `{type: "offers", title, weekStart, stores: [{store, name, url, validFrom, validUntil, offers}]}` e **incluye solo las tiendas que tienen ofertas**. `readBy`, `addedKeys` y `createdAt` no se incluyen aquí.
    - `mergeWeekNotification(existing: object|null, fresh: object): object`. Devuelve `fresh` más `readBy` y `addedKeys` de `existing` (o `[]` si no existe).

- [ ] **Step 1: Escribir los tests que fallan** en `test/logic.test.mjs`:
  - `habitualProducts`: "leche" 2 veces en 90 días → sale; "pan" 1 vez → no sale; "huevo" 2 veces pero una hace 91 días → no sale; `normalizedName: ""` 3 veces → no sale; `example` = el `rawName` de la línea más reciente.
  - `aldiMatches` con 2 ofertas y `raw = [{index:0,...habitual:"queso"}, {index:5,...}, {index:1, habitual:"inventado"}, {index:0,...habitual:"queso"}]` → 1 coincidencia con `key "aldi-<id0>"`, el precio de `offers[0]` y no el de Gemini.
  - `lidlMatches` con las páginas 1–3 y `raw` con `page: 9`, un habitual desconocido y `price: "1,59"` → se descartan los dos primeros y `price` pasa a `null`. Un raw válido de la página 2 con `habitual "tomate"` → `key "lidl-p2-tomate"`.
  - `offersTitle(1)` y `offersTitle(3)` con los textos exactos de arriba.
  - `buildNotification` sin ofertas → `null`. Con ofertas solo en Aldi → `stores` tiene longitud 1, `title "1 oferta…"` y `weekStart` calculado en Madrid.
  - `mergeWeekNotification({readBy:["u1"], addedKeys:["aldi-1"], title:"viejo"}, {title:"nuevo", stores:[]})` → `{title:"nuevo", stores:[], readBy:["u1"], addedKeys:["aldi-1"]}`. Con `existing = null` → `readBy: []` y `addedKeys: []`.

- [ ] **Step 2: Ejecutar y ver que fallan**
  Run: `cd tools/offers && npm test`
  Expected: FAIL

- [ ] **Step 3: Implementar `habitual.mjs`, `match.mjs` y `notification.mjs`** con las firmas de arriba. Los prompts se escriben en español. `callGemini` no lleva test, porque solo hace E/S.

- [ ] **Step 4: Ejecutar y ver que pasan**
  Run: `cd tools/offers && npm test`
  Expected: PASS

- [ ] **Step 5: Commit**
  ```bash
  git add tools/offers
  git commit -m "feat: productos habituales, cruce con Gemini y composición del aviso de ofertas"
  ```

### Task 3: Firestore, orquestación y workflow

**Files:**
- Create: `tools/offers/src/firestore.mjs`
- Create: `tools/offers/src/index.mjs`
- Create: `.github/workflows/offers.yml`
- Create: `tools/offers/README.md` (cómo se lanza en local y qué secrets usa)

**Interfaces:**
- Consumes: todo lo de las Tasks 1–2.
- Produces (`firestore.mjs`, recibe una instancia de `Firestore` de firebase-admin):
  - `readPurchases(db, code): Promise<{normalizedName, rawName, date: Date}[]>`
  - `upsertWeekNotification(db, code, fresh): Promise<"created"|"updated">`: busca `type=="offers"` con el mismo `weekStart`. Si existe, hace `set(mergeWeekNotification(existing, fresh) + createdAt)` sobre ese documento. Si no, crea uno con `readBy: []`, `addedKeys: []` y `createdAt: FieldValue.serverTimestamp()`.
  - `deleteOldNotifications(db, code, now): Promise<number>` (borra los que tienen `createdAt` de más de 28 días).

**`index.mjs`.** Los flags son `--dry-run` (no escribe) y `--print` (imprime el aviso completo; **se ignora si `process.env.CI` existe**). Las credenciales se leen de `FIREBASE_SERVICE_ACCOUNT` (el JSON) o, en local, de `FIREBASE_SERVICE_ACCOUNT_FILE` (una ruta), además de `HOUSEHOLD_CODE` y `GEMINI_API_KEY`. Si falta alguna, el script termina con exit 1 y un mensaje que solo nombra la variable que falta. El flujo es:
1. Lee los habituales. Si no hay ninguno: log `habituales: 0` y exit 0.
2. Ejecuta `fetchLidl` y `fetchAldi` por separado: si una tienda falla, se anota el error y se sigue con la otra.
3. Llama a Gemini para cada tienda que haya respondido.
4. Construye el aviso con `buildNotification`. Si es `null`, no escribe nada. Si no, llama a `upsertWeekNotification` (salvo con `--dry-run`).
5. Llama a `deleteOldNotifications`.
6. Termina con exit 1 si falló alguna tienda o alguna llamada a Gemini, y con exit 0 en caso contrario.

Logs, exactamente una línea por fase y solo con recuentos: `habituales: N`, `Lidl: N páginas` / `Lidl: ERROR <estado> <host>`, `Aldi: N ofertas`, `coincidencias: Lidl N, Aldi N`, `aviso: creado|actualizado|sin coincidencias|dry-run`, `borrados: N`.

**Workflow** `offers.yml`: `name: Avisos de ofertas`. Se dispara con `schedule: - cron: '0 7 * * 1'` y con `workflow_dispatch`, que tiene la entrada `dry_run` (boolean, por defecto `false`). Un job en `ubuntu-latest` con `actions/checkout@v4`, `actions/setup-node@v4` (`node-version: 22`, caché npm con `cache-dependency-path: tools/offers/package-lock.json`), `npm ci` y `node src/index.mjs` (más `--dry-run` si la entrada lo pide), con `working-directory: tools/offers` y las variables `FIREBASE_SERVICE_ACCOUNT`, `HOUSEHOLD_CODE` y `GEMINI_API_KEY` tomadas de `secrets.*`.

- [ ] **Step 1: Implementar `firestore.mjs`, `index.mjs`, el workflow y el README.** No hay tests nuevos: la lógica ya está probada en las Tasks 1–2.
- [ ] **Step 2: Revisar que ningún `console.log` ni `console.error` imprime productos, ofertas, el código de casa o URLs con query**
  Run: `grep -n "console\." tools/offers/src/*.mjs`
  Expected: solo las líneas de recuento de arriba y la salida de `--print` (protegida con `!process.env.CI`).
- [ ] **Step 3: Tests y sintaxis del workflow**
  Run: `cd tools/offers && npm test && node --check src/index.mjs`
  Expected: PASS y ningún error de sintaxis.
- [ ] **Step 4: Commit**
  ```bash
  git add tools/offers .github/workflows/offers.yml
  git commit -m "feat: script semanal de avisos de ofertas con GitHub Actions"
  ```

---

## Parte 2: app

### Task 4: Modelos y lógica pura de avisos

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/AppNotification.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/OffersLogic.kt`
- Test: `app/src/test/kotlin/com/homepantry/app/data/OffersLogicTest.kt`

**Interfaces:**
- Produces:
  - `data class AppNotification(@DocumentId val id: String = "", val type: String = "", val title: String = "", val weekStart: String = "", @ServerTimestamp val createdAt: Date? = null, val readBy: List<String> = emptyList(), val stores: List<OfferStore> = emptyList(), val addedKeys: List<String> = emptyList())`
  - `data class OfferStore(val store: String = "", val name: String = "", val url: String = "", val validFrom: String = "", val validUntil: String = "", val offers: List<OfferMatch> = emptyList())`
  - `data class OfferMatch(val key: String = "", val habitual: String = "", val habitualLabel: String = "", val product: String = "", val price: Double? = null, val oldPrice: Double? = null, val promo: String? = null, val page: Int? = null, val validFrom: String? = null, val validUntil: String? = null)`
  - `const val NOTIFICATION_TYPE_OFFERS = "offers"`
  - `fun isUnread(n: AppNotification, uid: String): Boolean` / `fun unreadCount(list: List<AppNotification>, uid: String): Int`
  - `fun nextMondayCheck(now: ZonedDateTime): ZonedDateTime`: el próximo lunes a las 11:00 en la zona de `now`, **estrictamente posterior** a `now`.
  - `fun inOffersRetryWindow(now: ZonedDateTime): Boolean`: desde el lunes a las 11:00 (incluido) hasta el martes a las 23:59:59.
  - `fun initialOffersCheck(now: ZonedDateTime): ZonedDateTime`: `now` si está en la ventana; si no, `nextMondayCheck(now)`.
  - `fun retryOrReschedule(now: ZonedDateTime): ZonedDateTime`: `now + 3 h` si ese momento sigue dentro de la ventana; si no, `nextMondayCheck(now)`.
  - `fun pendingOfferNotification(list: List<AppNotification>, uid: String, notifiedIds: Set<String>, now: Instant): AppNotification?`: de tipo offers, no leído, no notificado y con `createdAt` de los últimos 7 días. El más reciente.
  - `enum class OfferRowState { NEW, IN_LIST, ADDED }`
  - `fun offerRowState(offer: OfferMatch, addedKeys: List<String>, items: List<Item>): OfferRowState`: ADDED si su `key` está en `addedKeys`. IN_LIST si existe un item con `!done` cuyo `normalizeProductName(name)` es igual a `offer.habitual` o a `normalizeProductName(offer.habitualLabel)`. NEW en el resto.
  - `fun offerNote(offer: OfferMatch): String`
  - `fun offerItems(selected: List<Pair<String, OfferMatch>> /* tienda, oferta */, items: List<Item>, zones: List<Zone>, addedBy: String): List<Item>`

- [ ] **Step 1: Escribir los tests que fallan** en `OffersLogicTest.kt` (con `ZoneId.of("Europe/Madrid")`):
  - `unreadCount` con 3 avisos, uno con `readBy = [uid]` → 2.
  - `nextMondayCheck`: el miércoles 28/10/2026 a las 10:00 → el lunes 2/11 a las 11:00. El lunes 26/10 a las 09:00 → el 26/10 a las 11:00. El lunes 26/10 a las 11:00 → el 2/11 a las 11:00 (estrictamente posterior).
  - `retryOrReschedule`: lunes a las 11:00 → 14:00. Martes a las 22:00 → lunes siguiente a las 11:00 (la 01:00 del miércoles se sale de la ventana). Jueves → lunes siguiente.
  - `initialOffersCheck`: martes a las 15:00 → el mismo instante. Domingo → lunes a las 11:00.
  - `pendingOfferNotification`: ignora los ya leídos, los que están en `notifiedIds`, los de más de 7 días y los de `type != "offers"`. Entre dos válidos devuelve el más reciente.
  - `offerRowState`: con `addedKeys` que contiene la key → ADDED. Con un item "Tomate" `done=false` y la oferta `habitual "tomate"` → IN_LIST. Con el mismo item `done=true` → NEW.
  - `offerNote(OfferMatch(price=2.49, oldPrice=2.85, validUntil="2026-11-01"))` → `"Oferta 2,49 € (antes 2,85 €) hasta 1/11"`. Sin `oldPrice` → `"Oferta 2,49 € hasta 1/11"`. Sin precio ni fecha (Lidl con `page=7`) → `"Oferta en el folleto, pág. 7"`.
  - `offerItems`: el nombre es `habitualLabel`, `store` es la tienda, `note` es `offerNote`, `qty` 1.0, unidad `UD`, `done` false y `addedBy` el que se pasa. La zona es la del item existente con el mismo nombre normalizado. Si no hay ninguno, `zones.firstOrNull { it.parentZoneId == null }?.id ?: ""`, el mismo criterio que `AddItemSheet`.

- [ ] **Step 2: Ejecutar y ver que fallan**
  Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.OffersLogicTest"`
  Expected: FAIL (no compila)

- [ ] **Step 3: Implementar `AppNotification.kt` y `OffersLogic.kt`.** El formato de los números va en es-ES con 2 decimales (`String.format(Locale("es", "ES"), "%.2f", …)`), y la fecha con el formato `d/M`.

- [ ] **Step 4: Ejecutar y ver que pasan**
  Run: `./gradlew testDebugUnitTest --tests "com.homepantry.app.data.OffersLogicTest"`
  Expected: PASS

- [ ] **Step 5: Commit** `feat: modelos y lógica de avisos de ofertas`

### Task 5: Repositorio, ViewModel, pantallas y campana en Home

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/NotificationsRepository.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/NotificationsViewModel.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/NotificationsScreen.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/ui/screens/NotificationDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/HomeScreen.kt` (parámetros `unreadCount: Int` y `onOpenNotifications: () -> Unit`, y una campana en `actions` **antes** de Ajustes, con un `BadgedBox` que se oculta si el número es 0)
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt` (factory del ViewModel con la clave `"notifications-$code"`, rutas `notifications` y `notification/{id}`, y `updateUserName`)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: los modelos y `OffersLogic` (Task 4), `ItemsRepository.observeItems()` / `addItems(List<Item>)` y `ZonesRepository.observeZones()`.
- Produces:
  - `class NotificationsRepository(firestore: FirebaseFirestore, householdCode: String)` con `fun observeNotifications(): Flow<List<AppNotification>>` (`createdAt` desc), `suspend fun fetchNotifications(): List<AppNotification>`, `suspend fun markRead(id: String, uid: String)` y `suspend fun markAdded(id: String, keys: List<String>)`.
  - `data class NotificationsUiState(val notifications: List<AppNotification> = emptyList(), val items: List<Item> = emptyList(), val zones: List<Zone> = emptyList(), val unreadCount: Int = 0)`
  - `class NotificationsViewModel(notificationsRepository, itemsRepository, zonesRepository, uid: String, userName: String)` con `val state: StateFlow<NotificationsUiState>`, `fun markRead(id: String)`, `fun addOffers(notificationId: String, selected: List<Pair<String, OfferMatch>>, onDone: (Int) -> Unit)` (hace `addItems(offerItems(...))` y después `markAdded`, y llama a `onDone(n)` para el snackbar) y `fun updateUserName(name: String)`.
  - Rutas `"notifications"` y `"notification/{id}"`. La Task 6 navega a la segunda.

Pantallas, según la maqueta (opción B) de `.superpowers/brainstorm/629-1793021727/content/ofertas-flujo.html` y la spec, apartado "Pantallas":
- **NotificationsScreen**: tarjetas con el título y "Lunes 26 oct · Lidl y Aldi" (de `weekStart` y las tiendas), con un borde de color primario si no están leídas. Si no hay avisos, un texto vacío.
- **NotificationDetailScreen**: llama a `markRead` en un `LaunchedEffect(id)` y muestra el título "Ofertas 26 oct – 1 nov" (mínimo y máximo de las fechas de las tiendas).
  - Una sección por tienda con un enlace ("Ver folleto ↗" en Lidl, "Ver ofertas ↗" en Aldi) que abre `Intent.ACTION_VIEW`.
  - Filas con una casilla. La selección inicial son las filas `NEW`. Las `IN_LIST` salen desmarcadas con la etiqueta "ya en la lista". Las `ADDED` salen deshabilitadas con la etiqueta "añadida".
  - Botón fijo "Añadir N a la lista" (`plurals`), deshabilitado si N = 0, y un snackbar "N productos añadidos" (`plurals`).
  - Si el id no existe, por ejemplo porque se borró el aviso, se muestra un texto vacío y la flecha atrás.

- [ ] **Step 1: Implementar repositorio, ViewModel, pantallas, campana, rutas y strings.** Toda la lógica que decide algo ya está en `OffersLogic`, así que esta task no lleva tests nuevos.
- [ ] **Step 2: Compilar y pasar los tests**
  Run: `./gradlew testDebugUnitTest assembleDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 3: Commit** `feat: pantalla de avisos de ofertas y campana en Home`

### Task 6: Notificación semanal con WorkManager

**Files:**
- Modify: `app/build.gradle.kts` (`implementation("androidx.work:work-runtime-ktx:<última 2.x estable>")`)
- Modify: `app/src/main/AndroidManifest.xml` (`<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/UserPrefs.kt` (`val notifiedOfferIds: Flow<Set<String>>` y `suspend fun addNotifiedOfferId(id: String)`, que guarda como mucho los 20 últimos)
- Create: `app/src/main/kotlin/com/homepantry/app/OffersCheckWorker.kt` (`CoroutineWorker`, con los helpers `scheduleOffersCheck(context, at: ZonedDateTime, policy: ExistingWorkPolicy)` y `ensureOffersChannel(context)`)
- Modify: `MainActivity.kt`:
  - en `onCreate` y `onNewIntent`, leer el extra `EXTRA_NOTIFICATION_ID` y guardarlo en un estado que `ListaDeLaCasaApp` consume para navegar una sola vez a `notification/{id}`;
  - con la casa ya configurada, llamar a `scheduleOffersCheck(context, initialOffersCheck(now), KEEP)`.
- Modify: `NotificationsScreen.kt` (pedir `POST_NOTIFICATIONS` con accompanist `rememberPermissionState` solo en Android 13 o superior y solo la primera vez que se abre la pantalla)
- Modify: `strings.xml` (nombre del canal "Ofertas")

**Interfaces:**
- Consumes: `NotificationsRepository.fetchNotifications()` y `markRead` (Task 5); `pendingOfferNotification`, `initialOffersCheck` y `retryOrReschedule` (Task 4); `UserPrefs.householdCode`.
- Produces: `const val EXTRA_NOTIFICATION_ID = "com.homepantry.app.NOTIFICATION_ID"` y el nombre único de trabajo `"offers-check"`.

Comportamiento de `doWork()`:
1. Si no hay código de casa, devuelve `Result.success()` sin reprogramar (el usuario aún no ha entrado en ninguna casa).
2. Si `currentUser == null`, hace `signInAnonymously().await()`.
3. Lee los avisos y busca un aviso pendiente.
4. Si lo hay: si se tiene el permiso, lanza la notificación (canal "Ofertas", título = `title`, texto = las tiendas unidas con " y ", y un `PendingIntent` explícito a `MainActivity` con `EXTRA_NOTIFICATION_ID`, `FLAG_IMMUTABLE` y `FLAG_UPDATE_CURRENT`), guarda el id como notificado y lo reprograma a `nextMondayCheck(now)`.
5. Si no lo hay, o Firestore lanza una excepción (que no sea `CancellationException`): lo reprograma a `retryOrReschedule(now)`.
6. Siempre devuelve `Result.success()`. La reprogramación usa `REPLACE`, `setInitialDelay` y la restricción `NetworkType.CONNECTED`.

- [ ] **Step 1: Implementar la dependencia, el permiso, `UserPrefs`, el worker, la navegación desde el intent y la petición del permiso.**
- [ ] **Step 2: Verificación completa**
  Run: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`
  Expected: 0 tests fallidos, 0 errores de lint y `app-release.apk` de unos 4,5 MB (anota el tamaño).
- [ ] **Step 3: Commit** `feat: notificación semanal de ofertas con WorkManager`

### Task 7: Puesta en marcha y prueba de extremo a extremo (con el usuario)

- [ ] **Step 1 (usuario):** crea la cuenta de servicio en la [consola de Firebase](https://console.firebase.google.com/project/home-pantry-bed42/settings/serviceaccounts/adminsdk) → "Generar nueva clave privada", la guarda como `C:\Users\Sergio\Downloads\sa.json`, y guarda la API key de Gemini en `C:\Users\Sergio\Downloads\gemini.txt` y el código de casa en `C:\Users\Sergio\Downloads\casa.txt`.
- [ ] **Step 2: Prueba en local sin escribir.** **No hay que leer ni mostrar el contenido de los ficheros.**
  Run: `cd tools/offers && FIREBASE_SERVICE_ACCOUNT_FILE=/c/Users/Sergio/Downloads/sa.json HOUSEHOLD_CODE="$(cat /c/Users/Sergio/Downloads/casa.txt)" GEMINI_API_KEY="$(cat /c/Users/Sergio/Downloads/gemini.txt)" node src/index.mjs --dry-run --print > /c/Users/Sergio/Downloads/aviso.json`
  Expected: exit 0 y las líneas de recuento. El usuario revisa `aviso.json`: ¿tienen sentido las coincidencias?
- [ ] **Step 3: Crear los secrets** (con la confirmación del usuario):
  ```bash
  gh secret set FIREBASE_SERVICE_ACCOUNT -R SergioDC/HomePantry < /c/Users/Sergio/Downloads/sa.json
  gh secret set GEMINI_API_KEY -R SergioDC/HomePantry < /c/Users/Sergio/Downloads/gemini.txt
  gh secret set HOUSEHOLD_CODE -R SergioDC/HomePantry < /c/Users/Sergio/Downloads/casa.txt
  ```
  Después, borrar los cuatro ficheros de Descargas y recordar al usuario que vacíe la papelera.
- [ ] **Step 4:** con la rama subida, `gh workflow run offers.yml --ref ofertas-folletos` (con escritura real). Comprobar con `gh run watch` que acaba en verde y que el log solo contiene recuentos.
- [ ] **Step 5 (usuario, APK release en el S25):** la campana muestra 1. El detalle marca las ofertas nuevas. "Añadir N" las pone en la lista con la nota. Al volver a abrir el aviso salen como "añadida". En otro móvil o con otro miembro, el aviso sigue sin leer. Para probar la notificación del sistema, el worker se puede forzar con `adb shell cmd jobscheduler run -f com.homepantry.app <jobId>` o esperar a la ventana del lunes o el martes.
