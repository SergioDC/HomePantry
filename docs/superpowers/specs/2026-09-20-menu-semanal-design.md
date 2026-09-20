# Menú semanal: calendario, platos y sugerencias de compra

**Fecha:** 2026-09-20
**Estado:** Aprobado en brainstorming, pendiente de revisión del spec y plan de implementación

## Contexto y objetivo

La casa quiere planificar lo que se come. Se añade una 4ª pestaña, **Menú**, con un calendario
al estilo Google Calendar donde se ve el menú de días pasados, presentes y futuros. Cada día
tiene tres franjas (desayuno, comida y cena). Los platos se crean en una lista propia, con sus
ingredientes; al crear un plato se comprueba si esos ingredientes están en alguna zona y, si no,
se sugiere añadirlos a la lista de la compra. El menú se puede editar, borrar, duplicar a otra
semana y compartir como imagen.

Como el resto de la app, el menú es compartido por toda la casa y se sincroniza en tiempo real
por Firestore (`households/{code}/...`).

### Decisiones tomadas en el brainstorming

| Tema | Decisión |
|---|---|
| Ingredientes | Lista de texto libre por plato (nombre, cantidad y unidad opcionales), comparada por nombre normalizado con los productos de las zonas. |
| Calendario | Vista semana por defecto, con selector para pasar a vista mes. |
| Navegación entre semanas | Deslizar en horizontal (semana a semana, o mes a mes en la vista mes); scroll vertical dentro de cada semana. Flechas y «Hoy» como alternativa. |
| Duplicar semana | Si el destino está vacío copia directamente; si tiene platos, pregunta entre reemplazar y combinar. |
| Zona de lo que falta | Una hoja tras guardar el plato, con selector de zona por ingrediente (por defecto «Otros»). |
| Almacenamiento | Un documento por entrada de menú, no por día ni por semana. |

## Modelo de datos

```
households/{code}/dishes/{dishId}
households/{code}/mealEntries/{entryId}
```

Las reglas actuales (`match /households/{householdCode}/{document=**}`) ya cubren las dos
colecciones; no hay que tocar `firestore.rules`.

```kotlin
data class Ingredient(
    val name: String = "",
    val qty: Double? = null,
    val unit: String? = null      // nombre de data.Unit, como Item.unit
)

data class Dish(
    @DocumentId val id: String = "",
    val name: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val note: String? = null,
    val addedBy: String? = null,
    @ServerTimestamp val addedAt: Date? = null
)

enum class MealSlot { BREAKFAST, LUNCH, DINNER }

data class MealEntry(
    @DocumentId val id: String = "",
    val date: String = "",        // "yyyy-MM-dd" (ISO), zona horaria local
    val slot: String = MealSlot.LUNCH.name,
    val dishId: String? = null,   // null = texto libre
    val name: String = "",        // copia del nombre del plato en el momento de añadirlo
    val order: Int = 0,           // orden dentro de la franja
    val addedBy: String? = null
)
```

Ambos tienen constructor sin argumentos para el deserializador de Firestore, como `Item`.

**Por qué un documento por entrada.** Es el patrón que ya usan `Item` y `Purchase`. Dos personas
editando el mismo día no se pisan, editar y borrar una entrada es un solo documento, y duplicar
una semana es una escritura en lote. La fecha se guarda como texto ISO para que una semana sea
una consulta por rango sobre un único campo (`date >= lunes` y `date <= domingo`), que no
necesita índice compuesto. El orden por franja y `order` se aplica en el cliente.

**El nombre se copia en la entrada.** Si se borra o renombra un plato, el menú ya creado no se
rompe: la entrada conserva su `name` y pasa a comportarse como texto libre (`dishId` que ya no
existe se trata como null al mostrar). Al borrar un plato se avisa de cuántas entradas lo usan.

**Semanas.** Empiezan en lunes. Se usa `java.time` (`minSdk = 26`, sin desugaring).

## Lógica pura (con tests unitarios)

### `MenuCalendar.kt`

- `weekStart(date: LocalDate): LocalDate` → el lunes de esa semana.
- `weekDays(start: LocalDate): List<LocalDate>` → los 7 días.
- `monthGrid(month: YearMonth): List<LocalDate>` → semanas completas (lunes a domingo) que cubren
  el mes, con los días de relleno de los meses vecinos.
- `pageWeekStart(anchor: LocalDate, page: Int, anchorPage: Int): LocalDate` y
  `pageMonth(anchor: YearMonth, page: Int, anchorPage: Int): YearMonth` → la semana o el mes que
  corresponde a una página del pager, con la página de «hoy» como ancla (`anchorPage`).
- `shiftEntries(entries, fromWeekStart, toWeekStart): List<MealEntry>` → copias sin `id` con la
  fecha desplazada, manteniendo día de la semana, franja y orden.

### `IngredientCheck.kt`

