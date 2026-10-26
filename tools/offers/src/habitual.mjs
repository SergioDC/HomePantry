// Productos "habituales": los comprados al menos MIN_LINES veces en los últimos WINDOW_DAYS días,
// según las líneas de ticket (`purchases`), agrupando por nombre normalizado.
const MIN_LINES = 2;
const WINDOW_DAYS = 90;

export function habitualProducts(purchases, now) {
  const since = now.getTime() - WINDOW_DAYS * 86_400_000;
  const groups = new Map();
  for (const p of purchases) {
    if (!p.normalizedName || p.date.getTime() < since) continue;
    const g = groups.get(p.normalizedName) ?? { count: 0, latest: null };
    g.count++;
    if (!g.latest || p.date > g.latest.date) g.latest = p;
    groups.set(p.normalizedName, g);
  }
  return [...groups]
    .filter(([, g]) => g.count >= MIN_LINES)
    .map(([normalizedName, g]) => ({ normalizedName, example: g.latest.rawName }))
    .sort((a, b) => a.normalizedName.localeCompare(b.normalizedName));
}
