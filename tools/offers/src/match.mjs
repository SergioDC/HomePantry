// Cruce de ofertas con los productos habituales mediante Gemini, en tres pasos:
//   1. expandir los nombres abreviados del ticket ("sandia negra s/n sem" → "sandía negra sin semillas");
//   2. buscar en cada tienda los candidatos, cada uno con una nota de 0 a 100;
//   3. que otro modelo distinto revise los candidatos con la misma escala.
// Solo se avisa si las dos notas llegan a MIN_SCORE. Los datos que se guardan salen de la tienda
// siempre que se puede, para no depender de que Gemini transcriba bien precios y fechas.

/**
 * Nota mínima en las dos pasadas. El usuario pidió coincidencias "del 90 % en adelante": el mismo
 * producto aunque cambie la marca o el tamaño, no variantes como "Sandía mini" para "sandía".
 */
export const MIN_SCORE = 90;

// Escala común al buscador y al revisor. "Sandía mini" para "sandía negra sin semillas" salió en
// el aviso real del 2026-10-26 y el usuario lo dio por malo: las variantes, que antes contaban,
// ahora quedan por debajo de 90. Los demás ejemplos negativos salen de las pruebas reales.
const RUBRIC = `
Puntúa de 0 a 100 si el producto del folleto es EL MISMO producto que compra la casa:
- 100: el mismo producto. Da igual la marca, el peso, el tamaño del envase o las unidades del pack.
- 90-99: el mismo producto con una diferencia que no cambia lo que es: la talla o el calibre
  (huevos M o L), el origen, la categoría comercial ("extra", "selección"), sinónimos y abreviaturas
  ("s/n sem" es "sin semillas"; "pepitas" y "semillas"), o un folleto más genérico que lo que
  compran y que no añade nada (el folleto dice "Sandía" y compran "sandía negra sin semillas").
- 50-89: la misma familia, pero el folleto añade algo que lo hace otra variedad: otro tamaño de
  variedad ("mini"), otro sabor, otro tipo o curación, otra preparación (marinado, ahumado, rallado
  frente a entero), integral, light, sin lactosa…
- 0-49: otro producto que solo comparte un ingrediente, la sección del súper o el uso, o algo
  elaborado a partir de él (frito, confitado, zumo, en salsa, plato preparado, postre o snack).
Ejemplos (compran → folleto: nota):
- "sandía" → "Sandía mini": 60, "mini" es otra variedad.
- "sandía negra sin semillas" → "Sandía": 92, el folleto es más genérico y no añade nada.
- "leche entera" → "Leche entera Milbona 1 L": 100, solo cambian la marca y el envase.
- "leche entera" → "Leche semidesnatada": 55; "leche entera" → "Leche entera sin lactosa": 65.
- "pechuga de pollo" → "Pechuga de pollo marinada": 60.
- "pan de molde" → "Pan de molde integral": 60.
- "masa para pizza" → "Pizza 4 quesos": 15.
- "paté" → "Pechuga de pavo": 5.
- "tomate" → "Tomate frito": 20.
- "jengibre" → "Jengibre confitado": 20.
- "piña en rodajas" → "Zumo de piña": 10.
Ante la duda entre dos notas, pon la más baja.`;

// Se piden también las de 70 a 89 para ver en local (--print) lo que se queda fuera; el corte a
// MIN_SCORE lo hace el código. "reason" va antes que "score" para que razone antes de puntuar.
const FIND_INSTRUCTIONS = `
Devuelve solo los productos del folleto con nota 70 o más (si no hay ninguno, []). Para cada uno:
"habitual", copiado EXACTAMENTE de la lista (lo que va antes de los dos puntos); "habitualLabel",
un nombre genérico corto en español, con tildes y mayúscula inicial (p. ej. "Plátanos"); "reason",
por qué esa nota en menos de 15 palabras; y "score", la nota.`;

const habitualLines = (habituals) =>
  habituals.map((h) => `- ${h.normalizedName}: ${h.fullName ?? h.normalizedName} (en el ticket: ${h.example})`).join("\n");

