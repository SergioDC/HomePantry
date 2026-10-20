# Gastos: panel único, fecha real del ticket y hoja de revisión sin bloqueos

## Por qué

La sección de gastos estaba repartida en dos pestañas («Por mes» y «Productos») más una pantalla de estadísticas aparte, y tenía tres errores:

1. **Tienda sin visión de conjunto.** Al elegir una tienda, solo se filtraban los tickets y seguía viéndose un único mes cada vez. No había forma de ver el total de la tienda ni de comparar sus meses. Además, «Por supermercado» en Estadísticas solo contaba el mes actual.
2. **Fecha equivocada.** Cada compra se guardaba con la fecha en que se escaneó el ticket (`Date()` en `savePurchaseBatch`), no con la fecha impresa en él.
3. **Hoja de revisión bloqueada.** Si se bajaba hasta el final de la hoja de revisión y luego se subía del todo, ya no se podía volver a bajar. Los botones «Volver» y «Guardar compras» quedaban fuera de la pantalla.

## Diseño elegido: panel único

Mock A, el que eligió el usuario. Una sola pantalla (`purchaseHistory`) con, de arriba abajo:

1. **Chips de tienda.** «Todas» y después las tiendas, de la compra más reciente a la más antigua. La tienda elegida filtra todo lo que hay debajo. Cada tienda tiene un color fijo de la paleta «Natural» (verde, azul, ámbar, lima, turquesa y arena; sin morado ni coral), asignado por orden de primera compra para que no cambie.
2. **Pestañas de meses**, como las de antes, del más reciente al más antiguo y con el gasto de cada mes para la tienda elegida, más una primera pestaña «Todos los meses».
   - Al elegir una tienda se abre en «Todos los meses»: todos sus tickets agrupados, con una cabecera por mes (tickets y gasto). Al tocar una cabecera se abre ese mes.
   - El mes elegido se mantiene al cambiar de tienda, para comparar tiendas en el mismo mes.
   - Con «Todas» y sin mes elegido se muestra el mes más reciente.
3. **Tarjeta resumen del periodo elegido.** Con «Todos los meses» muestra el total, los tickets y la media mensual. Con un mes:
   - el total;
   - la diferencia con el mes anterior, en coral si sube y en el color primario si baja;
   - el número de tickets y la media por ticket;
   - si hay una tienda elegida, su total histórico y su media mensual (esto arregla el error 1).

   En los dos casos, debajo va una gráfica de barras de los últimos 12 meses. Al tocar una barra se elige ese mes.
4. **Reparto por tienda**, solo con «Todas»: el del mes elegido, o el de siempre con «Todos los meses». Al tocar una tienda se elige su chip.
5. **Tickets del periodo.** Cada uno lleva el color de su tienda y mantiene la marca de duplicado y el botón «Eliminar».
6. **«Más».** Enlaza a «Todos los productos» (la antigua pestaña, ahora la pantalla `purchaseProducts`) y a Estadísticas, que se queda con los productos más comprados, las subidas de precio y dónde es más barato.

## Fecha del ticket

- Gemini devuelve `date` (YYYY-MM-DD). Con el OCR clásico, la fecha se busca entre las líneas leídas.
- Una fecha posterior a hoy, o de hace más de 2 años, se descarta.
- La hoja de revisión tiene un campo «Fecha del ticket» con calendario. Viene rellena si se ha leído del ticket; si no, «Guardar» no se activa hasta que se elija una. Se guarda el día elegido con la hora actual, para que el orden entre tickets del mismo día siga siendo estable.
- Los tickets ya guardados pueden cambiar de fecha desde su detalle. Un ticket antiguo sin `ticketId` recibe como `ticketId` su clave antigua, para que la clave no cambie al cambiar la fecha.

## Hoja de revisión

Pasa a `sheetGesturesEnabled = false`: la hoja ya no se cierra ni se desplaza con gestos, solo con sus botones. Los botones van en una barra fija al pie, fuera de la lista, así que siempre se ven.

## Fuera de alcance

Releer automáticamente las fechas de los tickets antiguos a partir de su foto.
