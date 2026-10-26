// Script semanal: productos habituales × ofertas de Lidl y Aldi → aviso en Firestore.
//
// Uso: node src/index.mjs [--dry-run] [--print]
//   --dry-run  no escribe ni borra nada en Firestore.
//   --print    imprime el aviso completo; se ignora en CI porque los logs del repo son públicos.
//
// Variables: FIREBASE_SERVICE_ACCOUNT (JSON) o FIREBASE_SERVICE_ACCOUNT_FILE (ruta, en local),
// HOUSEHOLD_CODE, GEMINI_API_KEY y, opcional, GEMINI_MODEL.
//
// IMPORTANTE: los logs solo pueden llevar recuentos. Nada de código de casa, productos ni ofertas.
import { readFileSync } from "node:fs";
import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { fetchLidl } from "./lidl.mjs";
import { fetchAldi } from "./aldi.mjs";
import { habitualProducts } from "./habitual.mjs";
import { aldiPrompt, lidlPrompt, ALDI_SCHEMA, LIDL_SCHEMA, aldiMatches, lidlMatches, callGemini, DEFAULT_MODELS } from "./match.mjs";
import { buildNotification } from "./notification.mjs";
import { readPurchases, upsertWeekNotification, deleteOldNotifications } from "./firestore.mjs";

function requireEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    console.error(`Falta la variable ${name}`);
    process.exit(1);
  }
  return value;
}

function serviceAccount() {
  if (process.env.FIREBASE_SERVICE_ACCOUNT_FILE) {
    return JSON.parse(readFileSync(process.env.FIREBASE_SERVICE_ACCOUNT_FILE, "utf8"));
  }
  return JSON.parse(requireEnv("FIREBASE_SERVICE_ACCOUNT"));
}

/** Mensaje de error apto para un log público: los nuestros ya van sin datos; los ajenos, sin texto. */
const safeError = (e) => (/^(\d{3} |Gemini: |sin )/.test(e?.message ?? "") ? e.message : e?.name ?? "Error");

async function main() {
  const args = new Set(process.argv.slice(2));
  const dryRun = args.has("--dry-run");
  const print = args.has("--print") && !process.env.CI;

  const account = serviceAccount();
  const code = requireEnv("HOUSEHOLD_CODE");
  const apiKey = requireEnv("GEMINI_API_KEY");
  // GEMINI_MODEL fija un único modelo (sin respaldo); si no, se prueban los de DEFAULT_MODELS.
  const models = process.env.GEMINI_MODEL?.trim() ? [process.env.GEMINI_MODEL.trim()] : DEFAULT_MODELS;

  initializeApp({ credential: cert(account) });
  const db = getFirestore();
  const now = new Date();

  const habituals = habitualProducts(await readPurchases(db, code), now);
  console.log(`habituales: ${habituals.length}`);
  if (habituals.length === 0) return 0;

  let failed = false;
  const stores = [];
  const counts = {};

  const sources = [
    ["Lidl", () => fetchLidl(now), (s) => `${s.pages.length} páginas`,
      (s) => ({ prompt: lidlPrompt(habituals, s.pages), schema: LIDL_SCHEMA }),
      (raw, s) => lidlMatches(raw, s.pages, habituals)],
    ["Aldi", () => fetchAldi(), (s) => `${s.offers.length} ofertas`,
      (s) => ({ prompt: aldiPrompt(habituals, s.offers), schema: ALDI_SCHEMA }),
      (raw, s) => aldiMatches(raw, s.offers, habituals)],
  ];

  for (const [store, fetchSource, describe, request, toMatches] of sources) {
    let source;
    try {
      source = await fetchSource();
      console.log(`${store}: ${describe(source)}`);
    } catch (e) {
      failed = true;
      console.log(`${store}: ERROR ${safeError(e)}`);
      continue;
    }
    try {
      const { prompt, schema } = request(source);
      const offers = toMatches(await callGemini({ apiKey, models, prompt, schema }), source);
      counts[store] = offers.length;
      stores.push({ source, offers });
    } catch (e) {
      failed = true;
      console.log(`${store}: ERROR ${safeError(e)}`);
    }
  }
  console.log(`coincidencias: ${Object.entries(counts).map(([s, n]) => `${s} ${n}`).join(", ") || "ninguna"}`);

  const notification = buildNotification({ stores, now });
  if (print && notification) console.log(JSON.stringify(notification, null, 2));
  if (!notification) console.log("aviso: sin coincidencias");
  else if (dryRun) console.log("aviso: dry-run");
  else console.log(`aviso: ${(await upsertWeekNotification(db, code, notification)) === "created" ? "creado" : "actualizado"}`);

  if (!dryRun) console.log(`borrados: ${await deleteOldNotifications(db, code, now)}`);
  return failed ? 1 : 0;
}

main().then(
  (exitCode) => process.exit(exitCode),
  (e) => {
    console.error(`ERROR ${safeError(e)}`);
    process.exit(1);
  },
);
