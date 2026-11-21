# Panel de la tablet de la cocina (issue #2)

## Por qué

En casa hay una tablet Android de 10" en la cocina, junto a la nevera, encendida todo el día. Debe mostrar sin sacar el móvil lo que más se mira (el menú de hoy y la lista de la compra) y, a primera hora, el tiempo. También debe permitir, de pie y con prisa, apuntar lo que se acaba y tachar lo comprado. Más adelante controlará enchufes y lámparas (issue #4, con Home Assistant); este panel le deja el sitio.

## Objetivos

- Vista fija con: el tiempo (arriba, más destacado por la mañana), el menú de hoy y la lista de la compra.
- Tachar un producto de la lista con un toque.
- «Se ha acabado»: pasar a la lista algo que hay en casa con un toque, sin teclado; y apuntar algo nuevo por voz o con el teclado.
- Pantalla siempre encendida, con brillo bajo cuando nadie la toca.

**No es objetivo:** controlar dispositivos (es la #4); gastos, SNcount, recetas o inventario completo en el panel; varias tablets con configuraciones distintas.

**Sería un fracaso si** la tablet se apaga o se queda en otra pantalla y deja de servir de panel; si apuntar algo cuesta más que sacar el móvil; o si un toque sin querer tacha algo sin poder deshacerlo.

## Restricciones

- Misma app SNHome (mismo APK), mismos datos de Firestore, sin backend nuevo.
- La tablet entra en la casa como un miembro más, con el nombre «Cocina» (flujo de unirse de siempre).
- El APK solo incluye `arm64-v8a`. **Sin confirmar** que la tablet sea arm64: si es de 32 bits, hay que añadir `armeabi-v7a` (decisión del usuario, sube el tamaño del APK).
- Convenciones de CLAUDE.md: lógica pura en `data/` con tests, textos en `strings.xml`, tema oscuro y paleta de `Color.kt`, modelos de red en `data/` con valores por defecto (R8).

## Enfoques considerados

- **A (elegido): un «modo panel» dentro de SNHome.** Se activa en los Ajustes de la tablet; a partir de ahí la app arranca en el panel. Reutiliza repositorios, Firestore y la hoja de añadir producto.
- **B: una app aparte para la tablet.** Otro proyecto que mantener y que duplicaría la capa de datos. Descartado.
- **C: una web en el NAS.** Habría que repetir el acceso a Firestore y la lógica en JavaScript, y en una tablet Android no gana nada frente a la app. Descartado.

## Diseño

**Activación.** En Ajustes, un interruptor «Modo panel de cocina», guardado en `UserPrefs` del dispositivo (no se sincroniza). Con él activado, el destino inicial es `panel`. Para salir del panel: pulsación larga sobre la fecha → abre la Home normal; desde Ajustes se desactiva. La regla de navegación de CLAUDE.md no cambia: el panel va sin barra inferior.

**Pantalla `panel`** (horizontal; en vertical, las dos columnas se apilan):

```
┌───────────────────────────────────────────────────────────┐
│  Sábado 22 nov · 08:14              ☀ 14°  ↑18° ↓7°  0 %   │
├─────────────────────────────┬─────────────────────────────┤
│  MENÚ DE HOY                │  LISTA DE LA COMPRA  (12)   │
│  Comida                     │  ☐ Leche                    │
│   Lentejas con chorizo      │  ☐ Huevos                   │
│   Oihan: arroz a la cubana  │  ☐ …                        │
│  Cena                       │                             │
│   Tortilla y ensalada       │  [ + Se ha acabado… ]       │
└─────────────────────────────┴─────────────────────────────┘
```

- **Cabecera:** día, fecha y hora (se actualiza cada minuto y cambia de día a medianoche) y el tiempo.
- **Tiempo:** Open-Meteo (gratis, sin clave ni cuenta; solo recibe la ubicación). Temperatura actual, icono y texto del estado, máxima y mínima de hoy y probabilidad de lluvia. Hasta las 12:00 se muestra en grande, con la evolución por horas de 8 a 22 h; después, en una línea. Se refresca cada 30 minutos; sin red, se ve el último dato con su hora. La ubicación se elige una vez escribiendo el pueblo (buscador de Open-Meteo) y se guarda en `UserPrefs`; mientras no hay ubicación, el hueco del tiempo invita a configurarla.
- **Menú de hoy:** las entradas de `mealEntries` con la fecha de hoy, agrupadas por franja (Desayuno solo si hay; Comida; Cena) y, dentro, por persona: las de la familia sin etiqueta y las de otras personas (p. ej. el comedor de Oihan) con su nombre delante. Sin entradas: «Sin menú para hoy». Solo lectura.
- **Lista de la compra:** los productos con `done = false`, en el orden de la lista principal, en letra grande. Tocar uno lo tacha y, a los 3 segundos, pasa a «en casa» (`setDone(true)`); volver a tocarlo antes lo deja como estaba. Si la lista es larga, se desplaza.
- **Dispositivos (#4):** no se muestra nada hasta que exista; la columna o fila se añadirá entonces.

**Pantalla `panelRestock` («Se ha acabado…»):** cuadrícula de botones grandes con los productos en casa (`done = true`) agrupados por zonas, en el orden de zonas de la app; buscador arriba (sin tildes, como el resto de buscadores). Tocar un producto lo pasa a la lista (`setDone(false)`) y lo marca en la cuadrícula como «en la lista», sin salir, por si se acaba más de una cosa. Botón «Nuevo» → la hoja de añadir producto de siempre, donde el micrófono del teclado sirve para dictar. Flecha atrás → panel.

**Siempre encendido.** Mientras se ve el panel o «Se ha acabado», la pantalla no se apaga (`FLAG_KEEP_SCREEN_ON`). Tras 2 minutos sin tocar, el brillo de la ventana baja; el primer toque lo recupera (y no actúa sobre lo que haya debajo). Fuera del panel, tras 2 minutos sin tocar se vuelve al panel.

**Datos.** Un `PanelViewModel` nuevo con `ItemsRepository`, `ZonesRepository`, `MealEntriesRepository.observeRange(hoy, hoy)` y el tiempo. La lógica pura va en `data/` con tests: agrupar el menú de hoy, filtrar y ordenar «Se ha acabado» con el buscador, mapear la respuesta de Open-Meteo y el código del tiempo (WMO) a icono y texto, y decidir el formato de mañana o de tarde.

## Decisiones mías (revisables)

- El panel es un modo de la app, no una vista que salga sola por tamaño de pantalla: así el móvil nunca entra en él sin querer.
- Salir con pulsación larga en la fecha, para que nadie salga del panel tocando por accidente.
- Tachar con 3 segundos de margen para deshacer.
- El tiempo destacado hasta las 12:00, y Open-Meteo como fuente.
- Volver al panel tras 2 minutos sin tocar.

## Para quien lo construya

- Iconos del tiempo con los de Material incluidos; tamaños de letra y colores dentro de la paleta.
- Cómo se dibuja la evolución por horas (fila de horas con icono y temperatura basta).
