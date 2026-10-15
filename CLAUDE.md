# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es

SNHome (paquete `com.homepantry.app`): app Android nativa (Kotlin + Jetpack Compose, Material 3) para compartir en tiempo real la lista de la compra, el inventario por zonas, el historial de tickets y el menú semanal entre los miembros de una casa. Se instala por sideload en un único móvil objetivo, un Samsung S25 (arm64), y por eso el build filtra a `arm64-v8a` y solo a recursos en español.

- `SPEC.md` es el spec original (histórico) y explica la arquitectura base. Las funciones posteriores (tickets, subzonas, menú, estadísticas) tienen su diseño en `docs/superpowers/specs/` y sus planes en `docs/superpowers/plans/`.
- `web-prototype-reference.html` es el prototipo web validado con el usuario. Sirve de referencia de comportamiento, no para portarlo línea a línea.

## Comandos

En Git Bash, desde la raíz del repo. Hacen falta `local.properties` con `sdk.dir` y `app/google-services.json`; ninguno de los dos se sube a git.

```bash
./gradlew assembleDebug                 # APK en app/build/outputs/apk/debug/
./gradlew assembleRelease               # R8 + shrinkResources, firmado con la clave debug (sideload)
./gradlew testDebugUnitTest             # tests unitarios JVM (JUnit 4, sin Robolectric ni tests instrumentados)
./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ReceiptParsingTest"
./gradlew testDebugUnitTest --tests "com.homepantry.app.data.ReceiptParsingTest.nombre del test"
./gradlew lintDebug                     # informe en app/build/reports/lint-results-debug.txt
```

Verificación completa que usan los planes antes de cerrar una tarea: `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`. Debe acabar con 0 fallos de test, 0 errores de lint y un `app-release.apk` de unos 4,5 MB. Después se prueba el APK **release** en el S25, porque es el que pasa por R8.

Las reglas de `firestore.rules` y `storage.rules` se despliegan con la CLI de Firebase (`firebase.json` / `.firebaserc`).

## Arquitectura

**Arranque (`MainActivity.kt`):** inicia sesión anónima en Firebase Auth (si falla, pantalla con botón de reintentar). Después lee de DataStore (`UserPrefs`) el código de casa y el nombre; si falta alguno de los dos, muestra `JoinHouseholdScreen`. Si están, crea a mano (con un `ViewModelProvider.Factory` anónimo, sin DI) los dos ViewModels, con el código de casa como clave, y monta un `NavHost` con rutas en string (`zonesDashboard`, `mainList`, `menu`, `search`, `zoneDetail/{zoneId}`, `purchaseHistory`, `purchaseTicket/{ticketKey}`, …). Los bottom sheets y diálogos globales (`AddItemSheet`, etc.) se controlan con estado local en `ListaDeLaCasaApp`, no con rutas.

**ViewModels (MVVM con un único `StateFlow`):**
- `AppViewModel`: productos, zonas, miembros, compras y fotos. Expone `UiState`, que calcula las vistas derivadas (secciones, filas planas, tarjetas de zona, tickets, resúmenes) mediante funciones puras.
- `MenuViewModel`: platos, entradas del menú por día y personas. Lee también `ItemsRepository` para comprobar ingredientes.

**Capa `data/`:** cada colección tiene un repositorio que recibe `(FirebaseFirestore, householdCode)`. Los datos están en `households/{code}/{items|zones|members|purchases|dishes|mealEntries|people}` y las fotos en Storage bajo `households/{code}/…`. Las lecturas son `callbackFlow` sobre `addSnapshotListener`; las escrituras son `suspend` con `.await()` y usan batches troceados en las operaciones masivas. No hay cuentas: cualquiera autenticado que conozca el código de casa puede leer y escribir.

**Lógica pura separada para poder testearla:** la lógica de negocio va en ficheros sin dependencias de Android (`*Logic.kt`, `ReceiptParsing`, `PurchaseAggregation`, `PurchaseStats`, `MenuCalendar`, `MenuImport`, `ProductNormalization`, `Validation`, `Zone`, `ui/ItemListLogic.kt`…). Los tests de `app/src/test` cubren solo esas funciones, así que la lógica nueva testeable debe ir ahí y no dentro de Composables o repositorios.

**Integraciones externas:**
- Escaneo de códigos de barras con CameraX + ML Kit (versión de Play Services, sin empaquetar) y autocompletado del nombre con Open Food Facts (Retrofit).
- Tickets y menús leídos con OCR de ML Kit y con Gemini (`GeminiReceiptRecognizer`, `GeminiMenuRecognizer`, `GeminiReceiptApi` por REST a `generativelanguage.googleapis.com`). Los errores HTTP se convierten en subclases de `GeminiReceiptException`.
- Cada usuario pone su propia API key de Gemini, que nunca se sincroniza. `GeminiApiKeyStore` la cifra con el Android Keystore (`KeystoreSecretStorage`) y migra la del antiguo almacén `EncryptedSharedPreferences`. Las lecturas nunca lanzan, y un fallo pasajero del Keystore no debe borrar la clave.

## Convenciones y trampas

- **Modelos de Firestore:** `data class` con valores por defecto en todos los campos (Firestore necesita el constructor sin argumentos), `@DocumentId` para el id y `@ServerTimestamp` para las fechas. `proguard-rules.pro` conserva todo `com.homepantry.app.data.**` porque Firestore y Gson usan reflexión. Una clase serializable fuera de `data/` se rompe en release sin que falle la compilación.
- Para cambiar un solo campo se usa `update(campo, valor)`, no `set` del objeto entero, para no pisar los cambios que acabe de hacer otro miembro (ver `ItemsRepository.setDone`).
- Al capturar excepciones dentro de corrutinas, `CancellationException` se relanza siempre.
- La app y el código están en español: textos visibles en `res/values/strings.xml` (con `plurals` cuando corresponde), y comentarios y KDoc en español explicando el porqué. El tema es siempre oscuro y la paleta está en `ui/theme/Color.kt` (SPEC.md §6: el coral se reserva para alertas y para borrar).
- Commits en español con prefijo convencional (`feat:`, `fix:`, `build:`, `docs:`, `style:`).
- No cambiar sin motivo: JDK 17, `minSdk 26`, `compileSdk`/`targetSdk 36`, `abiFilters arm64-v8a`, `localeFilters es`, R8 en release. AGP y Gradle se quedan en la rama 8.x.
