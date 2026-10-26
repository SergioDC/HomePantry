# SNcount: pantalla de gasto, reparto por importes y opciones del grupo

Fecha: 2026-10-23. Continúa `2026-10-22-reparto-gastos-design.md` (Home + SNcount, mergeado en `a1ae327`).
Bocetos validados con el usuario: `.superpowers/brainstorm/547-1792750326/content/pantallas.html`.

## Por qué

SNcount ya funciona, pero el alta de gastos es una hoja modal que se queda corta. El usuario quiere que se parezca más a Tricount: una pantalla completa con emoji y la posibilidad de repartir por importes. También quiere gestionar cada grupo (mes o actividad) desde su propia pantalla: editarlo, compartirlo con la casa, archivarlo cuando ya no interesa y borrarlo.

## Objetivos

1. **Pantalla «Nuevo gasto» / «Editar gasto»** en lugar de `SplitExpenseSheet`, con el estilo del resto de la app.
2. **Emoji por gasto.** Por defecto se muestra el icono automático de ahora, según el concepto, y se puede cambiar por un emoji.
3. **Reparto igual o por importes.** Un interruptor «Dividir a partes iguales». Apagado, cada participante tiene su importe, y la suma tiene que cuadrar con el total.
4. **Menú ⋮ en la pantalla del SNcount** con Editar, Compartir, Archivar y Eliminar.
5. **Editar un SNcount:** el nombre (o el mes), su emoji y añadir participantes.
6. **Compartir:** un mensaje con un enlace que, en el móvil de alguien de casa, abre SNHome en ese SNcount.
7. **Archivar:** el grupo sale de la lista principal y pasa a «SNcounts archivados».

## No objetivos

- Repartir por partes o proporciones (el «x2» de Tricount). Solo hay reparto igual o por importes.
- Quitar participantes de un SNcount (descuadraría saldos de gastos ya hechos).
- Invitar a gente de fuera de la casa o instalar la app desde el enlace.
- Cambiar la hoja de crear SNcount (`SplitMonthFormSheet`) más allá de lo necesario.
- Rediseñar las pestañas Saldos y Fotos.

## Anti-objetivos (sería un fallo aunque funcione)

- Que los saldos no sumen 0, o que un gasto por importes se pueda guardar sin cuadrar.
- Que los gastos ya guardados (sin importes) cambien de reparto tras la actualización.
- Que la pantalla nueva desentone: tiene que usar el tema Nocturne y las piezas de Gastos/SNcount (`SpendSectionTitle`, `SpendHeaderCard`, `IconTile`, `MemberAvatar`, `ColorDot`, colores de `memberColors`).
- Que un mes archivado deje de poder consultarse o desarchivarse.

## Restricciones

- Las de `CLAUDE.md`: sin barra inferior en SNcount (flecha atrás), textos en `strings.xml` con `plurals`, lógica pura en `SplitLogic.kt` con tests JUnit, `update(campo, valor)` para cambios de un solo campo, relanzar `CancellationException`, modelos con valores por defecto, importes en céntimos (`Long`).
- **Compatibilidad entre móviles:** un móvil con la versión anterior ignora el campo nuevo `shares` y reparte ese gasto a partes iguales, así que sus saldos no coincidirían. Hay que actualizar todos los móviles de casa antes de usar el reparto por importes. Basta con decírselo al usuario al entregar; no hace falta bloquear nada en código.
- La app se instala por sideload y la release se firma con la clave debug de este equipo. La verificación de App Links depende de esa huella SHA-256.

## Lo que decidió el usuario

### Pantalla de gasto (boceto 1A/1B)

- Pantalla completa, no hoja modal. Barra superior con ← , el título «Nuevo gasto» o «Editar gasto» y la acción **Guardar** a la derecha.
- Orden de arriba abajo: **concepto con el icono a su derecha** (el usuario lo pidió explícitamente), importe grande, «Pagado por» (chips de miembro como ahora), fecha (tarjeta con calendario como ahora) y, **abajo del todo, la sección «Reparto»**.
- Reparto con el interruptor «Dividir a partes iguales» encendido: una fila por participante posible, con casilla, avatar, nombre y su parte calculada (`splitEqually`), en solo lectura.
- Reparto con el interruptor apagado: una fila por participante posible, con avatar, nombre y un campo de importe. Debajo, en vivo, «Faltan X € para cuadrar con Y €» o «Sobran X €» (coral), u «Cuadra» cuando la suma coincide. No se puede guardar sin cuadrar.
- Icono: al pulsarlo se abre una hoja pequeña con «Automático (según el concepto)» y una cuadrícula de emojis. El emoji elegido sustituye al icono automático en la pantalla y en la lista de gastos. Si se elige «Automático», vuelve el icono por categoría.

### Pantalla del SNcount (boceto 2)

