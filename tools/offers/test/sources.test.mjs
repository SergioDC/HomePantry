import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { todayMadrid, weekStartMadrid, unixToMadridDay } from "../src/dates.mjs";
import { lidlFlyerSlugs, pickCurrentFlyer, lidlPages } from "../src/lidl.mjs";
import { parseAldiOffers } from "../src/aldi.mjs";

const fixture = (name) => readFileSync(new URL(`./fixtures/${name}`, import.meta.url), "utf8");

test("weekStartMadrid usa la hora de Madrid, no UTC", () => {
  // Domingo 23:30 UTC = lunes 00:30 en Madrid (horario de invierno).
  assert.equal(weekStartMadrid(new Date("2026-11-01T23:30:00Z")), "2026-11-02");
  assert.equal(weekStartMadrid(new Date("2026-10-28T10:00:00Z")), "2026-10-26");
});

test("todayMadrid el día del cambio de hora", () => {
  assert.equal(todayMadrid(new Date("2026-10-25T22:30:00Z")), "2026-10-25");
});

test("unixToMadridDay convierte segundos Unix al día de Madrid", () => {
  assert.equal(unixToMadridDay(1793487599), "2026-10-31");
});

test("lidlFlyerSlugs devuelve solo folletos de alimentación, sin repetir", () => {
  assert.deepEqual(lidlFlyerSlugs(fixture("lidl-overview.html")), ["folleto-alimentacion-a", "folleto-alimentacion-c"]);
});

const flyerA = { name: "A", offerStartDate: "2026-10-26", offerEndDate: "2026-11-01" };
const flyerB = { name: "B", offerStartDate: "2026-11-02", offerEndDate: "2026-11-08" };

test("pickCurrentFlyer elige el vigente hoy", () => {
  assert.equal(pickCurrentFlyer([flyerB, flyerA], "2026-10-28"), flyerA);
});

test("pickCurrentFlyer sin vigente elige el que empieza antes", () => {
  assert.equal(pickCurrentFlyer([flyerB, flyerA], "2026-10-25"), flyerA);
});

test("lidlPages convierte textos nulos en vacíos y recorta espacios", () => {
  const pages = lidlPages({ pages: [{ number: 1, keyWords: null, altText: "  Frutas  " }] });
  assert.deepEqual(pages, [{ page: 1, text: "", description: "Frutas" }]);
});

test("parseAldiOffers devuelve solo productos rebajados, sin duplicados", () => {
  const offers = parseAldiOffers(fixture("aldi-ofertas.html"));
  assert.deepEqual(offers, [
    {
      id: "1", name: "Queso semicurado", brand: "PLENO SABOR", unit: "400 g",
      price: 2.49, oldPrice: 2.85, promo: "-12%", validFrom: "2026-10-25", validUntil: "2026-10-31",
    },
    {
      id: "2", name: "Panecillo integral", brand: null, unit: null,
      price: 0.25, oldPrice: null, promo: "4x1 €", validFrom: "2026-10-25", validUntil: "2026-10-31",
    },
  ]);
});

test("parseAldiOffers falla si no está __NEXT_DATA__", () => {
  assert.throws(() => parseAldiOffers("<html></html>"));
});
