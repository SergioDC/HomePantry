# Estadísticas de gasto — diseño

Fecha: 2026-09-30

## Objetivo

Una pantalla de estadísticas que resume las compras ya registradas (tickets) para tres
usos: controlar cuánto se gasta, vigilar precios y saber qué se compra más. Solo números
y barras dibujadas con Compose; sin librería de gráficos (no engordar el APK).

## Alcance

Incluido:
- Pantalla `PurchaseStatsScreen`, ruta `purchaseStats`, abierta desde un botón
  "Estadísticas" en la cabecera de `PurchaseHistoryScreen`.
- Funciones puras de cálculo en `data/PurchaseStats.kt`, con tests unitarios.

Fuera de alcance:
- Gasto por zona o categoría (`Purchase` no guarda esa información).
- Exportar, rangos de fechas libres y gráficos interactivos.
- Cambios en el modelo `Purchase` o en Firestore.

## Datos de partida

`Purchase` (ya existente): `normalizedName`, `rawName`, `price` (total de la línea), `date`,
`quantity`, `unit` (UD, KG, L), `store` (nullable), `ticketId` (nullable). Se reutilizan
`monthlySpend`, `productSummaries`, `tickets`, `ticketKey`, `storeKey` y `Purchase.unitPrice()`
de `PurchaseAggregation.kt`.

## Secciones de la pantalla (de arriba abajo)

1. **Este mes**: gasto total, variación frente al mes anterior (€ y %), número de tickets y
   gasto medio por ticket. Sin mes anterior con datos: no se muestra la variación.
2. **Últimos 6 meses**: una barra por mes con su total; el mes actual resaltado. Los meses
   sin compras dentro del rango cuentan como 0.
3. **Por supermercado**: barras horizontales con el gasto del mes actual y su porcentaje.
   Las compras sin `store` se agrupan como "Sin supermercado".
4. **Productos que más compras**: top 5, con selector entre "por gasto" y "por veces
   comprado". Una "vez" es un ticket distinto que contiene el producto.
5. **Precios**:
   - Los 5 productos que más han subido de precio unitario (primera compra frente a la
     última, mínimo 2 compras con la misma unidad y subida > 0).
   - Para productos con compras en 2 o más supermercados (misma unidad), el supermercado
     con menor precio unitario medio y el ahorro frente al más caro.
   - Tocar un producto abre `purchaseDetail/{normalizedName}` (ya existe).

## Arquitectura

`data/PurchaseStats.kt` (sin dependencias de Android ni Firestore), todas con parámetro
`referenceDate: Date = Date()` para poder testear:

- `monthSummary(purchases, referenceDate): MonthSummary` — total, total del mes anterior
  (nullable), nº de tickets, media por ticket.
- `lastMonthsSpend(purchases, months = 6, referenceDate): List<MonthlySpend>` — orden
  cronológico, huecos rellenos con 0.
- `spendByStore(purchases, referenceDate): List<StoreSpend>` — mes actual, orden por gasto
  descendente, con porcentaje.
- `topProducts(purchases, by: TopBy, limit = 5): List<TopProduct>` — `TopBy` = SPEND | COUNT.
- `priceRises(purchases, limit = 5): List<PriceRise>` y
  `cheapestStores(purchases, limit = 5): List<StoreComparison>`.

`ui/screens/PurchaseStatsScreen.kt` solo pinta estas data classes. Toma las compras del mismo
`viewModel` que el historial. Las barras son `Box` con `fillMaxWidth(fraction)`, con colores
del tema Nocturne. `MainActivity.kt` añade la ruta `purchaseStats` y `PurchaseHistoryScreen`
recibe un callback `onOpenStats`.

## Casos límite

- Sin compras: estado vacío global con texto explicativo.
- Menos de 2 meses de datos: sin variación ni subidas de precio.
- Precio unitario con `quantity <= 0`: ya lo gestiona `unitPrice()` (divide entre 1).
- Unidades distintas del mismo producto: no se comparan precios entre sí.
- Importes con dos decimales y coma decimal, formato es-ES.

## Tests

JUnit sobre `PurchaseStats.kt` con compras fabricadas y `referenceDate` fija: mes sin
datos, mes anterior ausente, relleno de huecos, compras sin supermercado, empate en el top,
unidades mezcladas y un solo supermercado. La pantalla se verifica compilando
(`assembleDebug`) y a mano en el dispositivo.
