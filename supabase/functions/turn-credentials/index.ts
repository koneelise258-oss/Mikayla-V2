// Supabase Edge Function : turn-credentials
// Fournit à l'appli des identifiants TEMPORAIRES du relais TURN Cloudflare pour les appels.
// La clé Cloudflare reste côté serveur (secrets CLOUDFLARE_TURN_KEY_ID et CLOUDFLARE_TURN_API_TOKEN).
// Sans ces secrets, la fonction répond { iceServers: [] } : l'appli tente alors la connexion directe.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.7";

const JSON_HEADERS = { "Content-Type": "application/json" };
const TTL_SECONDS = 6 * 60 * 60; // 6 h : largement assez pour un appel

function reply(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST" && req.method !== "GET") return reply({ error: "Method not allowed" }, 405);

  // Réservé aux utilisateurs connectés de l'appli
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return reply({ error: "Missing Authorization header" }, 401);
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_ANON_KEY") ?? "",
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: { user }, error } = await supabase.auth.getUser();
  if (error || !user) return reply({ error: "Unauthorized user session" }, 401);

  const keyId = (Deno.env.get("CLOUDFLARE_TURN_KEY_ID") ?? "").trim();
  const apiToken = (Deno.env.get("CLOUDFLARE_TURN_API_TOKEN") ?? "").trim();
  if (!keyId || !apiToken) {
    console.warn("turn-credentials : secrets Cloudflare absents");
    return reply({ iceServers: [], configured: false });
  }

  try {
    const resp = await fetch(
      `https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(keyId)}/credentials/generate-ice-servers`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${apiToken}`, "Content-Type": "application/json" },
        body: JSON.stringify({ ttl: TTL_SECONDS }),
      },
    );
    if (!resp.ok) {
      console.error(`turn-credentials : Cloudflare a refusé (statut ${resp.status})`);
      return reply({ iceServers: [], configured: true, error: "cloudflare_error", status: resp.status });
    }
    const data = await resp.json();
    return reply({ iceServers: data.iceServers ?? [], configured: true });
  } catch (e) {
    console.error("turn-credentials : erreur réseau", (e as Error)?.name ?? "Error");
    return reply({ iceServers: [], configured: true, error: "network_error" });
  }
});
