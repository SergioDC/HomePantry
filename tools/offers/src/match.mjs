// Cruce de ofertas con los productos habituales mediante Gemini (solo texto, una llamada por tienda).
// Gemini decide qué coincide; los datos que se guardan salen de la tienda siempre que se puede,
// para no depender de que Gemini transcriba bien precios y fechas.

// Ejemplos sacados de las pruebas reales (2026-10-26): sin ellos Gemini emparejaba "masa de pizza"
// con pizzas y "paté" con pavo; con un "ante la duda, no" a secas descartaba hasta el pan integral.
const CRITERIA = `
Hay coincidencia si la oferta es EL MISMO PRODUCTO que un habitual o una VARIANTE suya: otra marca,
otro sabor, otro tipo, otro tamaño o formato, o una preparación ligera (marinado, en lonchas,
rallado, integral, sin lactosa…). Lo que solo comparte un ingrediente, la sección del súper o el
uso es OTRO producto y no coincide; también lo elaborado a partir del habitual (confitado, en
salsa, plato preparado, postre o snack hecho con él).
Sí coinciden (variantes): "leche entera" y "leche semidesnatada"; "pollo" y "pechuga de pollo
marinada"; "pan" y "pan integral"; "patatas fritas" y "patatas fritas sabor jamón"; "queso
rallado" y "mozzarella rallada"; "yogur" y "yogur griego".
NO coinciden (otro producto): "masa de pizza" y "pizza preparada"; "paté" y "albóndigas de pavo";
"paté" y "pechuga de pavo"; "paté" y "stick de queso"; "leche" y "batido de chocolate"; "tomate" y
"tomate frito"; "queso" y "pizza con queso"; "pollo" y "nuggets de pollo"; "jengibre" y
"confit de jengibre"; "piña" y "zumo de piña".
Si no está claro que sea el mismo producto o una variante suya, no lo incluyas.
Para cada coincidencia devuelve "habitual" copiado EXACTAMENTE de la lista (campo normalizedName) y
"habitualLabel": un nombre genérico corto en español, con tildes y mayúscula inicial (p. ej. "Plátanos").
Si no hay ninguna coincidencia devuelve [].`;

const habitualLines = (habituals) =>
  habituals.map((h) => `- ${h.normalizedName} (en el ticket: ${h.example})`).join("\n");

export function aldiPrompt(habituals, offers) {
  const lines = offers.map((o, i) =>
    `${i}. ${o.name}${o.brand ? ` (${o.brand})` : ""}${o.unit ? `, ${o.unit}` : ""}: ${o.price} €` +
    `${o.oldPrice != null ? `, antes ${o.oldPrice} €` : ""}${o.promo ? `, ${o.promo}` : ""}`);
  return `Productos que compra habitualmente una casa:\n${habitualLines(habituals)}\n\n` +
    `Ofertas de esta semana en Aldi (índice. producto):\n${lines.join("\n")}\n\n` +
    `Indica qué ofertas coinciden con algún habitual, con su "index".${CRITERIA}`;
}

export function lidlPrompt(habituals, pages) {
  const lines = pages.map((p) => `Página ${p.page}: ${p.description}\n  Texto: ${p.text}`);
  // Sin precios: en la prueba real Gemini los sacaba mal del OCR (mezcla €/kg, packs y Lidl Plus).
  // Basta con el nombre del producto y la página; el enlace lleva al folleto.
  return `Productos que compra habitualmente una casa:\n${habitualLines(habituals)}\n\n` +
    `Folleto de Lidl de esta semana, página a página. El texto viene de un OCR desordenado:\n${lines.join("\n")}\n\n` +
    `Indica qué productos del folleto coinciden con algún habitual: "page" y "product" (nombre del ` +
    `producto tal y como aparece en el folleto, limpio y corto).${CRITERIA}`;
}

export const ALDI_SCHEMA = {
  type: "array",
  items: {
    type: "object",
    properties: { index: { type: "integer" }, habitual: { type: "string" }, habitualLabel: { type: "string" } },
    required: ["index", "habitual", "habitualLabel"],
  },
};

export const LIDL_SCHEMA = {
  type: "array",
  items: {
    type: "object",
    properties: {
      page: { type: "integer" }, habitual: { type: "string" }, habitualLabel: { type: "string" },
      product: { type: "string" },
    },
    required: ["page", "habitual", "habitualLabel", "product"],
  },
};


const label = (r) => (typeof r.habitualLabel === "string" && r.habitualLabel.trim()) || r.habitual;

function dedupeByKey(matches) {
  const seen = new Set();
  return matches.filter((m) => !seen.has(m.key) && seen.add(m.key));
}

const STOPWORDS = new Set(["de", "del", "la", "el", "los", "las", "con", "sin", "para", "en", "y", "al", "a", "s/n"]);

/** Palabras significativas: minúsculas, sin tildes ni plural simple, sin artículos ni preposiciones. */
function words(text) {
  return (text ?? "")
    .toLowerCase()
    .normalize("NFD").replace(/[̀-ͯ]/g, "")
    .split(/[^a-z0-9ñ/]+/)
    .filter((w) => w.length >= 3 && !STOPWORDS.has(w))
    .map((w) => (w.length > 3 && w.endsWith("s") ? w.slice(0, -1) : w));
}

