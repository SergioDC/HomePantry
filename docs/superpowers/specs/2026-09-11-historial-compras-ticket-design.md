# Historial de compras a partir de foto de ticket

**Fecha:** 2026-09-11
**Estado:** Aprobado en brainstorming, pendiente de plan de implementación

## Contexto y objetivo

HomePantry gestiona actualmente stock de despensa (`Item`: nombre, cantidad,
unidad, zona, tienda opcional, foto opcional), sin ningún concepto de precio
ni histórico de compras. El objetivo de esta funcionalidad es permitir al
usuario fotografiar un ticket de compra y, sin depender de ningún motor de
IA de pago ni de conexión a un servicio externo, extraer los productos y sus
precios para construir un histórico que permita ver el gasto mensual por
producto.

Restricción explícita del usuario: la solución debe funcionar sin coste
recurrente y sin requerir un "motor de IA" pesado — solo herramientas
on-device gratuitas, en línea con lo que la app ya usa (ML Kit para
código de barras).

## Alcance

- Catálogo de compras **independiente** del stock de despensa (`Item`). No
  hay vínculo automático con los `Item` existentes.
- Los productos del histórico se agrupan por un nombre normalizado simple
  (p.ej. "Tomates", "TOMATE RAMA" y "tomates pera" se agrupan si su forma
  normalizada coincide), no por matching inteligente.
- Se captura **solo nombre + precio total de la línea**. No se extrae ni
  almacena cantidad/unidades — si el ticket dice "2 TOMATE RAMA 1,50", se
  guarda como una línea de 1,50 sin desglosar unidades.
- El usuario **revisa y confirma** las líneas detectadas antes de guardar
  nada (no hay guardado automático silencioso).
- La foto del ticket se guarda (comprimida) como referencia.
- Fuera de alcance: reconocimiento de tienda/enseña, cálculo de IVA,
  matching automático con `Item`, edición de líneas ya guardadas (se puede
  añadir en una iteración futura), soporte multi-idioma de OCR más allá de
  script latino.

## Modelo de datos

Nueva colección Firestore, hermana de `items`/`zones`/`members`:

```
households/{householdCode}/purchases/{purchaseId}
  rawName: String          // texto tal cual detectado/editado por el usuario
  normalizedName: String   // clave de agrupación (ver normalización)
  price: Double             // precio total de la línea, en euros
  date: Date                 // fecha de la compra (fecha de escaneo por defecto)
  addedBy: String            // nombre de usuario, igual que Item.addedBy
  ticketPhotoUrl: String?    // URL en Firebase Storage de la foto del ticket
```

**Normalización** (`normalizedName`): minúsculas, trim, sin acentos,
colapsar espacios múltiples, quitar una "s" final simple si la palabra
tiene más de 3 caracteres (plural naive). Es una función pura, testeable,
sin dependencias externas. No pretende ser perfecta — el usuario puede
editar `rawName` antes de guardar para forzar que dos líneas se agrupen
igual.

**Foto del ticket**: se sube una sola vez por ticket escaneado a
`households/{code}/receipts/{purchaseBatchId}.jpg` (comprimida con la
misma utilidad `ImageCompression.kt` que ya usan las fotos de producto,
con calidad/tamaño medio) y su URL se copia en `ticketPhotoUrl` de cada
línea generada en ese escaneo.

## Navegación

- Nuevo icono en la `TopAppBar` de `ZonesDashboardScreen` (mismo patrón
  que el icono existente de "Gestionar zonas"), que navega a la nueva
  ruta `purchaseHistory`.
- No se añade una 4ª pestaña a la barra de navegación inferior — se
  mantiene el patrón actual de 3 pestañas (Almacén/Lista/Buscar) y las
  pantallas de gestión secundarias se cuelgan de iconos en la barra
  superior, igual que `manageZones`.

## Flujo de captura y parseo (sin motor de IA)

1. **Captura**: desde `PurchaseHistoryScreen`, un FAB "Escanear ticket"
   lanza `ActivityResultContracts.TakePicture` (cámara del sistema), igual
   de sencillo que el `PickVisualMedia` que ya usa `AddItemSheet` para
   fotos de producto, pero apuntando a la cámara en vez de a la galería.
   No se construye una pantalla de cámara custom con CameraX para esto.
