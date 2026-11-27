// Supabase Edge Function : account-recovery
// Crée un « code de récupération » pour le compte (anonyme) de l'utilisateur connecté.
// Le compte reçoit un identifiant + mot de passe dérivés du code : après une réinstallation
// ou une déconnexion, l'application se reconnecte au MÊME compte (couple et messages gardés)
// en saisissant ce code. Aucun e-mail n'est jamais envoyé (adresse technique confirmée d'office).
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.7";

const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans 0/O ni 1/I

function normalize(code: string): string {
  return code.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

async function sha256Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Identifiants dérivés du code (même calcul dans l'application Android). */
export async function credentialsFor(code: string) {
  const n = normalize(code);
  const email = `r${(await sha256Hex("mikayala-recovery:" + n)).slice(0, 24)}@recup.mikayala.app`;
  return { email, password: "Mk!" + n };
}

function newCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  const chars = Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join("");
  return `MIKA-${chars.slice(0, 4)}-${chars.slice(4, 8)}-${chars.slice(8, 12)}`;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!jwt) return json({ error: "not_authenticated" }, 401);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: userData, error: userErr } = await admin.auth.getUser(jwt);
  const user = userData?.user;
  if (userErr || !user) return json({ error: "not_authenticated" }, 401);

  // Un nouveau code remplace l'ancien (l'ancien ne marche plus)
  const code = newCode();
  const { email, password } = await credentialsFor(code);
  const { error } = await admin.auth.admin.updateUserById(user.id, {
    email,
    password,
    email_confirm: true,
  });
  if (error) {
    console.error("account-recovery: updateUserById", error.message);
    return json({ error: "update_failed" }, 500);
  }
  return json({ code });
});
