// Comprueba la escala de puntuación con casos etiquetados a mano (eval/cases.json), modelo a modelo,
// usando el prompt del revisor. Sirve para afinar la escala en local: no va en CI porque gasta
// cuota de Gemini. Los casos son genéricos; no metas aquí productos de la casa (el repo es público).
//
// Uso: GEMINI_API_KEY=... node src/eval.mjs [modelo...]   (por defecto, los de DEFAULT_MODELS)
import { readFileSync } from "node:fs";
import { callGemini, verifyPrompt, VERIFY_SCHEMA, MIN_SCORE, DEFAULT_MODELS } from "./match.mjs";

const apiKey = process.env.GEMINI_API_KEY?.trim();
if (!apiKey) {
  console.error("Falta la variable GEMINI_API_KEY");
  process.exit(1);
}

const cases = JSON.parse(readFileSync(new URL("../eval/cases.json", import.meta.url), "utf8"));
const habituals = cases.map((c, i) => ({ normalizedName: `h${i}`, example: c.ticket, fullName: c.compran }));
const candidates = cases.map((c, i) => ({ habitual: `h${i}`, product: c.folleto, page: null }));
const prompt = verifyPrompt(habituals, candidates, []);

const models = process.argv.length > 2 ? process.argv.slice(2) : DEFAULT_MODELS;
let failures = 0;
for (const model of models) {
  let items;
  try {
    ({ items } = await callGemini({ apiKey, models: [model], prompt, schema: VERIFY_SCHEMA }));
  } catch (e) {
    console.log(`${model}: ${e.message}`);
    continue;
  }
  const byIndex = new Map(items.map((r) => [r.index, r]));
  const wrong = cases.flatMap((c, i) => {
    const r = byIndex.get(i);
    if (r != null && r.score >= MIN_SCORE === c.vale) return [];
    return [`  ${c.compran} → ${c.folleto}: ${r?.score ?? "sin nota"} ` +
      `(${c.vale ? "debía avisar" : "no debía avisar"}) ${r?.reason ?? ""}`];
  });
  failures += wrong.length;
  console.log(`${model}: ${cases.length - wrong.length}/${cases.length} aciertos`);
  for (const line of wrong) console.log(line);
}
process.exit(failures > 0 ? 1 : 0);
