// Supabase Edge Function: send-fcm
// Dispatches push notifications to Android devices using FCM HTTP v1 API.
// Supports BOTH Supabase mode (JWT + Postgres) and Firebase mode (Firebase ID Token + Firestore),
// keeping push notifications 100% FREE without Firebase Blaze / Cloud Functions.
// Requires environment secret: FCM_SERVICE_ACCOUNT_JSON

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.7";
import {
  FIRESTORE_DATABASE_ID,
  buildPayloadData,
  extractFirestoreCouple,
  extractFirestoreFcmToken,
  isAllowedType,
  isPairedCoupleMember,
  normalizeServiceAccount,
  ttlFor,
  validateServiceAccount,
} from "./payload.ts";

interface SendFcmRequest {
  mode?: "firebase" | "supabase";
  coupleId: string;
  recipientId: string;
  type:
    | "chat_message" | "call" | "missed_call" | "audio" | "image" | "video"
    | "vault_add" | "game" | "game_turn" | "anniversary" | "mood_update"
    | "poll_created" | "love_bomb" | "calendar_event" | "cycle"
    | "call_declined" | "call_cancelled" | "call_ended";
  title?: string;
  body?: string;
  data?: Record<string, string>;
  hidePreview?: boolean;
}

function base64url(input: string | Uint8Array): string {
  const binary = typeof input === "string"
    ? new TextEncoder().encode(input)
    : input;
  let str = "";
  for (let i = 0; i < binary.length; i++) {
    str += String.fromCharCode(binary[i]);
  }
  const base64 = btoa(str);
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64urlToBytes(b64u: string): Uint8Array {
  const padded = b64u.replace(/-/g, "+").replace(/_/g, "/") +
    "=".repeat((4 - (b64u.length % 4)) % 4);
  const raw = atob(padded);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

let cachedJwks: { expiresAt: number; keys: Record<string, JsonWebKey> } | null = null;

async function getFirebasePublicKeys(): Promise<Record<string, JsonWebKey>> {
  const now = Date.now();
  if (cachedJwks && now < cachedJwks.expiresAt) {
    return cachedJwks.keys;
  }
  const res = await fetch(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
  );
  if (!res.ok) {
    throw new Error(`JWKS fetch failed (${res.status})`);
  }
  const json = await res.json();
  const map: Record<string, JsonWebKey> = {};
  for (const k of json.keys ?? []) {
    if (k.kid) map[k.kid] = k;
  }
  cachedJwks = { expiresAt: now + 3600_000, keys: map };
  return map;
}

async function verifyFirebaseIdToken(idToken: string, projectId: string): Promise<string | null> {
  const parts = idToken.trim().split(".");
  if (parts.length !== 3) return null;
  const [headerB64, payloadB64, sigB64] = parts;
  let header: { alg?: string; kid?: string };
  let payload: { aud?: string; iss?: string; exp?: number; sub?: string };
  try {
    header = JSON.parse(new TextDecoder().decode(base64urlToBytes(headerB64)));
    payload = JSON.parse(new TextDecoder().decode(base64urlToBytes(payloadB64)));
  } catch {
    return null;
  }
  if (header.alg !== "RS256" || !header.kid) return null;
  const nowSec = Math.floor(Date.now() / 1000);
  if (!payload.sub || payload.aud !== projectId) return null;
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (!payload.exp || payload.exp <= nowSec) return null;

  const keys = await getFirebasePublicKeys();
  const jwk = keys[header.kid];
  if (!jwk) return null;

  const cryptoKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: { name: "SHA-256" } },
    false,
    ["verify"]
  );
  const verified = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    base64urlToBytes(sigB64),
    new TextEncoder().encode(`${headerB64}.${payloadB64}`)
  );
  return verified ? payload.sub : null;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  const cleanKey = pem
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s+/g, "");
  const binaryKey = Uint8Array.from(atob(cleanKey), (c) => c.charCodeAt(0));

  return await crypto.subtle.importKey(
    "pkcs8",
    binaryKey.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: { name: "SHA-256" } },
    false,
    ["sign"]
  );
}

