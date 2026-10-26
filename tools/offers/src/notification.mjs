// Composición del documento households/{code}/notifications/{id} que lee la app (AppNotification).
import { weekStartMadrid } from "./dates.mjs";

export function offersTitle(n) {
  return n === 1 ? "1 oferta en cosas que compráis" : `${n} ofertas en cosas que compráis`;
}

/**
 * Aviso de la semana, o null si no hay ninguna oferta: un aviso vacío sería ruido.
 * Sin readBy, addedKeys ni createdAt: los pone quien lo guarda (ver mergeWeekNotification).
 */
export function buildNotification({ stores, now }) {
  const withOffers = stores.filter((s) => s.offers.length > 0);
  const total = withOffers.reduce((n, s) => n + s.offers.length, 0);
  if (total === 0) return null;
  return {
    type: "offers",
    title: offersTitle(total),
    weekStart: weekStartMadrid(now),
    stores: withOffers.map(({ source, offers }) => ({
      store: source.store,
      name: source.name,
      url: source.url,
      validFrom: source.validFrom,
      validUntil: source.validUntil,
      offers,
    })),
  };
}

/** Al relanzar en la misma semana se reescribe el aviso, pero sin perder quién lo leyó ni qué se añadió. */
export function mergeWeekNotification(existing, fresh) {
  return { ...fresh, readBy: existing?.readBy ?? [], addedKeys: existing?.addedKeys ?? [] };
}
