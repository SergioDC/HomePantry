# Transición de dependencias y del almacén de la clave de Gemini — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Poner al día las librerías de la app y sustituir `androidx.security:security-crypto` (obsoleta) sin romper nada en el S25, una tarea cada vez y sin empezar la siguiente hasta que la anterior haya pasado todas sus comprobaciones.

**Architecture:** Cada tarea cambia una sola capa del build (herramientas, compilador, UI, Firebase, resto de AndroidX, red, almacén cifrado), pasa la verificación automática **V1** y la prueba de humo en el móvil **V2**, y se cierra con un commit y una etiqueta `deps-paso-N`, que sirven de punto de vuelta atrás. El orden lo marcan las dependencias reales: Retrofit 3, Firebase BOM 35 y coroutines 1.11 traen metadatos de Kotlin 2.x, así que Kotlin 2 va antes; el almacén nuevo de la clave va en dos fases (migrar y luego limpiar) para no perder la clave guardada en ningún móvil.

**Tech Stack:** Gradle 8.13 → 8.14.6, AGP 8.13.0 → 8.13.2, Kotlin 1.9.24 → 2.4.21, Compose BOM 2024.10.01 → 2026.09.00, Firebase BOM 33.1.0 → 35.0.0, Retrofit 2.11.0 → 3.0.0, Android Keystore (AES/GCM) en lugar de EncryptedSharedPreferences.

