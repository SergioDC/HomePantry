# Avisos de ofertas de Lidl y Aldi — diseño

Fecha: 2026-10-26

## Intención

Cada lunes Lidl y Aldi publican sus ofertas. Queremos que SNHome avise a la casa de las ofertas que
coinciden con productos que solemos comprar, para **añadirlas a la lista de la compra** en el momento.
El objetivo es no perderse una rebaja de algo que se va a comprar igualmente, no descubrir productos nuevos.

Los tickets de la casa son de muchas tiendas (Mercadona, Eroski, Primaprix, Lidl, Aldi…), así que la
coincidencia es **por tipo de producto, no por marca ni variante**: si solemos comprar "leche entera
Hacendado", una oferta de "leche semidesnatada Milbona" cuenta.

## Objetivos

- Un aviso semanal (lunes) por casa con las ofertas de Lidl y Aldi que coinciden con los productos habituales.
- El aviso se guarda en Firestore y cada miembro lo ve como no leído hasta que lo abre él (un miembro
  que lo lee no lo marca como leído para los demás).
- Desde el aviso, marcar varias ofertas y añadirlas a la lista de una vez.
- Enlace al folleto o a la página de ofertas de cada tienda.
- Notificación del sistema el lunes, sin FCM.

## No objetivos

- Otras tiendas (Mercadona, Eroski…): no publican folletos aprovechables y quedan fuera.
- Comparar precios entre tiendas o mostrar el histórico de precios.
- Configurar desde la app el criterio de "habitual", las tiendas o el horario.
- Push inmediato con FCM. Se podría añadir más adelante sin cambiar el modelo de datos.
- Varias casas: el script trabaja con una sola casa, la configurada en los secrets.

## Antiobjetivos (aunque funcione técnicamente, sería un fracaso)

- Que el log público de GitHub Actions muestre el código de casa, productos u ofertas concretas. El repo
  `SergioDC/HomePantry` es **público** y cualquiera puede ver los logs. El código de casa es la única
  credencial de acceso a los datos.
- Avisos con ruido: ofertas que no tienen nada que ver con lo que compramos, o un aviso vacío.
- Duplicar en la lista productos que ya están pendientes.

## Arquitectura

```
GitHub Actions (lunes 07:00 UTC)
  └─ tools/offers/  (Node, script)
       1. lee purchases de la casa (Admin SDK)  → productos habituales
       2. descarga ofertas Lidl + Aldi
       3. Gemini (texto) cruza habituales × ofertas
       4. escribe households/{code}/notifications/{id}  (si hay coincidencias)
       5. borra avisos de más de 28 días

App (Android)
  ├─ WorkManager: lunes 11:00 → si hay aviso no leído → notificación local
  ├─ Home: campana con contador de no leídos
  ├─ Pantalla "Avisos": lista
  └─ Pantalla detalle: ofertas por tienda, marcar varias → "Añadir N a la lista"
```

## Parte 1: script (`tools/offers/`)

Node 22 y ES modules. Decisión mía: Node, para tener `fetch` nativo y `firebase-admin` oficial. El spike
`folletos.mjs` (ver "Fuentes de datos") ya está escrito en Node.

### Configuración (secrets de GitHub)

| Secret | Contenido |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | JSON de la cuenta de servicio del proyecto Firebase |
| `HOUSEHOLD_CODE` | Código de la casa |
| `GEMINI_API_KEY` | API key de Gemini del usuario (decidido: la suya) |

El workflow `.github/workflows/offers.yml` se lanza con `schedule: cron: '0 7 * * 1'` y con
`workflow_dispatch`, para poder probarlo a mano. Admite una entrada `dry_run` que imprime el aviso
completo **solo en local**. En Actions, `dry_run` no escribe en Firestore y solo imprime recuentos.

### Productos habituales

Se leen de `households/{code}/purchases`. Cada documento es una línea de ticket con `normalizedName`,
`date` y `store`. Un producto es **habitual si aparece en al menos 2 líneas en los últimos 90 días**,
agrupando por `normalizedName` (decidido con el usuario: 2 veces en 3 meses). Las líneas con
`normalizedName` vacío se descartan.

Se le pasa a Gemini el `normalizedName` y un `rawName` de ejemplo, que le da pistas del tipo de producto.

### Fuentes de datos (validadas con un spike el 2026-10-26)

