# SNHome — App Android nativa

## Contexto para Claude Code
Este proyecto es la versión nativa Android de una app web ya validada con el usuario
(un prototipo HTML funcionando). El objetivo de esta versión es una app instalable
(.apk) que TODOS los miembros de una casa usan desde su propio móvil Android, viendo
y editando la MISMA lista de compra/despensa en tiempo real.

Ya se creó el andamiaje del proyecto (Gradle, manifest, modelos de datos, repositorio
Firebase). **Tu tarea es completar las pantallas de Compose UI, conectar el
repositorio, implementar cámara/escaneo de código de barras, y dejar el proyecto
listo para compilar (`./gradlew assembleDebug`) y subir a GitHub.**

---

## 1. Qué hace la app (funcionalidad, ya validada con el usuario)

### 1.1 Lista compartida en tiempo real
- Todos los miembros de la casa ven y editan la misma lista.
- No hace falta login tradicional: se usa un **código de casa** (household code) que
  cada miembro ingresa una sola vez (ej. "CASA-4821"). Ese código determina el
  documento Firestore que comparten. Guardarlo localmente (DataStore) para no
  pedirlo cada vez.
- Cada persona pone su **nombre** una vez (solo local, DataStore), que se adjunta a
  los productos que agrega ("pedido por Ana").

### 1.2 Productos
Cada producto tiene:
- `name`: String (nombre del producto)
- `qty`: Double (cantidad)
- `unit`: enum { UD, KG, G, L, ML, PAQUETE, DOCENA }
- `note`: String opcional (ej. "de la marca X, sin gluten")
- `zone`: String (referencia a una Zona, ver 1.3)
- `photoUrl`: String opcional (foto adjunta, subida a Firebase Storage)
- `barcode`: String opcional (código de barras escaneado)
- `done`: Boolean (comprado o no — NO se borra al comprar, se marca)
- `addedBy`: String opcional (nombre de quien lo agregó)
- `addedAt`: Timestamp

Acciones: crear, marcar comprado/pendiente, eliminar, "vaciar comprados" (borra
todos los `done == true` de una vez).

### 1.3 Zonas (configurables)
- Las zonas son **ubicaciones dentro de la casa relacionadas con la despensa**
  (NO tareas del hogar). Por defecto: Nevera, Congelador, Despensa, Otros.
- El usuario puede desde la app: **crear**, **renombrar** y **eliminar** zonas.
- Al eliminar una zona con productos dentro, esos productos se reasignan a "Otros"
  (confirmar con el usuario antes de eliminar, mostrando cuántos productos afecta).
- Debe existir siempre al menos 1 zona.
- Cada zona tiene un color asignado (rotar sobre una paleta fija) para diferenciarlas
  visualmente en chips/tags.

### 1.4 Pantalla principal
- Cabecera con nombre de la app, fecha actual, y accesos a: nombre del usuario y
  gestión de zonas (ícono de ajustes).
- Barra/indicador de progreso: "X de Y listos" (comprados vs total).
- Tabs horizontales por zona (incluye "Todos") para filtrar.
- Lista de productos agrupada por zona, orden: pendientes primero, luego comprados
  (tachados, semi-transparentes). Cada tarjeta muestra: checkbox circular, nombre,
  cantidad+unidad, nota (si hay), miniatura de foto (si hay), quién lo pidió, botón
  eliminar.
- Botón flotante (FAB) para abrir el formulario de añadir producto.