**Spec:** sin spec de diseño propio. El alcance sale del barrido de estabilidad del 2026-10-10: son los 21 avisos de lint de versiones (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`) y las deprecaciones de `security-crypto` en `GeminiApiKeyStore.kt`.

## Global Constraints

- **Orden estricto:** no empezar la Tarea N+1 hasta que la Tarea N haya pasado V1 y V2 y tenga su commit y su etiqueta `deps-paso-N`.
- **Commits:** el CLAUDE.md global pide no commitear sin permiso. Cada tarea termina en commit y etiqueta; si el usuario no los ha autorizado expresamente al aprobar este plan, parar al final de la Tarea 0 y pedirlo.
- **Si una tarea no pasa V1 o V2 y no se arregla dentro de la propia tarea:** `git stash push -m "tarea N fallida"` y la app queda en `deps-paso-(N-1)`. No se pasa a la siguiente.
- **Alcance:** AGP 9.x y Gradle 9.x quedan fuera (es otra migración, con DSL y Kotlin integrado nuevos). Se queda en la rama 8.x: AGP 8.13.2 y Gradle 8.14.6.
- **No cambia:** JDK 17, `minSdk = 26`, `compileSdk = targetSdk = 36`, `abiFilters += "arm64-v8a"`, `localeFilters += "es"`, R8 con `isMinifyEnabled = true` y `isShrinkResources = true`, release firmado con la clave debug.
- Ninguna tarea cambia el comportamiento de la app, salvo la 7 (dónde se guarda la clave de Gemini, sin que el usuario lo note).
- **Avisos de deprecación nuevos:** se dejan. **Errores de compilación nuevos:** se corrigen en la propia tarea, con el cambio mínimo y sin cambiar comportamiento, y se anotan en el mensaje del commit.
- Textos visibles en español, en `res/values/strings.xml`.

### V1 — verificación automática (desde `C:\Programacion\Proyectos\HomePantry`, en Git Bash)

```bash
./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | awk -F'"' '{t+=$2;f+=$6;e+=$8} END {print "tests",t,"failures",f,"errors",e}'
grep -E "errors?, [0-9]+ warnings" app/build/reports/lint-results-debug.txt | tail -1
ls -l app/build/outputs/apk/release/app-release.apk
```

Pasa si:
- sale `BUILD SUCCESSFUL`;
- `failures 0 errors 0` y `tests` es al menos el de la tarea anterior (273 al empezar);
- lint dice `0 errors`;
- `app-release.apk` no supera 5.200.000 bytes (la línea base es 4.522.375, más un 15 %).

### V2 — prueba de humo en el S25 (APK **release**, no debug: es el que pasa por R8)

```bash
/c/Android/platform-tools/adb.exe devices          # el S25 debe aparecer como "device" (depuración USB activada)
/c/Android/platform-tools/adb.exe install -r app/build/outputs/apk/release/app-release.apk
```

Pasa si todos los puntos se cumplen y la app no se cierra en ningún momento:

1. **Arranque:** splash con el logo y luego Almacén con las zonas y sus conteos.
2. **Lista:** añadir «Prueba» con cantidad `0,5` y unidad kg. Aparece como «0,5 kg». Al marcarlo como comprado sale de la Lista y aparece en su zona.
3. **Tiempo real:** un cambio hecho en otro móvil de la casa aparece en menos de 5 s.
4. **Sin conexión:** con modo avión, cerrar y volver a abrir la app: abre con los datos. Añadir un producto, quitar el modo avión y comprobar que se sincroniza.
5. **Historial:** escanear un ticket con la cámara, revisarlo y guardarlo. Aparece en «Por mes» y en Estadísticas.
6. **Menú:** abrir un día y añadir un plato. «Compartir semana» abre el selector de compartir con la imagen.
7. **Ajustes:**
   - la clave de Gemini sigue configurada (el campo relleno y el botón «Quitar» visible);
   - tras cambiar el nombre, un producto nuevo dice «pedido por» con el nombre nuevo.
8. **Limpieza:** borrar el producto «Prueba» y el ticket de prueba (papelera del detalle del ticket) y devolver el nombre al original.

## Review Focus

- **Actualizar la app encima de la versión anterior conserva la clave de Gemini:** en la Tarea 7 se instala sobre la build de la Tarea 6 con la clave ya guardada y se comprueba en Ajustes y escaneando un ticket (paso 7.9). Los tests de `migrateSecret` (paso 7.1) cubren la lógica.
- **Clave cifrada ilegible** (restauración de copia de seguridad, otro móvil, clave del Keystore borrada): debe tratarse como «sin clave», sin cerrarse la app. `KeystoreSecretStorage.read` captura y borra (paso 7.5), y el fichero nuevo se excluye de las copias (paso 7.7).
- **Release con R8 tras subir Kotlin, Gson y Retrofit:** el escaneo de ticket con Gemini y el «Guardar» de la clave en Ajustes deben funcionar en el APK release, no solo en el debug (V2 de las tareas 2 y 6).
- **Arranque sin conexión con Firebase BOM 35:** la app abre desde la caché (punto 4 de V2, que en la Tarea 4 se repite dos veces).
- **Hojas modales tras subir Compose:** en «Añadir producto», atrás con el teclado abierto solo lo cierra; la hoja de revisión del ticket no se cierra al deslizar ni con atrás (comprobación extra en la Tarea 3).

---

### Tarea 0: Línea base

**Files:** ninguno (solo git y el móvil).

**Interfaces:**
- Produce: etiqueta `deps-paso-0` y la línea base de V1 (273 tests, 0 errores de lint, APK release de 4.522.375 bytes).

- [ ] **Paso 0.1:** Revisar qué queda sin commitear. Son los arreglos del barrido del 2026-10-10 y este plan:

```bash
git status --short
```

  Esperado: unos 41 archivos modificados o movidos bajo `app/`, `Images/logo_homepantry.png` y este plan. No debe aparecer `firebase-debug.log` ni nada bajo `app/build/`.

- [ ] **Paso 0.2:** Un único commit. Las dos rondas del barrido tocan los mismos archivos (strings, pantallas, AppViewModel), así que no se pueden separar limpiamente:

```bash
git add -A
git commit -m "fix: barrido de estabilidad (arranque sin red, zonas duplicadas, marcar comprado sin pisar cambios, nombre, coma decimal, permiso de cámara, fotos derechas, plurales y limpieza de lint)"
```

- [ ] **Paso 0.3:** Ejecutar V1. Esperado: 273 tests, `0 errors`, APK de unos 4,5 MB. Apuntar el tamaño exacto.
- [ ] **Paso 0.4:** Ejecutar V2 completa en el S25. Esta es además la prueba pendiente de los arreglos de hoy.
- [ ] **Paso 0.5:** Etiquetar la línea base.

```bash
git tag deps-paso-0
```

---

### Tarea 1: Herramientas de build (Gradle 8.14.6, AGP 8.13.2, google-services 4.4.4)

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties` (lo actualiza el comando del paso 1.1)
- Modify: `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` (también los regenera el paso 1.1)
- Modify: `build.gradle.kts` (raíz)

**Interfaces:**
- Consume: `deps-paso-0`.
- Produce: Gradle 8.14.6 y AGP 8.13.2, la base de la Tarea 2.

- [ ] **Paso 1.1:** Actualizar el wrapper. Se ejecuta dos veces: la segunda ya usa el Gradle nuevo y regenera los scripts.

```bash
./gradlew wrapper --gradle-version 8.14.6
./gradlew wrapper --gradle-version 8.14.6
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```

  Esperado: `distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.6-bin.zip`

- [ ] **Paso 1.2:** En `build.gradle.kts` (raíz), cambiar las versiones de AGP y google-services:

```kotlin
// Top-level build file
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}
```

- [ ] **Paso 1.3:** Ejecutar V1. Esperado: lint ya no da el aviso `AndroidGradlePluginVersion`.
- [ ] **Paso 1.4:** Ejecutar V2.
- [ ] **Paso 1.5:** Commit y etiqueta.

```bash
git add gradle/ gradlew gradlew.bat build.gradle.kts
git commit -m "build: Gradle 8.14.6, AGP 8.13.2 y google-services 4.4.4"
git tag deps-paso-1
```

---

### Tarea 2: Kotlin 2.4.21 con el plugin de compilador de Compose

**Files:**
- Modify: `build.gradle.kts` (raíz)
- Modify: `app/build.gradle.kts` (bloque `plugins`, `composeOptions` y `kotlinOptions`)

**Interfaces:**
- Consume: `deps-paso-1`.
- Produce: compilador Kotlin 2.x (K2). Las tareas 3 a 6 lo necesitan porque sus librerías traen metadatos de Kotlin 2.1 o posterior.

- [ ] **Paso 2.1:** En `build.gradle.kts` (raíz):

```kotlin
// Top-level build file
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}
```

- [ ] **Paso 2.2:** En `app/build.gradle.kts`, añadir el plugin de Compose al bloque `plugins`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}
```

- [ ] **Paso 2.3:** En `app/build.gradle.kts`, borrar entero este bloque. Con Kotlin 2, la versión del compilador de Compose la fija el plugin:

```kotlin
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
```

- [ ] **Paso 2.4:** En `app/build.gradle.kts`, borrar este bloque, que con Kotlin 2 está obsoleto:

```kotlin
    kotlinOptions {
        jvmTarget = "17"
    }