async function getGoogleAccessToken(serviceAccount: any, scopes: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claimSet = {
    iss: serviceAccount.client_email,
    scope: scopes,
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };

  const encodedHeader = base64url(JSON.stringify(header));
  const encodedClaimSet = base64url(JSON.stringify(claimSet));
  const signatureInput = `${encodedHeader}.${encodedClaimSet}`;

  const privateKey = await importPrivateKey(serviceAccount.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(signatureInput)
  );

  const jwt = `${signatureInput}.${base64url(new Uint8Array(signature))}`;

  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!tokenResponse.ok) {
    const errorText = await tokenResponse.text();
    throw new Error(`Failed to obtain Google access token: ${errorText}`);
  }

  const tokenData = await tokenResponse.json();
  return tokenData.access_token;
}

async function fetchFirestoreDoc(
  projectId: string,
  databaseId: string,
  docPath: string,
  googleToken: string
) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/${databaseId}/documents/${docPath}`;
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${googleToken}` },
  });
  if (resp.status === 404) return null;
  if (!resp.ok) {
    throw new Error(`Firestore read ${docPath} failed (${resp.status})`);
  }
  return await resp.json();
}

async function clearInvalidFirestoreFcmToken(
  projectId: string,
  databaseId: string,
  uid: string,
  googleToken: string
) {
  const url =
    `https://firestore.googleapis.com/v1/projects/${projectId}/databases/${databaseId}/documents/users/${uid}?updateMask.fieldPaths=fcmToken`;
  await fetch(url, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${googleToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields: { fcmToken: { stringValue: "" } } }),
  });
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-firebase-token",
      },
    });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const databaseId = Deno.env.get("FIRESTORE_DATABASE_ID") ?? FIRESTORE_DATABASE_ID;

    const fcmServiceAccountJson = normalizeServiceAccount(Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ?? "", {
      clientEmail: Deno.env.get("FCM_CLIENT_EMAIL") ?? undefined,
      privateKey: Deno.env.get("FCM_PRIVATE_KEY") ?? undefined,
      projectId: Deno.env.get("FCM_PROJECT_ID") ?? undefined,
    });

    const saError = validateServiceAccount(
      fcmServiceAccountJson,
      Deno.env.get("FCM_EXPECTED_PROJECT_ID") ?? undefined,
    );
    if (saError) {
      console.error(`send-fcm: configuration FCM invalide (${saError})`);
      return new Response(
        JSON.stringify({ error: "FCM configuration error", code: saError }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    const serviceAccount = JSON.parse(fcmServiceAccountJson);
    const projectId = serviceAccount.project_id;

    let body: SendFcmRequest;
    try {
      body = await req.json();
    } catch {
      return new Response(JSON.stringify({ error: "Invalid JSON body" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const firebaseToken = (req.headers.get("x-firebase-token") ?? "").trim();
    const isFirebaseMode = body.mode === "firebase" || firebaseToken.length > 0;

    const { coupleId, recipientId, type, hidePreview } = body;
    if (!recipientId || !type || !isAllowedType(type)) {
      return new Response(JSON.stringify({ error: "Missing recipientId or type parameter" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    let senderId = "";
    let tokens: string[] = [];
    let googleToken = "";
    const adminClient = (!isFirebaseMode && supabaseUrl && supabaseServiceKey)
      ? createClient(supabaseUrl, supabaseServiceKey)
      : null;

    if (isFirebaseMode) {
      if (!firebaseToken) {
        return new Response(JSON.stringify({ error: "Missing x-firebase-token header" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      }
      const verifiedUid = await verifyFirebaseIdToken(firebaseToken, projectId);
      if (!verifiedUid) {
        return new Response(JSON.stringify({ error: "Unauthorized Firebase ID token" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      }
      senderId = verifiedUid;
      if (senderId === recipientId) {
        return new Response(JSON.stringify({ message: "Skipping self-notification" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }

      googleToken = await getGoogleAccessToken(
        serviceAccount,
        "https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore"
      );

      let coupleCheck = false;
      if (coupleId && !coupleId.includes("/")) {
        const coupleDoc = await fetchFirestoreDoc(
          projectId,
          databaseId,
          `couple_spaces/${coupleId}`,
          googleToken
        );
        coupleCheck = isPairedCoupleMember(extractFirestoreCouple(coupleDoc), senderId, recipientId);
      }
      if (!coupleCheck) {
        return new Response(
          JSON.stringify({ error: "Forbidden: sender and recipient do not belong to this couple" }),
          { status: 403, headers: { "Content-Type": "application/json" } }
        );
      }

      if (!recipientId.includes("/")) {
        const recipientDoc = await fetchFirestoreDoc(
          projectId,
          databaseId,
          `users/${recipientId}`,
          googleToken
        );
        const token = extractFirestoreFcmToken(recipientDoc);
        if (token) tokens = [token];
      }
    } else {
      const authHeader = req.headers.get("Authorization");
      if (!authHeader) {
        return new Response(JSON.stringify({ error: "Missing Authorization header" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      }

      const userClient = createClient(supabaseUrl, supabaseAnonKey, {
        global: { headers: { Authorization: authHeader } },
      });
      const { data: { user }, error: userError } = await userClient.auth.getUser();
      if (userError || !user) {
        return new Response(JSON.stringify({ error: "Unauthorized user session" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      }

      senderId = user.id;
      if (senderId === recipientId) {
        return new Response(JSON.stringify({ message: "Skipping self-notification" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }

      let coupleCheck = false;
      if (coupleId && adminClient) {
        const { data: coupleRow, error: coupleError } = await adminClient
          .from("couples")
          .select("id, user1_id, user2_id, status")
          .eq("id", coupleId)
          .maybeSingle();
        if (coupleError) {
          console.error("send-fcm: lecture du couple impossible", coupleError.code ?? "");
          return new Response(JSON.stringify({ error: "Couple lookup failed" }), {
            status: 500,
            headers: { "Content-Type": "application/json" },
          });
        }
        coupleCheck = isPairedCoupleMember(coupleRow, senderId, recipientId);
      }
      if (!coupleCheck) {
        return new Response(
          JSON.stringify({ error: "Forbidden: sender and recipient do not belong to this couple" }),
          { status: 403, headers: { "Content-Type": "application/json" } }
        );
      }

      const { data: tokenRows, error: tokensError } = await adminClient!
        .from("device_push_tokens")
        .select("fcm_token")
        .eq("user_id", recipientId);
      if (!tokensError && tokenRows) {
        tokens = tokenRows.map((r: any) => r.fcm_token).filter(Boolean);
      }
      googleToken = await getGoogleAccessToken(
        serviceAccount,
        "https://www.googleapis.com/auth/firebase.messaging"
      );
    }

    if (tokens.length === 0) {
      return new Response(
        JSON.stringify({ message: "No active FCM tokens found for recipient", deliveredCount: 0 }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    const payloadData = buildPayloadData({
      type,
      coupleId,
      senderId,
      recipientId,
      body: body.body,
      data: body.data,
      hidePreview,
    });

    const fcmEndpoint = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
    let deliveredCount = 0;
    const tokensToRemove: string[] = [];

    for (const token of tokens) {
      const messageBody = {
        message: {
          token,
          data: payloadData,
          android: {
            priority: "HIGH",
            ttl: ttlFor(type),
          },
        },
      };

      try {
        const resp = await fetch(fcmEndpoint, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${googleToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(messageBody),
        });

        if (resp.ok) {
          deliveredCount++;
        } else {
          const respText = await resp.text();
          if (
            resp.status === 404 ||
            respText.includes("UNREGISTERED") ||
            respText.includes("registration-token-not-registered")
          ) {
            tokensToRemove.push(token);
          } else {
            const maskedToken = token.length > 10 ? `${token.substring(0, 6)}...${token.substring(token.length - 4)}` : "***";
            console.warn(`FCM dispatch warning for token ${maskedToken} (status ${resp.status}): ${respText}`);
          }
        }
      } catch (sendError) {
        console.error("Error dispatching FCM message:", sendError);
      }
    }

    if (tokensToRemove.length > 0) {
      if (isFirebaseMode && !recipientId.includes("/")) {
        await clearInvalidFirestoreFcmToken(projectId, databaseId, recipientId, googleToken);
      } else if (adminClient) {
        await adminClient
          .from("device_push_tokens")
          .delete()
          .in("fcm_token", tokensToRemove)
          .eq("user_id", recipientId);
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        deliveredCount,
        removedTokens: tokensToRemove.length,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    console.error("send-fcm: erreur interne", err?.name ?? "Error");
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