**Lidl.** La página `https://www.lidl.es/c/descubre-nuevas-ofertas-cada-semana-folletos-lidl/s10087402`
enlaza los folletos con URLs `/l/folletos/{slug}/ar/0`. Se usan solo los slugs que empiezan por
`folleto-alimentacion`. Para cada slug se llama a la API
`https://endpoints.leaflets.schwarz/v4/flyer?flyer_identifier={slug}&region_id=0&region_code=0`, que
devuelve `flyer` con:
- `name`, `offerStartDate`, `offerEndDate`, `flyerUrlAbsolute` (enlace al folleto) y `pdfUrl`;
- `pages[]` con `number`, `keyWords` (texto del OCR, desordenado y con los precios sin coma: "436 396"
  significa 4,36 → 3,96 €) y `altText` (una descripción en prosa de la página);
- `products` viene vacío: Lidl **no** da productos estructurados.

Se elige el folleto vigente hoy (`offerStartDate <= hoy <= offerEndDate`). El de la semana siguiente
suele estar publicado ya y se ignora.

**Aldi.** `https://www.aldi.es/ofertas.html` es una página Next.js. Dentro de
`<script id="__NEXT_DATA__">`, en `props.pageProps.apiData`, hay strings con JSON que contienen los
productos (de Algolia). Cada producto lleva `name`, `brandName`, `salesUnit`, `objectID` y
`currentPrice { priceValue, strikePrice?.strikePriceValue, priceTagLabels?.promoText1, validFrom,
validUntil }` (fechas en segundos Unix). Hay unos 270 productos y unos 110 tienen precio tachado o
promo. **Solo se consideran ofertas los que tienen `strikePrice` o `promoText1`.** No se ha
encontrado URL por producto, así que el enlace es la propia página de ofertas.

Hay que mandar cabeceras de navegador (`User-Agent` y `Accept-Language: es-ES`). Si una tienda falla,
la otra sigue adelante, y si fallan las dos, el script termina con error. Cualquier fallo hace que el
workflow acabe en rojo y GitHub avisa por correo.

### Cruce con Gemini

Una llamada de texto por tienda (sin imágenes), por REST a `generativelanguage.googleapis.com`, con
salida JSON (`responseMimeType: application/json` y `responseSchema`). El modelo y los modelos de
respaldo son los mismos que en `GeminiReceiptRecognizer` (`gemini-3.8-flash` primero).

- Entrada para Aldi: los habituales y la lista de productos en oferta (índice, nombre, marca, unidad,
  precio, precio anterior, promo y fechas).
- Entrada para Lidl: los habituales y, por página, `number`, `keyWords` y `altText`.
- Instrucción clave: hay coincidencia si es **el mismo tipo de producto** aunque cambien la marca o la
  variante ("leche entera" ↔ "leche semidesnatada" sí; "leche" ↔ "batido de chocolate" no). Ante la
  duda, no hay coincidencia (antiobjetivo: ruido).
- Salida: una lista de `{ habitual, producto, precio, precioAnterior, promo, desde, hasta, pagina }`.
  En Aldi los precios y las fechas se toman **de los datos de entrada por índice**, no del texto de
  Gemini, para evitar errores de transcripción. En Lidl el precio es lo que Gemini consiga sacar del
  texto y puede ser null. Si es null, la app muestra "ver pág. N".

Decisión mía: los prompts concretos los escribe quien implemente. Este documento solo fija los criterios.

### Escritura del aviso

Si no hay coincidencias en ninguna tienda, **no se escribe nada**. Si las hay, se crea un documento
en `households/{code}/notifications` (id automático):

```
type: "offers"
title: "3 ofertas en cosas que compráis"   (texto ya formado; la app lo muestra tal cual)
weekStart: "2026-10-26"                     (lunes de la semana; sirve para que no se repita)
createdAt: serverTimestamp
readBy: []                                  (uids de members)
stores: [
  { store: "Lidl", name: "FOLLETO ALIMENTACIÓN 26/10", url: <flyerUrlAbsolute>,
    validFrom: "2026-10-26", validUntil: "2026-11-01",
    offers: [ { key, habitual, product, price?, oldPrice?, promo?, page?, validFrom?, validUntil? } ] },
  { store: "Aldi", url: "https://www.aldi.es/ofertas.html", ..., offers: [...] }
]
addedKeys: []                               (claves de ofertas ya añadidas a la lista)
```

- `key` identifica la oferta dentro del aviso. Decisión mía: en Aldi es `aldi-{objectID}`; en Lidl,
  `lidl-p{página}-{normalizeProductName(habitual)}`. El script quita los duplicados por `key`, y en
  Lidl se queda con una coincidencia por habitual y página. En Lidl la clave **no** es estable si se
  relanza con otra respuesta de Gemini; se acepta, y la app ignora las `addedKeys` que ya no existan
  en el aviso.
