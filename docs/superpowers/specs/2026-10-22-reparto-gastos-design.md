# Home y reparto de gastos por mes (estilo Tricount)

Fecha: 2026-10-22
Estado: spec cerrado, plan en `docs/superpowers/plans/2026-10-22-home-y-reparto.md`

## Objetivo

Dos cambios:

1. **Pantalla Home** con tres tarjetas que separan las tres áreas de la app: Almacén, Gastos y Reparto.
2. **Reparto de gastos por mes**: sección nueva para repartir gastos entre los miembros de la casa dentro de un mes y saber quién debe a quién. Replica el comportamiento de Tricount (crear grupo, alta de gasto, saldos, reembolsos, fotos). No copia nombre, logo, textos ni diseño: usa la paleta de SNHome.

El reparto es independiente de `purchases` (tickets) y del gasto mensual de la casa. No comparten datos ni pantallas.

## Home

### Tarjetas

| Tarjeta | Contenido | Destino |
|---|---|---|
| Almacén | Inventario por zonas, lista de la compra, menú semanal, búsqueda | `zonesDashboard` (la barra inferior actual) |
| Gastos | Importar tickets, historial, gasto por meses, estadísticas | `purchaseHistory` |
| Reparto | Meses con gastos repartidos, saldos, fotos | `splitMonths` |

Cada tarjeta lleva icono, título y una línea de resumen con datos que ya existen:

- Almacén: productos pendientes en la lista (`state.items` sin `done`).
- Gastos: total del mes actual (`monthSummary(state.purchases).total`), o "Sin tickets este mes".
- Reparto: número de meses creados, o "Sin meses todavía".

### Diseño elegido

Tres tarjetas a ancho completo apiladas, con la cabecera de saludo y fecha que ya usa `ZonesDashboardScreen`, y un icono de ajustes arriba a la derecha (`manageZones`, que ya contiene el nombre y la clave de Gemini).

- Superficie `NocturneSurface`, icono en círculo `NocturnePrimaryContainer` con tinte `primary`.
- El coral no se usa: sigue reservado para alertas y borrar.
- Iconos: `Inventory2` (Almacén), `Receipt` (Gastos), `Groups` (Reparto).

Alternativas descartadas: cuadrícula con una tarjeta grande y dos pequeñas (la jerarquía implica que Almacén importa más que el resto) y tarjetas con cifra grande y mini gráfico (necesita más datos por tarjeta y más trabajo).

### Cambios de navegación

- `startDestination` pasa de `zonesDashboard` a `home`.
- La barra inferior se queda solo en el área Almacén y gana un primer destino **Inicio** (`home`). Así hay cinco: Inicio, Almacén, Lista, Menú, Buscar.
- La barra no se muestra en `home`, `purchaseHistory` ni `splitMonths`. `showBottomBar` debe exigir `currentRoute != null`: con `home` como inicio, la ruta es `null` el primer fotograma y hoy eso muestra la barra.
- Las pestañas de la barra ya hacen `popUpTo(startDestination)`, así que con `home` de inicio el botón atrás desde cualquier pestaña vuelve a Home.
- `purchaseHistory` y sus rutas hijas no cambian. El icono de recibo de `ZonesDashboardScreen` se mantiene como atajo.

## Reparto: alcance

Incluye:
- Crear, renombrar y borrar meses.
- Alta, edición y borrado de gastos con reparto igual entre los seleccionados.
- Reembolsos como tipo de gasto.
- Pestañas Gastos, Saldos y Fotos.
- Saldos por miembro y pagos para saldar.
- Fotos del mes, sueltas o ligadas a un gasto.

Fuera de alcance (primera versión):
- Reparto desigual (por importes o por partes).
- Multidivisa, enlace de invitación, exportación, vínculo con bancos.
- Total gastado por mes en la lista de meses (exigiría una lectura por mes o un total desnormalizado).
- Captura de cargos con Gemini. Fase posterior: asignaría cada cargo al mes según su fecha, con pantalla de revisión y deduplicación.

## Participantes

Los participantes son los `members` de la casa (documento por uid anónimo, con nombre). `payerId` y `participants` guardan uids.

