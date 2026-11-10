// Notificador de SNcount para el NAS (spec 2026-11-10-aviso-cierre-sncount-design.md).
//
// Un solo proceso que se queda en marcha:
//   1. Listener de grupos cerrados sin avisar → push a los demás participantes.
//   2. Cada 15 minutos, recordatorio de fin de mes a los grupos de tipo mes que siguen abiertos.
//
// Uso: node src/index.mjs [--dry-run] [--once]
//   --dry-run  no envía push ni escribe en Firestore; solo cuenta lo que haría.
//   --once     procesa lo pendiente y el recordatorio una vez y termina (para probar).
//
// Variables: HOUSEHOLD_CODE y FIREBASE_SERVICE_ACCOUNT_FILE (ruta al JSON de la cuenta de servicio).
//
// IMPORTANTE: el repo es público. Los logs solo llevan recuentos: nada de código de casa, nombres,
// ids ni tokens.
import { readFileSync } from "node:fs";
import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import {
  PUSH_CLOSED, PUSH_REMINDER, backoffMs, buildMessages, dueReminderMonthKeys, isReminderTarget,
  recipients, staleTokenUids,
} from "./logic.mjs";

const REMINDER_INTERVAL_MS = 15 * 60 * 1000;
/** Si un aviso de cierre falla, se reintenta lo pendiente pasado este tiempo. */
const RETRY_PENDING_MS = 60 * 1000;
/** `sendEach` admite como mucho 500 mensajes por llamada. */
const SEND_CHUNK = 500;

function requireEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    console.error(`Falta la variable ${name}`);
    process.exit(1);
  }
  return value;
}

/** Mensaje de error apto para un log público: el código de Firebase si lo hay, si no el tipo. */
const safeError = (e) => e?.code ?? e?.name ?? "Error";

const log = (msg) => console.log(`${new Date().toISOString()} ${msg}`);

const args = new Set(process.argv.slice(2));
const dryRun = args.has("--dry-run");
const once = args.has("--once");

const code = requireEnv("HOUSEHOLD_CODE");
const account = JSON.parse(readFileSync(requireEnv("FIREBASE_SERVICE_ACCOUNT_FILE"), "utf8"));
initializeApp({ credential: cert(account) });
const db = getFirestore();
const messaging = getMessaging();

const household = db.collection("households").doc(code);
const months = household.collection("splitMonths");
const pushTokens = household.collection("pushTokens");
const members = household.collection("members");

async function loadTokens() {
  const snap = await pushTokens.get();
  const map = new Map();
  for (const doc of snap.docs) {
    const token = doc.get("token");
    if (typeof token === "string" && token) map.set(doc.id, token);
  }
  return map;
}

async function memberName(uid) {
  if (!uid) return "";
  const doc = await members.doc(uid).get();
  return doc.exists ? (doc.get("name") ?? "") : "";
}

/** Envía en tandas de 500. Devuelve cuántos llegaron y las respuestas en el orden de `messages`. */
async function send(messages) {
  if (dryRun || messages.length === 0) return { sent: dryRun ? messages.length : 0, responses: [] };
  const responses = [];
  let sent = 0;
  for (let i = 0; i < messages.length; i += SEND_CHUNK) {
    const batch = await messaging.sendEach(messages.slice(i, i + SEND_CHUNK));
    sent += batch.successCount;
    responses.push(...batch.responses);
  }
  return { sent, responses };
}

/** Borra los tokens que FCM ya no acepta: el móvil guardará uno nuevo al abrir la app. */
async function removeStale(targets, responses) {
  const uids = staleTokenUids(targets, responses);
  if (dryRun || uids.length === 0) return;
  const batch = db.batch();
  uids.forEach((uid) => batch.delete(pushTokens.doc(uid)));
  await batch.commit();
  log(`tokens caducados borrados: ${uids.length}`);
}

// --- 1. Aviso de cierre -------------------------------------------------------------------------

const pendingQuery = months.where("closed", "==", true).where("notifiedAt", "==", null);
/** Grupos que se están avisando ahora: un snapshot repetido no debe mandar el aviso dos veces. */
const inFlight = new Set();
let retryTimer = null;

