// Fechas en hora de Madrid: el cron de GitHub va en UTC, pero "hoy" y "la semana" son los de la casa.
const madridDay = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Europe/Madrid", year: "numeric", month: "2-digit", day: "2-digit",
});

/** Día de `now` en Madrid, como "YYYY-MM-DD" (en-CA ya da ese formato). */
export function todayMadrid(now) {
  return madridDay.format(now);
}

/** Lunes de la semana de `now` en Madrid, como "YYYY-MM-DD". */
export function weekStartMadrid(now) {
  // Se trabaja sobre el día civil (mediodía UTC) para que restar días no dependa del cambio de hora.
  const day = new Date(`${todayMadrid(now)}T12:00:00Z`);
  const daysSinceMonday = (day.getUTCDay() + 6) % 7;
  day.setUTCDate(day.getUTCDate() - daysSinceMonday);
  return day.toISOString().slice(0, 10);
}

/** Día en Madrid de una marca de tiempo en segundos Unix (las fechas de Aldi vienen así). */
export function unixToMadridDay(seconds) {
  return todayMadrid(new Date(seconds * 1000));
}
