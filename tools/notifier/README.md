# Notificador de SNcount (NAS)

Proceso que corre en el NAS de casa y envía dos notificaciones push de SNcount:

- **Aviso de cierre:** cuando alguien cierra un grupo, avisa al momento a los demás participantes.
- **Recordatorio de fin de mes:** a las 20:00 del antepenúltimo día del mes, si un grupo de tipo mes
  sigue abierto. Si el NAS estuvo apagado, lo recupera hasta el día 3 del mes siguiente. Nunca
  entre las 22:00 y las 09:00.

Diseño: `docs/superpowers/specs/2026-11-10-aviso-cierre-sncount-design.md`.

Solo hace conexiones salientes a Firestore y FCM: no abre puertos ni guarda datos. Todo el estado
(`notifiedAt`, `reminderSentAt`) está en Firestore, así que el contenedor se puede borrar y volver
a crear sin perder nada.

**El repo es público.** Los logs solo llevan recuentos. No añadas logs con nombres, ids, tokens ni
el código de casa.

## 1. Cuenta de servicio dedicada

No uses la `firebase-adminsdk` por defecto: tiene permisos de sobra.

1. Google Cloud Console del proyecto Firebase → **IAM y administración → Cuentas de servicio →
   Crear**. Nombre: `snhome-notifier`.
2. Roles: **Cloud Datastore User** y **Firebase Cloud Messaging API Admin**.
3. En la cuenta creada → **Claves → Agregar clave → JSON**. Se descarga el fichero.
4. Firebase Console → Configuración del proyecto → **Cloud Messaging**: la «API de Firebase Cloud
   Messaging (V1)» tiene que estar habilitada.

Ese JSON da acceso total a los datos de la casa: no lo subas al repo ni lo dejes en una carpeta
compartida.

## 2. Instalación en el DS224+

1. Instala **Container Manager** desde el Centro de paquetes.
2. Copia esta carpeta (`tools/notifier`) a `/volume1/docker/snhome-notifier`, sin `node_modules`.
3. Dentro, crea `secrets/sa.json` con el JSON de la cuenta de servicio y `.env` a partir de
   `.env.example`.
4. Por SSH, deja el JSON legible solo por el usuario del contenedor y apunta su UID y GID en `.env`:
   ```bash
   cd /volume1/docker/snhome-notifier
   id tu_usuario                       # uid=1026(...) gid=100(users)
   sudo chown 1026:100 secrets/sa.json && sudo chmod 400 secrets/sa.json
   ```
5. Container Manager → **Proyecto → Crear**, ruta `/volume1/docker/snhome-notifier`, «Usar el
   compose.yaml existente». Se construye la imagen y arranca. Por SSH es lo mismo:
   ```bash
   sudo docker compose up -d --build
   ```
6. En los registros del contenedor tiene que salir `notificador en marcha`.

Prueba en seco (no envía ni escribe nada):
```bash
sudo docker compose run --rm snhome-notifier node src/index.mjs --once --dry-run
```

Para actualizar: copia la carpeta nueva encima y `sudo docker compose up -d --build`.

## Consumo

60–90 MB de RAM (límite 256 MB), CPU a ~0 % en reposo, imagen de unos 180 MB y logs topados a
2 MB. Menos de 100 lecturas de Firestore al mes; FCM es gratis.

## En local

```bash
cd tools/notifier
npm install
npm test
HOUSEHOLD_CODE=... FIREBASE_SERVICE_ACCOUNT_FILE=/ruta/sa.json node src/index.mjs --once --dry-run
```