/** Misma palabra o una variante de género/número ("rallado"/"rallada"): mismo tronco de 5 letras. */
const sameWord = (a, b) => a === b || (a.length >= 5 && b.length >= 5 && a.slice(0, 5) === b.slice(0, 5));

/**
 * Red de seguridad sin IA: la oferta tiene que compartir alguna palabra con el habitual (su nombre
 * normalizado o el del ticket). Las variantes reales la comparten ("pollo" → "pechuga de pollo
 * marinada"); las invenciones de Gemini no ("piña" → "tomate rama"). Se compara con lo que viene de
 * los tickets y no con `habitualLabel`, que también lo escribe Gemini.
 */
export function sharesWord(normalizedName, example, product) {
  const habitualWords = [...words(normalizedName), ...words(example)];
  return words(product).some((p) => habitualWords.some((h) => sameWord(p, h)));
}

export function aldiMatches(raw, offers, habituals) {
  const examples = new Map(habituals.map((h) => [h.normalizedName, h.example]));
  return dedupeByKey(raw
    .filter((r) => Number.isInteger(r.index) && r.index >= 0 && r.index < offers.length && examples.has(r.habitual))
    .filter((r) => sharesWord(r.habitual, examples.get(r.habitual), offers[r.index].name))
    .map((r) => {
      const o = offers[r.index];
      return {
        key: `aldi-${o.id}`,
        habitual: r.habitual,
        habitualLabel: label(r),
        product: o.brand ? `${o.name} (${o.brand})` : o.name,
        price: o.price,
        oldPrice: o.oldPrice,
        promo: o.promo,
        page: null,
        validFrom: o.validFrom,
        validUntil: o.validUntil,
      };
    }));
}

export function lidlMatches(raw, pages, habituals) {
  const examples = new Map(habituals.map((h) => [h.normalizedName, h.example]));
  const pageNumbers = new Set(pages.map((p) => p.page));
  return dedupeByKey(raw
    .filter((r) => pageNumbers.has(r.page) && examples.has(r.habitual))
    .filter((r) => sharesWord(r.habitual, examples.get(r.habitual), r.product))
    .map((r) => ({
      key: `lidl-p${r.page}-${r.habitual}`,
      habitual: r.habitual,
      habitualLabel: label(r),
      product: typeof r.product === "string" && r.product.trim() ? r.product.trim() : label(r),
      price: null,
      oldPrice: null,
      promo: null,
      page: r.page,
      validFrom: null,
      validUntil: null,
    })));
}

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
/**
 * Modelos por orden de preferencia. En las pruebas reales (2026-10-26) gemini-3.8-flash no respondía
 * en 5 min: por eso hay respaldo. Sin flash-lite: emparejaba "piña" con tomate y "leche" con pan.
 * Si ninguno responde, mejor no avisar esa semana (workflow en rojo) que avisar con ruido.
 */
export const DEFAULT_MODELS = ["gemini-3.8-flash", "gemini-3.5-flash"];
const ATTEMPTS_PER_MODEL = 2;
const RETRY_DELAY_MS = 2_000;
/** Sin límite, fetch espera 5 min a las cabeceras: mejor pasar antes al siguiente modelo. */
const REQUEST_TIMEOUT_MS = 90_000;

/** Error de Gemini que merece reintento u otro modelo: saturación, límite de cuota o sin respuesta. */
class TransientGeminiError extends Error {}

async function callModel({ apiKey, model, body, fetchImpl }) {
  let response;
  try {
    response = await fetchImpl(`${GEMINI_BASE}/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch {
    throw new TransientGeminiError("Gemini: sin respuesta");
  }
  if (!response.ok) {
    const message = `Gemini: ${response.status}`;
    throw response.status === 429 || response.status >= 500 ? new TransientGeminiError(message) : new Error(message);
  }
  const data = await response.json();
  const text = data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? "").join("") ?? "";
  const parsed = JSON.parse(text || "[]");
  if (!Array.isArray(parsed)) throw new Error("Gemini: respuesta que no es una lista");
  return parsed;
}

/**
 * Llama a Gemini con salida JSON, probando [models] por orden: cada uno se reintenta ante errores
 * pasajeros y, si sigue fallando, se pasa al siguiente. Un error que no es pasajero (p. ej. 400 o
 * key inválida) corta en seco. La key va en cabecera, nunca en la URL, y los errores solo llevan
 * el estado: los logs de Actions son públicos. Devuelve también qué modelo respondió, para el log.
 */
export async function callGemini({ apiKey, models = DEFAULT_MODELS, prompt, schema, fetchImpl = fetch, retryDelayMs = RETRY_DELAY_MS }) {
  const body = JSON.stringify({
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: { responseMimeType: "application/json", responseSchema: schema, temperature: 0 },
  });
  let lastError;
  for (const model of models) {
    for (let attempt = 1; attempt <= ATTEMPTS_PER_MODEL; attempt++) {
      try {
        return { items: await callModel({ apiKey, model, body, fetchImpl }), model };
      } catch (e) {
        if (!(e instanceof TransientGeminiError)) throw e;
        lastError = e;
        if (attempt < ATTEMPTS_PER_MODEL) await new Promise((r) => setTimeout(r, retryDelayMs));
      }
    }
  }
  throw new Error(lastError.message);
}
