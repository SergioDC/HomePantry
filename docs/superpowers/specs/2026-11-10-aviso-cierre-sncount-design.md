# Aviso de cierre y recordatorio de fin de mes en SNcount — diseño

Fecha: 2026-11-10

## Intención

Dos notificaciones push para los grupos de SNcount de tipo mes:

1. **Recordatorio de fin de mes.** Si el mes sigue abierto cuando está acabando, se avisa a sus
   participantes para que lo ajusten y lo cierren.
2. **Aviso de cierre.** Cuando un miembro cierra un mes, los demás participantes reciben una
   notificación al momento, estén en casa o fuera y con la app cerrada.

La app no puede enviar push por sí sola: hace falta un emisor con credenciales de servidor. Ese
emisor es un contenedor en el NAS de casa (Synology DS224+), que habla con Firestore y FCM.

## Objetivos

- Cerrar y reabrir un grupo desde su menú ⋮. Un grupo cerrado muestra «Cerrado por X».
- Push inmediato a los demás participantes al cerrar. El que cierra no lo recibe.
- Un único recordatorio por mes, a las 20:00 (Madrid) dos días antes de fin de mes, si sigue abierto.
- Ningún aviso se pierde si el NAS está apagado: al volver procesa lo pendiente, sin duplicar.
- Tocar la notificación abre el grupo.
- El NAS no abre puertos ni guarda datos. Todo el estado vive en Firestore.

## No objetivos

- Recordatorios de actividades (viajes, cenas): no tienen fin de mes.
- Otros avisos por push (lista de la compra, gastos nuevos). El notificador queda preparado para
  añadirlos, pero no entran aquí.
- Varias casas: igual que `tools/offers`, el notificador trabaja con la casa de `HOUSEHOLD_CODE`.
- Mostrar estos avisos en la campana de Home. La bandeja actual está pensada para ofertas
  (`weekStart`, `stores`). El respaldo dentro de la app es la etiqueta «Cerrado por X» del grupo.
- Bloquear con reglas de Firestore los campos que escribe el NAS. En esta app cualquier miembro
  puede escribir cualquier documento de la casa, así que proteger un campo no añade seguridad.

## Alternativas descartadas

- **Solo en el móvil (JobScheduler/AlarmManager).** Sirve para el recordatorio, pero no puede
  avisar a otro móvil de que se ha cerrado un mes.
- **Credenciales de FCM dentro del APK.** Cualquiera que lo descompile tendría acceso total a
  Firestore. La API antigua con «server key» ya no existe.
- **Cloud Functions.** Exigen el plan Blaze.
- **GitHub Actions**, como `tools/offers`. Vale para el recordatorio (cron), pero no para el aviso
  de cierre: no hay un proceso escuchando, y el cron de Actions puede retrasarse horas.
- **ntfy.sh.** La notificación llegaría desde la app de ntfy y el topic estaría en un servidor público.

## Modelo de datos

### `households/{code}/splitMonths/{monthId}` (campos nuevos de `SplitMonth`)

| Campo | Tipo | Quién lo escribe | Significado |
|---|---|---|---|
| `closed` | Boolean | app | El grupo está cerrado. |
| `closedBy` | String | app | Uid del miembro que lo cerró; vacío si está abierto. |
| `closedAt` | Timestamp? | app | Momento del cierre (`serverTimestamp`); null si está abierto. |
| `notifiedAt` | Timestamp? | app (null) / NAS | null = cierre pendiente de avisar. El NAS lo rellena al avisar. |
| `reminderSentAt` | Timestamp? | NAS | Ya se envió el recordatorio de ese mes. |

- `closedAt` **no** lleva `@ServerTimestamp`: `createMonth` escribe el objeto entero y la anotación
  pondría la fecha de creación como fecha de cierre. Se escribe con `FieldValue.serverTimestamp()`.
- Al cerrar, la app escribe `notifiedAt = null` explícitamente. La consulta del NAS
  (`closed == true` y `notifiedAt == null`) no encuentra documentos sin el campo.
- Al reabrir: `closed = false`, `closedBy = ""`, `closedAt = null`. Si se vuelve a cerrar, se
  vuelve a avisar. `reminderSentAt` no se toca: un mes recibe un solo recordatorio.
- Cerrar no archiva ni bloquea la edición. Es una marca de «mes ajustado».

### `households/{code}/pushTokens/{uid}` (colección nueva)

| Campo | Tipo |
|---|---|
| `token` | String (token FCM del móvil) |
| `updatedAt` | Timestamp (`serverTimestamp`) |

Va aparte de `members` porque `MembersRepository.upsertSelf` reescribe el documento del miembro
entero en cada arranque y borraría el token.

## App Android

- **Dependencia:** `com.google.firebase:firebase-messaging` (versión del BOM).
- **Token:** al entrar en la casa, `FirebaseMessaging.getInstance().token` se guarda en
  `pushTokens/{uid}`. `onNewToken` del servicio hace lo mismo cuando el token cambia. Los fallos se
  ignoran: en el siguiente arranque se reintenta.
- **`PushMessagingService`:** recibe mensajes solo de datos y monta la notificación con los textos
  de `strings.xml`. Canal propio «SNcount».
