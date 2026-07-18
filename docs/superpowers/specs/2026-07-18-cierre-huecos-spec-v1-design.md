# Cierre de huecos del SPEC v1 — "Lista de la Casa"

## Contexto

`SPEC.md` v1 está implementado y compila (checklist §5 completo salvo el `git push`
pendiente de confirmación del usuario). Este documento cierra los puntos que el
SPEC v1 dejaba sin definir explícitamente: comportamientos borde, validaciones y
pequeñas piezas de UI que surgieron al repasar la especificación funcional. No
introduce subsistemas nuevos (sin notificaciones push, sin multi-casa) — es una
adenda de precisión, no una v2.

Estos cambios se aplican sobre el modelo de datos y las pantallas ya existentes:
`Item`, `Zone`, `ItemsRepository`, `ZonesRepository`, `MainListScreen`,
`AddItemSheet`, `ManageZonesScreen`, `UserPrefs` (DataStore).

---

## 1. Edición de productos

**Decisión:** edición completa. Tocar una tarjeta de producto en `MainListScreen`
abre `AddItemSheet` en modo edición, precargado con los valores actuales del
`Item` (nombre, cantidad, unidad, zona, nota, foto, barcode). Cualquier campo es
editable, incluyendo volver a escanear un código de barras o cambiar/quitar la
foto.

- Al guardar, `ItemsRepository` actualiza el documento existente
  (`households/{code}/items/{itemId}`) en vez de crear uno nuevo.
- `addedBy` y `addedAt` **no cambian** al editar — siguen reflejando la creación
  original. No se añade un campo `editedBy`/`editedAt` (se descartó por
  simplicidad; ver sección 12).
- `AddItemSheet` necesita un parámetro de entrada (`itemToEdit: Item? = null`) que
  distinga "crear" de "editar" y ajuste el texto del botón de acción
  ("Añadir" vs "Guardar cambios").

## 2. Offline-first

**Decisión:** activar persistencia offline de Firestore
(`FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true)` o el
equivalente KTX, configurado una vez al inicializar Firestore en la app).

- Con la persistencia activa, crear/editar/marcar/borrar productos funciona sin
  conexión; los cambios se encolan localmente y se sincronizan solos al recuperar
  red, sin código adicional de cola manual.
- La app observa el estado de conectividad (`ConnectivityManager` o el callback de
  Firestore) y muestra un indicador sutil (chip pequeño, ej. "Sin conexión") en la
  cabecera de `MainListScreen` cuando no hay red. Desaparece al reconectar.
- **Conflictos de edición concurrente:** se acepta *last-write-wins*, el
  comportamiento nativo de Firestore (el último `set`/`update` que llega
  sobrescribe el documento). No se implementa merge por campo ni aviso de
  conflicto — para una lista de la compra doméstica el riesgo de pérdida puntual
  de un campo es aceptable.

## 3. Duplicados al escanear código de barras

**Decisión:** si el código de barras escaneado coincide con el `barcode` de un
`Item` ya existente con `done == false` en la casa actual, mostrar un diálogo
antes de abrir `AddItemSheet`:

> "Ya tienes '{nombre}' en la lista"
> - **Sumar cantidad** — incrementa el `qty` del item existente en 1 (o en la
>   cantidad que el usuario ajuste en el propio diálogo) y cierra el flujo de
>   escaneo sin crear un item nuevo.
> - **Añadir como nuevo** — descarta el aviso y continúa el flujo normal de
>   `AddItemSheet` con los datos autocompletados por Open Food Facts, creando un
>   segundo item independiente.

Si el producto coincidente ya está `done == true` (comprado), no se avisa — se
trata como si fuera nuevo, porque probablemente se está reponiendo.

## 4. Zona "Otros" protegida

**Decisión:** "Otros" es una zona especial que no se puede renombrar ni eliminar.

- En `ManageZonesScreen`, cuando la zona iterada tiene `name == "Otros"`
  (comparación exacta, ya que es el nombre reservado por defecto de SPEC §1.3),
  los botones de renombrar y eliminar se muestran deshabilitados, con un texto
  explicativo breve (ej. "zona de reserva, no se puede modificar").
- Sigue siendo el destino automático de reasignación cuando se elimina cualquier
  otra zona con productos dentro (comportamiento ya definido en SPEC §1.3).
- Si por algún motivo no existiera (ej. datos migrados de una versión anterior),
  se recrea automáticamente al iniciar sesión en la casa, igual que la regla
  existente de "siempre debe existir al menos 1 zona".

