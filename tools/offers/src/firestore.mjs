// Lectura de tickets y escritura de avisos con el Admin SDK (se salta las reglas de seguridad).
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { mergeWeekNotification } from "./notification.mjs";

const MAX_AGE_DAYS = 28;

const household = (db, code) => db.collection("households").doc(code);

export async function readPurchases(db, code) {
  const snapshot = await household(db, code).collection("purchases").get();
  return snapshot.docs.map((d) => {
    const data = d.data();
    return {
      normalizedName: data.normalizedName ?? "",
      rawName: data.rawName ?? "",
      date: data.date instanceof Timestamp ? data.date.toDate() : new Date(0),
    };
  });
}

/** Un aviso por semana: si ya existe el de este `weekStart` se reescribe conservando readBy y addedKeys. */
export async function upsertWeekNotification(db, code, fresh) {
  const collection = household(db, code).collection("notifications");
  const existing = await collection
    .where("type", "==", "offers")
    .where("weekStart", "==", fresh.weekStart)
    .limit(1)
    .get();
  const doc = existing.empty ? null : existing.docs[0];
  const data = { ...mergeWeekNotification(doc?.data() ?? null, fresh), createdAt: FieldValue.serverTimestamp() };
  if (doc) {
    await doc.ref.set(data);
    return "updated";
  }
  await collection.add(data);
  return "created";
}

export async function deleteOldNotifications(db, code, now) {
  const limit = Timestamp.fromMillis(now.getTime() - MAX_AGE_DAYS * 86_400_000);
  const old = await household(db, code).collection("notifications").where("createdAt", "<", limit).get();
  if (old.empty) return 0;
  const batch = db.batch();
  old.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
  return old.size;
}