- Icono ⋮ en la barra superior con **Editar, Compartir, Archivar y Eliminar** (este último en coral). En un SNcount archivado, «Archivar» pasa a ser «Desarchivar».
- La cabecera grande muestra el emoji del SNcount si tiene; si no, el icono actual (`SplitMonth.icon()`).
- La lista de gastos muestra el emoji de cada gasto si lo tiene.

### Editar SNcount (boceto 2, centro)

- Pantalla completa con la misma barra (← «Editar SNcount» Guardar).
- Campo de nombre con el icono del SNcount a su derecha, igual que en el gasto (decisión mía por coherencia, a partir de lo que pidió el usuario para el gasto). En un SNcount de tipo mes, en lugar del nombre libre aparece el selector de mes, como al crearlo.
- «Participantes»: los actuales aparecen marcados y no se pueden desmarcar. Los miembros de la casa que no están se pueden marcar para añadirlos.

### Lista de SNcounts (boceto 2, derecha)

- Los archivados no salen en la lista principal. Al final aparece la fila «SNcounts archivados» con el número de archivados; si no hay ninguno, no aparece.
- La fila abre una pantalla con los archivados, con las mismas tarjetas. Desde ahí se abre cada uno igual que los demás.

### Compartir (boceto 3)

- Hoja de compartir de Android (`ACTION_SEND`, como `WeekShare`) con un texto como: «He creado el SNcount «Viaje a Asturias» en SNHome. Ábrelo aquí: https://home-pantry-bed42.web.app/sncount/{id}».
- Lo reciben miembros de la casa que ya tienen la app. Al pulsar el enlace en su móvil se abre SNHome directamente en ese SNcount.

## Cómo se construye (decisiones mías, revisables)

### Datos

- `SplitExpense.emoji: String = ""`: vacío significa icono automático.
- `SplitExpense.shares: Map<String, Long> = emptyMap()`: céntimos por participante. Vacío significa reparto a partes iguales entre `participants`, que es lo que tienen todos los gastos actuales. Si no está vacío, `participants` sigue guardándose y contiene las claves con importe > 0, para que el código que solo mira `participants` siga siendo correcto.
- `SplitMonth.emoji: String = ""` y `SplitMonth.archived: Boolean = false`. El nombre es `archived` y no `isArchived` por la trampa de Firestore ya documentada en el modelo.

### Lógica pura (`SplitLogic.kt`, con tests)

- `expenseShares(expense): Map<String, Long>` devuelve `shares` si no está vacío y, si lo está, `splitEqually(amountCents, participants)`. `computeBalances` y `myShareCents` pasan a usarla en vez de llamar a `splitEqually` directamente.
- Validación: `validateSplitExpense` recibe además los importes por persona del modo manual (o null en modo igual) y añade `SplitExpenseError.SHARES` si alguno no es válido o si la suma no es igual al total. Un participante con importe vacío o 0 no participa. Hace falta al menos uno con importe > 0 (si no, `PARTICIPANTS`).
- Función pura para el aviso en vivo: la diferencia entre el total y la suma de importes (positiva = faltan, negativa = sobran), para que la UI solo formatee.
- Para leer cada importe por persona se usa `parseCents`, aceptando vacío como 0.
- Lista de archivados y no archivados en `SplitUiState`, con funciones puras testeables, como el resto de vistas derivadas.

### Comportamiento del formulario

- Al apagar el interruptor, los campos se rellenan con el reparto igual actual, para que se parta de una suma que ya cuadra. Al volver a encenderlo, se descartan los importes manuales y se mantienen las casillas de quién participa.
- Al editar un gasto con `shares`, la pantalla se abre con el interruptor apagado y esos importes.
- Un **reembolso** no muestra el interruptor ni el selector de emoji: sigue como ahora, con un solo receptor y el icono de intercambio.
- Eliminar un gasto (solo al editar): icono de papelera en coral en la barra superior, con el mismo diálogo de confirmación que ahora.
- Los errores de validación se muestran como ahora, bajo cada campo.

### Navegación

- Rutas nuevas, sin barra inferior: `splitExpense/{monthId}?expenseId={id}`, `splitMonthEdit/{monthId}` y `splitArchived`.
- **Trampa:** `SplitMonthScreen` abre el mes en un `DisposableEffect` y lo cierra en `onDispose`. Al navegar a la pantalla del gasto, la del mes sale de la composición y su `onDispose` llamaría a `closeMonth(monthId)` con el mismo id, lo que vaciaría los gastos que la pantalla nueva necesita. Hay que resolverlo, por ejemplo con un contador de pantallas que tienen el mes abierto en el ViewModel, o vinculando la apertura al `NavBackStackEntry` del mes. La solución la elige quien lo construya, pero tiene que estar cubierta.
- Si mientras se edita otro miembro borra el SNcount (o el gasto), la pantalla vuelve atrás, como ya hace `SplitMonthScreen`.
- Eliminar el SNcount desde el menú: mismo diálogo de confirmación que ahora y, al confirmar, vuelta a la lista.
- Archivar desde la pantalla del SNcount: se queda en la pantalla y aparece un snackbar «Archivado». Desarchivar funciona igual.
- El menú ⋮ de cada tarjeta de la lista (ahora Renombrar y Eliminar) pasa a tener las mismas cuatro opciones que el de la pantalla, reutilizando el mismo componente. «Renombrar» desaparece porque Editar lo incluye.

