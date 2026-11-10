// Lógica pura del notificador: fechas del recordatorio, destinatarios y mensajes FCM.
// Sin firebase-admin para poder probarla con `node --test`.

export const ZONE = "Europe/Madrid";

/** Tipos de la clave `type` del mensaje; la app los lee en `SplitPush.kt`. */
export const PUSH_CLOSED = "sncount_closed";
export const PUSH_REMINDER = "sncount_reminder";

/** El recordatorio abre a las 20:00 del antepenúltimo día del mes (dos días antes del último). */
export const REMINDER_HOUR = 20;
export const REMINDER_DAYS_BEFORE = 2;
/** Días del mes siguiente en los que aún se recuerda, por si el NAS estuvo apagado. */
export const CATCH_UP_DAYS = 3;
/** Horas sin recordatorios: de 22:00 a 09:00 no se molesta a nadie. */
export const QUIET_FROM = 22;
export const QUIET_UNTIL = 9;

/** Los mensajes caducan a los 3 días: un móvil apagado más tiempo ya no necesita el aviso. */
export const TTL_MS = 3 * 24 * 60 * 60 * 1000;

/** Códigos de FCM que indican que el token ya no vale y hay que borrarlo. */
const STALE_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

const madridFormat = new Intl.DateTimeFormat("en-CA", {
  timeZone: ZONE, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", hourCycle: "h23",
});

/** Año, mes (1-12), día y hora de `now` en Madrid. */
export function madridParts(now) {
  const p = Object.fromEntries(madridFormat.formatToParts(now).map((x) => [x.type, x.value]));
  return { year: Number(p.year), month: Number(p.month), day: Number(p.day), hour: Number(p.hour) };
}

export const daysInMonth = (year, month) => new Date(Date.UTC(year, month, 0)).getUTCDate();

/** "yyyy-MM", el mismo formato que `SplitMonth.monthKey`. */
export const monthKeyOf = (year, month) => `${year}-${String(month).padStart(2, "0")}`;

/**
 * Meses ("yyyy-MM") cuyo recordatorio toca enviar en `now`:
 * - el mes en curso, desde el antepenúltimo día a las 20:00 hasta fin de mes;
 * - el mes anterior, los días 1 a 3 (recuperación si el NAS estuvo apagado).
 * Nunca en horas de silencio. Que se haya enviado ya lo decide `reminderSentAt`, no esta función.
 */
export function dueReminderMonthKeys(now) {
  const { year, month, day, hour } = madridParts(now);
  if (hour >= QUIET_FROM || hour < QUIET_UNTIL) return [];
  const keys = [];
  const opensOn = daysInMonth(year, month) - REMINDER_DAYS_BEFORE;
  if (day > opensOn || (day === opensOn && hour >= REMINDER_HOUR)) keys.push(monthKeyOf(year, month));
  if (day <= CATCH_UP_DAYS) {
    keys.push(month === 1 ? monthKeyOf(year - 1, 12) : monthKeyOf(year, month - 1));
  }
  return keys;
}

/** Grupo al que hay que recordar: de tipo mes, de ese `key`, abierto, sin archivar y sin aviso previo. */
export function isReminderTarget(month, key) {
  return month.monthKey === key && month.activity !== true && month.closed !== true &&
    month.archived !== true && !month.reminderSentAt;
}

/**
 * Destinatarios: los participantes del grupo con token, menos `excludeUid` (quien cerró).
 * Un mismo token en dos uids (móvil reinstalado) se envía una sola vez.
 */
export function recipients(memberIds, tokensByUid, excludeUid = null) {
  const seen = new Set();
  const out = [];
  for (const uid of memberIds ?? []) {
    if (uid === excludeUid) continue;
    const token = tokensByUid.get(uid);
    if (!token || seen.has(token)) continue;
    seen.add(token);
    out.push({ uid, token });
  }
  return out;
}

/**
 * Mensajes FCM solo de datos: la app monta el texto con sus `strings.xml`. Prioridad alta para que
 * Android lo entregue aunque el móvil esté en reposo.
 */
export function buildMessages(type, { monthId, monthName, byName }, targets) {
  const data = { type, monthId, monthName: monthName ?? "" };
  if (type === PUSH_CLOSED) data.byName = byName ?? "";
  return targets.map(({ token }) => ({
    token,
    data,
    android: { priority: "high", ttl: TTL_MS },
  }));
}

/** Uids cuyo token rechazó FCM por inválido. `responses` va en el mismo orden que `targets`. */
export function staleTokenUids(targets, responses) {
  return targets
    .filter((_, i) => {
      const r = responses?.[i];
      return r && !r.success && STALE_TOKEN_CODES.has(r.error?.code);
    })
    .map((t) => t.uid);
}

/** Espera antes de volver a suscribir el listener: 5 s, 10 s, 20 s… hasta 5 minutos. */
export function backoffMs(attempt) {
  return Math.min(5_000 * 2 ** attempt, 300_000);
}