2. **OCR on-device**: se añade la dependencia
   `com.google.mlkit:text-recognition` (script latino), del mismo estilo
   que `com.google.mlkit:barcode-scanning` ya presente en
   `app/build.gradle.kts`. Es gratis, funciona offline y no requiere
   backend ni llamadas a APIs de pago. Se ejecuta sobre el bitmap
   capturado y devuelve el texto detectado línea por línea.
3. **Parseo por reglas** (Kotlin puro, sin ML):
   - Para cada línea de texto, se busca un patrón de precio al final de la
     línea: `\d{1,3}(?:\.\d{3})*,\d{2}` (formato español) opcionalmente
     precedido de `€`.
   - Si hay match, el resto de la línea (recortado) es el nombre
     candidato y el número parseado (coma como separador decimal) es el
     precio.
   - Se descartan líneas cuyo nombre candidato contenga palabras de una
     lista negra de líneas de resumen: `TOTAL`, `SUBTOTAL`, `IVA`,
     `CAMBIO`, `TARJETA`, `EFECTIVO`, `BASE IMPONIBLE`.
   - Líneas sin patrón de precio reconocible se ignoran (no se muestran
     como candidatas).
4. **Revisión**: se muestra una pantalla/hoja con la lista de líneas
   candidatas, cada una editable (nombre + precio), con opción de borrar
   cualquier línea y de añadir una línea manual (para cuando el OCR se
   salta un producto). Nada se guarda hasta que el usuario pulsa
   "Guardar".
5. **Guardado**: al confirmar, se sube la foto comprimida del ticket una
   vez, y se escribe un documento en `purchases` por cada línea
   confirmada, con la misma `ticketPhotoUrl`.

## Histórico y gasto mensual

- `PurchaseHistoryScreen` agrupa las compras cargadas (mismo patrón
  reactivo de listener de Firestore que `ItemsRepository`) por
  `normalizedName`, mostrando por producto: gasto acumulado del mes en
  curso y gasto total histórico.
- Al entrar en un producto se ve el desglose de compras (fecha + precio)
  y el gasto agregado por mes (agrupación por año-mes de `date`).
- Toda la agregación se hace en el cliente (`ViewModel`/repositorio
  nuevo, p.ej. `PurchasesRepository`), sin Cloud Functions ni
  procesamiento en servidor — coherente con que el resto de la app no usa
  backend propio, solo Firestore/Storage/Auth directos.

## Manejo de errores

Se sigue el mismo patrón que el resto de la app (fijado en el commit
`38b8f55`, que corrigió fallos silenciosos de Firestore/foto):

- Fallos de subida de la foto del ticket o de escritura en `purchases` se
  capturan y se exponen vía `state.error`, igual que en
  `ItemsRepository`/`StorageRepository`.
- Si ML Kit no detecta texto en la foto (ticket borroso, mala luz, etc.),
  se muestra un aviso ("No se ha detectado texto en el ticket") y se deja
  al usuario añadir líneas manualmente desde la propia pantalla de
  revisión, sin bloquear el flujo.

## Testing

- Tests unitarios (Kotlin puro, sin dispositivo) para:
  - La función de normalización de nombre (`normalizedName`).
  - El parseo de líneas de ticket a `(nombre, precio)` a partir de
    strings de ejemplo (incluyendo casos con líneas de TOTAL/IVA que deben
    descartarse).
  - La agregación de gasto mensual a partir de una lista de compras de
    prueba.
- El flujo de cámara + ML Kit no es fácilmente testeable de forma
  automática; se valida manualmente en dispositivo con tickets reales
  variados (buena/mala luz, tickets largos, formatos de distintas
  tiendas) antes de dar la funcionalidad por completada.

## Dependencias nuevas

- `com.google.mlkit:text-recognition` (OCR on-device, gratis, offline).

No se añaden más dependencias: la captura reutiliza contratos estándar de
Android (`TakePicture`), la compresión reutiliza `ImageCompression.kt`
existente, y la persistencia reutiliza el patrón de repositorios +
Firestore/Storage ya presente en la app.
