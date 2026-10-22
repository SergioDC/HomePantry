# Reparto de gastos por mes (estilo Tricount)

Fecha: 2026-10-22
Estado: pendiente de plan

## Objetivo

Sección nueva en SNHome para repartir gastos entre los miembros de la casa dentro de un mes y saber quién debe a quién. Replica el comportamiento de Tricount (crear grupo, alta de gasto, saldos, reembolsos, fotos). No copia nombre, logo, textos ni diseño: usa la paleta de SNHome.

Es independiente de `purchases` (tickets) y del gasto mensual de la casa. No comparten datos ni pantallas.

## Alcance

Incluye:
- Crear, renombrar y borrar meses.
- Alta, edición y borrado de gastos con reparto igual entre los seleccionados.
- Reembolsos como tipo de gasto.
- Pestañas Gastos, Saldos y Fotos.
- Saldos por miembro y pagos mínimos para saldar.
- Fotos del mes, sueltas o ligadas a un gasto.

Fuera de alcance (primera versión):
- Reparto desigual (por importes o por partes).
- Multidivisa, enlace de invitación, exportación, vínculo con bancos.
- Captura de cargos con Gemini. Queda como fase posterior: asignaría cada cargo al mes según su fecha, con pantalla de revisión y deduplicación.

## Modelo de datos (Firestore)

Todos los modelos van en `com.homepantry.app.data` (R8 conserva ese paquete por reflexión). `data class` con valores por defecto, `@DocumentId` para el id y `@ServerTimestamp` para las fechas de creación.

```
households/{code}/splitMonths/{monthId}
  name: String            // "Octubre 2026"
  monthKey: String        // "2026-10"
  memberIds: List<String> // participantes del mes (ids de members)
  createdAt: Timestamp

households/{code}/splitMonths/{monthId}/expenses/{expenseId}
  title: String
  amountCents: Long
  payerId: String
  participants: List<String>
  date: Timestamp         // dentro de monthKey
  isReimbursement: Boolean = false
  createdAt: Timestamp

households/{code}/splitMonths/{monthId}/photos/{photoId}
  storagePath: String
  note: String
  expenseId: String?      // opcional
  createdAt: Timestamp
```

Storage: `households/{code}/splitMonths/{monthId}/{photoId}.jpg`.

Reglas: comprobar que `firestore.rules` y `storage.rules` cubren estas subcolecciones bajo `households/{code}/`. Si no, añadirlas con el mismo criterio que el resto (usuario autenticado que conoce el código).

### Reembolso

Se guarda como gasto con `isReimbursement = true`, `payerId` = quien paga y `participants` = [receptor]. Ajusta saldos y no cuenta como gasto del mes.

## Lógica pura (`data/SplitLogic.kt`, sin dependencias de Android)

- `computeBalances(expenses, memberIds): List<MemberBalance>`
  - `MemberBalance(memberId, paidCents, shareCents, netCents)`.
  - `net = paid - share`. Positivo: le deben. Negativo: debe.
  - La suma de `net` es siempre 0.
- `splitEqually(amountCents, participants): Map<String, Long>`
  - Reparto en céntimos. Los sobrantes se asignan de uno en uno siguiendo el orden fijo de `memberIds` del mes, para que el resultado sea determinista y sume exactamente `amountCents`.
- `computeSettlements(balances): List<Settlement>`
  - `Settlement(fromId, toId, amountCents)`.
  - Algoritmo voraz: empareja el mayor deudor con el mayor acreedor hasta dejar todo a 0.
- `monthKeyOf(date)` y `isDateInMonth(date, monthKey)` para validar la fecha del gasto.

Importes siempre en `Long` (céntimos). Conversión a texto solo en la UI.

## Repositorios

- `SplitMonthsRepository(firestore, householdCode)`: flujo de meses, crear, renombrar, borrar. Borrar un mes elimina sus `expenses` y `photos` en batches troceados y sus fotos en Storage.
- `SplitExpensesRepository(firestore, householdCode, monthId)`: flujo de gastos, alta, edición con `update` de campos, borrado.
- `SplitPhotosRepository(firestore, storage, householdCode, monthId)`: flujo de fotos, subida, borrado.

Lecturas con `callbackFlow` sobre `addSnapshotListener`. Escrituras `suspend` con `.await()`. `CancellationException` se relanza siempre.

## ViewModel

`SplitViewModel` con un único `StateFlow<UiState>`: meses, gastos del mes abierto, saldos, pagos sugeridos y fotos. Las vistas derivadas se calculan con funciones de `SplitLogic`. Construcción manual con `ViewModelProvider.Factory`, como los demás.

## Navegación y pantallas

Rutas nuevas en el `NavHost`: `splitMonths` y `splitMonth/{monthId}`. El alta de gasto es un bottom sheet con estado local en `ListaDeLaCasaApp`.

- **Lista de meses:** meses ordenados por `monthKey` descendente, con el total gastado. Botón para crear mes (nombre, mes, participantes; por defecto todos los miembros).
- **Detalle del mes**, tres pestañas:
  - **Gastos:** lista con título, pagador, importe y participantes. Reembolsos marcados aparte. Alta y edición en bottom sheet: título, importe, pagador, "repartido entre" (todos por defecto), fecha (validada dentro del mes).
  - **Saldos:** por miembro, pagado, su parte y saldo neto, ordenado por saldo. Debajo, "quién debe a quién" con el botón Saldar, que crea el reembolso correspondiente.
  - **Fotos:** galería con subida desde cámara o galería, nota opcional y enlace opcional a un gasto.

Paleta de `ui/theme/Color.kt`. El coral se usa para deudas y borrado; el saldo a favor usa el color de acento positivo ya existente.

## Textos

Todo en `res/values/strings.xml`, con `plurals` donde corresponda. Comentarios y KDoc en español explicando el porqué.

## Tests (`app/src/test`, JUnit 4)

`SplitLogicTest`:
- La suma de saldos es 0 en cualquier combinación.
- Reparto con céntimos sobrantes (100 entre 3, 1 entre 2, importes grandes).
- Participantes parciales (no todos pagan su parte).
- Reembolso que deja dos saldos a 0.
- Mes sin gastos.
- Pagos mínimos: casos de 2, 3 y 5 miembros.
- Fecha dentro y fuera del mes.

## Fases

1. Modelos y `SplitLogic` con tests.
2. Repositorios y `SplitViewModel`.
3. Lista de meses y detalle con pestaña Gastos y alta de gasto.
4. Pestaña Saldos y reembolsos.
5. Pestaña Fotos.
6. Verificación completa: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease` y prueba del APK release en el S25.
7. (Posterior) Captura de cargos con Gemini.
