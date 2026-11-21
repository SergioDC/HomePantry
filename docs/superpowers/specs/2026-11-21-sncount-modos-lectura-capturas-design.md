# SNcount: elegir cómo se leen las capturas del banco (issue #3)

## Por qué

Hoy la importación de cargos (issue #1, spec `2026-11-21-sncount-importar-capturas-design.md`) manda las capturas del banco como imagen a Gemini. Con la clave gratuita, Google puede usar y revisar lo que se envía, y en una captura del banco puede aparecer el nombre, el saldo o el IBAN. El usuario quiere poder elegir, en cada importación, cuánto sale del móvil, y comparar los modos con capturas reales.

## Objetivo

Tres modos de lectura, elegidos al importar:

| Modo | Qué sale del móvil | Necesita clave |
|---|---|---|
| Gemini (imagen) | las capturas | sí |
| OCR + Gemini (texto) | solo el texto leído en el móvil | sí |
| Solo OCR | nada | no |

Las capturas son siempre de la app de **Kutxabank**.

**No es objetivo:** soportar otros bancos en «Solo OCR»; cambiar la revisión, la detección de duplicados, el pagador o el guardado (son los mismos en los tres modos).

**Sería un fracaso si** «Solo OCR» mandara algo fuera del móvil, o si un modo que lee mal guardara gastos sin pasar por la revisión.

## Restricción

No hay capturas de ejemplo de Kutxabank. El lector de «Solo OCR» se escribe con patrones genéricos y se ajusta después con el texto real de la primera importación (ver «Copiar texto leído»).

## Enfoques considerados

- **A (elegido):** lector genérico + botón «Copiar texto leído» para ajustarlo a Kutxabank con la primera importación real. Los tres modos funcionan desde el primer día; el tercero mejora tras la primera prueba.
- **B:** solo los modos 1 y 2 y el 3 cuando haya una captura de ejemplo. Descartado: retrasa justo el modo que motiva el cambio.
- **C:** lector genérico sin herramienta de ajuste. Descartado: si lee mal, no hay con qué arreglarlo sin capturas.

## Diseño

**Selector.** Al pulsar el icono de importar en el grupo, antes del selector de imágenes, un diálogo con los tres modos (radio) y una línea bajo cada uno con lo que sale del móvil. Se recuerda el último modo usado en `UserPrefs` (DataStore del móvil, no se sincroniza). Sin clave de Gemini, los dos primeros salen deshabilitados con «Necesita clave de Gemini» y queda marcado «Solo OCR». Por defecto: Gemini (imagen) si hay clave; si no, Solo OCR.

**OCR con posición.** Nueva función que devuelve, por captura, las líneas de ML Kit con su caja (`OcrLine(text, top, bottom, left)`), reutilizando la decodificación reducida de `ReceiptTextRecognizer`. Una función pura agrupa en filas las líneas que se solapan en vertical y las ordena de arriba abajo y de izquierda a derecha. Motivo: ML Kit puede devolver la columna de conceptos y la de importes como bloques separados, y sin la posición no se sabe qué importe va con qué concepto.

**OCR + Gemini.** Se manda el texto de las filas (una fila por línea, con `--- Captura N ---` entre capturas) con un prompt equivalente al actual y el mismo esquema JSON; la respuesta pasa por `mapCardMovementsOutput`, así que el resto no cambia.

**Solo OCR.** Función pura `parseBankMovements(rows, today): List<CardMovement>` en `data/` (fichero nuevo, con tests), sin dependencias de Android:
- Importe: `[-−+]?` + número con miles con punto y decimales con coma (`1.234,56`) + `€` opcional. Una fila con importe es un movimiento.
- Cargo o ingreso: solo un «+» explícito marca un ingreso; con «-» (o cualquier guion) o sin signo es un cargo. Si el OCR se come un signo, es mejor ver el cargo en la revisión y desmarcarlo que perderlo sin enterarse.
- Cabeceras de fecha con día de la semana («Viernes, 20 de noviembre», «Hoy, 21 nov») también fijan la fecha. Una fila sin importe solo titula el importe siguiente si tiene al menos 3 letras.
- Fecha: una fila que solo es fecha («Hoy», «Ayer», `20/11`, `20/11/2026`, `20 nov`, `20 de noviembre`) fija la fecha de los movimientos que la siguen; una fecha dentro de la fila del movimiento manda sobre la cabecera. El año que falta se deduce para que la fecha no sea futura respecto a hoy.
- Concepto: el resto de la fila sin importe ni fecha; si queda vacío, la fila anterior que no sea fecha ni importe (concepto en una línea y importe en la siguiente). Si sigue vacío, se descarta (no hay con qué titularlo).
- Filas de saldo («Saldo», «Disponible») se ignoran.

Después, los movimientos siguen el camino de siempre: `cardImportDraft` (duplicados, fuera de mes, sin fecha), revisión y guardado.

**Copiar texto leído.** En los dos modos con OCR, el texto de las filas se guarda en el borrador (campo nuevo `ocrText`, con valor por defecto para Gson/R8). En la revisión, el menú ⋮ tiene «Copiar texto leído», que lo copia al portapapeles. Si «Solo OCR» no encuentra ningún cargo, la revisión se abre vacía con un aviso («No se ha encontrado ningún cargo») y la misma opción de copiar, para poder pasarme el texto y ajustar el lector.

**Reintentar** repite la lectura con el mismo modo.

## Decisiones mías (revisables)

- El modo se elige en cada importación (recordando el último) y no en Ajustes: así se pueden comparar los modos con las mismas capturas sin salir de SNcount.
- La caja del OCR y la agrupación por filas en vez de usar el orden de ML Kit.
- La regla de «si no hay ningún menos, todo son cargos».
- El texto copiado lleva solo las filas, no la imagen.

## Para quien lo construya

- Textos de la interfaz y patrones de fecha concretos, siguiendo `strings.xml` y las convenciones de CLAUDE.md.
- Ajuste del umbral de solape vertical para agrupar filas.
