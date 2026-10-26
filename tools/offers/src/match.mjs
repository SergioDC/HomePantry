// Cruce de ofertas con los productos habituales mediante Gemini (solo texto, una llamada por tienda).
// Gemini decide qué coincide; los datos que se guardan salen de la tienda siempre que se puede,
// para no depender de que Gemini transcriba bien precios y fechas.

const CRITERIA = `
Hay coincidencia si la oferta es EL MISMO TIPO DE PRODUCTO que un habitual, aunque cambien la marca
o la variante: "leche entera" y "leche semidesnatada" sí coinciden; "leche" y "batido de chocolate" no;
"pollo" y "pechuga de pollo" sí; "tomate" y "tomate frito" no. Ante la duda, NO hay coincidencia:
es mejor perder una oferta que avisar de algo que no compramos.
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
  return `Productos que compra habitualmente una casa:\n${habitualLines(habituals)}\n\n` +
    `Folleto de Lidl de esta semana, página a página. El texto viene de un OCR desordenado; los precios ` +
    `aparecen sin coma ("436 396" significa 4,36 € antes y 3,96 € ahora):\n${lines.join("\n")}\n\n` +
    `Indica qué productos del folleto coinciden con algún habitual: "page", "product" (nombre del folleto), ` +
    `"price" y "oldPrice" en euros si los puedes deducir con seguridad (si no, null).${CRITERIA}`;
}

const nullableNumber = { type: "number", nullable: true };

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
      product: { type: "string" }, price: nullableNumber, oldPrice: nullableNumber,
    },
    required: ["page", "habitual", "habitualLabel", "product"],
  },
};

const numberOrNull = (v) => (typeof v === "number" && Number.isFinite(v) ? v : null);

const label = (r) => (typeof r.habitualLabel === "string" && r.habitualLabel.trim()) || r.habitual;

function dedupeByKey(matches) {
  const seen = new Set();
  return matches.filter((m) => !seen.has(m.key) && seen.add(m.key));
}

export function aldiMatches(raw, offers, habituals) {
  const known = new Set(habituals.map((h) => h.normalizedName));
  return dedupeByKey(raw
    .filter((r) => Number.isInteger(r.index) && r.index >= 0 && r.index < offers.length && known.has(r.habitual))
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
  const known = new Set(habituals.map((h) => h.normalizedName));
  const pageNumbers = new Set(pages.map((p) => p.page));
  return dedupeByKey(raw
    .filter((r) => pageNumbers.has(r.page) && known.has(r.habitual))
    .map((r) => ({
      key: `lidl-p${r.page}-${r.habitual}`,
      habitual: r.habitual,
      habitualLabel: label(r),
      product: typeof r.product === "string" && r.product.trim() ? r.product.trim() : label(r),
      price: numberOrNull(r.price),
      oldPrice: numberOrNull(r.oldPrice),
      promo: null,
      page: r.page,
      validFrom: null,
      validUntil: null,
    })));
}

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const MAX_ATTEMPTS = 3;
const INITIAL_DELAY_MS = 2_000;

/**
 * Llama a Gemini con salida JSON. La key va en cabecera, nunca en la URL, y los errores solo
 * llevan el estado: los logs de Actions son públicos.
 */
export async function callGemini({ apiKey, model, prompt, schema }) {
  const url = `${GEMINI_BASE}/${model}:generateContent`;
  const body = JSON.stringify({
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: { responseMimeType: "application/json", responseSchema: schema, temperature: 0 },
  });
  let delay = INITIAL_DELAY_MS;
  for (let attempt = 1; ; attempt++) {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body,
    });
    if (response.ok) {
      const data = await response.json();
      const text = data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? "").join("") ?? "";
      const parsed = JSON.parse(text || "[]");
      if (!Array.isArray(parsed)) throw new Error("Gemini: respuesta que no es una lista");
      return parsed;
    }
    const retryable = response.status === 429 || response.status >= 500;
    if (!retryable || attempt >= MAX_ATTEMPTS) throw new Error(`Gemini: ${response.status}`);
    await new Promise((r) => setTimeout(r, delay));
    delay *= 2;
  }
}
