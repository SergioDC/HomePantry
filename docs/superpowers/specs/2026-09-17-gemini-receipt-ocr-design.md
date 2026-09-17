# Interpretación de tickets con Gemini (API key propia del usuario)

**Fecha:** 2026-09-17
**Estado:** Aprobado en brainstorming, pendiente de plan de implementación

## Contexto y objetivo

La funcionalidad de historial de compras (spec
`2026-09-11-historial-compras-ticket-design.md`) parte de una restricción
explícita: solo OCR on-device gratuito (ML Kit) + parseo por reglas, sin
motor de IA. En uso real con tickets fotografiados (Lidl, Aldi) se ha
comprobado que esa restricción produce resultados incorrectos de forma
sistemática, no en casos raros: ML Kit agrupa el texto detectado en
"bloques" cuyo orden de lectura no respeta las filas visuales del ticket
cuando hay dos columnas (nombre a la izquierda, precio a la derecha) — ver
capturas de log reales en la sesión que originó esta spec, donde 7 precios
consecutivos aparecen sin ningún nombre entre medias, y nombres de producto
aparecen pegados al precio de OTRO producto. La información necesaria para
emparejar nombre↔precio se pierde antes de llegar a cualquier parser de
texto, así que ningún ajuste de regex sobre `ReceiptParsing.kt` puede
arreglarlo — es un problema de arquitectura (falta de información
geométrica), no de reglas de parseo.

Objetivo de esta spec: añadir una vía alternativa de interpretación de
tickets usando Gemini (modelo con visión, multimodal) como sustituto
completo del OCR clásico cuando el usuario lo configura, manteniendo la
restricción de **sin coste**: cada usuario aporta su propia API key
gratuita de Google AI Studio (no Vertex AI, que sí es de pago), así que
nunca hay gasto para el propietario de la app ni para nadie — como mucho,
si se agota el cupo gratuito diario de una key, esos escaneos fallan ese
día y caen al OCR clásico existente.

Restricción explícita del usuario (aclarada en brainstorming): la app es
de uso doméstico, no comercial — **no** se bundlea una key de fábrica
compartida; cada usuario mete la suya.

## Alcance

