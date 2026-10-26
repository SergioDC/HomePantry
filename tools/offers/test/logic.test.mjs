import { test } from "node:test";
import assert from "node:assert/strict";
import { habitualProducts } from "../src/habitual.mjs";
import { aldiMatches, lidlMatches, aldiPrompt, lidlPrompt } from "../src/match.mjs";
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
    { index: 0, habitual: "queso", habitualLabel: "Queso", price: 99, score: 95 },
    { index: 5, habitual: "queso", habitualLabel: "Queso", score: 95 },
    { index: 1, habitual: "inventado", habitualLabel: "Inventado", score: 95 },
    { index: 0, habitual: "queso", habitualLabel: "Queso", score: 95 },
  ];
  assert.deepEqual(aldiMatches(raw, aldiOffers, habituals), [{
    key: "aldi-10", habitual: "queso", habitualLabel: "Queso", product: "Queso semicurado (PLENO SABOR)",
    price: 2.49, oldPrice: 2.85, promo: "-12%", page: null, validFrom: "2026-10-25", validUntil: "2026-11-01",
  }]);
});

test("lidlMatches descarta páginas inexistentes y habituales desconocidos, y nunca da precio", () => {
  const pages = [1, 2, 3].map((page) => ({ page, text: "", description: "" }));
  const raw = [
    { page: 9, habitual: "tomate", habitualLabel: "Tomate", product: "Tomate rama", price: 1.59, oldPrice: null, score: 95 },
    { page: 2, habitual: "inventado", habitualLabel: "X", product: "X", price: 1, oldPrice: null, score: 95 },
    { page: 2, habitual: "tomate", habitualLabel: "Tomate", product: "Tomate rama", price: "1,59", oldPrice: 1.69, score: 95 },
  ];
  assert.deepEqual(lidlMatches(raw, pages, habituals), [{
    key: "lidl-p2-tomate", habitual: "tomate", habitualLabel: "Tomate", product: "Tomate rama",
    price: null, oldPrice: null, promo: null, page: 2, validFrom: null, validUntil: null,
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
  const match = aldiMatches([{ index: 0, habitual: "queso", habitualLabel: "Queso", score: 95 }], aldiOffers, habituals)[0];
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

import { callGemini } from "../src/match.mjs";

const okResponse = (payload) => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify(payload) }] } }] }), { status: 200 });

test("callGemini pasa al siguiente modelo si uno está saturado o no responde", async () => {
  const calls = [];
  const fetchImpl = async (url) => {
    const model = url.split("/models/")[1].split(":")[0];
    calls.push(model);
    if (model === "lento") throw Object.assign(new Error("timeout"), { name: "TimeoutError" });
    if (model === "saturado") return new Response("{}", { status: 503 });
    return okResponse([{ index: 0 }]);
  };
  const result = await callGemini({ apiKey: "k", models: ["lento", "saturado", "bueno"], prompt: "p", schema: {}, fetchImpl, retryDelayMs: 0 });
  assert.deepEqual(result, { items: [{ index: 0 }], model: "bueno" });
  assert.deepEqual(calls.filter((m) => m === "bueno"), ["bueno"]);
  assert.ok(calls.includes("lento") && calls.includes("saturado"));
});

test("callGemini no prueba otros modelos ante un error que no es pasajero", async () => {
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(url);
    return new Response("{}", { status: 400 });
  };
  await assert.rejects(
    callGemini({ apiKey: "k", models: ["a", "b"], prompt: "p", schema: {}, fetchImpl, retryDelayMs: 0 }),
    /Gemini: 400/,
  );
  assert.equal(calls.length, 1);
});

test("callGemini con todos los modelos caídos falla con el último estado", async () => {
  const fetchImpl = async () => new Response("{}", { status: 503 });
  await assert.rejects(
    callGemini({ apiKey: "k", models: ["a", "b"], prompt: "p", schema: {}, fetchImpl, retryDelayMs: 0 }),
    /Gemini: 503/,
  );
});