### 1.5 Añadir producto (bottom sheet / pantalla)
Campos: nombre, cantidad + unidad, selector de zona (chips, con opción "+ nueva
zona" inline), nota opcional, y dos acciones de captura:
- **📷 Foto**: abre la cámara (o galería) del dispositivo, comprime la imagen antes
  de subirla a Firebase Storage, se ve como miniatura junto al producto.
- **📊 Escanear código de barras**: abre la cámara en vivo con **CameraX + ML Kit
  Barcode Scanning** (nativo de Google, funciona offline y es mucho más confiable
  que una librería JS). Al detectar un código:
  1. Intentar autocompletar el nombre del producto llamando a la API pública
     **Open Food Facts** (`https://world.openfoodfacts.org/api/v0/product/{barcode}.json`).
  2. Si no hay resultado o no hay conexión, dejar el campo nombre editable con el
     código visible para que el usuario lo complete a mano.

### 1.6 Gestión de zonas (pantalla o diálogo)
Lista de zonas existentes, cada una con: nombre editable inline, contador de
productos, botón eliminar (con confirmación). Campo para crear zona nueva al final.

---

## 2. Arquitectura técnica recomendada

- **Lenguaje/UI**: Kotlin + Jetpack Compose (Material 3)
- **Arquitectura**: MVVM simple — `ViewModel` + `StateFlow`, un `Repository` por
  entidad (`ItemsRepository`, `ZonesRepository`)
- **Backend/sync en tiempo real**: Firebase Firestore
  - Estructura sugerida:
    ```
    households/{householdCode}/items/{itemId}
    households/{householdCode}/zones/{zoneId}
    ```
  - Usar `addSnapshotListener` de Firestore para reactividad en tiempo real —
    equivalente al `window.storage` compartido del prototipo web.
- **Fotos**: Firebase Storage, path `households/{householdCode}/photos/{itemId}.jpg`
- **Persistencia local ligera** (nombre de usuario, código de casa): Jetpack
  DataStore Preferences
- **Cámara / escaneo**: CameraX + `com.google.mlkit:barcode-scanning`
- **Carga de imágenes**: Coil (`io.coil-kt:coil-compose`)
- **Navegación**: Navigation Compose (rutas: `list`, `addItem`, `manageZones`,
  `joinHousehold` si no hay código guardado)

## 3. Autenticación / identidad
No hace falta login con contraseña. Usar **Firebase Anonymous Auth** para que cada
dispositivo tenga un `uid` (necesario para las reglas de seguridad de Firestore),
combinado con el código de casa como clave de agrupación de datos. El nombre visible
es solo un dato de perfil local, no un sistema de cuentas.

## 4. Reglas de seguridad de Firestore (sugerencia inicial)
Cualquier usuario autenticado (aunque sea anónimo) puede leer/escribir dentro de su
`householdCode` — no hay nada sensible en una lista de compras, así que no hace
falta un modelo de permisos complejo:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /households/{householdCode}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## 5. Qué falta por hacer (checklist para Claude Code)
- [x] Crear proyecto Firebase (instrucciones en README.md; el usuario debe crear
      su propio proyecto y pegar su `google-services.json` real — el que hay en
      el repo local ahora es un placeholder solo para poder compilar)
- [x] Implementar `JoinHouseholdScreen` (ingresar/crear código de casa + nombre)
- [x] Implementar `MainListScreen` (Compose) según sección 1.4
- [x] Implementar `AddItemSheet` según sección 1.5, incluyendo CameraX + ML Kit
- [x] Implementar `ManageZonesScreen` según sección 1.6
- [x] Conectar `ItemsRepository` y `ZonesRepository` a Firestore con listeners en
      tiempo real
- [x] Manejo de estados vacíos, errores de red, y permisos de cámara (runtime
      permissions)
- [x] Ícono de app y nombre "SNHome"
- [x] Verificar que compila con `./gradlew assembleDebug` (BUILD SUCCESSFUL,
      `app/build/outputs/apk/debug/app-debug.apk` generado)
- [ ] Crear repo en GitHub y subir el proyecto (el remoto `origin` ya existe y
      tenía commits previos; el trabajo de esta sesión está commiteado en local,
      pendiente de `git push` con confirmación del usuario)
- [x] Documentar en el README los pasos para generar el `.apk` firmado si el
      usuario quiere instalarlo fuera de modo debug

## 6. Paleta de colores (mantener consistencia con el prototipo web validado)
Dirección elegida por el usuario: **menta como color principal**, con **verde lima**
como segundo acento y **coral** (su complementario) reservado para alertas puntuales
y la acción de eliminar — no usar coral para nada más, para que mantenga su valor
de "atención".
```
Primary (menta):     #0F6E56   (variantes: #1D9E75, #5DCAA5, #9FE1CB, #E1F5EE)
Lime (2do acento):    #639922   (variantes: #97C459, #C0DD97, #EAF3DE)
Coral (complementario, solo alertas/eliminar): #D85A30 (variante suave: #FAECE7)
Background:  #F4F8F5
Card:        #FFFFFF
Ink:         #16261F
Ink soft:    #6E8079
Línea/borde: #E3EEE7
Zonas (rotar): #1D9E75, #639922, #D85A30, #0F6E56, #97C459, #993C1D, #5DCAA5
```
En Compose, definir estos como `Color(...)` en un archivo `ui/theme/Color.kt` y
mapearlos en el `ColorScheme` de Material 3 (menta → `primary`, lima →
`secondary`, coral → `error`), para que Claude Code no tenga que adivinar la
intención de cada tono.

## 7. Referencia
El prototipo web (HTML) con toda esta lógica ya implementada en JS está incluido en
este mismo paquete como `web-prototype-reference.html` — sirve como referencia
1:1 de comportamiento esperado (no como código a portar literalmente).
