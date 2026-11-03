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