test("el prompt de Lidl no pide precios", () => {
  assert.doesNotMatch(lidlPrompt(habituals, []), /precio|price/i);
});

import { sharesWord, DEFAULT_MODELS } from "../src/match.mjs";

test("sharesWord acepta variantes que comparten palabra, sin tildes ni plurales", () => {
  assert.equal(sharesWord("pollo", "Pollo", "Pechuga Pollo Marinada"), true);
  assert.equal(sharesWord("mozzarella rallado", "Mozzarella rallada", "Milbona Mozzarella Rallada"), true);
  assert.equal(sharesWord("pina en rodaja", "Piña", "Piña en almíbar"), true);
  assert.equal(sharesWord("uva sin pepita", "Uvas", "Uva blanca"), true);
});

test("sharesWord rechaza ofertas sin ninguna palabra en común", () => {
  assert.equal(sharesWord("pina en rodaja", "Piña", "Tomate Rama"), false);
  assert.equal(sharesWord("leche entera", "Leche", "Pan Integral"), false);
  // "de", "en", "sin"… no cuentan como palabra en común.
  assert.equal(sharesWord("masa de pizza", "Masa de pizza", "Crema de calabaza"), false);
});

test("aldiMatches y lidlMatches descartan coincidencias sin palabras en común", () => {
  const pages = [{ page: 7, text: "", description: "" }];
  const lidl = lidlMatches([{ page: 7, habitual: "tomate", habitualLabel: "Tomate", product: "Patatas Sabor Jamón", score: 95 }], pages, habituals);
  assert.deepEqual(lidl, []);
  const aldi = aldiMatches([{ index: 1, habitual: "queso", habitualLabel: "Queso", score: 95 }], aldiOffers, habituals);
  assert.deepEqual(aldi, []);
});

test("los modelos de respaldo no incluyen flash-lite", () => {
  assert.ok(DEFAULT_MODELS.length >= 2);
  assert.ok(DEFAULT_MODELS.every((m) => !m.includes("lite")));
});

