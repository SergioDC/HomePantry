import { test } from "node:test";
import assert from "node:assert/strict";
import {
  PUSH_CLOSED, PUSH_REMINDER, TTL_MS, backoffMs, buildMessages, daysInMonth, dueReminderMonthKeys,
  isReminderTarget, madridParts, recipients, staleTokenUids,
} from "../src/logic.mjs";

const at = (iso) => dueReminderMonthKeys(new Date(iso));

test("madridParts pasa a hora de Madrid con y sin horario de verano", () => {
  assert.deepEqual(madridParts(new Date("2026-06-28T18:00:00Z")), { year: 2026, month: 6, day: 28, hour: 20 });
  assert.deepEqual(madridParts(new Date("2026-10-29T19:00:00Z")), { year: 2026, month: 10, day: 29, hour: 20 });
  assert.deepEqual(madridParts(new Date("2026-12-31T23:30:00Z")), { year: 2027, month: 1, day: 1, hour: 0 });
});

test("daysInMonth: 28, 29, 30 y 31 días", () => {
  assert.equal(daysInMonth(2027, 2), 28);
  assert.equal(daysInMonth(2028, 2), 29);
  assert.equal(daysInMonth(2026, 4), 30);
  assert.equal(daysInMonth(2026, 10), 31);
});

test("el recordatorio abre a las 20:00 del antepenúltimo día (octubre, horario de invierno)", () => {
  assert.deepEqual(at("2026-10-29T18:59:00Z"), []); // 19:59 en Madrid
  assert.deepEqual(at("2026-10-29T19:00:00Z"), ["2026-10"]); // 20:00
  assert.deepEqual(at("2026-10-28T19:00:00Z"), []); // un día antes
});

test("el recordatorio abre a las 20:00 en horario de verano", () => {
  assert.deepEqual(at("2026-06-28T17:59:00Z"), []); // 19:59 en Madrid
  assert.deepEqual(at("2026-06-28T18:00:00Z"), ["2026-06"]); // 20:00 (30 días → día 28)
});

test("febrero: día 26 en año normal y 27 en bisiesto", () => {
  assert.deepEqual(at("2027-02-26T19:00:00Z"), ["2027-02"]);
  assert.deepEqual(at("2028-02-26T19:00:00Z"), []);
  assert.deepEqual(at("2028-02-27T19:00:00Z"), ["2028-02"]);
});

test("sigue abierto hasta fin de mes, salvo en horas de silencio", () => {
  assert.deepEqual(at("2026-10-30T08:00:00Z"), ["2026-10"]); // 09:00
  assert.deepEqual(at("2026-10-31T20:59:00Z"), ["2026-10"]); // 21:59
  assert.deepEqual(at("2026-10-31T21:00:00Z"), []); // 22:00
  assert.deepEqual(at("2026-10-30T07:59:00Z"), []); // 08:59
});

test("recuperación del mes anterior los días 1 a 3", () => {
  assert.deepEqual(at("2026-11-01T09:00:00Z"), ["2026-10"]); // 10:00
  assert.deepEqual(at("2026-11-03T20:00:00Z"), ["2026-10"]); // 21:00
  assert.deepEqual(at("2026-11-04T09:00:00Z"), []);
  assert.deepEqual(at("2027-01-02T10:00:00Z"), ["2026-12"]); // cambio de año
  assert.deepEqual(at("2026-11-01T06:00:00Z"), []); // 07:00, silencio
});

test("isReminderTarget: solo meses abiertos, sin archivar y sin aviso previo", () => {
  const base = { monthKey: "2026-10", activity: false, closed: false, archived: false, reminderSentAt: null };
  assert.equal(isReminderTarget(base, "2026-10"), true);
  assert.equal(isReminderTarget({ monthKey: "2026-10" }, "2026-10"), true); // grupos antiguos sin los campos
  assert.equal(isReminderTarget(base, "2026-11"), false);
  assert.equal(isReminderTarget({ ...base, activity: true }, "2026-10"), false);
  assert.equal(isReminderTarget({ ...base, closed: true }, "2026-10"), false);
  assert.equal(isReminderTarget({ ...base, archived: true }, "2026-10"), false);
  assert.equal(isReminderTarget({ ...base, reminderSentAt: new Date() }, "2026-10"), false);
});

test("recipients: participantes con token, sin quien cierra y sin tokens repetidos", () => {
  const tokens = new Map([["ana", "t-ana"], ["luis", "t-luis"], ["eva", "t-luis"], ["fuera", "t-fuera"]]);
  assert.deepEqual(recipients(["ana", "luis", "eva", "sin-token"], tokens, "ana"), [{ uid: "luis", token: "t-luis" }]);
  assert.deepEqual(recipients(["ana", "luis"], tokens), [
    { uid: "ana", token: "t-ana" },
    { uid: "luis", token: "t-luis" },
  ]);
  assert.deepEqual(recipients(undefined, tokens), []);
});

test("buildMessages: solo datos, prioridad alta y byName solo en el cierre", () => {
  const targets = [{ uid: "luis", token: "t-luis" }];
  assert.deepEqual(buildMessages(PUSH_CLOSED, { monthId: "m1", monthName: "Octubre", byName: "Ana" }, targets), [{
    token: "t-luis",
    data: { type: "sncount_closed", monthId: "m1", monthName: "Octubre", byName: "Ana" },
    android: { priority: "high", ttl: TTL_MS },
  }]);
  const [reminder] = buildMessages(PUSH_REMINDER, { monthId: "m1", monthName: undefined }, targets);
  assert.deepEqual(reminder.data, { type: "sncount_reminder", monthId: "m1", monthName: "" });
});

test("staleTokenUids: solo los errores de token inválido", () => {
  const targets = [{ uid: "a", token: "1" }, { uid: "b", token: "2" }, { uid: "c", token: "3" }, { uid: "d", token: "4" }];
  const responses = [
    { success: true },
    { success: false, error: { code: "messaging/registration-token-not-registered" } },
    { success: false, error: { code: "messaging/internal-error" } },
    { success: false, error: { code: "messaging/invalid-registration-token" } },
  ];
  assert.deepEqual(staleTokenUids(targets, responses), ["b", "d"]);
  assert.deepEqual(staleTokenUids(targets, []), []);
});

test("backoffMs crece hasta 5 minutos", () => {
  assert.equal(backoffMs(0), 5_000);
  assert.equal(backoffMs(3), 40_000);
  assert.equal(backoffMs(20), 300_000);
});