- Fechas: todo (el "hoy" para elegir el folleto de Lidl y `weekStart`) se calcula en Europe/Madrid.
- **Idempotencia:** si ya existe un aviso `type=offers` con el mismo `weekStart`, se sobrescribe ese
  documento en vez de crear otro (por ejemplo, al relanzar el workflow a mano), pero se conservan
  `readBy` y `addedKeys`.
- Limpieza: se borran los avisos con `createdAt` de hace más de 28 días.

### Logs

Solo se imprimen recuentos: `Lidl: 53 páginas · Aldi: 108 ofertas · habituales: 41 · coincidencias:
Lidl 1, Aldi 2 · aviso escrito`. Nunca el código de casa, nombres de productos, ofertas ni respuestas
de Gemini. Los errores HTTP se imprimen con el estado y el host, sin la query (la URL de Gemini lleva
la key).

## Parte 2: app

### Datos (`data/`)

- `AppNotification`, `OfferStore` y `OfferMatch`: `data class` de Firestore con valores por defecto,
  `@DocumentId` y `@ServerTimestamp`, dentro de `data/` (por la regla de R8 de `proguard-rules.pro`).
- `NotificationsRepository(firestore, householdCode)`:
  - `observeNotifications(): Flow<List<AppNotification>>` ordenado por `createdAt` desc (`callbackFlow`);
  - `markRead(id, uid)` → `update("readBy", FieldValue.arrayUnion(uid))`;
  - `markAdded(id, keys)` → `update("addedKeys", FieldValue.arrayUnion(*keys))`;
  - una lectura puntual (`get().await()`) para el worker.
- `firestore.rules`: no cambia. La regla `households/{householdCode}/{document=**}` ya cubre
  `notifications`, así que no hay nada que desplegar.

### Identidad del lector

`readBy` guarda el `uid` de Firebase Auth, que es también el id de `members/{uid}`. Decisión mía: si
alguien reinstala la app, su uid cambia y verá como no leídos los avisos de las últimas 4 semanas.
Se acepta.

### Lógica pura (testeable, en `data/OffersLogic.kt` o similar)

- `unreadCount(notifications, uid)`.
- `nextMondayCheck(now, zone)`: el próximo lunes a las 11:00 (hora de Europe/Madrid, que es la del
  móvil). Si hoy es lunes antes de las 11:00, hoy.
- `retryOrReschedule(now)`: entre el lunes a las 11:00 y el martes a las 23:59, reintentar en 3 h; fuera
  de esa ventana, el próximo lunes.
- `offerItems(selected, existingItems, zones, addedBy)` → `List<Item>`:
  - `name` = `habitual` con la primera letra en mayúscula, `store` = tienda, `note` = "Oferta 2,49 €
    (antes 2,85 €) hasta 1/11" (sin la parte que falte), `qty` 1 y unidad `UD`;
  - `zone`: la zona de un item existente con el mismo nombre normalizado (`normalizeProductName`). Si no
    hay ninguno, la misma zona por defecto que usa el alta normal de la lista. **Quien implemente debe
    comprobar cuál es en `AddItemSheet`/`AppViewModel`.**
- `alreadyInList(offer, items)`: hay un item con `done = false` y el mismo nombre normalizado que `habitual`.

### Pantallas

Las maquetas acordadas están en `.superpowers/brainstorm/629-1793021727/content/ofertas-flujo.html`
(opción B elegida).

- **Home:** campana en la barra superior con un badge que muestra el número de no leídos (oculto si
  es 0). Lleva a la ruta `notifications`. Cumple la regla de navegación: los accesos que no son de un
  área concreta van en Home.
- **`notifications` (Avisos):** sin barra inferior y con flecha atrás. Una tarjeta por aviso con el
  título y "Lunes 26 oct · Lidl y Aldi". Los no leídos llevan una marca (borde con el color primario,
  no coral). Si no hay avisos: texto vacío explicativo.
- **`notification/{id}` (detalle):** al abrirse llama a `markRead`. Título "Ofertas 26 oct – 1 nov".
  Sección por tienda con un enlace "Ver folleto ↗" (Lidl) o "Ver ofertas ↗" (Aldi) que abre el
  navegador con `Intent.ACTION_VIEW`. Una fila por oferta con:
  - una casilla;
  - `habitual` en grande y debajo `product · precio`, con el precio anterior tachado, la promo y la
    página en Lidl (o "ver pág. N" si no hay precio);
  - estados: las ya añadidas (`addedKeys`) aparecen deshabilitadas con "añadida"; las que ya están en
    la lista aparecen desmarcadas con "ya en la lista" (se pueden marcar igualmente). El resto aparece
    **marcada por defecto**. Decisión mía: si el aviso las propone es que interesan, así que marcar
    es el caso habitual.
  - Botón fijo abajo, "Añadir N a la lista" (deshabilitado si N = 0). Llama a `addItems(offerItems(…))`
    y después a `markAdded`, y muestra un snackbar "N productos añadidos".
