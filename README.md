# Lista de la Casa (Android)

App nativa Android para gestionar la lista de compra/despensa compartida entre los
miembros de una casa, en tiempo real.

## Para Claude Code
Leer primero **SPEC.md** — tiene toda la especificación funcional y técnica. Este
README es solo la puesta en marcha del entorno.

## Requisitos antes de compilar
1. **Android Studio** (o el SDK de línea de comandos) instalado, con:
   - Android SDK Platform 34
   - Kotlin plugin actualizado
2. **Un proyecto Firebase** (gratis):
   - Ir a https://console.firebase.google.com → crear proyecto
   - Añadir una app Android con el `applicationId`: `com.listacasa.app`
   - Descargar el archivo `google-services.json` generado y colocarlo en
     `app/google-services.json` (NO se sube a git, ver `.gitignore`)
   - Activar en la consola de Firebase:
     - **Firestore Database** (modo producción, luego pegar las reglas de
       seguridad de `SPEC.md` sección 4)
     - **Authentication → Anonymous** (habilitar el proveedor anónimo)
     - **Storage** (para las fotos de productos)

## Compilar
El wrapper de Gradle ya está incluido en el repo. Antes de compilar, crea
`local.properties` (no se sube a git) apuntando a tu SDK de Android, por ejemplo:
```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```
En Windows, escapa las barras y los dos puntos: `sdk.dir=C\:\\Android`.

```bash
./gradlew assembleDebug
```
El APK generado queda en `app/build/outputs/apk/debug/app-debug.apk`, instalable
directamente en un móvil Android (activando "orígenes desconocidos").

## Generar APK firmado (release)
Para instalar fuera de modo debug (por ejemplo, para repartir el APK a los
miembros de la casa sin pasar por Play Store) hace falta firmarlo:

1. Generar un keystore (una sola vez, guárdalo en un sitio seguro, **no** en el repo):
   ```bash
   keytool -genkey -v -keystore release.keystore -alias listacasa \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Añadir la ruta y contraseñas a tu `local.properties` local (nunca se commitea):
   ```properties
   RELEASE_STORE_FILE=/ruta/a/release.keystore
   RELEASE_STORE_PASSWORD=tu_password
   RELEASE_KEY_ALIAS=listacasa
   RELEASE_KEY_PASSWORD=tu_password
   ```
3. Añadir un `signingConfigs` en `app/build.gradle.kts` que lea esas propiedades:
   ```kotlin
   import java.util.Properties

   val localProps = Properties().apply {
       val f = rootProject.file("local.properties")
       if (f.exists()) load(f.inputStream())
   }

   android {
       signingConfigs {
           create("release") {
               storeFile = localProps.getProperty("RELEASE_STORE_FILE")?.let { file(it) }
               storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
               keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
               keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
           }
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
               // ... resto de la config existente
           }
       }
   }
   ```
4. Compilar:
   ```bash
   ./gradlew assembleRelease
   ```
   El APK firmado queda en `app/build/outputs/apk/release/app-release.apk`.

## Estructura
```
app/src/main/kotlin/com/listacasa/app/
  data/          -> modelos (Item, Zone, Household) y repositorios Firebase
  ui/screens/    -> pantallas Compose (lista principal, añadir producto, zonas)
  ui/components/ -> componentes reutilizables (tarjeta de producto, chips de zona...)
  MainActivity.kt
```

## Siguientes pasos sugeridos
Ver el checklist en `SPEC.md` sección 5.