test("los modelos de respaldo son los Flash vigentes, del más nuevo al más antiguo", () => {
  assert.deepEqual(DEFAULT_MODELS, ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash"]);
});

import { MIN_SCORE, expandPrompt, expandHabituals, verifyPrompt, keepVerified, otherModels } from "../src/match.mjs";

test("la nota mínima para avisar es 90", () => {
  assert.equal(MIN_SCORE, 90);
});

test("aldiMatches y lidlMatches descartan las notas por debajo de 90 o sin nota", () => {
  const aldi = aldiMatches([
    { index: 0, habitual: "queso", habitualLabel: "Queso", score: 89 },
    { index: 0, habitual: "queso", habitualLabel: "Queso" },
  ], aldiOffers, habituals);
  assert.deepEqual(aldi, []);
  const pages = [{ page: 2, text: "", description: "" }];
  const lidl = lidlMatches([
    { page: 2, habitual: "tomate", habitualLabel: "Tomate", product: "Tomate rama", score: 60 },
    { page: 2, habitual: "tomate", habitualLabel: "Tomate", product: "Tomate pera", score: 90 },
  ], pages, habituals);
  assert.deepEqual(lidl.map((m) => m.product), ["Tomate pera"]);
});

test("expandPrompt pasa los nombres del ticket y el ejemplo de abreviatura", () => {
  const prompt = expandPrompt([{ normalizedName: "sandia negra s/n sem", example: "SANDIA NEGRA S/N SEM" }]);
  assert.match(prompt, /sandia negra s\/n sem/);
  assert.match(prompt, /SANDIA NEGRA S\/N SEM/);
  assert.match(prompt, /sin semillas/);
});

test("expandHabituals añade fullName y, si falta o viene vacío, usa el nombre normalizado", () => {
  const raw = [
    { normalizedName: "queso", fullName: "  queso semicurado  " },
    { normalizedName: "tomate", fullName: " " },
    { normalizedName: "inventado", fullName: "algo" },
  ];
  assert.deepEqual(expandHabituals(raw, [...habituals, { normalizedName: "pan", example: "PAN" }]), [
    { normalizedName: "queso", example: "QUESO", fullName: "queso semicurado" },
    { normalizedName: "tomate", example: "TOMATE", fullName: "tomate" },
    { normalizedName: "pan", example: "PAN", fullName: "pan" },
  ]);
});

const expanded = [
  { normalizedName: "sandia negra s/n sem", example: "SANDIA NEGRA S/N SEM", fullName: "sandía negra sin semillas" },
  { normalizedName: "queso", example: "QUESO", fullName: "queso" },
];

test("verifyPrompt numera las parejas con el nombre completo, el del ticket y la oferta", () => {
  const prompt = verifyPrompt(expanded, [
    { habitual: "queso", product: "Queso semicurado (PLENO SABOR)", page: null },
    { habitual: "sandia negra s/n sem", product: "Sandía mini", page: 5 },
  ], [{ page: 5, description: "Fruta", text: "SANDIA MINI 1,49" }]);
  assert.match(prompt, /0\. .*queso.*Queso semicurado \(PLENO SABOR\)/);
  assert.match(prompt, /1\. .*sandía negra sin semillas.*SANDIA NEGRA S\/N SEM.*Sandía mini/);
  assert.match(prompt, /SANDIA MINI 1,49/);
});

test("verifyPrompt solo adjunta el texto de las páginas con candidatas", () => {
  const prompt = verifyPrompt(expanded, [{ habitual: "queso", product: "Queso", page: 2 }],
    [{ page: 2, description: "", text: "QUESO TIERNO" }, { page: 3, description: "", text: "OTRA PAGINA" }]);
  assert.match(prompt, /QUESO TIERNO/);
  assert.doesNotMatch(prompt, /OTRA PAGINA/);
});

test("keepVerified deja solo las candidatas con nota ≥ 90 del revisor y, si repite, la peor", () => {
  const candidates = ["a", "b", "c", "d"].map((product) => ({ product }));
  const raw = [
    { index: 0, score: 95 },
    { index: 1, score: 70 },
    { index: 3, score: 99 },
    { index: 3, score: 40 },
    { index: 9, score: 100 },
  ];
  assert.deepEqual(keepVerified(candidates, raw), [{ product: "a" }]);
});

test("otherModels quita el modelo que ya ha respondido", () => {
  assert.deepEqual(otherModels(["a", "b", "c"], "b"), ["a", "c"]);
});

test("los prompts puntúan con la escala y el caso de la sandía mini", () => {
  for (const prompt of [aldiPrompt(expanded, aldiOffers), lidlPrompt(expanded, []), verifyPrompt(expanded, [], [])]) {
    assert.match(prompt, /0 a 100/);
    assert.match(prompt, /Sandía mini/);
    assert.match(prompt, /Jengibre confitado/);
    assert.match(prompt, /más baja/);
  }
});

test("los prompts del buscador muestran el nombre completo junto al del ticket", () => {
  assert.match(aldiPrompt(expanded, aldiOffers), /sandia negra s\/n sem: sandía negra sin semillas \(en el ticket: SANDIA NEGRA S\/N SEM\)/);
});

test("callGemini pide razonar a fondo y no fija la temperatura", async () => {
  let sent;
  const fetchImpl = async (url, init) => {
    sent = JSON.parse(init.body);
    return okResponse([]);
  };
  await callGemini({ apiKey: "k", models: ["a"], prompt: "p", schema: {}, fetchImpl, retryDelayMs: 0 });
  assert.equal(sent.generationConfig.thinkingConfig.thinkingLevel, "high");
  assert.equal(sent.generationConfig.temperature, undefined);
});
