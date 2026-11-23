// Supabase Edge Function: media-bridge
// Free secure storage bridge for Firebase-mode Mikayala users (avoids Firebase Blaze Storage plan).
// Verifies Firebase ID Token + Firestore couple membership via FCM_SERVICE_ACCOUNT_JSON,
// then issues signed upload/download URLs or deletes objects in Supabase Storage.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.7";

const FIRESTORE_DATABASE_ID =
  Deno.env.get("FIRESTORE_DATABASE_ID") ??
  "ai-studio-android-mikayala-9f74e746-a740-4f00-85b2-7a4d380087a6";

const ALLOWED_BUCKETS = new Set(["messages-media", "avatars", "chat_media"]);

interface MediaBridgeRequest {
  action: "upload-url" | "sign" | "delete";
  bucket: string;
  path: string;
  coupleId?: string;
  expiresIn?: number;
}

function base64url(input: string | Uint8Array): string {
  const binary = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let str = "";
  for (let i = 0; i < binary.length; i++) str += String.fromCharCode(binary[i]);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
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
  if (cachedJwks && now < cachedJwks.expiresAt) return cachedJwks.keys;
  const res = await fetch(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
  );
  if (!res.ok) throw new Error(`JWKS fetch failed (${res.status})`);
  const json = await res.json();
  const map: Record<string, JsonWebKey> = {};
  for (const k of json.keys ?? []) if (k.kid) map[k.kid] = k;
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
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    base64urlToBytes(sigB64),
    new TextEncoder().encode(`${headerB64}.${payloadB64}`)
  );
  return ok ? payload.sub : null;
}

function parseServiceAccount(): { client_email: string; private_key: string; project_id: string } | null {
  let raw = (Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ?? "").trim();
  if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith('"') && raw.endsWith('"') && !raw.startsWith('"{'))) {
    raw = raw.slice(1, -1).trim();
  }
  if (!raw) return null;
  let sa: any = null;
  try {
    const parsed = JSON.parse(raw);
    sa = typeof parsed === "string" ? JSON.parse(parsed) : parsed;
  } catch {
    return null;
  }
  if (!sa?.client_email || !sa?.private_key || !sa?.project_id) return null;
  sa.private_key = String(sa.private_key).replace(/\\n/g, "\n").replace(/\r/g, "");
  return sa;
}

async function getFirestoreAccessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  }));
  const input = `${header}.${claim}`;
  const cleanKey = sa.private_key
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const bin = Uint8Array.from(atob(cleanKey), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    bin.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: { name: "SHA-256" } },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(input));
  const jwt = `${input}.${base64url(new Uint8Array(sig))}`;
  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!resp.ok) throw new Error("Google OAuth failed");
  const json = await resp.json();
  return json.access_token;
}

async function isUidInFirestoreCouple(
  projectId: string,
  coupleId: string,
  uid: string,
  googleToken: string
): Promise<boolean> {
  if (!coupleId || coupleId.includes("/") || coupleId.includes("..")) return false;
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/${FIRESTORE_DATABASE_ID}/documents/couple_spaces/${coupleId}`;
  const resp = await fetch(url, { headers: { Authorization: `Bearer ${googleToken}` } });
  if (!resp.ok) return false;
  const doc = await resp.json();
  const members: string[] = (doc?.fields?.memberUids?.arrayValue?.values ?? [])
    .map((v: any) => v?.stringValue ?? "")
    .filter(Boolean);
  return members.includes(uid);
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
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const sa = parseServiceAccount();
    if (!sa || !supabaseUrl || !supabaseServiceKey) {
      return new Response(JSON.stringify({ error: "Server not configured" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    const firebaseToken = (req.headers.get("x-firebase-token") ?? "").trim();
    const uid = firebaseToken ? await verifyFirebaseIdToken(firebaseToken, sa.project_id) : null;
    if (!uid) {
      return new Response(JSON.stringify({ error: "Unauthorized Firebase token" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const body: MediaBridgeRequest = await req.json();
    const bucket = (body.bucket ?? "").trim();
    const cleanPath = (body.path ?? "").trim().replace(/^\/+/, "");
    if (!ALLOWED_BUCKETS.has(bucket) || !cleanPath || cleanPath.includes("..")) {
      return new Response(JSON.stringify({ error: "Invalid bucket or path" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const folderRoot = cleanPath.split("/")[0] ?? "";
    const googleToken = await getFirestoreAccessToken(sa);

    if (bucket === "avatars") {
      if (body.action === "upload-url" || body.action === "delete") {
        if (folderRoot !== uid) {
          return new Response(JSON.stringify({ error: "Forbidden avatar path" }), {
            status: 403,
            headers: { "Content-Type": "application/json" },
          });
        }
      } else if (folderRoot !== uid) {
        const coupleOk = body.coupleId
          ? await isUidInFirestoreCouple(sa.project_id, body.coupleId, uid, googleToken)
          : false;
        if (!coupleOk) {
          return new Response(JSON.stringify({ error: "Forbidden avatar read" }), {
            status: 403,
            headers: { "Content-Type": "application/json" },
          });
        }
      }
    } else {
      const coupleId = body.coupleId?.trim() || folderRoot;
      if (folderRoot !== coupleId) {
        return new Response(JSON.stringify({ error: "Path does not match coupleId" }), {
          status: 403,
          headers: { "Content-Type": "application/json" },
        });
      }
      const allowed = await isUidInFirestoreCouple(sa.project_id, coupleId, uid, googleToken);
      if (!allowed) {
        return new Response(JSON.stringify({ error: "Forbidden couple media access" }), {
          status: 403,
          headers: { "Content-Type": "application/json" },
        });
      }
    }

    const admin = createClient(supabaseUrl, supabaseServiceKey);

    if (body.action === "upload-url") {
      const { data, error } = await admin.storage
        .from(bucket)
        .createSignedUploadUrl(cleanPath, { upsert: true });
      if (error || !data) {
        return new Response(JSON.stringify({ error: error?.message ?? "Failed to create upload URL" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({ signedUrl: data.signedUrl, token: data.token, path: data.path }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (body.action === "sign") {
      const expiresIn = Math.min(Math.max(body.expiresIn ?? 604800, 60), 2592000);
      const { data, error } = await admin.storage
        .from(bucket)
        .createSignedUrl(cleanPath, expiresIn);
      if (error || !data?.signedUrl) {
        return new Response(JSON.stringify({ error: error?.message ?? "Failed to sign URL" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({ signedUrl: data.signedUrl }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (body.action === "delete") {
      const { error } = await admin.storage.from(bucket).remove([cleanPath]);
      if (error) {
        return new Response(JSON.stringify({ error: error.message }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({ success: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ error: "Unsupported action" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err: any) {
    console.error("media-bridge error:", err?.name ?? "Error");
    return new Response(JSON.stringify({ error: "Internal server error" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
