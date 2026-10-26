// Lidl publica sus folletos en el visor de Schwarz (leaflets.schwarz). La página de folletos enlaza
// cada uno por su slug y la API devuelve, por página, el texto del OCR y una descripción. No hay
// productos estructurados (`products` viene vacío).
import { getText } from "./http.mjs";
import { todayMadrid } from "./dates.mjs";

const OVERVIEW_URL = "https://www.lidl.es/c/descubre-nuevas-ofertas-cada-semana-folletos-lidl/s10087402";
const FLYER_API = "https://endpoints.leaflets.schwarz/v4/flyer";

/** Slugs de los folletos de alimentación enlazados en la página, sin repetir y en orden de aparición. */
export function lidlFlyerSlugs(html) {
  const slugs = [...html.matchAll(/\/l\/folletos\/(folleto-alimentacion[^/"'\s]+)\/ar\/0/g)].map((m) => m[1]);
  return [...new Set(slugs)];
}

/** El folleto vigente `today`; si no hay ninguno (p. ej. domingo), el que empieza antes. */
export function pickCurrentFlyer(flyers, today) {
  return (
    flyers.find((f) => f.offerStartDate <= today && today <= f.offerEndDate) ??
    [...flyers].sort((a, b) => a.offerStartDate.localeCompare(b.offerStartDate))[0]
  );
}

export function lidlPages(flyer) {
  return flyer.pages.map((p) => ({
    page: p.number,
    text: (p.keyWords ?? "").trim(),
    description: (p.altText ?? "").trim(),
  }));
}

export async function fetchLidl(now) {
  const slugs = lidlFlyerSlugs(await getText(OVERVIEW_URL));
  if (!slugs.length) throw new Error("sin folletos de alimentación www.lidl.es");

  const flyers = [];
  for (const slug of slugs) {
    const url = `${FLYER_API}?flyer_identifier=${encodeURIComponent(slug)}&region_id=0&region_code=0`;
    flyers.push(JSON.parse(await getText(url)).flyer);
  }
  const flyer = pickCurrentFlyer(flyers, todayMadrid(now));
  return {
    store: "Lidl",
    name: flyer.name,
    url: flyer.flyerUrlAbsolute,
    validFrom: flyer.offerStartDate,
    validUntil: flyer.offerEndDate,
    pages: lidlPages(flyer),
  };
}