```kotlin
fun missingIngredients(dish: Dish, items: List<Item>): List<Ingredient>
```

Para cada ingrediente compara `normalizeProductName(ingredient.name)` con el de cada `Item`:

- Coincide con un `Item` `done = true` (lo tienes): no se sugiere.
- Coincide con un `Item` `done = false` (ya está en la lista de la compra): no se sugiere,
  para no duplicar.
- Sin coincidencia: se sugiere.
- Ingredientes repetidos en el mismo plato (tras normalizar) se devuelven una sola vez.
- Un ingrediente con nombre en blanco se ignora.

La coincidencia es exacta tras normalizar (minúsculas, sin acentos, sin «s» final). No hay
coincidencia parcial: «tomate» no casa con «tomate frito». Si esto da falsos negativos en el uso
real, se revisa en una segunda iteración.

### Duplicar semana

Funciones puras que reciben las entradas de origen y las del destino y devuelven qué escribir y
qué borrar:

- **Destino vacío o combinar:** se escriben las copias de `shiftEntries`; no se borra nada. En
  combinar, el `order` de las copias se desplaza para ir detrás de lo que ya hay en esa franja.
- **Reemplazar:** se borran las entradas de la semana destino y se escriben las copias.

La escritura real va en lotes de Firestore, en trozos de como mucho 500 operaciones.

## Repositorios

Mismo estilo que `ItemsRepository` (`callbackFlow` con `addSnapshotListener`, `await()`).

- `DishesRepository`: `observeDishes()`, `addDish(dish): String`, `updateDish(dish)`,
  `deleteDish(id)`.
- `MealEntriesRepository`:
  - `observeRange(from: String, to: String)`: consulta por rango de `date`.
  - `getRange(from, to)`: lectura única, para duplicar.
  - `addEntry`, `updateEntry`, `deleteEntry`.
  - `applyBatch(toWrite, toDelete)`: escritura en lote para duplicar.

`observeRange` escucha el rango de la página visible **más la anterior y la siguiente** (tres
semanas, o tres meses con relleno en la vista mes), para que al deslizar la página vecina ya
esté cargada y no aparezca vacía un instante. Al cambiar de página se cancela la suscripción y
se abre una nueva con el rango desplazado; Firestore sirve de su caché local lo que ya había
leído. Así la colección puede crecer sin que la app cargue todo el histórico.

## ViewModel

Se crea `MenuViewModel`, aparte de `AppViewModel` (ya tiene unas 320 líneas y cinco
repositorios). Recibe `DishesRepository`, `MealEntriesRepository`, `ItemsRepository` (para añadir
lo que falta a la lista de la compra) y el nombre de usuario. Los `Item` y las zonas ya cargados
los toma del `state` de `AppViewModel`, que se le pasa como parámetro de los métodos que los
necesitan (`missingIngredients`, zonas del selector), para no cargarlos dos veces.

Estado: platos, entradas del rango visible, fecha de referencia, modo (semana o mes) y `error`,
que se muestra como en el resto de la app.

## Pantallas

### Navegación

4ª entrada en `BottomNavBar`: «Menú», icono `RestaurantMenu`, ruta `menu`, añadida a
`BOTTOM_NAV_ROUTES` de `MainActivity.kt`. Dentro, un `TabRow` con dos pestañas: **Menú** y
**Platos**.

### Pestaña Menú

- Cabecera: rango de la semana («21–27 sep»), flechas anterior y siguiente, botón «Hoy», un
  `SegmentedToggle` Semana/Mes y un menú ⋮ con «Compartir semana» y «Duplicar semana».
- **Navegación:** el contenido es un `HorizontalPager` (de `compose.foundation`, ya incluido en
  el BOM del proyecto, sin dependencias nuevas). Deslizar en horizontal pasa a la semana
  anterior o siguiente (o al mes anterior o siguiente en la vista mes), y las flechas y «Hoy»
  hacen lo mismo con animación. Las páginas son relativas a la semana actual y el pager se
  sitúa en la de «hoy»; se puede ir tan lejos al pasado o al futuro como se quiera, sin límite
  fijo. La cabecera se actualiza con la página visible.
- **Semana:** 7 filas (lunes a domingo). Cada una muestra el día, resaltado si es hoy, y 3 líneas
  (desayuno, comida, cena) con sus platos en chips. Como los 7 días con sus franjas pueden no
  caber en pantalla, cada página hace **scroll vertical** por su cuenta.
- **Mes:** cuadrícula de lunes a domingo con un punto en los días que tienen algo; se desliza
  entre meses con el mismo pager. Pulsar un día cambia a la vista semana de esa semana.
- Al cambiar entre Semana y Mes se conserva la fecha de referencia (la semana visible pasa al mes
  que la contiene, y el mes pasa a su primera semana).
- Semana sin entradas: texto «Aún no hay menú» con un botón para empezar.