- **Carga del mensaje** (`data`, todo String):

  | Clave | Valores |
  |---|---|
  | `type` | `sncount_closed` o `sncount_reminder` |
  | `monthId` | Id del grupo |
  | `monthName` | Nombre del grupo |
  | `byName` | Nombre de quien cerró (solo en `sncount_closed`) |

  `parseSplitPush` (lógica pura, con tests) valida la carga. Un mensaje desconocido o con un id
  inválido se descarta.
- **Notificación:** id `monthId.hashCode()` para los dos tipos. El aviso de cierre sustituye al
  recordatorio de ese mes si seguía en la bandeja. Al cerrar desde el propio móvil se cancela.
- **Toque:** `Intent` explícito a `MainActivity` con `data = splitShareUrl(monthId)`. Reutiliza
  la navegación de los enlaces de SNcount (`splitMonthIdFromLink`).
- **Permiso `POST_NOTIFICATIONS`:** se pide al abrir la lista de SNcount, como en Avisos. Sin
  permiso, la etiqueta «Cerrado por X» sigue informando.
- **UI:** opción «Cerrar» / «Reabrir» en `SplitMonthMenu`, con confirmación al cerrar. La tarjeta
  del grupo muestra «Cerrado por X».

## Notificador del NAS (`tools/notifier`)

Node 22 + `firebase-admin`, el mismo esquema que `tools/offers`. Un solo proceso:

1. **Listener** sobre `splitMonths` con `closed == true` y `notifiedAt == null`. Por cada grupo:
   lee `pushTokens` y `members`, envía a los tokens de los participantes (`memberIds`) menos
   `closedBy`, borra los tokens que FCM da por inválidos y escribe `notifiedAt`.
   - Al arrancar, el primer snapshot trae lo que quedó pendiente con el NAS apagado.
   - Entrega «al menos una vez»: si el proceso muere entre el envío y `notifiedAt`, al volver se
     reenvía. Es poco probable y preferible a perder el aviso.
   - Si el listener da error, se vuelve a suscribir con espera creciente (5 s a 5 min).
2. **Recordatorio**: cada 15 minutos (y al arrancar) mira qué meses tocan:
   - El mes en curso, desde el antepenúltimo día a las 20:00 hasta fin de mes.
   - El mes anterior, los días 1 a 3, por si el NAS estuvo apagado en la ventana.
   - Nunca entre las 22:00 y las 09:00: si toca de noche, sale a las 09:00.
   - Solo grupos de tipo mes, abiertos, no archivados y sin `reminderSentAt`.

Las fechas van en `Europe/Madrid`. La lógica de fechas y destinatarios está en `src/logic.mjs`,
con tests de `node --test`.

**Mensajes FCM:** solo datos, `android.priority = "high"` para que lleguen aunque el móvil esté en
reposo, y `ttl` de 3 días.

**Logs:** el repo es público. Como en `tools/offers`, solo recuentos: nada de código de casa,
nombres ni tokens.

### Despliegue en el DS224+

- Container Manager con `tools/notifier/compose.yaml`: `restart: unless-stopped`, `read_only`,
  usuario sin privilegios, `mem_limit: 256m`, logs topados a 2 MB, `TZ=Europe/Madrid`.
- Sin `cpus`: el kernel de DSM no soporta CFS y el contenedor no arrancaría.
- Cuenta de servicio dedicada con solo `Cloud Datastore User` y
  `Firebase Cloud Messaging API Admin`. El JSON se monta en solo lectura desde una carpeta a la que
  solo accede el administrador. La API «Firebase Cloud Messaging API (V1)» tiene que estar activa.
- Solo conexiones salientes a `firestore.googleapis.com` y `fcm.googleapis.com`. No se abren
  puertos ni se toca el OpenVPN.

### Consumo estimado

| Recurso | Consumo |
|---|---|
| RAM del contenedor | 60–90 MB (límite 256 MB) |
| RAM de Container Manager | 100–150 MB si no estaba instalado |
| CPU | ~0 % en reposo; picos de menos de 1 s por evento |
| Disco | ~180 MB de imagen; logs ≤ 2 MB |
| Red | Keepalives de pocos KB/hora; < 10 MB/mes |
| Lecturas de Firestore | < 100/mes (cuota gratuita: 50.000/día) |
| FCM | Gratis |

Con Container Manager activo los discos casi nunca hibernan, con o sin este contenedor.

## Fallos y respaldo

| Fallo | Efecto |
|---|---|
| NAS apagado | Los avisos se retrasan hasta que vuelva; no se duplican. |
| Móvil sin permiso de notificaciones | No ve el push; la tarjeta muestra «Cerrado por X». |
| Token caducado | FCM lo rechaza, el NAS lo borra y el móvil guarda uno nuevo al arrancar. |
| Usuario que fuerza la detención de la app | Android no entrega push hasta que la abra. |

## Pruebas

- `app/src/test/.../SplitPushTest.kt`: `parseSplitPush` con cargas válidas, tipo desconocido, id
  inválido y campos vacíos.
- `tools/notifier/test/logic.test.mjs`: ventana del recordatorio (meses de 28, 29, 30 y 31 días,
  cambio de hora, recuperación de los días 1 a 3, horas de silencio), destinatarios (sin quien
  cierra, solo participantes, sin token) y construcción de mensajes.
- En el S25, con el APK release: cerrar desde un móvil y comprobar la notificación en el otro con
  la app cerrada; reabrir y volver a cerrar; apagar el contenedor, cerrar y comprobar el aviso al
  arrancarlo.