- Los textos van en `strings.xml` (con `plurals` para "N ofertas" y "Añadir N"). La paleta es la de
  `ui/theme`.

Decisión mía: un `NotificationsViewModel` aparte, como `MenuViewModel` y `SplitViewModel`, creado en
`MainActivity` con el código de casa como clave. Lee `NotificationsRepository` e `ItemsRepository` (para
saber qué está ya en la lista y para añadir). El uid lo pasa `MainActivity`.

### Notificación del sistema

- Dependencia nueva: `androidx.work:work-runtime-ktx`.
- `OffersCheckWorker` (`CoroutineWorker`), programado con `OneTimeWorkRequest`,
  `enqueueUniqueWork("offers-check", REPLACE)` y la restricción `NetworkType.CONNECTED`:
  1. Lee los avisos y busca alguno de tipo `offers` creado en los últimos 7 días que el usuario no haya
     leído y que no se haya notificado ya (ids notificados en DataStore, `UserPrefs`). Si el móvil
     estuvo apagado lunes y martes, la notificación no llega y solo queda la campana. Se acepta.
  2. Si lo hay: lanza la notificación local (canal "Ofertas", título = `title` del aviso, texto = las
     tiendas). Al tocarla se abre `notification/{id}`. Guarda el id como notificado y se programa para
     el próximo lunes.
  3. Si no lo hay: `retryOrReschedule(now)`.
  4. Si Firestore falla, se aplica la misma regla de reintento (sin `Result.failure`, para no romper la
     cadena). `CancellationException` se relanza.
- El worker no tiene sesión de la app: necesita el código de casa (de `UserPrefs`) y Auth anónimo
  (`currentUser` debería existir. Si no existe, se hace `signInAnonymously` antes de leer).
- Al arrancar `MainActivity` (con casa configurada) se programa con `enqueueUniqueWork(..., KEEP)` para
  el próximo lunes, así que nunca hay dos programaciones a la vez.
- Permiso `POST_NOTIFICATIONS`: se pide la primera vez que se abre la pantalla de Avisos o, si no,
  al arrancar la app después de que exista el primer aviso. Decisión mía: no al arrancar la app sin
  más, para que se entienda para qué es. Si se deniega, la campana sigue funcionando.
- Abrir la app desde la notificación: un `PendingIntent` explícito a `MainActivity` con el id del aviso
  como extra, que navega a `notification/{id}`. Decisión mía: no se tocan los App Links de SNcount
  (`SplitLink`).

## Pasos manuales del usuario (fuera del código)

El plan debe dejarlos como tareas explícitas del usuario, porque bloquean la prueba de extremo a extremo
del workflow:
1. Crear una cuenta de servicio en la consola de Firebase/Google Cloud y descargar su JSON.
2. Crear en GitHub (Settings → Secrets → Actions) `FIREBASE_SERVICE_ACCOUNT`, `HOUSEHOLD_CODE` y
   `GEMINI_API_KEY`.
3. Lanzar el workflow a mano (`workflow_dispatch`) para la primera prueba.

## Restricciones

- Las de `CLAUDE.md`: JDK 17, minSdk 26, AGP/Gradle 8.x, R8 en release, textos en español, sin DI y
  commits en español.
- El APK debe seguir rondando 4,5 MB. WorkManager añade poco.
- El cron de GitHub va en UTC y puede retrasarse. A las 11:00 en España hay margen de sobra.
- Scraping: Lidl o Aldi pueden cambiar su web en cualquier momento. El script tiene que fallar de forma
  ruidosa (workflow en rojo) y no escribir un aviso vacío ni basura.

## Lo que queda a criterio de quien implemente

- Los prompts y el `responseSchema` concretos de Gemini.
- La estructura interna de `tools/offers/` (un fichero o varios) y sus tests. Como mínimo hay que
  probar con fixtures guardados (los HTML/JSON del spike) el parseo de Aldi, la elección del folleto
  de Lidl y el cálculo de habituales.
- La cuenta de servicio: si se crea una con permisos mínimos (Cloud Datastore User) en vez de usar la
  de Firebase Admin.
- La animación, iconos y espaciados de las pantallas, siguiendo el estilo actual.