### Hoja del día

Al pulsar un día se abre `DayMealsSheet` con 3 pestañas: Desayuno, Comida y Cena.

- Cada pestaña lista sus platos con editar y borrar. Borrar ofrece deshacer, como en compras.
- «Añadir» abre un campo de texto con sugerencias de los platos creados que coinciden con lo
  escrito. Si se elige una sugerencia se guarda con `dishId`; si no, como texto libre.
- Editar permite cambiar el texto o elegir otro plato.

### Pestaña Platos

- Lista de platos con buscador y un FAB para crear uno.
- `DishFormSheet`: nombre, filas de ingredientes (nombre, cantidad y unidad opcionales, con botón
  «+ ingrediente») y nota. También sirve para editar.
- Borrar un plato pide confirmación y dice cuántas entradas del menú lo usan; esas entradas se
  quedan como texto libre.

### Ingredientes que faltan

Al guardar un plato (crear o editar) se calcula `missingIngredients`. Si hay alguno se abre
`MissingIngredientsSheet` («Te faltan estos ingredientes»): todos marcados, cada uno con un
selector de zona (por defecto «Otros»; si no existiese, la primera zona). «Añadir a la lista»
crea los marcados como `Item` pendientes (`done = false`) con `addedBy` y cantidad/unidad del
ingrediente si las tiene (si no, 1 unidad). «Ahora no» descarta la sugerencia. Con la
comprobación en el plato no se repite al ponerlo en el menú.

### Duplicar semana

`DuplicateWeekDialog`: selector de semana destino (por defecto la siguiente a la que se ve).
Si el destino está vacío copia directamente; si tiene platos, pregunta entre **Reemplazar** y
**Combinar** (ver «Duplicar semana» en Lógica pura). Duplicar sobre la propia semana de origen
no se permite.

### Compartir

La imagen es una tarjeta clara pensada para captura, no la pantalla tal cual: marca SNHome,
rango de fechas y los 7 días con sus 3 franjas. Se dibuja a `Bitmap` con `android.graphics.Canvas`
y `StaticLayout` (en `WeekShare.kt`, sin un composable ni montar Compose fuera de pantalla, así
el tamaño es fijo y no depende del tema), se guarda como PNG en `cacheDir` y se envía con
`Intent.ACTION_SEND` mediante el `FileProvider` que ya existe (`file_paths.xml` declara
`cache-path path="."`, así que no hay que tocar el manifest). Las franjas sin platos se
muestran como «—».

## Estructura de archivos

**Datos** (`app/src/main/kotlin/com/homepantry/app/data/`): `Dish.kt`, `MealEntry.kt`,
`DishesRepository.kt`, `MealEntriesRepository.kt`, `MenuCalendar.kt`, `IngredientCheck.kt`,
`MealEntriesLogic.kt` (duplicado, orden y coincidencia de platos), `WeekShare.kt` (render a
bitmap y envío).

**UI** (`ui/`): `screens/MenuScreen.kt`, `MenuCalendarTab.kt`, `DayMealsSheet.kt`,
`DishesTab.kt`, `DishFormSheet.kt`, `MissingIngredientsSheet.kt`, `DuplicateWeekDialog.kt`;
`MenuViewModel.kt`.

**Existentes que cambian:** `MainActivity.kt` (4ª pestaña, ruta `menu`, repositorios y
`MenuViewModel`) y `res/values/strings.xml` (textos nuevos).

## Tests

JUnit 4, sin dependencias nuevas, en `app/src/test/kotlin/com/homepantry/app/data/`:

- `MenuCalendarTest`: semana de lunes a domingo, cambio de mes y de año, rejilla del mes,
  `pageWeekStart` y `pageMonth` (página de «hoy», anteriores y posteriores, cruzando fin de año)
  y `shiftEntries` (día de la semana, franja y orden se conservan).
- `IngredientCheckTest`: no sugiere lo que tienes, no sugiere lo ya pendiente, coincide sin
  distinguir mayúsculas, acentos ni plural simple, no repite ingredientes y ignora nombres en
  blanco.
- `MealEntriesLogicTest`: duplicar con destino vacío, combinar (el `order` se desplaza) y
  reemplazar (qué se borra y qué se escribe), y que una entrada cuyo plato ya no existe se trate
  como texto libre.

La UI de Compose no lleva tests, como el resto de pantallas del proyecto; se comprueba con
`./gradlew assembleDebug` y a mano en el móvil.

## Fuera del alcance de la primera versión

- Notificaciones o recordatorios del menú.
- Recetas con pasos, calorías o fotos de platos.
- Arrastrar y soltar entradas entre días.
- Autocompletado de ingredientes con los productos existentes.
- Coincidencia parcial de ingredientes («tomate» con «tomate frito»).
- Comprobar ingredientes al poner un plato en el menú (solo se comprueba al crear o editar el
  plato).
