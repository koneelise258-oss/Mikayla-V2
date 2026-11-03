// Supabase Edge Function: send-fcm
// Dispatches push notifications to Android devices using FCM HTTP v1 API
// Requires environment secret: FCM_SERVICE_ACCOUNT_JSON

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.7";
import { buildPayloadData, isAllowedType, ttlFor } from "./payload.ts";

interface SendFcmRequest {
  coupleId: string;
  recipientId: string;
  type: "chat_message" | "call" | "missed_call" | "audio" | "image" | "video" | "vault_add" | "game" | "game_turn" | "anniversary" | "mood_update" | "poll_created" | "love_bomb" | "calendar_event" | "cycle" | "call_declined" | "call_cancelled" | "call_ended";
  title?: string;
  body?: string;
  data?: Record<string, string>;
  hidePreview?: boolean;
}

// Helper: Convert string to base64url
function base64url(input: string | Uint8Array): string {
  const binary = typeof input === "string" 
    ? new TextEncoder().encode(input) 
    : input;
  let base64 = btoa(String.fromCharCode(...binary));
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// Helper: Import PEM private key for RS256
async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  const cleanKey = pem
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s+/g, "");
  const binaryKey = Uint8Array.from(atob(cleanKey), c => c.charCodeAt(0));

  return await crypto.subtle.importKey(
    "pkcs8",
    binaryKey.buffer,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: { name: "SHA-256" },
    },
    false,
    ["sign"]
  );
}

// Generate Google OAuth 2.0 access token from Service Account JSON
async function getGoogleAccessToken(serviceAccount: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claimSet = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
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

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const fcmServiceAccountJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ?? "";

    if (!fcmServiceAccountJson) {
      return new Response(
        JSON.stringify({ error: "FCM_SERVICE_ACCOUNT_JSON is not configured in Supabase secrets" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    const serviceAccount = JSON.parse(fcmServiceAccountJson);
    const projectId = serviceAccount.project_id;

    // 1. Authenticate caller using JWT
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

    const senderId = user.id;
    const body: SendFcmRequest = await req.json();
    const { coupleId, recipientId, type, title, hidePreview } = body;

    if (!recipientId || !type || !isAllowedType(type)) {
      return new Response(JSON.stringify({ error: "Missing recipientId or type parameter" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Never send push notifications to oneself
    if (senderId === recipientId) {
      return new Response(JSON.stringify({ message: "Skipping self-notification" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 2. Validate couple membership (sender and recipient must belong to the couple)
    const adminClient = createClient(supabaseUrl, supabaseServiceKey);
    let coupleCheck = false;
    if (coupleId) {
      const { data: coupleRow } = await adminClient
        .from("couples")
        .select("id, user1_id, user2_id")
        .eq("id", coupleId)
        .maybeSingle();

      if (coupleRow) {
        const isSenderMember = coupleRow.user1_id === senderId || coupleRow.user2_id === senderId;
        const isRecipientMember = coupleRow.user1_id === recipientId || coupleRow.user2_id === recipientId;
        coupleCheck = isSenderMember && isRecipientMember;
      }
    }

    if (!coupleCheck) {
      return new Response(JSON.stringify({ error: "Forbidden: sender and recipient do not belong to this couple" }), {
        status: 403,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 3. Retrieve active device push tokens for recipient
    const { data: tokens, error: tokensError } = await adminClient
      .from("device_push_tokens")
      .select("fcm_token")
      .eq("user_id", recipientId);

    if (tokensError || !tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ message: "No active FCM tokens found for recipient", deliveredCount: 0 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 4. Format payload according to privacy settings
    // Payload filtré (liste blanche stricte pour le cycle) — voir payload.ts et payload.test.ts
    const payloadData = buildPayloadData({
      type,
      coupleId,
      senderId,
      body: body.body,
      data: body.data,
      hidePreview,
    });

    const accessToken = await getGoogleAccessToken(serviceAccount);
    const fcmEndpoint = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

    let deliveredCount = 0;
    const tokensToRemove: string[] = [];

    // 5. Send to each recipient device token
    for (const tokenRow of tokens) {
      const token = tokenRow.fcm_token;
      const messageBody = {
        message: {
          token: token,
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
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(messageBody),
        });

        if (resp.ok) {
          deliveredCount++;
        } else {
          const respText = await resp.text();
          // Supprimer UNIQUEMENT si l'invalidité du jeton est confirmée par Firebase (UNREGISTERED, token non enregistré ou 404)
          // NE JAMAIS supprimer un jeton sur l'erreur INVALID_ARGUMENT qui indique un payload mal formé
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

    // 6. Clean up invalid tokens strictly scoped to the recipient
    if (tokensToRemove.length > 0) {
      await adminClient
        .from("device_push_tokens")
        .delete()
        .in("fcm_token", tokensToRemove)
        .eq("user_id", recipientId);
    }

    return new Response(
      JSON.stringify({
        success: true,
        deliveredCount,
        removedTokens: tokensToRemove.length,
      }),
      {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ error: err.message || "Internal server error" }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
});