- Nueva vía de interpretación de tickets vía Gemini, que **sustituye
  completamente** al OCR clásico para un usuario que tenga una API key
  configurada (no son dos modos que conviven por escaneo; es "si hay key,
  se usa; si no, no").
- Configuración de la API key en la pantalla de Ajustes ya existente
  (`ManageZonesScreen.kt`), no una pantalla de ajustes nueva.
- Si la llamada a Gemini falla por cualquier motivo en un escaneo concreto
  (sin red, key inválida, cuota agotada, respuesta inesperada), ese
  escaneo cae automáticamente al camino de OCR clásico (ML Kit +
  `parseReceiptLines`), con un aviso corto al usuario explicando que se
  usó el modo clásico.
- Fuera de alcance: streaming de la respuesta, reintentos automáticos con
  backoff, telemetría de uso/cuota, soporte para proveedores de IA
  distintos de Gemini, edición del prompt desde la UI.

## Arquitectura y flujo de datos

`PurchaseHistoryScreen.processReceiptUri(uri)` pasa a consultar primero si
hay una API key guardada:

```
hay key guardada?
  no  -> camino actual sin cambios: recognizeReceiptTextLines() + parseReceiptLines()
  sí  -> GeminiReceiptRecognizer.recognize(uri, key)
           éxito -> usar su List<ParsedReceiptLine> directamente
                    (NO pasa por parseReceiptLines; Gemini ya devuelve
                    nombre+precio estructurados)
           fallo -> snackbar informativo ("No se pudo usar IA (<motivo
                    corto>), se ha usado el modo clásico") y fallback al
                    camino actual (ML Kit + parseReceiptLines), sin
                    reintentar Gemini
```

`ParsedReceiptLine` (ya existente en `ReceiptParsing.kt`) se mantiene como
el tipo de salida común de ambos caminos — la pantalla de revisión
(`ReceiptReviewSheet`) no necesita saber cuál de los dos se usó.

## Componentes nuevos

- **`GeminiApiKeyStore`** (`data`): almacén dedicado de la API key,
  usando `EncryptedSharedPreferences` (Android Keystore por debajo) — no
  el `DataStore` en texto plano que usa `UserPrefs` para nombre/código de
  casa, porque una API key es una credencial, no un dato de perfil.
  Expone `val apiKey: Flow<String?>`, `suspend fun save(key: String)`,
  `suspend fun clear()`.
- **`GeminiReceiptApi`** + **`GeminiReceiptClient`** (`data`): mismo
  patrón que `OpenFoodFactsApi.kt` — interfaz Retrofit + Gson apuntando a
  `generativelanguage.googleapis.com` (endpoint gratuito de Google AI
  Studio). El modelo exacto y la forma final del endpoint/`responseSchema`
  se confirman contra la documentación vigente de Gemini al implementar
  (evitar fijar aquí un nombre de modelo que pueda haber quedado
  obsoleto).
- **`GeminiReceiptRecognizer`** (`data`): función suspend
  `recognize(context, imageUri, apiKey): List<ParsedReceiptLine>` que:
  1. Lee y codifica la foto en base64 (reutilizando el mismo límite de
     tamaño/downsampling que `ReceiptTextRecognizer.MAX_OCR_SIDE`, para no
     mandar una imagen a resolución nativa de cámara).
  2. Construye la petición con la imagen + un prompt pidiendo la lista de
     productos y precios del ticket, forzando salida JSON estructurada
     (`{"products":[{"name": string, "price": number}]}`) vía el
     mecanismo de `responseSchema` de Gemini, para no depender de parsear
     texto libre.
  3. Mapea la respuesta a `List<ParsedReceiptLine>`.
  4. Lanza excepciones tipadas según la causa —
     `GeminiAuthException` (401/403), `GeminiQuotaException` (429),
     `GeminiNetworkException` (sin conexión/timeout),
     `GeminiResponseException` (JSON inesperado/vacío) — para que la
     capa de UI decida el mensaje de fallback sin tener que inspeccionar
     códigos HTTP.
- **Tarjeta nueva en `ManageZonesScreen`** ("Ajustes"): campo de texto
  para la API key (enmascarado tipo contraseña, con icono para
  mostrar/ocultar), botón "Guardar" y botón "Quitar key". Al pulsar
  Guardar se hace una llamada mínima de validación contra Gemini antes de
  persistir; si falla, se muestra el motivo (misma jerarquía de
  excepciones típadas) y no se guarda nada. Incluye un texto corto de
  ayuda ("¿Cómo consigo mi clave gratuita?") con enlace directo a la
  página de creación de keys en Google AI Studio y 2-3 líneas de
  instrucciones en español.

## Seguridad y almacenamiento

- La key vive únicamente en `EncryptedSharedPreferences`, local al
  dispositivo — no se sincroniza a Firestore ni se comparte entre
  miembros del hogar (cada persona configura la suya en su propio
  móvil).
- Nunca se escribe la key en logs (`Log.d`, etc.) ni en mensajes de
  error mostrados al usuario.
- La foto del ticket se envía a la API de Google al usar este modo; se
  menciona explícitamente en el texto de ayuda de la tarjeta de Ajustes
  para que el usuario lo sepa antes de activarlo.

## Manejo de errores

Reutiliza el patrón ya fijado en la app (exponer errores vía
`state.error`/snackbar, nunca fallos silenciosos):

- Guardar key inválida → error inmediato en la propia tarjeta de
  Ajustes, no se guarda.
- Fallo de Gemini durante un escaneo → snackbar corto + fallback
  automático a OCR clásico (nunca se deja al usuario sin resultado
  cuando el clásico sí podría darle algo).
- 0 resultados (de cualquiera de los dos caminos) → se mantiene el aviso
  ya existente (`purchase_history_ocr_empty`).

## Testing

- Unit tests (JVM, sin red) para:
  - Mapeo de una respuesta JSON de ejemplo de Gemini a
    `List<ParsedReceiptLine>`.
  - Mapeo de códigos de estado HTTP (401, 403, 429, 5xx, timeout) a las
    excepciones tipadas correspondientes.
  - `GeminiApiKeyStore` (si es viable sin instrumentación de Android; si
    no, se deja fuera y se valida a mano igual que el resto del flujo de
    cámara/OCR).
- La llamada real a la API de Gemini y el flujo end-to-end en pantalla no
  son testeables de forma automática (dependen de red + key real); se
  valida a mano en dispositivo con tickets reales, igual que se ha hecho
  con el OCR clásico en esta misma sesión.

## Dependencias nuevas

- `androidx.security:security-crypto` (almacenamiento cifrado de la API
  key, gratis).
- Ninguna dependencia nueva de red: se reutiliza Retrofit + Gson, ya
  presentes en el proyecto para `OpenFoodFactsApi`.
