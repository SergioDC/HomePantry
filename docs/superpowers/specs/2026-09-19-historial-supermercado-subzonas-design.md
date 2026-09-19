# Historial por supermercado con precio unitario, y subzonas en una sola pantalla

**Fecha:** 2026-09-19
**Estado:** Aprobado en brainstorming, pendiente de revisión del spec y plan de implementación

## Contexto y objetivo

Dos mejoras independientes que salen de uso real de la app:

1. **Historial de compras** (spec `2026-09-11-historial-compras-ticket-design.md`).
   Hoy cada fila muestra "gasto del mes / gasto total" por producto. Eso no
   sirve para comparar precios: lo útil es el precio **unitario** (por ud, kg o
   litro) y saber en qué **supermercado** se pagó. Cada `Purchase` guarda solo
   el total de la línea (`price`), sin cantidad ni tienda.
2. **Subzonas** (spec `2026-07-18-cierre-huecos-spec-v1-design.md` §subzonas).
   Al abrir una zona raíz (p. ej. Nevera) solo se ven sus productos directos;
   los de cada subzona están en pantallas aparte a las que se llega por chips.
   Se quiere ver todo de un vistazo, y poder elegir la subzona al añadir un
   producto.

Son dos sub-proyectos sin dependencias entre sí. Comparten spec por haberse
pedido juntos, pero cada uno debe poder implementarse y probarse por separado.

## Parte 1: Historial de compras

### Modelo de datos

`Purchase` (`households/{code}/purchases/{id}`) gana tres campos:

| Campo      | Tipo      | Por defecto | Notas                                         |
|------------|-----------|-------------|-----------------------------------------------|
| `quantity` | `Double`  | `1.0`       | Cantidad comprada en esa línea.               |
| `unit`     | `String`  | `"UD"`      | Nombre de un valor de `Unit`: `UD`, `KG`, `L`.|
| `store`    | `String?` | `null`      | Supermercado, tal y como se muestra.          |

- `price` **sigue siendo el total de la línea**. El gasto mensual no cambia y
  las compras antiguas siguen siendo válidas (cantidad 1, unidad `UD`, sin
  supermercado). Las reglas de Firestore no validan campos de `purchases`, no
  hay que tocarlas.
- **Precio unitario = `price / quantity`**, calculado, no guardado. Incluye los
  descuentos de la línea. Ejemplo: 0,850 kg por 3,39 € da 3,99 €/kg. Si
  `quantity <= 0` se trata como 1 para no dividir por cero.
- El supermercado es texto libre por compra, como `Item.store`. No hay
  colección de tiendas. Para agrupar se usa una clave normalizada (`trim` +
  minúsculas) y se muestra la grafía de la compra más reciente del grupo, para
  que "MERCADONA" y "Mercadona" no se separen.

### Lectura del ticket

- **Gemini** (`GeminiReceiptRecognizer.kt`, `GeminiReceiptApi.kt`):
  - `PRODUCTS_JSON_SCHEMA` añade `store` (string, opcional) a nivel de ticket
    y, por producto, `quantity` (number) y `unit` (enum `UD`/`KG`/`L`). Solo
    `name` y `price` siguen siendo obligatorios.
  - `RECEIPT_PROMPT` deja de decir que se ignoren los datos de la tienda: pide
    leer el nombre del supermercado de la cabecera, y la cantidad y unidad de
    cada línea cuando el ticket las trae (`2 x 1,25`, `0,850 kg x 3,99 €/kg`).
    Si no aparecen, cantidad 1 y unidad `UD`. `price` sigue siendo el total de
    la línea.
  - Un `quantity` ausente, no positivo o una `unit` desconocida se sanean a
    `1.0` / `UD` al mapear, sin descartar la línea.
- **OCR clásico** (respaldo): sin supermercado y con cantidad 1 / `UD`. El
  usuario lo completa en la hoja de revisión.
- Tipos: `ParsedReceiptLine` gana `quantity` y `unit` (con valores por
  defecto, para no romper `parseReceiptLines`). Aparece
  `ParsedReceipt(store: String?, lines: List<ParsedReceiptLine>)` como
  resultado del reconocimiento. `recognizeReceiptWithGemini` y la rama de OCR
  clásico de `PurchaseHistoryScreen` devuelven ese tipo.

### Hoja de revisión (`ReceiptReviewSheet`)

- Campo "Supermercado" arriba, ya rellenado con lo detectado, con
  autocompletado inline contra los supermercados ya usados en compras
  anteriores (mismo patrón que `storeSuggestions` en `AddItemSheet`).
- Cada línea gana cantidad y unidad (ud/kg/l) además de nombre y precio. Sigue
  siendo revisión obligatoria: nada se escribe hasta pulsar "Guardar compras".
- Una cantidad no numérica o `<= 0` en una línea se trata como 1 (no bloquea el
  guardado); un precio no numérico sigue descartando la línea, como hoy.
- `AppViewModel.savePurchaseBatch` recibe el supermercado y escribe
  `store`, `quantity` y `unit` en cada `Purchase` del lote. Un supermercado en
  blanco se guarda como `null`.

### Pantalla del historial (`PurchaseHistoryScreen`)

- Una sección por supermercado, ordenadas por su compra más reciente
  (descendente). Al final, "Sin supermercado" con las compras que no tienen.
- Dentro de cada sección, un producto por fila, ordenados por su última compra
  en ese supermercado. Cada fila muestra:
  - nombre (`rawName` de la última compra en ese súper),
  - **último precio unitario en ese supermercado**, con su unidad
    ("1,25 €/ud", "3,99 €/kg", "0,89 €/l"),
  - "última compra 12 sep · 5 compras".
  Desaparece el "mes / total" de la fila.
