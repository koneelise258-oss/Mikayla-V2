// Construction pure (sans I/O) du payload FCM "data", testable sans Deno ni réseau.

export const ALLOWED_TYPES = [
  "chat_message", "call", "missed_call", "audio", "image", "video", "vault_add", "game",
  "game_turn", "anniversary", "mood_update", "poll_created", "love_bomb", "calendar_event",
  "cycle", "call_declined", "call_cancelled", "call_ended",
] as const;

export const CYCLE_NEUTRAL_TEXT = "Vous avez une nouvelle notification";

// Types qui doivent atteindre l'appareil même si elles sont courtes : TTL réduit (sonnerie)
export const SHORT_TTL_TYPES = ["call", "call_cancelled", "call_ended"];

export interface PayloadInput {
  type: string;
  coupleId?: string;
  senderId: string;
  recipientId?: string;
  body?: string;
  data?: Record<string, unknown>;
  hidePreview?: boolean;
}

export function isAllowedType(type: unknown): boolean {
  return typeof type === "string" && (ALLOWED_TYPES as readonly string[]).includes(type);
}

/** FCM exige des valeurs string ; on convertit et on ignore null/undefined/objets. */
function stringValues(data: Record<string, unknown> | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  if (!data) return out;
  for (const [k, v] of Object.entries(data)) {
    if (v === null || v === undefined || typeof v === "object") continue;
    out[k] = String(v);
  }
  return out;
}

export function buildPayloadData(input: PayloadInput): Record<string, string> {
  const base = {
    type: input.type,
    coupleId: input.coupleId || "",
    senderId: input.senderId,
    recipientId: input.recipientId || "",
  };

  // Cycle menstruel : liste blanche stricte. Aucun champ client (phaseName, dates, symptômes,
  // prédictions, surnom...) n'est transmis, quel que soit le contenu de la requête.
  if (input.type === "cycle") {
    return { ...base, messageText: CYCLE_NEUTRAL_TEXT };
  }

  let messageText = input.body || "";
  if (input.hidePreview) {
    messageText = "Nouveau message reçu 🔒";
  }
  // Les champs réservés ne peuvent pas être écrasés par data
  return { ...stringValues(input.data), ...base, messageText };
}

export function ttlFor(type: string): string {
  return SHORT_TTL_TYPES.includes(type) ? "30s" : "86400s";
}

export interface CoupleRow {
  user1_id?: string | null;
  user2_id?: string | null;
  status?: string | null;
}

/** Couple apparié (status = 'paired') ET expéditeur + destinataire distincts, tous deux membres. */
export function isPairedCoupleMember(row: CoupleRow | null | undefined, senderId: string, recipientId: string): boolean {
  if (!row || row.status !== "paired") return false;
  if (!senderId || !recipientId || senderId === recipientId) return false;
  const members = [row.user1_id, row.user2_id];
  return members.includes(senderId) && members.includes(recipientId);
}

export const EXPECTED_FCM_PROJECT_ID = "gen-lang-client-0988385912";

/**
 * Valide le compte de service sans jamais renvoyer ni journaliser de valeur secrète.
 * Retourne null si valide, sinon un code d'erreur neutre.
 */
export function validateServiceAccount(raw: string, expectedProjectId = EXPECTED_FCM_PROJECT_ID): string | null {
  if (!raw) return "missing_service_account";
  let sa: Record<string, unknown>;
  try {
    sa = JSON.parse(raw);
  } catch {
    return "invalid_service_account_json";
  }
  if (sa.type !== "service_account") return "not_a_service_account";
  if (typeof sa.client_email !== "string" || !sa.client_email) return "missing_client_email";
  if (typeof sa.private_key !== "string" || !String(sa.private_key).includes("PRIVATE KEY")) return "missing_private_key";
  if (sa.project_id !== expectedProjectId) return "unexpected_project_id";
  return null;
}
