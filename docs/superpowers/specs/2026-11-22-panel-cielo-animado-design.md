# Panel de cocina con cielo animado (issue #2)

## Por qué

El panel de la tablet (spec `2026-11-21-panel-tablet-cocina-design.md`) funciona, pero es una pantalla plana de texto sobre fondo oscuro. El usuario lo quiere más amigable: con fondo y animaciones, y con el tiempo animado según lo que haga fuera. Maqueta validada: https://claude.ai/artifact/GGE3n2ncjZ8GqLLTV8vSSL (estilo «Cielo completo»).

## Objetivos

- El fondo de todo el panel es un cielo animado que refleja el tiempo actual (los 8 `WeatherKind`) y el momento del día (día, atardecer, noche).
- El menú y la lista van en tarjetas translúcidas encima del cielo; la cabecera muestra un saludo, la fecha, el reloj y el tiempo en grande.
- Pequeñas animaciones de interfaz: los dos puntos del reloj parpadean, las tarjetas entran con un fundido y la casilla de la lista se anima al tachar.

**No es objetivo:** fotos o imágenes de fondo; cambiar el contenido del panel (menú, lista, «Se ha acabado», previsión por horas solo por la mañana); tocar el resto de la app.

**Sería un fracaso si** el texto deja de leerse sobre un cielo claro (nieve, niebla); si la tablet se calienta o gasta batería por animar toda la noche con el brillo bajado; o si el APK crece de forma notable.

## Enfoques considerados

- **A (elegido): «Cielo completo».** La escena ocupa toda la pantalla y las tarjetas son translúcidas. Es lo que eligió el usuario en la maqueta.
- **B: «Banda del tiempo».** El cielo animado solo en una franja arriba y el resto con el tema oscuro. Descartado por el usuario.
- **Imágenes o Lottie de fondo.** Añaden peso al APK y una dependencia. Descartado: todo se dibuja con Compose (`Canvas` + `rememberInfiniteTransition`).

## Diseño

**Escena (`ui/screens/PanelSky.kt`, Composable `PanelSky`).** Un `Canvas` a pantalla completa detrás del contenido del panel, con:
- Degradado vertical de tres colores según `WeatherKind` y fase del día (los colores de la maqueta).
- Sol con rayos que giran y halo que late (despejado y poco nuboso, de día; más bajo y anaranjado al atardecer). Luna creciente y estrellas que titilan de noche (despejado, poco nuboso y nieve).
- Nubes que cruzan despacio de izquierda a derecha; su número y color dependen del tiempo (de 0 a 7, más oscuras con lluvia y tormenta).
- Lluvia en diagonal (90 gotas), llovizna (45, más lentas y finas), tormenta (130 gotas y un relámpago cada ~7 s), nieve (70 copos con vaivén) y niebla (4 bandas difuminadas que se mecen).
- Un velo oscuro degradado en la parte de arriba para que la cabecera blanca se lea siempre.

Las partículas se generan una vez con una semilla fija (posición, tamaño, velocidad y desfase) y se dibujan con un único reloj de animación, sin un Composable por partícula.

**Lógica pura (`data/PanelSkyLogic.kt`, con tests):**
- `skyPhase(now, sunrise, sunset)`: `NIGHT` antes del amanecer o después del ocaso, `DUSK` en los 90 minutos antes del ocaso, `DAY` el resto. Sin datos de sol, se usa 7:00–19:00.
- `skyGradient(kind, phase)`: los tres colores (ARGB).
- `sceneSpec(kind, phase)`: qué elementos lleva la escena (sol, luna, estrellas, nº y tono de nubes, tipo y nº de partículas, relámpagos, niebla).
- `greeting(time)`: «Buenos días» hasta las 14:00, «Buenas tardes» hasta las 21:00, «Buenas noches» después (texto en `strings.xml`).

**Datos del tiempo.** La petición de Open-Meteo añade `sunrise,sunset` a `daily`; `WeatherSnapshot` guarda ambas horas (anulables, con valores por defecto, por R8).

**Pantalla del panel.** `PanelScreen` pasa a un `Box`: `PanelSky` al fondo y el contenido actual encima, con el `Scaffold` transparente. Cabecera en blanco con sombra suave: saludo, fecha, reloj grande (los dos puntos parpadean) y, a la derecha, estado del tiempo, temperatura grande, máx./mín./lluvia y pueblo. La previsión por horas pasa a «píldoras» translúcidas. Menú y lista en tarjetas translúcidas oscuras (fondo `NocturneSurface` al ~55 % y borde blanco al 12 %; sin desenfoque real, que en Compose solo existe desde Android 12). Los títulos de franja («Comida», «Cena») en un lila más claro que el primario para que contrasten sobre la tarjeta. Sin pueblo elegido o sin datos, el cielo es el de «nublado» con la fase del día.

**Batería.** `PanelIdleHost` expone si el panel está atenuado (un `CompositionLocal`). Atenuado, o con la pantalla del panel fuera de primer plano, la escena se queda quieta en su último fotograma. Si el sistema tiene las animaciones desactivadas, también se queda quieta.

**Tamaño.** Sin recursos nuevos: el APK release debe seguir en ~5,5 MB.

## Lo que queda al constructor

Tamaños exactos, curvas y duraciones de las animaciones (tomar la maqueta como referencia), y cómo repartir el dibujo entre funciones de `DrawScope`.
