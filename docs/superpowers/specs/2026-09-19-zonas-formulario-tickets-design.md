# Colores de zona fijos, formulario sin foto/escáner y tickets del historial

**Fecha:** 2026-09-19
**Estado:** Aprobado en brainstorming, pendiente de revisión del spec y plan de implementación

## Contexto y objetivo

Cuatro cambios pedidos tras probar la app, en tres partes independientes:

1. **Formulario de añadir producto** (`AddItemSheet`): quitar los botones «Foto» y «Escanear».
2. **Colores de zonas y subzonas.** Una zona se crea sin color (`Zone.color = null`) y cada
   pantalla lo deriva de su posición en una lista distinta: Almacén cuenta solo las raíces,
   la pantalla de zona cuenta los hermanos y el formulario cuenta todas las zonas. La misma
   subzona sale con colores distintos según la pantalla. Además, las tarjetas de Almacén
   deben llevar el borde del color de su zona.
3. **Historial de compras** (spec `2026-09-19-historial-supermercado-subzonas-design.md`).
   Un ticket escaneado dos veces deja líneas duplicadas y hoy no hay forma de borrarlas: una
   compra no guarda a qué ticket pertenece y el historial solo agrupa por supermercado y
   producto. Se añade una lista de tickets con borrado de ticket entero y de líneas sueltas.

Las partes A y B tocan zonas y formulario; la C es el historial. Se implementan en dos planes
independientes (A+B y C). Mockups de la parte C:
`docs/superpowers/mockups/2026-09-19-historial-tickets-mockups.md`.

## Parte A: formulario de añadir producto

- Se elimina de `AddItemSheet.kt` la fila con los botones «Foto» y «Escanear» (los dos
  `OutlinedButton` que lanzan el selector de foto y el escáner). Nada más se retira.
- El resto del código se conserva sin botón que lo active, porque la consulta a OpenFoodFacts
  puede reutilizarse más adelante: selector de foto, diálogo del escáner, lectura del código,
  aviso de duplicado por código de barras, vista previa de la foto actual al editar y los
  textos `add_item_photo` y `add_item_scan`. Un comentario en el sitio del botón explica que
  se conserva a propósito. El compilador avisará de que el lanzador del selector de foto no se
  usa; es esperado.
- Los productos ya guardados conservan su foto y su código de barras.

## Parte B: colores de zona fijos

### Color guardado al crear

- Toda zona nueva guarda su `color` en Firestore al crearse, sea raíz o subzona:
  «+ Nueva zona» (Almacén y formulario), «+ Subzona» y las zonas por defecto de una casa nueva
  (`seedDefaultZones`, con `zoneColorFor(index)`).
- Cada subzona recibe **su propio color**; no hereda el de su zona.
- Nueva `nextZoneColor(zones: List<Zone>): String` en `data/Zone.kt`: devuelve el color de
  `ZONE_COLORS` menos usado entre las zonas existentes (comparando el hex sin distinguir
  mayúsculas); en un empate, el primero en el orden de la paleta.
- `ZonesRepository.addZone` recibe el color; `AppViewModel.createZone` lo calcula con
  `nextZoneColor(state.zones)`.

### Relleno único de las zonas existentes

- Al cargar las zonas, si alguna tiene `color` nulo o en blanco, la app lo rellena una vez:
  - Raíces sin color: `zoneColorFor(posición)`, donde la posición es la de la raíz entre todas
    las raíces ordenadas por `order`. Es el color que ya se ve hoy en Almacén.
  - Subzonas sin color: cada una recibe `nextZoneColor` calculado en orden estable (por el
    `order` de su zona padre y luego el suyo), contando los colores ya asignados en esa misma
    pasada. Una subzona cuyo padre no existe va al final.
- Nueva función pura `zoneColorBackfill(zones: List<Zone>): Map<String, String>` en
  `data/Zone.kt` (id de zona a hex, solo zonas que lo necesitan). Vacía cuando todas tienen color.
- Nuevo `ZonesRepository.updateZoneColors(colors: Map<String, String>)` que escribe todo en un
  `batch`. El `AppViewModel` lo llama cuando el mapa no está vacío.
- Es una escritura en Firestore sobre datos compartidos de la casa. Es idempotente: el mismo
  cálculo en cada móvil da el mismo resultado, y en cuanto llega el snapshot con colores el
  mapa queda vacío y no vuelve a escribir.
- `resolvedZoneColor(zone, index)` se mantiene como respaldo para el instante anterior al
  relleno; el resto del tiempo todas las pantallas leen `zone.color`.

### Borde en las tarjetas de Almacén

- `ZoneCard` añade `border = BorderStroke(2.dp, color de la zona)` a su `Card`. El color es el
  mismo que ya usa el círculo de la letra (`colorHex`).
- La tarjeta «+ Nueva zona» no cambia.

## Parte C: tickets del historial

### Modelo de datos

- `Purchase` gana `ticketId: String? = null`. `savePurchaseBatch` guarda en todas las líneas de
  un escaneo el `batchId` que ya genera (el mismo UUID de la foto del ticket).
