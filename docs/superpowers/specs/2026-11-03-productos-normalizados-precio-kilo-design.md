# Productos normalizados y precio por kilo — diseño

Fecha: 2026-11-03

## Por qué

Al importar tickets, el mismo producto entra con nombres distintos según la tienda y el envase:
"ARANDANO 300GR", "Arándano 150gr", "ARAND. CULTIVADO". La clave de agrupación actual
(`normalizeProductName`: minúsculas, sin acentos, sin "s" final) los deja como productos
distintos, así que el historial y las estadísticas (`priceRises`, `cheapestStores`,
`productSummaries`) no pueden comparar precios entre tiendas ni entre meses.

Hay un segundo problema: aunque se agruparan, el precio por envase engaña. 2,50 € la bandeja de
300 g frente a 1,60 € la de 150 g no dice cuál es más barato; el precio por kilo sí
(8,33 €/kg frente a 10,67 €/kg). Hoy esas líneas se guardan como `quantity 1, unit UD` y el peso
se pierde dentro del nombre.

**Éxito:** en el historial y en las estadísticas los arándanos aparecen como un solo producto
"Arándanos" y se comparan en €/kg entre tiendas y meses, también con los tickets ya importados.

## Decisiones del usuario

- **Granularidad:** un producto es *tipo + variedad*. La marca y el tamaño del envase no separan.
  "Leche entera Hacendado" y "Leche entera Pascual" son "Leche entera"; "Leche semidesnatada"
  es otro producto.
- **Precio de comparación:** €/kg (o €/l en líquidos) cuando se conoce el peso o el volumen.
- **Tickets antiguos:** se arreglan con Gemini, que propone a partir de los nombres impresos ya
  guardados (solo texto, sin volver a leer fotos), **con revisión**: el usuario ve cada propuesta
  y la confirma o corrige antes de guardar nada.
- **Unir a mano:** en la lista de productos del historial se pueden seleccionar varios y
  "Unir" bajo un nombre.

## Objetivos

1. Cada compra tiene un **nombre de producto** genérico (tipo + variedad) que es lo que se
   muestra y por lo que se agrupa. El nombre impreso (`rawName`) se conserva tal cual.
2. Cada compra puede llevar el **tamaño del envase** (peso en kg o volumen en l por unidad), para
   calcular €/kg o €/l.
3. Al importar un ticket nuevo, Gemini devuelve el nombre genérico y el tamaño del envase; el
   usuario los ve y puede corregirlos en la revisión.
4. La casa **recuerda las correcciones** (alias): la próxima vez que aparezca el mismo nombre
   impreso, se aplica la corrección sin intervención.
5. Un proceso de **normalización del histórico** con revisión para las compras ya guardadas.
6. **Unir productos** a mano desde la lista de productos del historial.
7. Las comparativas usan el precio comparable (€/kg, €/l o €/ud).

## No objetivos

- Seguir marcas o comparar marcas entre sí.
- Enlazar las compras con el inventario (`Item`) o con la lista de la compra.
- Usar Open Food Facts u otras bases de datos de productos.
- Un catálogo de productos editable aparte (más allá de los alias).

## Anti-objetivos (sería un fallo aunque funcione)

- Que se reescriban compras antiguas sin que el usuario lo haya confirmado.
- Perder el nombre impreso original.
- Que un fallo o la falta de clave de Gemini impida guardar un ticket.
- Mezclar en una misma comparativa precios por kilo con precios por unidad.

## Modelo de datos

### `Purchase` (campos nuevos, todos con valor por defecto)

- `productName: String = ""`: nombre genérico mostrado ("Arándanos"). Vacío en compras antiguas
  aún no normalizadas; en ese caso se muestra `rawName`, como hasta ahora.
- `packSize: Double? = null`: cantidad de `packUnit` que contiene **una** unidad del envase
  (0.3 para 300 g). Solo tiene sentido cuando `unit == UD`.
- `packUnit: String? = null`: `KG` o `L`.

`normalizedName` sigue siendo la clave de agrupación, ahora calculada con
`normalizeProductName(productName.ifBlank { rawName })`. Todo el código que agrupa por
`normalizedName` sigue funcionando.

### Alias: `households/{code}/productAliases/{id}`

Nuevo modelo en `data/` (lo conserva la regla de ProGuard existente):

