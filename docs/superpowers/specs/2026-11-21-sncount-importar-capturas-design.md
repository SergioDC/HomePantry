# SNcount: importar gastos desde capturas de cargos de tarjeta

Issue #1. Diseño corto acordado con el usuario el 2026-11-21.

## Por qué

Para cuadrar el mes, cada miembro abre la app de su banco y mete sus cargos a mano en SNcount, uno por uno. Casi todo se reparte a medias y lo personal se descarta. Queremos que pueda hacer capturas de esa lista y que la app cree los gastos.

## Objetivos

- Desde un grupo de SNcount, elegir una o varias capturas de la app del banco (de cualquier banco) y obtener la lista de cargos.
- Revisar la lista con todo marcado, desmarcar lo personal y crear los gastos en bloque.

## No objetivos

- Compartir la captura desde la galería o la app del banco (posible extra más adelante).
- Un lector distinto para cada banco, o leer extractos en PDF o CSV.
- Repartos distintos por cargo durante la revisión: si hace falta, se edita el gasto después.

## Anti-objetivos

- Que se dupliquen gastos al importar capturas solapadas, o cargos ya metidos a mano.
- Perder la lectura si el usuario sale de la pantalla (igual que con los tickets).

## Enfoques

1. **Gemini con la imagen (elegido).** Se manda la captura con la clave de Gemini del usuario, por el mismo REST que tickets y menú, y devuelve JSON con los cargos. Reutiliza errores, reintentos y borrador, y aguanta formatos de bancos distintos.
2. OCR de ML Kit y reglas propias. Habría que hacer un lector por banco y se rompería con cada cambio de la app. Descartado.
3. Compartir desde otra app. No es una forma de leer, solo otra forma de entrar; queda como extra.

## Diseño

- **Entrada:** en la pantalla del grupo (pestaña de gastos), acción «Importar de capturas» junto a añadir gasto. Selector de varias imágenes.
- **Lectura:** las imágenes se mandan juntas a Gemini y vuelve una lista de movimientos `{concepto, importe en céntimos, fecha, esCargo}`. El mapeo de la respuesta y la lógica de selección son funciones puras testeables en `data/`.
- **Revisión:** pantalla completa con un cargo por fila (casilla, emoji automático, concepto, fecha, importe). Arriba, el pagador: por defecto quien importa, se puede cambiar. Los participantes son todos los miembros del grupo. Tocar una fila deja editar concepto, importe y fecha. Al pie, «Añadir N gastos», que los escribe en un batch.
- **Desmarcados de entrada, con el motivo visible:**
  - posible duplicado: ya hay en el grupo un gasto con el mismo importe y la misma fecha, o se repite entre capturas;
  - fuera del mes: solo en grupos de tipo mes, si la fecha es de otro `monthKey`;
  - los ingresos y devoluciones no se muestran.
- **Borrador:** la lectura y las casillas se guardan en `filesDir`, como `ReceiptScanDraft`. Salir pide confirmación. Mientras Gemini lee, aplica el mismo aviso de salida.
- **Errores:** las subclases de `GeminiReceiptException`, con botón de reintentar. Sin clave de Gemini, se manda a Ajustes como en los tickets.

## Lo que queda al implementador

Ubicación exacta de la acción en la UI, el prompt y el esquema JSON para Gemini, el nombre de los ficheros y si la fecha sin año se completa con el año del grupo.
