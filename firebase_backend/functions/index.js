const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const DATABASE_ID = "ai-studio-android-mikayala-9f74e746-a740-4f00-85b2-7a4d380087a6";
const SHORT_TTL_TYPES = new Set(["call", "call_cancelled", "call_ended"]);
const CYCLE_NEUTRAL_TEXT = "Vous avez une nouvelle notification";

function stringValues(rawJson) {
  if (!rawJson || typeof rawJson !== "string") return {};
  try {
    const parsed = JSON.parse(rawJson);
    const out = {};
    for (const [k, v] of Object.entries(parsed || {})) {
      if (v !== null && v !== undefined && typeof v !== "object") {
        out[k] = String(v);
      }
    }
    return out;
  } catch {
    return {};
  }
}

exports.dispatchCouplePush = onDocumentCreated(
  {
    document: "couple_spaces/{coupleId}/push_requests/{requestId}",
    database: DATABASE_ID,
    region: "europe-west1",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const req = snap.data();
    const coupleId = event.params.coupleId;
    const { senderId, recipientId, type, body, dataJson, hidePreview } = req || {};

    if (!senderId || !recipientId || senderId === recipientId || !type) {
      await snap.ref.delete().catch(() => {});
      return;
    }

    const db = getFirestore(DATABASE_ID);

    // 1. Vérifier que le couple est apparié et que les deux membres y appartiennent
    const coupleSnap = await db.collection("couple_spaces").doc(coupleId).get();
    const couple = coupleSnap.data();
    if (
      !couple ||
      couple.status !== "paired" ||
      !Array.isArray(couple.memberUids) ||
      !couple.memberUids.includes(senderId) ||
      !couple.memberUids.includes(recipientId)
    ) {
      await snap.ref.delete().catch(() => {});
      return;
    }

    // 2. Récupérer le jeton FCM privé du destinataire depuis /users/{recipientId}
    const userSnap = await db.collection("users").doc(recipientId).get();
    const fcmToken = userSnap.data()?.fcmToken;
    if (!fcmToken || typeof fcmToken !== "string") {
      await snap.ref.delete().catch(() => {});
      return;
    }

    // 3. Construire le payload FCM data-only (confidentialité cycle + aperçu)
    const baseData = {
      type: String(type),
      coupleId: String(coupleId),
      senderId: String(senderId),
      recipientId: String(recipientId),
    };

    const payloadData =
      type === "cycle"
        ? { ...baseData, messageText: CYCLE_NEUTRAL_TEXT }
        : {
            ...stringValues(dataJson),
            ...baseData,
            messageText: hidePreview ? "Nouveau message reçu 🔒" : String(body || ""),
          };

    const ttlMs = SHORT_TTL_TYPES.has(type) ? 30 * 1000 : 86400 * 1000;

    try {
      await getMessaging().send({
        token: fcmToken,
        data: payloadData,
        android: {
          priority: "high",
          ttl: ttlMs,
        },
      });
    } catch (err) {
      const code = err?.code || "";
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        await db
          .collection("users")
          .doc(recipientId)
          .update({ fcmToken: "" })
          .catch(() => {});
      }
      console.error("dispatchCouplePush error:", code, err?.message);
    } finally {
      // Nettoyer la demande traitée pour garder Firestore léger
      await snap.ref.delete().catch(() => {});
    }
  }
);
