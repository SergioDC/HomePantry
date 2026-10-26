import { test } from "node:test";
import assert from "node:assert/strict";
import { habitualProducts } from "../src/habitual.mjs";
import { aldiMatches, lidlMatches } from "../src/match.mjs";
import { offersTitle, buildNotification, mergeWeekNotification } from "../src/notification.mjs";

const now = new Date("2026-10-26T07:00:00Z");
const daysAgo = (n) => new Date(now.getTime() - n * 86_400_000);

test("habitualProducts: ≥ 2 líneas en 90 días, sin nombres vacíos", () => {
  const purchases = [
    { normalizedName: "leche", rawName: "LECHE ENTERA", date: daysAgo(40) },
    { normalizedName: "leche", rawName: "LECHE ENT HACENDADO", date: daysAgo(3) },
    { normalizedName: "pan", rawName: "PAN", date: daysAgo(2) },
    { normalizedName: "huevo", rawName: "HUEVOS", date: daysAgo(10) },
    { normalizedName: "huevo", rawName: "HUEVOS L", date: daysAgo(91) },
    { normalizedName: "", rawName: "?", date: daysAgo(1) },
    { normalizedName: "", rawName: "?", date: daysAgo(2) },
    { normalizedName: "", rawName: "?", date: daysAgo(3) },
  ];
  assert.deepEqual(habitualProducts(purchases, now), [{ normalizedName: "leche", example: "LECHE ENT HACENDADO" }]);
});

const habituals = [{ normalizedName: "queso", example: "QUESO" }, { normalizedName: "tomate", example: "TOMATE" }];
const aldiOffers = [
  { id: "10", name: "Queso semicurado", brand: "PLENO SABOR", unit: null, price: 2.49, oldPrice: 2.85, promo: "-12%", validFrom: "2026-10-25", validUntil: "2026-11-01" },
  { id: "11", name: "Bacon", brand: null, unit: null, price: 1.65, oldPrice: 1.85, promo: null, validFrom: "2026-10-25", validUntil: "2026-11-01" },
];

test("aldiMatches toma los datos de la oferta por índice y descarta lo inválido", () => {
  const raw = [
    { index: 0, habitual: "queso", habitualLabel: "Queso", price: 99 },
    { index: 5, habitual: "queso", habitualLabel: "Queso" },
    { index: 1, habitual: "inventado", habitualLabel: "Inventado" },
    { index: 0, habitual: "queso", habitualLabel: "Queso" },
  ];
  assert.deepEqual(aldiMatches(raw, aldiOffers, habituals), [{
    key: "aldi-10", habitual: "queso", habitualLabel: "Queso", product: "Queso semicurado (PLENO SABOR)",
    price: 2.49, oldPrice: 2.85, promo: "-12%", page: null, validFrom: "2026-10-25", validUntil: "2026-11-01",
  }]);
});

test("lidlMatches descarta páginas inexistentes, habituales desconocidos y precios no numéricos", () => {
  const pages = [1, 2, 3].map((page) => ({ page, text: "", description: "" }));
  const raw = [
    { page: 9, habitual: "tomate", habitualLabel: "Tomate", product: "Tomate rama", price: 1.59, oldPrice: null },
    { page: 2, habitual: "inventado", habitualLabel: "X", product: "X", price: 1, oldPrice: null },
    { page: 2, habitual: "tomate", habitualLabel: "Tomate", product: "Tomate rama", price: "1,59", oldPrice: 1.69 },
  ];
  assert.deepEqual(lidlMatches(raw, pages, habituals), [{
    key: "lidl-p2-tomate", habitual: "tomate", habitualLabel: "Tomate", product: "Tomate rama",
    price: null, oldPrice: 1.69, promo: null, page: 2, validFrom: null, validUntil: null,
  }]);
});

test("offersTitle en singular y plural", () => {
  assert.equal(offersTitle(1), "1 oferta en cosas que compráis");
  assert.equal(offersTitle(3), "3 ofertas en cosas que compráis");
});

const aldiSource = { store: "Aldi", name: "Ofertas ALDI", url: "https://www.aldi.es/ofertas.html", validFrom: "2026-10-25", validUntil: "2026-11-01", offers: aldiOffers };
const lidlSource = { store: "Lidl", name: "FOLLETO", url: "https://lidl", validFrom: "2026-10-26", validUntil: "2026-11-01", pages: [] };

test("buildNotification sin ofertas devuelve null", () => {
  assert.equal(buildNotification({ stores: [{ source: lidlSource, offers: [] }], now }), null);
});

test("buildNotification incluye solo las tiendas con ofertas", () => {
  const match = aldiMatches([{ index: 0, habitual: "queso", habitualLabel: "Queso" }], aldiOffers, habituals)[0];
  const n = buildNotification({
    stores: [{ source: lidlSource, offers: [] }, { source: aldiSource, offers: [match] }],
    now: new Date("2026-11-01T23:30:00Z"),
  });
  assert.equal(n.type, "offers");
  assert.equal(n.title, "1 oferta en cosas que compráis");
  assert.equal(n.weekStart, "2026-11-02");
  assert.deepEqual(n.stores, [{
    store: "Aldi", name: "Ofertas ALDI", url: "https://www.aldi.es/ofertas.html",
    validFrom: "2026-10-25", validUntil: "2026-11-01", offers: [match],
  }]);
});

test("mergeWeekNotification conserva readBy y addedKeys del aviso existente", () => {
  assert.deepEqual(
    mergeWeekNotification({ readBy: ["u1"], addedKeys: ["aldi-1"], title: "viejo" }, { title: "nuevo", stores: [] }),
    { title: "nuevo", stores: [], readBy: ["u1"], addedKeys: ["aldi-1"] },
  );
  assert.deepEqual(mergeWeekNotification(null, { title: "nuevo" }), { title: "nuevo", readBy: [], addedKeys: [] });
});