- Un producto comprado en dos supermercados aparece en ambas secciones (así se
  comparan precios). La clave de cada fila es supermercado + `normalizedName`.
- Tocar una fila abre el detalle **del producto** (todos los supermercados), no
  del par producto-supermercado.
- Estado vacío, indicador de proceso OCR y FAB de escaneo: sin cambios.

### Detalle del producto (`PurchaseDetailScreen`)

- Se mantiene el bloque de gasto mensual.
- Cada compra del histórico muestra fecha, supermercado (o "Sin
  supermercado"), cantidad con unidad, **precio unitario** y total de la línea.

### Lógica pura (`PurchaseAggregation.kt`)

- `Purchase.unitPrice()`: `price / quantity` con la salvaguarda anterior.
- `ProductSummary` gana `latestUnitPrice` y `latestUnit` (de la compra más
  reciente del grupo).
- Nueva `storeSections(purchases, referenceDate)`: devuelve
  `List<StoreSection(storeKey, displayName, products: List<ProductSummary>)>`
  con el orden descrito arriba, agrupando por clave de supermercado y luego por
  `normalizedName`. `productSummaries` se conserva para el detalle (agrupa por
  producto sin distinguir supermercado).
- `UiState` expone `storeSections` junto a `purchaseSummaries`.

### Compatibilidad

- Compras existentes: `quantity = 1.0`, `unit = "UD"`, `store = null`. Su
  precio unitario es igual a su total y aparecen en "Sin supermercado". No hay
  migración.
- El deserializador de Firestore rellena los valores por defecto de los campos
  que faltan en documentos antiguos.

## Parte 2: Subzonas

### Pantalla única (`ZoneDetailScreen`)

- Al abrir una **zona raíz**, una sola `LazyColumn` con secciones:
  1. Productos que están directamente en la zona (encabezado con el nombre de
     la zona).
  2. Una sección por subzona, en su orden: encabezado con punto de color,
     nombre y contador, y sus productos debajo.
- Las subzonas **vacías también se muestran** (encabezado sin productos), para
  poder añadir en ellas.
- Se sigue mostrando solo lo que se tiene (`done = true`), como hoy; lo
  pendiente vive en la Lista de la compra.
- Desaparece la fila de chips de subzonas y el chip "Nueva subzona". Crear una
  subzona se mantiene desde esta misma pantalla mediante un elemento propio de
  la lista (al final, "Nueva subzona"), con el mismo diálogo actual.
- Tocar el encabezado de una subzona abre su pantalla actual
  (`zoneDetail/{subzoneId}`), donde se sigue pudiendo renombrar, cambiar el
  color o eliminar. Esa pantalla, al ser de una subzona, no muestra secciones
  (una subzona no tiene subzonas).
- Una zona sin subzonas se ve exactamente como hoy (una sola sección, sin
  encabezado añadido).

### Añadir a una subzona

- Cada sección (zona y subzonas) lleva un botón "+" que abre `AddItemSheet` con
  esa zona o subzona ya elegida. El FAB de la pantalla se mantiene y añade a la
  zona raíz.
- En `AddItemSheet`, la fila de zonas pasa a listar solo zonas raíz. Al elegir
  una raíz que tiene subzonas, aparece debajo una segunda fila de chips
  "Subzona (opcional)" con esas subzonas. Elegir una la selecciona como
  destino; volver a tocar la subzona ya elegida la deselecciona y deja la raíz
  como destino.
  Se elimina la etiqueta plana "Nevera > Cajón" de los chips actuales.
- Si `initialZoneId` (o el producto que se edita) ya es una subzona, el
  formulario abre con la raíz correspondiente seleccionada y esa subzona marcada.
- El valor guardado en `Item.zone` sigue siendo un único id de zona: no cambia
  el modelo de datos ni las reglas.

### Lógica pura (`ItemListLogic.kt`)

- Nueva función que, dada una zona raíz, devuelve sus secciones (la propia y
  sus subzonas) con sus productos `done`, reutilizando `groupAndSort` y
  `subzonesOf` para no duplicar el orden/filtrado.

## Fuera de alcance

- Gestión de supermercados como entidad (renombrar/fusionar tiendas).
- Migrar compras antiguas a un supermercado o cantidad.
- Gráficas de evolución del precio unitario.
- Cambiar el dashboard "Almacén" (sus contadores por zona ya suman las subzonas).
- Más de un nivel de subzonas.

## Pruebas

- `PurchaseAggregationTest`: precio unitario (incluido `quantity <= 0`),
  `storeSections` (agrupación, normalización de la clave, orden, "Sin
  supermercado", producto repetido en dos súperes), `latestUnitPrice`/`Unit`.
- `GeminiReceiptRecognizerTest`: mapeo con `store`, `quantity` y `unit`,
  saneado de valores ausentes o inválidos, y compatibilidad con respuestas sin
  los campos nuevos.
- `ItemListLogicTest`: secciones de una zona raíz (propia + subzonas, subzonas
  vacías, solo `done`, zona sin subzonas).
- `UiStateTest`: `storeSections` expuesto en el estado.
- UI (`ReceiptReviewSheet`, `PurchaseHistoryScreen`, `PurchaseDetailScreen`,
  `ZoneDetailScreen`, `AddItemSheet`): se comprueba que compila y se revisa en
  el dispositivo.
