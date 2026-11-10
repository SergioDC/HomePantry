# Aviso de cierre y recordatorio de fin de mes de SNcount — pendiente en la máquina

> **For agentic workers:** el código ya está en `main` (commits `e13ce34`, `c401d2d`, `2f8121d`). Este plan es solo lo que falta comprobar y desplegar. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** dejar funcionando los push de SNcount: aviso inmediato al cerrar un grupo y recordatorio de fin de mes, enviados por el contenedor del NAS.

**Spec:** `docs/superpowers/specs/2026-11-10-aviso-cierre-sncount-design.md`.

## Estado

- Hecho y probado: `tools/notifier` (12 tests de Node en verde) y la lógica pura `SplitPush` / `SplitLink` (11 tests JUnit en verde, compilados fuera de Gradle).
- **Sin compilar:** el resto del código Android. Se escribió sin SDK de Android: puede haber errores de compilación o de lint que corregir.

Ficheros Android tocados: `SplitMonth.kt`, `SplitMonthsRepository.kt`, `SplitLink.kt`, `SplitPush.kt` (nuevo), `PushTokensRepository.kt` (nuevo), `PushMessagingService.kt` (nuevo), `SplitViewModel.kt`, `SplitMonthMenu.kt`, `SplitMonthsScreen.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` (`firebase-messaging`), `strings.xml`.

## Task 1: compilar y verificar la app

- [x] `git pull` en `main`.
- [x] `./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug assembleRelease`: 0 fallos de test y 0 errores de lint.
- [x] Puntos a revisar si falla la compilación:
  - `SplitMonthsRepository.setClosed`: `update(Map<String, Any?>)` con valores null.
  - `PushMessagingService.kt`: imports de `firebase-messaging` y `NotificationManagerCompat.notify` (lint `MissingPermission`).
  - `SplitMonthsScreen.kt`: permiso con accompanist, igual que `NotificationsScreen`.
- [x] Tamaño de `app-release.apk`: unos 4,5 MB más lo que añada `firebase-messaging`. Anotar el nuevo tamaño en `CLAUDE.md` si cambia.
- [x] Commit `fix:` con lo que haya hecho falta corregir.

> **2026-11-10:** compilado sin correcciones. 480 tests en verde, 0 errores de lint (solo avisos de versiones y un `UseKtx` en `PushMessagingService.kt:109`) y `app-release.apk` de 5,45 MB.

## Task 2: Firebase

- [x] Google Cloud Console → crear la cuenta de servicio `snhome-notifier` con los roles **Cloud Datastore User** y **Firebase Cloud Messaging API Admin**. Descargar la clave JSON.
- [x] Firebase Console → Configuración del proyecto → Cloud Messaging: comprobar que la API de FCM (V1) está habilitada.
- [x] No hace falta tocar `firestore.rules`: `pushTokens` y los campos nuevos de `splitMonths` quedan dentro de la regla de la casa.

> **2026-11-10:** cuenta `snhome-notifier` creada con los dos roles. La API de FCM (V1) ya estaba habilitada. La clave está solo en el NAS y en local, fuera del repo.

## Task 3: desplegar el notificador en el DS224+

Pasos detallados en `tools/notifier/README.md`.

- [x] Instalar Container Manager.
- [x] Copiar `tools/notifier` a `/volume1/docker/snhome-notifier` (sin `node_modules`).
- [x] Crear `secrets/sa.json` (`chown` al usuario de `PUID`, `chmod 400`) y `.env` desde `.env.example`.
- [x] Prueba en seco: `sudo docker compose run --rm snhome-notifier node src/index.mjs --once --dry-run`.
- [x] Levantar el proyecto y comprobar `notificador en marcha` en los registros.
- [x] Mirar la RAM real del contenedor en Container Manager (esperado: 60–90 MB).

> **2026-11-10:** Container Manager ya estaba instalado. Código copiado del commit `23701b7`, `secrets/sa.json` con `chown 1026:100` y `chmod 400`, y `.env` con `PUID=1026` y `PGID=100`. Sin SSH: se hizo con una tarea temporal de root en el Programador de tareas de DSM, ya borrada. La prueba en seco terminó sin errores (`--once terminado (dry-run)`). El contenedor está levantado, sale `notificador en marcha` en los registros y gasta 49 MiB de RAM según `docker stats` (límite 256 MiB), por debajo de lo esperado. `npm ci` avisa de 8 vulnerabilidades moderadas en dependencias.

## Task 4: probar en los móviles (APK release)

- [ ] Instalar el release en el S25 y en el otro móvil. Abrir SNcount y aceptar el permiso de notificaciones.
- [ ] Comprobar en Firestore que aparecen `households/{code}/pushTokens/{uid}` de los dos.
- [ ] Cerrar un grupo de mes en un móvil, con la app cerrada en el otro → llega «X cerrado», y al tocarla abre el grupo.
- [ ] Quien cierra no recibe push. La tarjeta muestra «Cerrado por X» en los dos.
- [ ] Reabrir y volver a cerrar → vuelve a avisar.
- [ ] Parar el contenedor, cerrar un grupo, arrancarlo → llega el aviso una sola vez.
- [ ] Recordatorio: crear un grupo de prueba del mes en curso y lanzar `--once` dentro de la ventana (o esperar al antepenúltimo día a las 20:00). Comprobar que solo llega una vez (`reminderSentAt`).
- [ ] Samsung: si los push llegan tarde, añadir SNHome a «Apps que nunca se suspenden».