Limitación conocida: reinstalar la app crea un uid nuevo, y el miembro antiguo sigue en `members`. Al crear un mes se eligen los participantes y el usuario desmarca el duplicado. Un mes ya creado conserva sus `memberIds`.

## Modelo de datos (Firestore)

Todos los modelos van en `com.homepantry.app.data` (R8 conserva ese paquete por reflexión). `data class` con valores por defecto, `@DocumentId` para el id y `@ServerTimestamp` para las fechas de creación.

```
households/{code}/splitMonths/{monthId}
  name: String            // "Octubre 2026"
  monthKey: String        // "2026-10"
  memberIds: List<String> // participantes del mes (uids de members)
  createdAt: Timestamp

households/{code}/splitMonths/{monthId}/expenses/{expenseId}
  title: String
  amountCents: Long
  payerId: String
  participants: List<String>
  date: Timestamp         // dentro de monthKey, salvo en reembolsos
  reimbursement: Boolean = false
  createdAt: Timestamp

households/{code}/splitMonths/{monthId}/photos/{photoId}
  url: String             // URL de descarga, para pintarla con Coil
  storagePath: String     // para borrar el objeto de Storage
  note: String
  expenseId: String?      // opcional
  createdAt: Timestamp
```

El campo booleano se llama `reimbursement`, no `isReimbursement`: Firestore quita el prefijo `is` al mapear propiedades Kotlin y el campo guardado no coincidiría con el leído.

Storage: `households/{code}/splitMonths/{monthId}/{photoId}.jpg`.

Reglas: `firestore.rules` y `storage.rules` ya cubren `households/{code}/{document=**}`, así que las subcolecciones nuevas no requieren cambios.

### Reembolso

Se guarda como gasto con `reimbursement = true`, `payerId` = quien paga y `participants` = [receptor]. El cálculo de saldos lo trata igual que un gasto normal (el pagador suma lo pagado y el receptor suma su parte), así que no necesita ramas especiales. Solo se excluye del total del mes. No se valida que su fecha caiga dentro del mes.

## Lógica pura (`data/SplitLogic.kt`, sin dependencias de Android)

- `splitEqually(amountCents, participants): Map<String, Long>`
  - Reparto en céntimos entre los participantes (sin duplicados). Los sobrantes se asignan de uno en uno siguiendo el orden alfabético de uid, para que el resultado sea determinista, no dependa del orden de la lista y sume exactamente `amountCents`.
- `computeBalances(expenses, memberIds): List<MemberBalance>`
  - `MemberBalance(memberId, paidCents, shareCents)` con `netCents = paid - share`. Positivo: le deben. Negativo: debe.
  - Incluye los miembros del mes y cualquier uid que aparezca en un gasto (ex-miembros), para que la suma de saldos sea siempre 0.
  - Ignora gastos sin participantes o con importe no positivo.
  - Orden: saldo descendente, desempate por uid.
- `computeSettlements(balances): List<Settlement>`
  - `Settlement(fromId, toId, amountCents)`.
  - Algoritmo voraz: empareja el mayor deudor con el mayor acreedor hasta dejar todo a 0. Da como mucho n-1 pagos. No siempre es el mínimo absoluto (ese problema es NP-difícil), pero basta para un grupo doméstico.
- `monthTotalCents(expenses): Long`: suma de gastos que no son reembolso.
- `monthKeyOf(date, timeZone)` y `isDateInMonth(date, monthKey, timeZone)`: zona horaria por defecto del dispositivo, inyectable para los tests.
- `parseCents(text): Long?`: acepta `12`, `12,5`, `12.50`; rechaza vacío, negativos, cero, más de dos decimales, separador de miles y más de 999.999,99.

Importes siempre en `Long` (céntimos). Conversión a texto solo en la UI, con formato es-ES.

## Repositorios

Reciben `(FirebaseFirestore, householdCode)`. El `monthId` va en cada llamada, no en el constructor, para poder cambiar de mes con `flatMapLatest`.

- `SplitMonthsRepository`: flujo de meses, crear, renombrar, borrar. Borrar un mes elimina sus `expenses` y `photos` en batches troceados y los objetos de Storage de sus fotos.
- `SplitExpensesRepository`: flujo de gastos de un mes, alta, edición con `update` de los campos editables, borrado.
- `SplitPhotosRepository`: flujo de fotos de un mes, alta y borrado del documento.
- `StorageRepository` gana `uploadSplitPhoto(monthId, photoId, uri)` (reusa la compresión) y `deleteSplitPhoto(path)` (ignora "objeto no existe").

