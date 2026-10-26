// Aldi es una web Next.js: los productos van dentro de __NEXT_DATA__, en strings con JSON
// serializado (datos de Algolia). Ahí también hay productos que no están rebajados.
import { getText } from "./http.mjs";
import { unixToMadridDay } from "./dates.mjs";

export const ALDI_URL = "https://www.aldi.es/ofertas.html";

/** Productos rebajados (precio tachado o texto de promo) de la página de ofertas, uno por objectID. */
export function parseAldiOffers(html) {
  const m = html.match(/<script id="__NEXT_DATA__" type="application\/json"[^>]*>([\s\S]*?)<\/script>/);
  if (!m) throw new Error("sin __NEXT_DATA__ www.aldi.es");

  const found = new Map();
  const walk = (o) => {
    if (typeof o === "string") {
      const t = o.trim();
      if ((t[0] === "{" || t[0] === "[") && t.includes("currentPrice")) {
        try { walk(JSON.parse(t)); } catch { /* string que no es JSON */ }
      }
    } else if (Array.isArray(o)) {
      o.forEach(walk);
    } else if (o && typeof o === "object") {
      if (o.currentPrice && o.name) {
        const id = String(o.objectID ?? o.name);
        if (!found.has(id)) found.set(id, o);
      } else {
        Object.values(o).forEach(walk);
      }
    }
  };
  walk(JSON.parse(m[1]));

  const offers = [];
  for (const [id, o] of found) {
    const p = o.currentPrice;
    const oldPrice = p.strikePrice?.strikePriceValue ?? null;
    const promo = p.priceTagLabels?.promoText1 ?? null;
    if (oldPrice == null && promo == null) continue;
    offers.push({
      id,
      name: o.name,
      brand: (o.brandName ?? "").replace(/®/g, "").trim() || null,
      unit: o.salesUnit ?? null,
      price: p.priceValue,
      oldPrice,
      promo,
      validFrom: unixToMadridDay(p.validFrom),
      validUntil: unixToMadridDay(p.validUntil),
    });
  }
  return offers;
}

export async function fetchAldi() {
  const offers = parseAldiOffers(await getText(ALDI_URL));
  const froms = offers.map((o) => o.validFrom).sort();
  const untils = offers.map((o) => o.validUntil).sort();
  return {
    store: "Aldi",
    name: "Ofertas ALDI",
    url: ALDI_URL,
    validFrom: froms[0] ?? null,
    validUntil: untils.at(-1) ?? null,
    offers,
  };
}