export function aldiPrompt(habituals, offers) {
  const lines = offers.map((o, i) =>
    `${i}. ${o.name}${o.brand ? ` (${o.brand})` : ""}${o.unit ? `, ${o.unit}` : ""}: ${o.price} €` +
    `${o.oldPrice != null ? `, antes ${o.oldPrice} €` : ""}${o.promo ? `, ${o.promo}` : ""}`);
  return `Productos que compra habitualmente una casa (nombre: nombre completo):\n${habitualLines(habituals)}\n\n` +
    `Ofertas de esta semana en Aldi (índice. producto):\n${lines.join("\n")}\n\n` +
    `Busca las ofertas que sean alguno de esos productos, con su "index".${RUBRIC}${FIND_INSTRUCTIONS}`;
}

export function lidlPrompt(habituals, pages) {
  const lines = pages.map((p) => `Página ${p.page}: ${p.description}\n  Texto: ${p.text}`);
  // Sin precios: en la prueba real Gemini los sacaba mal del OCR (mezcla €/kg, packs y Lidl Plus).
  // Basta con el nombre del producto y la página; el enlace lleva al folleto.
  return `Productos que compra habitualmente una casa (nombre: nombre completo):\n${habitualLines(habituals)}\n\n` +
    `Folleto de Lidl de esta semana, página a página. El texto viene de un OCR desordenado:\n${lines.join("\n")}\n\n` +
    `Busca los productos del folleto que sean alguno de esos productos: "page" y "product" (nombre ` +
    `completo del producto tal y como aparece en el folleto, con todas sus palabras, como "mini" o ` +
    `"marinado").${RUBRIC}${FIND_INSTRUCTIONS}`;
}

/** Lista de objetos con [fields] más "reason" y "score", en ese orden. */
function scoredSchema(fields) {
  const properties = { ...fields, reason: { type: "string" }, score: { type: "integer" } };
  const order = Object.keys(properties);
  return { type: "array", items: { type: "object", properties, required: order, propertyOrdering: order } };
}

export const ALDI_SCHEMA = scoredSchema({
  index: { type: "integer" }, habitual: { type: "string" }, habitualLabel: { type: "string" },
});

export const LIDL_SCHEMA = scoredSchema({
  page: { type: "integer" }, habitual: { type: "string" }, habitualLabel: { type: "string" },
  product: { type: "string" },
});

export const VERIFY_SCHEMA = scoredSchema({ index: { type: "integer" } });

export const EXPAND_SCHEMA = {
  type: "array",
  items: {
    type: "object",
    properties: { normalizedName: { type: "string" }, fullName: { type: "string" } },
    required: ["normalizedName", "fullName"],
  },
};

export function expandPrompt(habituals) {
  return `Nombres de productos sacados de tickets de supermercado españoles, a menudo abreviados:\n` +
    `${habituals.map((h) => `- ${h.normalizedName} (en el ticket: ${h.example})`).join("\n")}\n\n` +
    `Escribe el nombre completo de cada producto en español, con tildes y sin marca ni peso, como lo ` +
    `diría alguien en casa. Ejemplo: "SANDIA NEGRA S/N SEM" es "sandía negra sin semillas". Si una ` +
    `abreviatura no se entiende, déjala como está: no inventes. Devuelve "normalizedName", copiado ` +
    `EXACTAMENTE de la lista (lo que va antes del paréntesis), y "fullName".`;
}

/** Añade `fullName` a cada habitual; si Gemini no lo da, vale el nombre normalizado. */
export function expandHabituals(raw, habituals) {
  const names = new Map(raw
    .filter((r) => typeof r.fullName === "string" && r.fullName.trim())
    .map((r) => [r.normalizedName, r.fullName.trim()]));
  return habituals.map((h) => ({ ...h, fullName: names.get(h.normalizedName) ?? h.normalizedName }));
}

