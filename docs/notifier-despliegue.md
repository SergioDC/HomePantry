# Despliegue del notificador de SNcount

Cómo quedó configurado el 10/11/2026 el envío de push de SNcount (Tareas 2 y 3 del plan
`docs/superpowers/plans/2026-11-10-aviso-cierre-sncount.md`). El código y su diseño están en
`tools/notifier/README.md` y `docs/superpowers/specs/2026-11-10-aviso-cierre-sncount-design.md`.

**El repo es público.** Aquí no hay claves, ni el código de casa, ni ids del proyecto de Firebase.

## Resumen

| Pieza | Estado |
| --- | --- |
| Cuenta de servicio | `snhome-notifier`, con los roles **Cloud Datastore User** y **Firebase Cloud Messaging API Admin** |
| API de FCM (V1) | Habilitada (ya lo estaba) |
| `firestore.rules` | Sin cambios |
| NAS | DS224+, Docker 24.0.2, Compose v2.20.1 (Container Manager) |
| Carpeta | `/volume1/docker/snhome-notifier` |
| Código desplegado | Commit `23701b7` de `main` |
| Contenedor | `snhome-notifier`, `restart: unless-stopped`, 49 MiB de RAM en reposo (límite 256 MiB) |

## 1. Firebase / Google Cloud

- La cuenta de Google con acceso al proyecto es la **tercera** sesión de Chrome (`/u/2/` en la
  consola de Firebase y `authuser=2` en la de Google Cloud). Las otras dos no tienen permisos.
- Cuenta de servicio dedicada `snhome-notifier`, creada desde **IAM y administración → Cuentas de
  servicio**. No se usa la `firebase-adminsdk` por defecto porque tiene permisos de sobra.
- Roles (en la consola, en español):
  - Usuario de Cloud Datastore (`roles/datastore.user`).
  - Administrador de la API de Firebase Cloud Messaging (`roles/firebasecloudmessaging.admin`).
- Clave JSON creada el 10/11/2026. Es la única clave de esa cuenta.
- La clave da acceso a todos los datos de la casa. Copias que existen:
  - NAS: `/volume1/docker/snhome-notifier/secrets/sa.json`.
  - PC: `docs/secrets/sa-notifier.json` (carpeta ignorada por git).
- No confundir con `docs/secrets/sa.json`, que es la clave `firebase-adminsdk` del script de ofertas.

### Rotar o revocar la clave

1. Google Cloud → Cuentas de servicio → `snhome-notifier` → **Claves → Agregar clave → JSON**.
2. Sustituir `secrets/sa.json` en el NAS (mismo `chown` y `chmod` que abajo) y en `docs/secrets/`.
3. `docker compose up -d` para que el contenedor la recargue.
4. Borrar la clave antigua en la misma pantalla de Claves.

Si se filtra, basta con borrar la clave en esa pantalla: el notificador deja de funcionar al momento.

## 2. NAS

### Ficheros

```
/volume1/docker/snhome-notifier/      root:root
├── .dockerignore  Dockerfile  compose.yaml  package.json  package-lock.json  README.md
├── .env                              root:root  600
├── .env.example
├── secrets/                          usuario de DSM:users
│   └── sa.json                       1026:100   400
├── src/
└── test/
```

- `.env` sale de `.env.example` con tres valores: `HOUSEHOLD_CODE` (el código de casa),
  `PUID=1026` y `PGID=100`. Son el UID y GID del usuario de DSM dueño de `sa.json`.
- `compose.yaml` ejecuta el contenedor como `PUID:PGID`, con sistema de ficheros de solo lectura,
  `sa.json` montado en solo lectura, `mem_limit: 256m`, zona `Europe/Madrid` y logs de 2 × 1 MB.
- El contenedor no abre puertos. Solo hace conexiones salientes a Firestore y FCM.

### Cómo se instaló

No había SSH: el shell local no arranca porque la virtualización está desactivada en la BIOS del PC.
Se hizo todo desde la web de DSM:

