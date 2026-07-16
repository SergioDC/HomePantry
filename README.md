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
```bash
./gradlew assembleDebug
```
El APK generado queda en `app/build/outputs/apk/debug/app-debug.apk`, instalable
directamente en un móvil Android (activando "orígenes desconocidos").

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
