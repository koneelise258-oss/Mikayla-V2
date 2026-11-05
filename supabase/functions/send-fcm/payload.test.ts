// Exécution : node --experimental-strip-types --test supabase/functions/send-fcm/payload.test.ts
// (compatible Deno : `deno test supabase/functions/send-fcm/payload.test.ts`)
import { test } from "node:test";
import assert from "node:assert/strict";
import { buildPayloadData, CYCLE_NEUTRAL_TEXT, isAllowedType, ttlFor } from "./payload.ts";

const SENSITIVE = ["phaseName", "partnerNickname", "periodStart", "fertileWindow", "ovulation", "symptoms", "prediction"];

test("cycle : aucun champ sensible même si le payload les contient", () => {
  const out = buildPayloadData({
    type: "cycle",
    coupleId: "c1",
    senderId: "u1",
    body: "Règles prévues le 12, fenêtre fertile du 20 au 25",
    data: {
      phaseName: "Ovulation",
      partnerNickname: "Chérie",
      periodStart: "2026-11-12",
      fertileWindow: "20-25",
      ovulation: "2026-11-22",
      symptoms: "crampes",
      prediction: "J+2",
    },
  });
  assert.deepEqual(Object.keys(out).sort(), ["coupleId", "messageText", "recipientId", "senderId", "type"]);
  assert.equal(out.messageText, CYCLE_NEUTRAL_TEXT);
  const serialized = JSON.stringify(out);
  for (const word of ["Ovulation", "Chérie", "2026-11-12", "crampes", "fertile", "Règles", "J+2"]) {
    assert.ok(!serialized.includes(word), `fuite: ${word}`);
  }
  for (const k of SENSITIVE) assert.ok(!(k in out));
});

test("cycle : hidePreview ne réintroduit pas de contenu", () => {
  const out = buildPayloadData({ type: "cycle", senderId: "u1", body: "secret", hidePreview: true });
  assert.equal(out.messageText, CYCLE_NEUTRAL_TEXT);
});

test("message ordinaire : aperçu et nom transmis", () => {
  const out = buildPayloadData({
    type: "chat_message", coupleId: "c1", senderId: "u1", body: "Coucou",
    data: { messageId: "m1", senderName: "Alex" },
  });
  assert.equal(out.messageText, "Coucou");
  assert.equal(out.senderName, "Alex");
  assert.equal(out.messageId, "m1");
});

test("data ne peut pas usurper type ni senderId", () => {
  const out = buildPayloadData({ type: "game", senderId: "u1", data: { type: "cycle", senderId: "evil", gameTitle: "Quiz" } });
  assert.equal(out.type, "game");
  assert.equal(out.senderId, "u1");
  assert.equal(out.gameTitle, "Quiz");
});

test("calendrier : champs conservés, valeurs non-string filtrées ou converties", () => {
  const out = buildPayloadData({ type: "calendar_event", senderId: "u1", data: { eventTitle: "Dîner", n: 3, obj: { a: 1 }, nul: null } });
  assert.equal(out.eventTitle, "Dîner");
  assert.equal(out.n, "3");
  assert.ok(!("obj" in out));
  assert.ok(!("nul" in out));
});

test("types autorisés et TTL", () => {
  assert.ok(isAllowedType("call_cancelled"));
  assert.ok(isAllowedType("call_ended"));
  assert.ok(!isAllowedType("drop_table"));
  assert.equal(ttlFor("call"), "30s");
  assert.equal(ttlFor("call_cancelled"), "30s");
  assert.equal(ttlFor("chat_message"), "86400s");
});

test("recipientId transmis et non usurpable par data", () => {
  const out = buildPayloadData({ type: "chat_message", senderId: "u1", recipientId: "u2", data: { recipientId: "evil" } });
  assert.equal(out.recipientId, "u2");
});

import { isPairedCoupleMember, validateServiceAccount } from "./payload.ts";

test("couple : status paired obligatoire", () => {
  const row = { user1_id: "a", user2_id: "b", status: "paired" };
  assert.ok(isPairedCoupleMember(row, "a", "b"));
  assert.ok(isPairedCoupleMember(row, "b", "a"));
  assert.ok(!isPairedCoupleMember({ ...row, status: "pending" }, "a", "b"));
  assert.ok(!isPairedCoupleMember({ ...row, status: null }, "a", "b"));
  assert.ok(!isPairedCoupleMember(row, "a", "x"));
  assert.ok(!isPairedCoupleMember(row, "a", "a"));
  assert.ok(!isPairedCoupleMember(null, "a", "b"));
  assert.ok(!isPairedCoupleMember({ user1_id: "a", user2_id: null, status: "paired" }, "a", ""));
});

test("compte de service : validé sans exposer de valeur", () => {
  const ok = JSON.stringify({ type: "service_account", project_id: "gen-lang-client-0988385912", client_email: "x@y", private_key: "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----" });
  assert.equal(validateServiceAccount(ok), null);
  assert.equal(validateServiceAccount(""), "missing_service_account");
  assert.equal(validateServiceAccount("{oops"), "invalid_service_account_json");
  assert.equal(validateServiceAccount(ok.replace("gen-lang-client-0988385912", "autre-projet")), "unexpected_project_id");
  assert.equal(validateServiceAccount(JSON.stringify({ type: "service_account", project_id: "gen-lang-client-0988385912", client_email: "x@y" })), "missing_private_key");
  // le code d'erreur ne contient jamais la clé
  const bad = validateServiceAccount(ok.replace("gen-lang-client-0988385912", "p"));
  assert.ok(!String(bad).includes("PRIVATE"));
});