/**
 * Prompt del revisor: cada candidata con lo que compran (nombre completo y del ticket) y el producto
 * del folleto. De Lidl se adjunta el texto de sus páginas para que compruebe que el producto está
 * de verdad y con ese nombre; de Aldi no hace falta, el producto viene de la ficha de la tienda.
 */
export function verifyPrompt(habituals, candidates, pages) {
  const byName = new Map(habituals.map((h) => [h.normalizedName, h]));
  const lines = candidates.map((c, i) => {
    const h = byName.get(c.habitual);
    const page = c.page != null ? ` (página ${c.page})` : "";
    return `${i}. Compran "${h?.fullName ?? c.habitual}" (en el ticket: ${h?.example ?? c.habitual}) → folleto: "${c.product}"${page}`;
  });
  const pageNumbers = new Set(candidates.map((c) => c.page).filter((p) => p != null));
  const texts = pages.filter((p) => pageNumbers.has(p.page)).map((p) => `Página ${p.page}: ${p.description}\n  Texto: ${p.text}`);
  return `Otro asistente ha emparejado ofertas de un folleto con productos que compra una casa. ` +
    `Revisa cada pareja por separado, sin fiarte de su criterio.${RUBRIC}\n` +
    (texts.length ? `Comprueba en el texto de su página que el producto está y cómo se llama: si no aparece, la ` +
      `nota es 0; si aparece con más palabras (p. ej. "mini"), puntúa el nombre completo que aparece.\n` : "") +
    `\nParejas (índice. compran → folleto):\n${lines.join("\n")}\n` +
    (texts.length ? `\nTexto de esas páginas del folleto (OCR desordenado):\n${texts.join("\n")}\n` : "") +
    `\nDevuelve una entrada por pareja: "index"; "reason", por qué esa nota en menos de 15 palabras; y "score", la nota.`;
}

/** Candidatas que el revisor puntúa con MIN_SCORE o más; si puntúa una dos veces, cuenta la peor. */
export function keepVerified(candidates, raw) {
  const scores = new Map();
  for (const r of raw) {
    if (!Number.isInteger(r.index) || typeof r.score !== "number") continue;
    scores.set(r.index, Math.min(r.score, scores.get(r.index) ?? Infinity));
  }
  return candidates.filter((_, i) => (scores.get(i) ?? -1) >= MIN_SCORE);
}

/** Modelos para el revisor: cualquiera menos el que ha buscado, para no repetir sus mismos errores. */
export const otherModels = (models, used) => models.filter((m) => m !== used);

const passes = (r) => typeof r.score === "number" && r.score >= MIN_SCORE;

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
    .filter((r) => passes(r) && Number.isInteger(r.index) && r.index >= 0 && r.index < offers.length && examples.has(r.habitual))
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
    .filter((r) => passes(r) && pageNumbers.has(r.page) && examples.has(r.habitual))
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
 * Los Flash vigentes (lista de la API a 2026-10-26), del más nuevo al más antiguo. En las pruebas
 * reales los más nuevos daban 503/429 a ratos mientras los otros respondían: por eso hay respaldo.
 * Sin flash-lite: emparejaba "piña" con tomate y "leche" con pan. Si ninguno responde, mejor no
 * avisar esa semana (workflow en rojo) que avisar con ruido.
 */
export const DEFAULT_MODELS = ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash"];
const ATTEMPTS_PER_MODEL = 2;
const RETRY_DELAY_MS = 2_000;
/**
 * Sin límite, fetch espera 5 min a las cabeceras: mejor pasar antes al siguiente modelo. Con el
 * razonamiento a fondo el folleto de Lidl tarda más que antes (90 s se quedaban cortos).
 */
const REQUEST_TIMEOUT_MS = 180_000;

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
  // Se ejecuta una vez por semana: compensa que razone a fondo. Sin temperatura: Google recomienda
  // dejar la de por defecto en los Gemini 3, porque bajarla puede hacer que se repitan en bucle.
  const body = JSON.stringify({
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: { responseMimeType: "application/json", responseSchema: schema, thinkingConfig: { thinkingLevel: "high" } },
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