## 5. Editar nombre de usuario

**Decisión:** se añade una opción en la pantalla/diálogo de ajustes de usuario
(accesible desde el icono de nombre en la cabecera de `MainListScreen`, ya
previsto en SPEC §1.4) para cambiar el nombre guardado en `UserPrefs`
(DataStore).

- Cambiarlo solo afecta a productos añadidos **a partir de ese momento**; los
  `addedBy` de productos ya creados no se actualizan retroactivamente.
- **Cambiar de casa se descarta explícitamente** — no se implementa una opción de
  "salir/unirse a otra casa" en esta ronda. Si el usuario se equivocó de casa, la
  única vía sigue siendo borrar datos de la app o reinstalar.

## 6. Compresión de fotos

**Decisión:** redimensionar la imagen capturada/seleccionada a un máximo de
1024px en el lado mayor (manteniendo proporción), recodificar como JPEG con
calidad ~80%, antes de subir a `households/{code}/photos/{itemId}.jpg`. Objetivo
aproximado: 150–300KB por foto.

## 7. Confirmación al vaciar comprados

**Decisión:** al pulsar "vaciar comprados", mostrar un diálogo de confirmación:

> "¿Borrar {N} productos comprados? Esta acción no se puede deshacer."
> [Cancelar] [Confirmar]

Sin Snackbar de deshacer — si el usuario confirma, el borrado en Firestore es
inmediato y definitivo.

## 8. Eliminar producto individual

**Decisión:** se mantiene sin confirmación, tal como en el SPEC v1 — el botón
eliminar de una tarjeta borra el producto al instante, sin diálogo ni undo. Es
una acción de bajo riesgo sobre un solo item y añadir fricción aquí perjudicaría
el uso diario.

## 9. Validación de cantidad

**Decisión:** `qty` debe ser estrictamente mayor que 0. El campo de texto de
cantidad en `AddItemSheet` valida esto antes de habilitar el botón de
guardar/añadir. Si el usuario deja el campo vacío, se asume `1.0` por defecto en
vez de bloquear el guardado.

## 10. Orden de productos

**Decisión:** nuevo selector "ordenar por" en la cabecera de `MainListScreen`
(junto a los tabs de zona), con tres opciones:

1. **Más reciente primero** (por `addedAt` descendente) — opción por defecto.
2. **Más antiguo primero** (por `addedAt` ascendente).
3. **Alfabético A-Z** (por `name`).

El orden elegido se aplica dentro de cada grupo (pendientes / comprados) de la
zona/tab activa, sin alterar la separación pendientes-antes-que-comprados ya
definida en SPEC §1.4. La preferencia de orden es solo de sesión/UI (no persiste
en DataStore ni Firestore), salvo que surja la necesidad de recordarla entre
sesiones más adelante.

## 11. Buscador

**Decisión:** icono de lupa en la cabecera de `MainListScreen` que despliega un
campo de texto. Filtra client-side, en tiempo real (sobre el `StateFlow` de items
ya cargado por los listeners de Firestore existentes), por coincidencia parcial
insensible a mayúsculas en `name`. Se combina con el tab de zona activo y el
orden elegido (busca solo dentro de lo que ya se está mostrando).

## 12. Sin notificaciones push

**Decisión:** se descarta explícitamente. La app sigue dependiendo únicamente de
los listeners de Firestore en tiempo real (`addSnapshotListener`) para reflejar
cambios de otros miembros, y esto solo ocurre mientras la app está abierta en
primer o segundo plano activo. No se añade Firebase Cloud Messaging ni Cloud
Functions.

## 13. Seguridad del código de casa

**Decisión:** se mantienen las reglas de Firestore de SPEC §4 sin cambios
(cualquier usuario autenticado, aunque sea anónimo, puede leer/escribir dentro de
un `householdCode` conocido). Aceptable para el caso de uso doméstico — no hay
datos sensibles en una lista de la compra, y el coste de fuerza bruta sobre
`CASA-####` no justifica añadir complejidad de reglas o alargar el formato del
código en esta ronda.

---

## Fuera de alcance (explícitamente descartado en esta ronda)

- Registrar quién hizo la última edición (`editedBy`/`editedAt`) — solo se
  mantiene `addedBy` del creador original.
- Cambiar/salir de la casa desde la app.
- Notificaciones push.
- Merge de campos en conflictos de edición concurrente.
- Endurecer el formato del código de casa o las reglas de seguridad de Firestore.
- Snackbar de deshacer para borrados (ni individuales ni de "vaciar comprados").