Lecturas con `callbackFlow` sobre `addSnapshotListener`. Escrituras `suspend` con `.await()`. `CancellationException` se relanza siempre.

## ViewModel

`SplitViewModel` con un único `StateFlow<SplitUiState>`: meses, miembros, mes abierto, gastos, fotos y errores. Observa `MembersRepository` por su cuenta (como `MenuViewModel` lee otros repositorios). Gastos y fotos se observan para el mes abierto con `flatMapLatest`. Saldos, pagos sugeridos y total del mes se derivan con funciones de `SplitLogic`. Construcción manual con `ViewModelProvider.Factory` y clave `split-$code`.

Validación al guardar un gasto: título no vacío, importe con `parseCents`, al menos un participante, pagador entre los miembros del mes y fecha dentro del mes (salvo reembolsos).

## Navegación y pantallas

Rutas nuevas: `home`, `splitMonths` y `splitMonth/{monthId}`. El alta y edición de gasto es un bottom sheet con estado local, como `AddItemSheet`.

- **Lista de meses:** ordenados por `monthKey` descendente, con nombre y participantes. Botón para crear mes (nombre por defecto "Mes Año", mes y participantes; por defecto todos los miembros). Menú por mes: renombrar y borrar (con confirmación).
- **Detalle del mes**, tres pestañas:
  - **Gastos:** lista con título, pagador, importe y participantes. Los reembolsos van marcados. Alta y edición en bottom sheet: título, importe, pagador, "repartido entre" (todos por defecto), fecha. Total del mes arriba.
  - **Saldos:** por miembro, pagado, su parte y saldo neto, ordenado por saldo (así se ve quién gasta más y menos). Debajo, "quién debe a quién" con botón Saldar, que crea el reembolso.
  - **Fotos:** galería con subida desde cámara o galería (mismos lanzadores que `PurchaseHistoryScreen`), nota opcional y enlace opcional a un gasto.

Colores: positivo con `primary`, deuda y borrado con coral.

## Textos

Todo en `res/values/strings.xml`, con `plurals` donde corresponda. Comentarios y KDoc en español explicando el porqué.

## Tests (`app/src/test`, JUnit 4)

`SplitLogicTest`:
- La suma de saldos es 0 en cualquier combinación.
- Reparto con céntimos sobrantes, independiente del orden de la lista.
- Participantes parciales y gastos sin participantes.
- Reembolso que deja un saldo a 0.
- Ex-miembros que aparecen en gastos.
- Pagos: casos de 2 y 3 acreedores/deudores, aplicar los pagos deja todo a 0 y nunca hay más de n-1.
- Mes sin gastos.
- Mes por zona horaria y validación de fecha.
- `parseCents` con entradas válidas e inválidas.

## Cambios tras la primera prueba (2026-10-22)

- La sección se llama **SNcount** (antes «Reparto»).
- Un grupo puede ser un **mes** o una **actividad** (viaje, cena…). La actividad se guarda en `splitMonths` con `activity = true` y `monthKey` = mes de creación (solo para ordenar); sus gastos admiten cualquier fecha.
- La página de cada grupo sigue la referencia de Tricount: cabecera con icono y nombre, pestañas segmentadas, «Mis gastos» y «Total gastos», gastos agrupados por día con el icono de su tipo y «Pagado por X», y botón «+» centrado abajo.
- El icono de cada gasto se deduce del concepto con `expenseCategoryOf` (`data/ExpenseCategory.kt`, por palabras clave); no se guarda.
- Almacén pierde los iconos de Gastos y Ajustes. Regla de navegación: barra inferior solo en el área Almacén; el resto sin barra y con flecha atrás.

## Fases

Ver el plan. Resumen: lógica con tests, repositorios, ViewModel, lista de meses con detalle vacío, Home y navegación, pestañas Gastos, Saldos y Fotos, verificación completa y prueba del APK release en el S25. Captura con Gemini queda como fase posterior.