1. **File Station:** crear `docker/snhome-notifier/secrets` y subir la clave como `sa-notifier.json`.
2. **Panel de control → Programador de tareas → Crear → Tarea activada → Script definido por
   usuario**, con usuario `root`, sin marcar *Habilitado* y con el script
   `bash /volume1/docker/snhome-notifier-setup.sh`. Se lanzó a mano con **Ejecutar**.
3. Ese script descargó `tools/notifier` de GitHub, preparó `secrets/` y `.env`, construyó la
   imagen e hizo la prueba en seco. Una segunda versión del script levantó el contenedor.
4. La salida de cada paso quedó en `/volume1/docker/snhome-notifier-setup.log`, que se lee con
   File Station → **Abrir con → Editor de texto**.
5. Al acabar se borró la tarea. Una tarea de root que ejecuta un fichero de una carpeta
   compartida es una puerta para escalar privilegios, así que no hay que dejarla creada.

Equivalente por SSH, para la próxima vez:

```bash
cd /volume1/docker/snhome-notifier
id <usuario_dsm>                          # uid=1026 gid=100(users)
sudo chown 1026:100 secrets/sa.json && sudo chmod 400 secrets/sa.json
cp .env.example .env && vi .env           # HOUSEHOLD_CODE, PUID, PGID
sudo docker compose run --rm snhome-notifier node src/index.mjs --once --dry-run
sudo docker compose up -d --build
```

### Resultado de la verificación

- Prueba en seco: `--once terminado (dry-run)`, sin errores.
- Registros tras levantarlo: `notificador en marcha`.
- `docker stats`: 49,24 MiB / 256 MiB (19 %), CPU 0 %.

## 3. Operación

Por SSH, desde `/volume1/docker/snhome-notifier`:

| Qué | Comando |
| --- | --- |
| Ver registros | `sudo docker compose logs --tail 50 -f` |
| Estado y RAM | `sudo docker compose ps` y `sudo docker stats --no-stream snhome-notifier` |
| Reiniciar | `sudo docker compose restart` |
| Parar / arrancar | `sudo docker compose stop` / `sudo docker compose up -d` |
| Prueba en seco | `sudo docker compose run --rm snhome-notifier node src/index.mjs --once --dry-run` |
| Lanzar un ciclo real | `sudo docker compose run --rm snhome-notifier node src/index.mjs --once` |

Sin SSH: Container Manager → **Contenedor → snhome-notifier** permite ver registros, RAM y
reiniciar. El contenedor se creó con `docker compose`, así que puede no salir en la lista de
**Proyecto** de Container Manager.

### Actualizar el código

1. Copiar `tools/notifier` encima de la carpeta del NAS sin `node_modules`. No hay que tocar
   `secrets/` ni `.env`.
2. `sudo docker compose up -d --build`.
3. Comprobar `notificador en marcha` en los registros.

No hace falta guardar estado: `notifiedAt` y `reminderSentAt` viven en Firestore, así que el
contenedor se puede borrar y volver a crear.

## 4. Problemas encontrados

- **«Error de red» al guardar la tarea en el Programador de tareas:** las peticiones devolvían
  HTTP 200, así que era un rechazo de DSM. Pasó con el script largo escrito en el cuadro de texto.
  Con una sola línea (`bash /ruta/script.sh`) se guardó a la primera.
- **Subidas de File Station en la carpeta equivocada:** al subir por el campo de fichero de la
  web, los ficheros cayeron en `docker/` aunque la vista estaba en `docker/snhome-notifier/secrets`.
  Hay que comprobar dónde quedan después de subirlos.
- **Clave con el mismo nombre que la de ofertas:** el plan pedía `docs/secrets/sa.json`, pero ese
  nombre ya era la clave del script de ofertas. La del notificador se guardó como
  `sa-notifier.json`. En el NAS sí se llama `sa.json`, que es lo que espera `compose.yaml`.

## 5. Pendiente

- Tarea 4 del plan: probar los push en los dos móviles con el APK release.
- `npm ci` avisa de 8 vulnerabilidades moderadas en dependencias de `tools/notifier`.
- Se pueden borrar `/volume1/docker/snhome-notifier-setup.sh` y su `.log`: solo sirvieron para la
  instalación.