- `@DocumentId id`: la clave del nombre impreso **sin el tamaño**: se le quita el tamaño con las
  mismas reglas locales del respaldo (ver Flujo 1, paso 3) y después se aplica
  `normalizeProductName`. Así "ARANDANO 300GR" y "ARANDANO 150GR" comparten un alias. La clave se
  codifica para que sea un id de documento válido (sin "/").
- `rawName`: un ejemplo del nombre impreso.
- `productName`: nombre genérico.
- `packKnown: Boolean = false`, `packSize: Double? = null`, `packUnit: String? = null`: el envase
  recordado. **Solo se guarda cuando el nombre impreso no lleva tamaño** (p. ej. "LECHE ENT.
  HACENDADO", que siempre es el brik de 1 l). Si lo lleva, el envase sale siempre del propio
  nombre de cada línea, porque el alias es común a todos los tamaños. `packKnown = true` con
  `packSize = null` significa "sin envase" de forma explícita: el usuario quitó un envase mal
  propuesto y eso también se recuerda, para que Gemini no lo vuelva a poner.
- `updatedAt` con `@ServerTimestamp`.

*(Decisiones mías: la clave sin tamaño y la regla de cuándo se recuerda el envase.)*

Las reglas de Firestore actuales (`households/{code}/{document=**}`) ya cubren la colección.

### Precio comparable (lógica pura, testeable)

En `PurchaseAggregation.kt`, o en un fichero `*Logic.kt` nuevo:

- `Purchase.measure(): Pair<Double, String>`: cantidad y unidad de comparación.
  - `unit` KG o L → (`quantity`, `unit`).
  - `unit` UD con `packSize` > 0 y `packUnit` → (`quantity × packSize`, `packUnit`).
  - en otro caso → (`quantity`, `UD`).
- `Purchase.comparablePrice()` = `price / measure.cantidad` (una cantidad no positiva cuenta
  como 1, igual que `unitPrice()`), con la unidad de `measure`.
- `priceRises`, `cheapestStores` y `productSummaries` agrupan por
  `normalizedName` + **unidad de comparación** (no por `unit` crudo) y usan `comparablePrice()`.
  Así, una bandeja de 300 g (UD + pack) y 0,5 kg a granel (KG) se comparan juntas en €/kg, y nunca
  se mezcla €/kg con €/ud.
- Las pantallas que muestran el precio unitario (`PurchaseDetailScreen`,
  `PurchaseTicketDetailScreen`, estadísticas) pasan a mostrar el comparable con su sufijo
  (`unitSuffix`).
- **Producto con líneas en unidades de comparación distintas** (p. ej. compras antiguas sin
  envase en €/ud junto a otras en €/kg): en el detalle del producto cada compra muestra su precio
  con su propio sufijo, y el precio de cabecera (y el de la lista de productos) usa la unidad de
  comparación con más compras. Las estadísticas comparan cada unidad por separado, nunca
  mezcladas. *(Decisión mía.)*

## Flujo 1: importar un ticket nuevo

1. **Gemini** (`RECEIPT_PROMPT` y el esquema JSON en `GeminiReceiptApi`): por cada producto,
   además de `name`/`price`/`quantity`/`unit`, devuelve
   - `productName`: nombre genérico en español, tipo + variedad, sin marca ni tamaño, con la
     primera letra en mayúscula ("Arándanos", "Leche entera", "Leche semidesnatada").
   - `packSize` y `packUnit`: peso o volumen de una unidad si el nombre impreso lo indica
     ("300GR" → 0.3 KG, "1,5L" → 1.5 L); se omiten si no.
2. **Alias:** antes de mostrar la revisión, por cada línea se busca el alias por su clave sin
   tamaño. Si existe, su `productName` **sustituye** a lo propuesto por Gemini, y su envase
   también cuando `packKnown` es true: las correcciones del usuario mandan.
3. **Respaldo sin Gemini** (lectura por OCR de ML Kit, o sin clave): si no hay alias,
   `productName` y el envase se sacan con reglas locales: se extrae un tamaño del final o del
   cuerpo del nombre (`300GR`, `300 g`, `1KG`, `1,5 L`, `500ML`, `33CL`) y lo que queda es el nombre.
   No pretende agrupar abreviaturas; las arreglan los alias. *(Decisión mía: el usuario eligió las
   opciones 1 y 3; las reglas son solo el respaldo para no dejar el tamaño metido en el nombre.)*
4. **Revisión (`ReceiptReviewSheet`):** cada línea muestra el nombre genérico como dato
   principal y el impreso debajo, en pequeño. Se pueden editar el nombre genérico y el envase
   (cantidad + g/kg/ml/l, solo con unidad UD).
5. **Al guardar:** se escriben las compras con los campos nuevos. Por cada línea en la que el
   usuario **cambió** el nombre genérico o el envase respecto a lo propuesto, se escribe o
   actualiza el alias de su nombre impreso. Si falla la escritura de alias, las compras quedan
   igualmente guardadas.

Editar después una compra ya guardada (`updatePurchase`) también permite cambiar el nombre
genérico y el envase, recalcula `normalizedName` y actualiza el alias.

## Flujo 2: normalizar el histórico (con revisión)

- **Entrada:** una acción en la barra superior del historial de compras (área Gastos), visible
  mientras haya compras sin `productName`. Necesita la clave de Gemini; sin ella, un mensaje
  explica que hace falta y dónde se pone.
- **Petición:** se agrupan las compras sin `productName` por `normalizeProductName(rawName)`,
  primero aplicando los alias existentes (esos no van a Gemini). Los nombres impresos que quedan
  se envían a Gemini **solo como texto**, en tandas (p. ej. 100 nombres por petición), con un
  prompt que sigue las mismas reglas de nombre genérico y envase, y devuelve un JSON
  `[{rawName, productName, packSize?, packUnit?}]`.
- **Revisión:** pantalla (ruta nueva, sin barra inferior, con flecha atrás) con una fila por
  nombre impreso: "ARANDANO 300GR → Arándanos · 300 g · 4 compras". Las filas se ordenan por nombre
  genérico, para que se vea qué se va a juntar. Cada fila se puede editar (nombre y envase) o
  desmarcar para dejarla como está. Un botón "Aplicar" guarda.
- **Guardar:** actualización por campos (`update`, no `set`) de `productName`,
  `normalizedName`, `packSize`, `packUnit` en las compras afectadas, con batches troceados como
  el resto de operaciones masivas; y un alias por cada fila aplicada. Las filas desmarcadas no
  se tocan.
- **Errores:** los fallos HTTP de Gemini usan las subclases de `GeminiReceiptException`. Si
  falla una tanda, lo ya recibido se puede revisar igualmente y el resto queda para volver a
  lanzarlo. La lectura en curso no debe perderse al girar la pantalla (como la lectura de tickets).

## Flujo 3: unir productos a mano

- En `PurchaseProductsScreen`, una pulsación larga entra en modo selección; con 2+ productos
  seleccionados aparece "Unir".
- Un diálogo pide el nombre final, propuesto con el del producto con más compras y editable.
- Al confirmar: todas las compras de esos productos pasan a ese `productName` (y su
  `normalizedName`), con `update` por campos y batches; y se escribe un alias por cada nombre
  impreso distinto implicado, para que los tickets futuros entren ya unidos.
- No hay "separar". Para deshacerlo se edita cada compra o se vuelve a unir con otro nombre.
  *(Decisión mía.)*

## Restricciones

- Lógica nueva testeable (extracción de tamaño, precio comparable, aplicación de alias, armado de
  las filas de revisión, agrupación) en ficheros puros de `data/` con tests JUnit en
  `app/src/test`.
- Modelos de Firestore como `data class` con valores por defecto en `data/`.
- Textos en `res/values/strings.xml` (con `plurals` para "N compras").
- `CancellationException` se relanza siempre.
- La clave de Gemini sigue siendo local y no se sincroniza.

## Lo que se deja al implementador

- Nombres exactos de funciones, ficheros y rutas, y el diseño visual concreto de las filas y los
  diálogos, siguiendo el estilo de las pantallas actuales.
- El tamaño de las tandas a Gemini y qué modelo usar para la petición de texto (el mismo
  mecanismo de modelos que la lectura de tickets).
- Cómo codificar la clave del alias como id de documento.
- Si el respaldo por reglas también se aplica en el Flujo 2 cuando Gemini no devuelve una fila.