```

  y añadir este otro fuera de `android { }`, justo antes de `dependencies {`:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
```

- [ ] **Paso 2.5:** Compilar solo el código para ver los errores nuevos de K2:

```bash
./gradlew --no-daemon compileDebugKotlin compileDebugUnitTestKotlin
```

  Esperado: `BUILD SUCCESSFUL`. Si Gradle avisa de que Kotlin 2.4.21 no admite AGP 8.13.2, repetir los pasos 2.1 y 2.5 con `2.3.21` en las dos líneas de Kotlin. Si aparece un error `e:` en el código, corregirlo en ese archivo sin cambiar comportamiento y anotarlo para el commit.

- [ ] **Paso 2.6:** Ejecutar V1.
- [ ] **Paso 2.7:** Ejecutar V2. Además, escanear un ticket **con Gemini** (sin aviso de «fallback») y pulsar «Guardar» en la tarjeta de Gemini de Ajustes. Así se comprueba que Gson y R8 siguen leyendo los modelos compilados con K2.
- [ ] **Paso 2.8:** Commit y etiqueta.

```bash
git add build.gradle.kts app/build.gradle.kts app/src
git commit -m "build: Kotlin 2.4.21 con el plugin de compilador de Compose"
git tag deps-paso-2
```

---

### Tarea 3: Compose y AndroidX de UI

**Files:**
- Modify: `app/build.gradle.kts` (dependencias de Compose, activity, lifecycle, navigation y accompanist)
- Modify, solo si no compila: `app/src/main/kotlin/com/homepantry/app/ui/screens/ManageZonesScreen.kt:300`, `app/src/main/kotlin/com/homepantry/app/ui/components/BarcodeScannerView.kt:20`

**Interfaces:**
- Consume: `deps-paso-2` (Kotlin 2).
- Produce: Compose UI 1.12.1, material3 1.4.0 y material-icons-extended 1.7.8 (las versiones que fija el BOM 2026.09.00).

- [ ] **Paso 3.1:** En `app/build.gradle.kts`, sustituir estas líneas:

```kotlin
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    ...
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    ...
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
```

  por:

```kotlin
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    ...
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    ...
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")
```

  Las líneas `androidx.compose.ui:ui`, `ui-graphics`, `material3` y `material-icons-extended` se dejan sin versión: la pone el BOM.

- [ ] **Paso 3.2:** Compilar:

```bash
./gradlew --no-daemon compileDebugKotlin
```

  Si da error en `ManageZonesScreen.kt` por `MenuAnchorType`, cambiar el import y el uso:

```kotlin
import androidx.compose.material3.ExposedDropdownMenuAnchorType
// ...
modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
```

  Si da error en `BarcodeScannerView.kt` por `LocalLifecycleOwner`, cambiar el import:

```kotlin
import androidx.lifecycle.compose.LocalLifecycleOwner
```

  Esperado: `BUILD SUCCESSFUL`. Los avisos `is deprecated` (por ejemplo `TabRow` o `LocalClipboardManager`) se dejan.

- [ ] **Paso 3.3:** Ejecutar V1.
- [ ] **Paso 3.4:** Ejecutar V2, más estas comprobaciones de las hojas modales:
  - En «Añadir producto», con el teclado abierto, atrás solo cierra el teclado; un segundo atrás cierra la hoja.
  - La hoja de revisión del ticket no se cierra al deslizarla hacia abajo ni con atrás, solo con «Volver» o «Guardar».
  - Las pestañas de Historial («Por mes» / «Por producto»), del Menú y de Estadísticas (años y meses) cambian bien.
- [ ] **Paso 3.5:** Commit y etiqueta.

```bash
git add app/build.gradle.kts app/src
git commit -m "build: Compose BOM 2026.09.00, activity 1.13, lifecycle 2.11, navigation 2.10.2 y accompanist 0.37.3"
git tag deps-paso-3
```

---

### Tarea 4: Firebase BOM 35 (sin módulos -ktx) y coroutines-play-services 1.11

**Files:**
- Modify: `app/build.gradle.kts` (bloque `// Firebase` y la línea de `kotlinx-coroutines-play-services`)

**Interfaces:**
- Consume: `deps-paso-3`.
- Produce: Firebase sin los artefactos `-ktx`. En el BOM 35 ya no existen; el código no usa sus APIs, solo `FirebaseFirestore.getInstance()`, `FirebaseAuth.getInstance()`, `FirebaseStorage.getInstance()` y `.await()`.

- [ ] **Paso 4.1:** Comprobar que ningún archivo importa las extensiones KTX de Firebase:

```bash
grep -rn "import com.google.firebase.*\.ktx" app/src/main/kotlin
```

  Esperado: ninguna línea.

- [ ] **Paso 4.2:** En `app/build.gradle.kts`, sustituir:

```kotlin
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
```

  por:

```kotlin
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
```

  y `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")` por:

```kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
```

- [ ] **Paso 4.3:** Ejecutar V1.
- [ ] **Paso 4.4:** Ejecutar V2. Repetir el punto 4 (sin conexión) dos veces, porque Firestore guarda su caché local con un formato que podría cambiar entre versiones.
- [ ] **Paso 4.5:** Commit y etiqueta.

```bash
git add app/build.gradle.kts
git commit -m "build: Firebase BOM 35 (módulos sin -ktx) y coroutines-play-services 1.11"
git tag deps-paso-4
```

---

### Tarea 5: Resto de AndroidX y utilidades (CameraX, core, splash, exif, DataStore, Guava, Coil)

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consume: `deps-paso-4`.
- Produce: dependencias de cámara, splash, imágenes y DataStore al día.

- [ ] **Paso 5.1:** En `app/build.gradle.kts`, cambiar solo los números de versión:

```kotlin
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.guava:guava:33.7.2-android")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
```

  Las dos de ML Kit (`play-services-mlkit-*`) no se tocan: lint no avisa de ellas.

- [ ] **Paso 5.2:** Ejecutar V1.
- [ ] **Paso 5.3:** Ejecutar V2, fijándose en:
  - **Splash (punto 1):** el logo crece y se desvanece; si cambiara, `setOnExitAnimationListener` de `MainActivity.kt` es el sitio.
  - **Escaneo de ticket (punto 5):** probar también con una foto en vertical elegida de la galería, porque así se ejercita la orientación EXIF.
  - **Cambio de nombre (punto 7):** se guarda en DataStore.
- [ ] **Paso 5.4:** Commit y etiqueta.

```bash
git add app/build.gradle.kts
git commit -m "build: CameraX 1.6.2, core 1.19.1, splash 1.2.0, exif 1.4.2, DataStore 1.2.1, Guava y Coil"
git tag deps-paso-5
```

---

### Tarea 6: Retrofit 3

**Files:**
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/kotlin/com/homepantry/app/data/OpenFoodFactsApiTest.kt` y `GeminiReceiptRecognizerTest.kt` (ya existen; tienen que seguir pasando sin cambios)

**Interfaces:**
- Consume: `deps-paso-5` y Kotlin 2 (`deps-paso-2`), porque Retrofit 3.0.0 depende de `kotlin-stdlib` 2.1.21 y OkHttp 4.12.0.
- Produce: Retrofit 3, con las mismas clases `retrofit2.*` y `okhttp3.OkHttpClient` que usan `GeminiReceiptApi.kt` y `OpenFoodFactsApi.kt`.

- [ ] **Paso 6.1:** En `app/build.gradle.kts`:

```kotlin
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
```

- [ ] **Paso 6.2:** Pasar los tests de red:

```bash
./gradlew --no-daemon testDebugUnitTest --tests "com.homepantry.app.data.OpenFoodFactsApiTest" --tests "com.homepantry.app.data.GeminiReceiptRecognizerTest"
```

  Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 6.3:** Ejecutar V1.
- [ ] **Paso 6.4:** Ejecutar V2, más estas comprobaciones de red:
  - En Ajustes, pulsar «Guardar» en la tarjeta de Gemini con la clave actual: debe salir el aviso de clave guardada (valida la clave contra Google).
  - Escanear un ticket y comprobar que **no** sale el aviso de que se usó el lector clásico.
  - Importar el menú desde una foto (Menú → ⋮ → Importar) y descartar la revisión con atrás, sin guardar nada.
- [ ] **Paso 6.5:** Commit y etiqueta.

```bash
git add app/build.gradle.kts
git commit -m "build: Retrofit 3.0.0"
git tag deps-paso-6
```

---

### Tarea 7: Almacén nuevo de la clave de Gemini (Android Keystore), fase 1: migrar

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/SecretStorage.kt`
- Create: `app/src/main/kotlin/com/homepantry/app/data/KeystoreSecretStorage.kt`
- Modify: `app/src/main/kotlin/com/homepantry/app/data/GeminiApiKeyStore.kt` (se reescribe entero)
- Modify: `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- Test: `app/src/test/kotlin/com/homepantry/app/data/SecretStorageTest.kt`

**Interfaces:**
- Consume: `deps-paso-6`.
- Produce, para la Tarea 8:
  - `interface SecretStorage { fun read(): String?; fun write(value: String); fun clear() }`
  - `fun migrateSecret(legacy: SecretStorage, target: SecretStorage): String?`
  - `class PlainPrefStorage(prefs: SharedPreferences, key: String) : SecretStorage`
  - `class KeystoreSecretStorage(prefs: SharedPreferences, prefKey: String, keyAlias: String) : SecretStorage`
  - `GeminiApiKeyStore` conserva su API pública: `suspend fun getApiKey(): String?`, `suspend fun getModel(): String`, `suspend fun save(key: String, model: String)` y `suspend fun clear()`.

- [ ] **Paso 7.1: Escribir el test que falla.** Crear `app/src/test/kotlin/com/homepantry/app/data/SecretStorageTest.kt`:

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretStorageTest {
    private class FakeStorage(var value: String? = null) : SecretStorage {
        var cleared = false
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
        override fun clear() { value = null; cleared = true }
    }

    @Test fun `copies the legacy value when the new store is empty and clears the legacy one`() {
        val legacy = FakeStorage("clave-antigua")
        val target = FakeStorage()
        assertEquals("clave-antigua", migrateSecret(legacy, target))
        assertEquals("clave-antigua", target.value)
        assertNull(legacy.value)
    }

    @Test fun `keeps the new value when both stores have one, and still clears the legacy one`() {
        val legacy = FakeStorage("clave-antigua")
        val target = FakeStorage("clave-nueva")
        assertEquals("clave-nueva", migrateSecret(legacy, target))
        assertEquals("clave-nueva", target.value)
        assertNull(legacy.value)
    }

    @Test fun `with nothing in the legacy store returns the new value and touches nothing`() {
        val legacy = FakeStorage()
        val target = FakeStorage("clave-nueva")
        assertEquals("clave-nueva", migrateSecret(legacy, target))
        assertEquals(false, legacy.cleared)
    }

    @Test fun `with both stores empty returns null`() {
        assertNull(migrateSecret(FakeStorage(), FakeStorage()))
    }
}
```

- [ ] **Paso 7.2:** Ejecutarlo para ver que falla:

```bash
./gradlew --no-daemon testDebugUnitTest --tests "com.homepantry.app.data.SecretStorageTest"
```

  Esperado: falla al compilar con `Unresolved reference 'SecretStorage'` y `Unresolved reference 'migrateSecret'`.

- [ ] **Paso 7.3:** Crear `app/src/main/kotlin/com/homepantry/app/data/SecretStorage.kt`:

```kotlin
package com.homepantry.app.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Un único valor de texto guardado en el móvil (cifrado o no, según la implementación). */
interface SecretStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

/**
 * Pasa un valor del almacén antiguo al nuevo una sola vez: si el nuevo está vacío se copia el
 * antiguo, y el antiguo se borra siempre que tenga algo (el nuevo manda si ambos tienen valor).
 * @return el valor que queda en el almacén nuevo.
 */
fun migrateSecret(legacy: SecretStorage, target: SecretStorage): String? {
    val current = target.read()
    val old = legacy.read()
    if (current == null && old != null) target.write(old)
    if (old != null) legacy.clear()
    return current ?: old
}

/** Valor sin cifrar en SharedPreferences: para el modelo de Gemini, que no es secreto. */
class PlainPrefStorage(private val prefs: SharedPreferences, private val key: String) : SecretStorage {
    override fun read(): String? = prefs.getString(key, null)
    override fun write(value: String) = prefs.edit { putString(key, value) }
    override fun clear() = prefs.edit { remove(key) }
}
```

- [ ] **Paso 7.4:** Comprobar que el test pasa:

```bash
./gradlew --no-daemon testDebugUnitTest --tests "com.homepantry.app.data.SecretStorageTest"
```

  Esperado: `BUILD SUCCESSFUL` con los 4 tests en verde.

- [ ] **Paso 7.5:** Crear `app/src/main/kotlin/com/homepantry/app/data/KeystoreSecretStorage.kt`. No tiene test JVM, porque `AndroidKeyStore` solo existe en el móvil; se verifica en V2.

```kotlin
package com.homepantry.app.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_BITS = 128

/**
 * Valor cifrado con AES-GCM y una clave del Android Keystore (no sale del móvil), guardado en
 * SharedPreferences como Base64(iv + texto cifrado). Sustituye a EncryptedSharedPreferences,
 * obsoleta. Nunca lanza al leer: si el valor no se puede descifrar (copia restaurada en otro móvil,
 * clave del Keystore borrada), se borra y cuenta como "no hay valor".
 */
class KeystoreSecretStorage(
    private val prefs: SharedPreferences,
    private val prefKey: String,
    private val keyAlias: String
) : SecretStorage {

    override fun read(): String? {
        val stored = prefs.getString(prefKey, null) ?: return null
        return try {
            val key = existingKey() ?: throw IllegalStateException("sin clave en el Keystore")
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_SIZE))
            String(cipher.doFinal(bytes, GCM_IV_SIZE, bytes.size - GCM_IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    override fun write(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit { putString(prefKey, Base64.encodeToString(encrypted, Base64.NO_WRAP)) }
    }

    override fun clear() = prefs.edit { remove(prefKey) }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun getOrCreateKey(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }
        .generateKey()
}
```

- [ ] **Paso 7.6:** Reescribir `app/src/main/kotlin/com/homepantry/app/data/GeminiApiKeyStore.kt` entero:

```kotlin
package com.homepantry.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fichero de EncryptedSharedPreferences de versiones anteriores; solo se lee para migrar (Tarea 8 lo quita). */
private const val LEGACY_PREFS_FILE_NAME = "gemini_api_key_store"
private const val PREFS_FILE_NAME = "gemini_settings"
private const val KEY_API_KEY = "api_key"
private const val KEY_API_KEY_ENCRYPTED = "api_key_encrypted"
private const val KEY_MODEL = "model"
private const val KEYSTORE_ALIAS = "gemini_api_key"

/**
 * API key de Gemini que aporta cada usuario, cifrada con el Android Keystore
 * ([KeystoreSecretStorage]); el modelo elegido no es secreto y va sin cifrar. Nunca se
 * sincroniza entre miembros del hogar. Las lecturas nunca lanzan: se llaman en cada escaneo de
 * ticket, también para quien nunca configuró nada.
 */
class GeminiApiKeyStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }
    private val apiKey by lazy { KeystoreSecretStorage(prefs, KEY_API_KEY_ENCRYPTED, KEYSTORE_ALIAS) }
    private val model by lazy { PlainPrefStorage(prefs, KEY_MODEL) }

    @Volatile
    private var migrated = false

    /** Copia la clave y el modelo del almacén antiguo (si existe) y borra su fichero. Idempotente. */
    private fun migrateLegacyOnce() {
        if (migrated) return
        if (File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_FILE_NAME.xml").exists()) {
            migrateSecret(LegacyEncryptedStore(context, KEY_API_KEY), apiKey)
            migrateSecret(LegacyEncryptedStore(context, KEY_MODEL), model)
            context.deleteSharedPreferences(LEGACY_PREFS_FILE_NAME)
        }
        migrated = true
    }

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        try {
            migrateLegacyOnce()
            apiKey.read()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Mismo criterio de "nunca lanza" que [getApiKey]: sin modelo guardado, el modelo por defecto. */
    suspend fun getModel(): String = withContext(Dispatchers.IO) {
        try {
            migrateLegacyOnce()
            model.read()?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        } catch (e: Exception) {
            DEFAULT_GEMINI_MODEL
        }
    }

    suspend fun save(key: String, model: String) = withContext(Dispatchers.IO) {
        migrateLegacyOnce()
        apiKey.write(key.trim())
        this@GeminiApiKeyStore.model.write(model.trim().ifBlank { DEFAULT_GEMINI_MODEL })
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        // Migrar antes de borrar: si no, la siguiente lectura traería de vuelta la clave antigua.
        migrateLegacyOnce()
        apiKey.clear()
        model.clear()
    }
}

/** Lectura del almacén antiguo, solo para [migrateSecret]. Si no se puede abrir, cuenta como vacío. */
@Suppress("DEPRECATION")
private class LegacyEncryptedStore(private val context: Context, private val key: String) : SecretStorage {
    private val prefs: SharedPreferences? by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                LEGACY_PREFS_FILE_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun read(): String? = try {
        prefs?.getString(key, null)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    override fun write(value: String) = throw UnsupportedOperationException("El almacén antiguo es de solo lectura")

    override fun clear() {
        try {
            prefs?.edit { remove(key) }
        } catch (e: Exception) {
            // El fichero entero se borra justo después en migrateLegacyOnce.
        }
    }
}
```

- [ ] **Paso 7.7:** Excluir el fichero nuevo de las copias de seguridad. La clave del Keystore no viaja en la copia, así que el valor restaurado sería ilegible. En `app/src/main/res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="gemini_api_key_store.xml"/>
    <exclude domain="sharedpref" path="gemini_settings.xml"/>
</full-backup-content>
```

  y en `app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="gemini_api_key_store.xml"/>
        <exclude domain="sharedpref" path="gemini_settings.xml"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="gemini_api_key_store.xml"/>
        <exclude domain="sharedpref" path="gemini_settings.xml"/>
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Paso 7.8:** Ejecutar V1. Esperado: tests en 277 (los 273 de antes más los 4 nuevos). Los avisos de deprecación de `security-crypto` quedan solo dentro de `LegacyEncryptedStore`.
- [ ] **Paso 7.9:** Ejecutar V2 con este orden concreto, que es la prueba de la migración:
  1. **Antes de instalar:** en el S25, con la build de la Tarea 6, abrir Ajustes y confirmar que la clave de Gemini está guardada.
  2. Instalar la build nueva con `adb install -r`, **sin desinstalar** (si se desinstala, se pierde justo lo que hay que probar).
  3. Ajustes: la clave aparece rellena, con el mismo modelo, y se ve «Quitar».
  4. Escanear un ticket: va por Gemini, sin aviso de lector clásico.
  5. Cerrar la app desde recientes y volver a abrirla: la clave sigue ahí.
  6. Pulsar «Quitar», volver a abrir la app: no hay clave. Pegarla de nuevo y «Guardar»: se guarda.
  7. Hacer el resto de V2.
- [ ] **Paso 7.10:** Commit y etiqueta.

```bash
git add app/src/main/kotlin/com/homepantry/app/data/SecretStorage.kt \
  app/src/main/kotlin/com/homepantry/app/data/KeystoreSecretStorage.kt \
  app/src/main/kotlin/com/homepantry/app/data/GeminiApiKeyStore.kt \
  app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml \
  app/src/test/kotlin/com/homepantry/app/data/SecretStorageTest.kt
git commit -m "feat: clave de Gemini cifrada con Android Keystore, migrando la de EncryptedSharedPreferences"
git tag deps-paso-7
```

---

### Tarea 8: Almacén de la clave, fase 2: quitar security-crypto

> **Requisito previo, sin excepción:** **todos** los móviles de la casa que tenían clave de Gemini han abierto la build de la Tarea 7 al menos una vez y muestran la clave en Ajustes. Si se salta, ese móvil pierde la clave y hay que volver a pegarla.

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/GeminiApiKeyStore.kt` (quitar la migración y `LegacyEncryptedStore`)
- Modify: `app/src/main/kotlin/com/homepantry/app/data/SecretStorage.kt` (quitar `migrateSecret`)
- Delete: `app/src/test/kotlin/com/homepantry/app/data/SecretStorageTest.kt`
- Modify: `app/build.gradle.kts` (quitar `security-crypto`)
- Modify: `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` (quitar las líneas de `gemini_api_key_store.xml`)

**Interfaces:**
- Consume: `deps-paso-7` y el requisito previo.
- Produce: la misma API pública de `GeminiApiKeyStore`, sin dependencia de `androidx.security`.

- [ ] **Paso 8.1:** En `app/src/main/kotlin/com/homepantry/app/data/SecretStorage.kt`, borrar la función `migrateSecret` y su comentario. El archivo queda así:

```kotlin
package com.homepantry.app.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Un único valor de texto guardado en el móvil (cifrado o no, según la implementación). */
interface SecretStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

/** Valor sin cifrar en SharedPreferences: para el modelo de Gemini, que no es secreto. */
class PlainPrefStorage(private val prefs: SharedPreferences, private val key: String) : SecretStorage {
    override fun read(): String? = prefs.getString(key, null)
    override fun write(value: String) = prefs.edit { putString(key, value) }
    override fun clear() = prefs.edit { remove(key) }
}
```

- [ ] **Paso 8.2:** Borrar el test, que solo cubría `migrateSecret`:

```bash
git rm app/src/test/kotlin/com/homepantry/app/data/SecretStorageTest.kt
```

- [ ] **Paso 8.3:** Reescribir `app/src/main/kotlin/com/homepantry/app/data/GeminiApiKeyStore.kt` entero:

```kotlin
package com.homepantry.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_FILE_NAME = "gemini_settings"
private const val KEY_API_KEY_ENCRYPTED = "api_key_encrypted"
private const val KEY_MODEL = "model"
private const val KEYSTORE_ALIAS = "gemini_api_key"

/**
 * API key de Gemini que aporta cada usuario, cifrada con el Android Keystore
 * ([KeystoreSecretStorage]); el modelo elegido no es secreto y va sin cifrar. Nunca se
 * sincroniza entre miembros del hogar. Las lecturas nunca lanzan: se llaman en cada escaneo de
 * ticket, también para quien nunca configuró nada.
 */
class GeminiApiKeyStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }
    private val apiKey by lazy { KeystoreSecretStorage(prefs, KEY_API_KEY_ENCRYPTED, KEYSTORE_ALIAS) }
    private val model by lazy { PlainPrefStorage(prefs, KEY_MODEL) }

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        try {
            apiKey.read()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Mismo criterio de "nunca lanza" que [getApiKey]: sin modelo guardado, el modelo por defecto. */
    suspend fun getModel(): String = withContext(Dispatchers.IO) {
        try {
            model.read()?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        } catch (e: Exception) {
            DEFAULT_GEMINI_MODEL
        }
    }

    suspend fun save(key: String, model: String) = withContext(Dispatchers.IO) {
        apiKey.write(key.trim())
        this@GeminiApiKeyStore.model.write(model.trim().ifBlank { DEFAULT_GEMINI_MODEL })
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        apiKey.clear()
        model.clear()
    }
}
```

- [ ] **Paso 8.4:** En `app/build.gradle.kts`, borrar estas dos líneas:

```kotlin
    // Almacenamiento cifrado (API key de Gemini que aporta cada usuario)
    implementation("androidx.security:security-crypto:1.1.0")
```

- [ ] **Paso 8.5:** Dejar las reglas de copia de seguridad solo con el fichero nuevo. `app/src/main/res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="gemini_settings.xml"/>
</full-backup-content>
```

  `app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="gemini_settings.xml"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="gemini_settings.xml"/>
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Paso 8.6:** Comprobar que no queda rastro de la librería antigua:

```bash
grep -rn "androidx.security\|EncryptedSharedPreferences\|MasterKey\|gemini_api_key_store" app/src app/build.gradle.kts
```

  Esperado: ninguna línea.

- [ ] **Paso 8.7:** Ejecutar V1. Esperado: 273 tests y ningún aviso de compilación sobre `EncryptedSharedPreferences`.
- [ ] **Paso 8.8:** Ejecutar V2 instalando con `-r` encima de la Tarea 7: la clave de Gemini sigue ahí y el escaneo va por Gemini.
- [ ] **Paso 8.9:** Commit y etiqueta.

```bash
git add -A app/
git commit -m "build: quitar security-crypto tras migrar la clave de Gemini al Keystore"
git tag deps-paso-8
```

---

### Tarea 9: Cierre

**Files:** ninguno, salvo que V1 muestre algo inesperado.

**Interfaces:**
- Consume: `deps-paso-8`.
- Produce: la etiqueta `deps-fin`.

- [ ] **Paso 9.1:** Ejecutar V1 y listar los avisos de lint que quedan:

```bash
grep -E ": (Error|Warning):" app/build/reports/lint-results-debug.txt | sed -E 's/.*\[([A-Za-z]+)\]$/\1/' | sort | uniq -c
```

  Esperado: `0 errors`. Como mucho quedan avisos de versión que apuntan a AGP 9.x o Gradle 9.x, fuera de alcance según Global Constraints. Si aparece un aviso de otro tipo, se trata como un error de la tarea que lo introdujo.

- [ ] **Paso 9.2:** Comparar el tamaño de `app-release.apk` con la línea base de la Tarea 0 (4.522.375 bytes):

```bash
stat -c %s app/build/outputs/apk/release/app-release.apk
```

  Esperado: 5.200.000 bytes como máximo.

- [ ] **Paso 9.3:** Ejecutar V2 completa una última vez, y que otro miembro de la casa haga los puntos 2 y 3 en su móvil.
- [ ] **Paso 9.4:** Etiquetar el final con el tamaño medido:

```bash
git tag -a deps-fin -m "Transición de dependencias terminada. APK release: $(stat -c %s app/build/outputs/apk/release/app-release.apk) bytes"
```