async function notifyClosed(doc) {
  if (inFlight.has(doc.id)) return;
  inFlight.add(doc.id);
  try {
    const month = doc.data();
    const targets = recipients(month.memberIds, await loadTokens(), month.closedBy);
    const messages = buildMessages(
      PUSH_CLOSED,
      { monthId: doc.id, monthName: month.name, byName: await memberName(month.closedBy) },
      targets,
    );
    const { sent, responses } = await send(messages);
    await removeStale(targets, responses);
    // Entrega «al menos una vez»: se marca después de enviar. Si se reabrió entretanto da igual:
    // al volver a cerrar, la app pone `notifiedAt` a null y se avisa otra vez.
    if (!dryRun) await doc.ref.update({ notifiedAt: FieldValue.serverTimestamp() });
    log(`cierre avisado: ${sent}/${messages.length}`);
  } catch (e) {
    log(`cierre: ERROR ${safeError(e)}; se reintenta en ${RETRY_PENDING_MS / 1000} s`);
    scheduleRetry();
  } finally {
    inFlight.delete(doc.id);
  }
}

/** El listener no vuelve a disparar si el documento no cambia, así que el reintento es una consulta. */
function scheduleRetry() {
  if (retryTimer || once) return;
  retryTimer = setTimeout(async () => {
    retryTimer = null;
    try {
      const snap = await pendingQuery.get();
      await Promise.all(snap.docs.map(notifyClosed));
    } catch (e) {
      log(`reintento: ERROR ${safeError(e)}`);
      scheduleRetry();
    }
  }, RETRY_PENDING_MS);
}

let unsubscribe = null;
let listenAttempt = 0;

/** El primer snapshot trae lo que quedó pendiente mientras el NAS estaba apagado. */
function listen() {
  unsubscribe = pendingQuery.onSnapshot(
    (snap) => {
      listenAttempt = 0;
      for (const change of snap.docChanges()) {
        if (change.type !== "removed") notifyClosed(change.doc);
      }
    },
    (e) => {
      const wait = backoffMs(listenAttempt++);
      log(`listener: ERROR ${safeError(e)}; reintento en ${wait / 1000} s`);
      setTimeout(listen, wait);
    },
  );
}

// --- 2. Recordatorio de fin de mes ----------------------------------------------------------------

let reminderRunning = false;

async function reminderTick() {
  if (reminderRunning) return;
  reminderRunning = true;
  try {
    for (const key of dueReminderMonthKeys(new Date())) {
      const snap = await months.where("monthKey", "==", key).get();
      const due = snap.docs.filter((doc) => isReminderTarget(doc.data(), key));
      if (due.length === 0) continue;
      const tokens = await loadTokens();
      for (const doc of due) {
        const month = doc.data();
        const targets = recipients(month.memberIds, tokens);
        const messages = buildMessages(PUSH_REMINDER, { monthId: doc.id, monthName: month.name }, targets);
        const { sent, responses } = await send(messages);
        await removeStale(targets, responses);
        if (!dryRun) await doc.ref.update({ reminderSentAt: FieldValue.serverTimestamp() });
        log(`recordatorio enviado: ${sent}/${messages.length}`);
      }
    }
  } catch (e) {
    // Sin `reminderSentAt` escrito, el siguiente tick (15 min) lo vuelve a intentar.
    log(`recordatorio: ERROR ${safeError(e)}`);
  } finally {
    reminderRunning = false;
  }
}

// --- Arranque -----------------------------------------------------------------------------------

if (once) {
  const snap = await pendingQuery.get();
  await Promise.all(snap.docs.map(notifyClosed));
  await reminderTick();
  log(`--once terminado${dryRun ? " (dry-run)" : ""}`);
  process.exit(0);
}

listen();
await reminderTick();
const reminderTimer = setInterval(reminderTick, REMINDER_INTERVAL_MS);
log(`notificador en marcha${dryRun ? " (dry-run)" : ""}`);

// `docker stop` manda SIGTERM; sin esto Node (PID 1) lo ignora y Docker lo mata a los 10 s.
for (const signal of ["SIGTERM", "SIGINT"]) {
  process.on(signal, () => {
    unsubscribe?.();
    clearInterval(reminderTimer);
    if (retryTimer) clearTimeout(retryTimer);
    log("parado");
    process.exit(0);
  });
}