### Editar SNcount

- El selector de mes de un SNcount de tipo mes solo se puede cambiar si el SNcount no tiene gastos. Si tiene, se muestra deshabilitado con una nota, porque sus gastos están validados contra ese mes. El nombre de un mes sigue saliendo de `splitMonthDisplayName`.
- Se guarda con `update` de los campos que cambian (`name`, `monthKey`, `emoji` y `memberIds` con `FieldValue.arrayUnion`, para no pisar a otro miembro que añada a alguien a la vez).
- Crear un SNcount sigue con `SplitMonthFormSheet` sin emoji; el emoji se pone desde Editar.

### Selector de emoji

- Lista fija de unos 40 emojis de gasto (comida, bebida, súper, transporte, viaje, casa, suministros, ocio, regalos, salud, mascotas, ropa…) en un fichero Kotlin. Sin librerías nuevas. Se usa el mismo selector para gastos y para SNcounts.

### Compartir y enlace

- Firebase Hosting en el proyecto `home-pantry-bed42`: añadir `hosting` a `firebase.json` con una carpeta `public/` que contiene:
  - `.well-known/assetlinks.json` con el paquete `com.homepantry.app` y la huella SHA-256 de la clave que firma la release (la debug de este equipo, `~/.android/debug.keystore`).
  - Una página `sncount/…` (con rewrite de `/sncount/**`) que, si se abre en el navegador, dice que se abra desde el móvil con SNHome y ofrece un botón «Abrir en SNHome» con un enlace `intent://` como plan B por si la verificación de App Links falla.
- `AndroidManifest.xml`: `intent-filter` con `android:autoVerify="true"` para `https://home-pantry-bed42.web.app/sncount/*`. `MainActivity` lee el id del intent (también en `onNewIntent` si ya estaba abierta) y, cuando la casa está cargada, navega a Home → SNcount → ese SNcount, para que «atrás» funcione con normalidad. Si aún no se ha unido a una casa, el enlace se ignora. Si el SNcount no existe en su casa, se queda en la lista de SNcounts.
- **El despliegue (`firebase deploy --only hosting`) es una acción externa: lo lanza el usuario o se le pide permiso antes.**

### Decisiones tras la revisión del documento (mías)

- **Clave de firma:** `app/build.gradle.kts` firma la release con `signingConfigs.getByName("debug")`, es decir, con `~/.android/debug.keystore` de este equipo. La huella de `assetlinks.json` se saca con `keytool -list -v` de ese fichero. Si la verificación de App Links falla, el plan B del botón `intent://` de la página web es suficiente.
- **Enlace en arranque en frío:** el id del enlace se guarda como pendiente en `MainActivity` hasta que termina el inicio de sesión anónimo y se cargan la casa y los ViewModels; entonces se navega. Si no hay casa, se descarta.
- **Miembros añadidos después:** al editar un gasto antiguo en modo por importes, los miembros nuevos del SNcount salen como filas con 0. Los gastos guardados (iguales o por importes) nunca incluyen solos a nadie nuevo, porque `participants` y `shares` quedan fijados al guardar.
- **Volver a modo igual:** al guardar se escribe `shares = {}`. `SplitExpensesRepository.update` usa un mapa explícito de campos, así que hay que añadirle `shares` y `emoji`. En modo igual, la regla es «al menos una casilla marcada».
- **Archivado:** un SNcount archivado sigue siendo totalmente editable (gastos, fotos, saldar). Solo cambia dónde aparece en la lista.
- **Reembolsos:** siempre con el icono de intercambio, sin emoji.
- **Emoji en SNcounts de tipo mes:** también se puede editar.

## Lo que queda para quien lo construya

- El reparto en componentes y ficheros: una pantalla nueva que sustituye a `SplitExpenseSheet`, y qué partes de ese fichero se reutilizan (chips, fila de fecha, calendario).
- La forma exacta de resolver la trampa de `openMonth`/`closeMonth`.
- La lista concreta de emojis y la cuadrícula de la hoja.
- Los textos exactos de `strings.xml`, en el tono de los actuales.
- Los tests: como mínimo, `expenseShares`, saldos con gastos mixtos (iguales y por importes) que suman 0, la validación de la suma y la clasificación de archivados.
- La verificación final de siempre (tests, debug, lint, release) y la prueba del APK release en el S25, incluido el enlace desde WhatsApp.