- Las compras antiguas tienen `ticketId = null`. Su clave de ticket es
  `legacy:<storeKey>:<milisegundos de la fecha>`: las líneas de un mismo escaneo comparten
  exactamente la misma `date`, así que las antiguas también forman tickets.
- Las reglas de Firestore ya permiten borrar bajo `households/{code}/**`; no se tocan.

### Lógica pura (`PurchaseAggregation.kt`)

- `ticketKey(purchase: Purchase): String`: `ticketId` o la clave `legacy:` anterior.
- `data class Ticket(key: String, storeKey: String, store: String, date: Date, purchases: List<Purchase>, total: Double, possibleDuplicate: Boolean)`.
  `store` vacío = sin supermercado; `purchases` ordenadas por `rawName` sin distinguir
  mayúsculas; `total` es la suma de `price`.
- `tickets(purchases: List<Purchase>): List<Ticket>`: agrupa por `ticketKey`, ordenados del más
  reciente al más antiguo (empate por clave).
- **Posible duplicado:** tickets con el mismo `storeKey`, el mismo total (en céntimos) y el
  mismo día (zona horaria local). Se marcan todos menos el más antiguo del grupo (empate por
  clave). Solo avisa, no borra.
- `UiState.purchaseTickets: List<Ticket>` junto a `purchaseSummaries` y `purchaseStoreSections`.

### Repositorio y ViewModel

- `PurchasesRepository.deletePurchases(ids: List<String>)`: borra en `batch`, en tandas de
  como mucho 500 (límite de Firestore). `restorePurchase(purchase: Purchase)`: vuelve a
  escribir la compra en `document(purchase.id)`.
- `AppViewModel`: `deleteTicket(ticketKey: String)` (busca los ids del ticket en el estado
  actual), `deletePurchase(purchaseId: String)` y `restorePurchase(purchase: Purchase)`.
  Los fallos se muestran en `state.error`, como el resto.

### Pantallas

- **Historial (`PurchaseHistoryScreen`)**: se queda igual y gana un botón «Tickets» en la barra
  superior que abre la lista de tickets.
- **Lista de tickets (`PurchaseTicketsScreen`, ruta `purchaseTickets`):**
  - Chips de filtro por supermercado («Todos» y los de `knownStores`).
  - Tickets agrupados por mes (más reciente primero), cada uno con supermercado (o «Sin
    supermercado»), fecha, número de líneas y total. Tocar un ticket abre su detalle.
  - Un ticket con `possibleDuplicate` muestra una marca «Posible duplicado» y un botón
    «Eliminar» que abre el diálogo de confirmación.
  - Estado vacío con un texto.
- **Detalle del ticket (`PurchaseTicketDetailScreen`, ruta `purchaseTicket/{ticketKey}`, la
  clave codificada con `Uri.encode`):**
  - Cabecera con supermercado, fecha, número de líneas y total.
  - Papelera en la barra superior: diálogo de confirmación («¿Eliminar este ticket?», con
    supermercado, fecha, total y «Se borrarán sus N líneas»); al confirmar borra el ticket y
    vuelve a la lista.
  - Cada línea muestra nombre, cantidad con unidad, precio unitario y total, con su papelera.
    Al pulsarla se borra la línea y sale un aviso «Línea eliminada» con «Deshacer», que la
    restaura.
  - Si el ticket se queda sin líneas (o su clave ya no existe), vuelve a la lista.

## Fuera de alcance

- Borrar la foto del ticket en Storage al borrar el ticket (la foto queda huérfana; limpiarla
  exigiría comprobar que ninguna otra compra la usa).
- «Ver el otro» en la marca de posible duplicado, y la selección múltiple de tickets.
- Borrar líneas desde el detalle de un producto.
- Cambios en la pantalla actual del historial más allá del botón «Tickets».
- Heredar colores de zona a subzona, o cambiar la paleta.
- Quitar el código de foto y de OpenFoodFacts del formulario (se conserva a propósito).

## Pruebas

- `ZoneTest`: `nextZoneColor` (zona vacía, color menos usado, empate por orden de paleta,
  mayúsculas del hex) y `zoneColorBackfill` (raíces por posición entre raíces, subzonas con su
  propio color en orden estable, zonas ya coloreadas intactas, mapa vacío si todas tienen color,
  subzona huérfana al final).
- `PurchaseAggregationTest`: `ticketKey` (con `ticketId` y `legacy`), `tickets` (agrupación,
  orden, total, líneas ordenadas, compras antiguas por supermercado y fecha) y la marca de
  duplicado (mismo súper, total y día; solo las copias posteriores; distinto día o total no marca).
- `UiStateTest`: `purchaseTickets` expuesto en el estado.
- UI (`AddItemSheet`, `ZoneCard`, `PurchaseHistoryScreen`, `PurchaseTicketsScreen`,
  `PurchaseTicketDetailScreen`): se comprueba que compila y se revisa en el dispositivo.
