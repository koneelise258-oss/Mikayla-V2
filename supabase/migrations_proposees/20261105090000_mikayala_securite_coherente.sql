-- ============================================================================
-- MIKAYALA V2 — MIGRATION DE SÉCURITÉ COHÉRENTE (PROPOSITION, NON APPLIQUÉE)
-- ============================================================================
-- Préalable : la migration déjà appliquée « harden_mikayala_function_search_paths_and_trigger_privileges »
-- n'est ni répétée ni défaite ici.
-- Idempotente : peut être rejouée sans effet de bord. Aucune donnée, table, colonne ni politique
-- existante n'est supprimée, à l'exception des politiques créées PAR CE FICHIER (DROP IF EXISTS
-- de leurs propres noms, pour pouvoir le rejouer).
--
-- AVANT D'APPLIQUER : exécuter docs/AUDIT_SUPABASE_REQUETES.sql (sections 1, 2, 5, 9 à 13) et vérifier
-- les hypothèses notées « HYPOTHÈSE » ci-dessous. Si une hypothèse est fausse, adapter avant d'appliquer.
--
-- ORDRE DE DÉPLOIEMENT :
--   1. Partie A (jetons FCM)  — compatible avec l'APK actuel ET l'ancien (repli automatique côté app).
--   2. Partie B (droits RPC)  — vérifier d'abord les signatures réelles (requête 2).
--   3. Partie C (Storage)     — APRÈS installation du nouvel APK sur les deux téléphones
--                               (l'ancien APK envoyait les fichiers avec la clé anon).
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- PARTIE A — Enregistrement sécurisé des jetons FCM
-- ---------------------------------------------------------------------------
-- Problème : UNIQUE(fcm_token) + RLS « auth.uid() = user_id ». Un téléphone passé d'un compte A à un
-- compte B ne peut pas reprendre son jeton si A ne l'a pas supprimé. La RPC proposée
-- claim_device_push_token réglait ce cas mais permettait de s'approprier le jeton d'un tiers.
-- Solution : secret d'installation (généré par l'app, jamais stocké en clair côté serveur).
--   - même installation (même secret)      → reprise autorisée (changement de compte, renouvellement)
--   - autre installation / secret inconnu  → refus
--   - plusieurs appareils                  → une ligne par installation
--   - renouvellement de jeton              → la ligne de l'installation reçoit le nouveau jeton
-- HYPOTHÈSE : extension pgcrypto disponible (schéma « extensions », standard sur Supabase).

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

ALTER TABLE public.device_push_tokens
  ADD COLUMN IF NOT EXISTS installation_hash text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_push_tokens_installation_hash
  ON public.device_push_tokens (installation_hash)
  WHERE installation_hash IS NOT NULL;

CREATE OR REPLACE FUNCTION public.register_device_push_token(
  p_installation_secret text,
  p_token text,
  p_label text DEFAULT 'Android'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
  v_uid    uuid := auth.uid();
  v_hash   text;
  v_token  text := btrim(coalesce(p_token, ''));
  v_label  text := left(coalesce(nullif(btrim(p_label), ''), 'Android'), 100);
  v_row    public.device_push_tokens%ROWTYPE;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'authentication required' USING ERRCODE = '42501';
  END IF;
  IF p_installation_secret IS NULL OR length(p_installation_secret) <> 64
     OR p_installation_secret !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'invalid installation secret' USING ERRCODE = '22023';
  END IF;
  IF length(v_token) < 20 OR length(v_token) > 4096 THEN
    RAISE EXCEPTION 'invalid token' USING ERRCODE = '22023';
  END IF;

  v_hash := encode(extensions.digest(p_installation_secret, 'sha256'), 'hex');

  -- Le jeton existe déjà ?
  SELECT * INTO v_row FROM public.device_push_tokens WHERE fcm_token = v_token FOR UPDATE;
  IF FOUND THEN
    IF v_row.installation_hash IS NOT NULL AND v_row.installation_hash <> v_hash THEN
      -- Jeton enregistré par une AUTRE installation : on refuse (pas d'appropriation).
      RAISE EXCEPTION 'token owned by another installation' USING ERRCODE = '42501';
    END IF;
    IF v_row.installation_hash IS NULL AND v_row.user_id <> v_uid THEN
      -- Ligne ancienne (sans empreinte) d'un autre compte : possession impossible à prouver → refus.
      -- L'ancien compte la supprimera à sa déconnexion, ou elle sera purgée par FCM (UNREGISTERED).
      RAISE EXCEPTION 'legacy token owned by another account' USING ERRCODE = '42501';
    END IF;
  END IF;

  -- Renouvellement : l'installation avait un autre jeton → on le remplace (une ligne par installation).
  DELETE FROM public.device_push_tokens
   WHERE installation_hash = v_hash AND fcm_token <> v_token;

  INSERT INTO public.device_push_tokens (user_id, fcm_token, platform, device_label, installation_hash, updated_at, last_seen_at)
  VALUES (v_uid, v_token, 'android', v_label, v_hash, now(), now())
  ON CONFLICT (fcm_token) DO UPDATE
    SET user_id           = EXCLUDED.user_id,
        device_label      = EXCLUDED.device_label,
        installation_hash = EXCLUDED.installation_hash,
        platform          = 'android',
        updated_at        = now(),
        last_seen_at      = now();
END;
$$;

REVOKE ALL ON FUNCTION public.register_device_push_token(text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.register_device_push_token(text, text, text) FROM anon;
GRANT EXECUTE ON FUNCTION public.register_device_push_token(text, text, text) TO authenticated;

-- La colonne installation_hash n'est lisible par personne via l'API : les politiques SELECT existantes
-- (auth.uid() = user_id) exposent la ligne à son propriétaire uniquement ; l'empreinte n'est pas un secret
-- exploitable (SHA-256 d'un aléa de 256 bits).

-- ---------------------------------------------------------------------------
-- PARTIE B — Droits EXECUTE des fonctions appelées par l'application
-- ---------------------------------------------------------------------------
-- L'APK appelle exactement : mark_messages_delivered, mark_messages_read, touch_user_activity.
-- Les comptes de l'app (y compris les sessions anonymes Supabase) ont le rôle « authenticated » :
-- le rôle « anon » (aucune session) n'a aucune raison de les exécuter.
-- La boucle s'applique à toutes les surcharges existantes, sans connaître leurs arguments.
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS sig
    FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname IN ('mark_messages_delivered', 'mark_messages_read', 'touch_user_activity')
  LOOP
    EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon', r.sig);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated', r.sig);
  END LOOP;
END $$;
-- Les autres fonctions SECURITY DEFINER exécutables par anon (8 relevées) : NON traitées ici à l'aveugle.
-- Les lister avec la requête 2, vérifier leur code (requête 3) puis compléter cette partie fonction par fonction.

-- ---------------------------------------------------------------------------
-- PARTIE C — Storage : écritures réservées aux membres du couple (lectures inchangées)
-- ---------------------------------------------------------------------------
-- Chemins réellement utilisés par l'APK :
--   messages-media/<couple_id>/{photos,audio,video,documents,vault}/<fichier>   (privé, URL signées)
--   avatars/<user_id>/<uuid>.webp                                               (privé, URL signées)
--   chat_media/<couple_id>/<fichier>   anciens documents, URL publiques          (public en lecture)
--   vault_media : aucune écriture par l'APK                                      (public en lecture)
-- Aucun bucket ne change de visibilité : les anciennes pièces jointes restent accessibles.
-- HYPOTHÈSE : couples(id uuid, user1_id uuid, user2_id uuid, status text) — vérifier (requête 8).

CREATE OR REPLACE FUNCTION public.mikayala_is_couple_member(p_couple_folder text)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.couples c
    WHERE c.id::text = p_couple_folder
      AND c.status = 'paired'
      AND auth.uid() IN (c.user1_id, c.user2_id)
  );
$$;
REVOKE ALL ON FUNCTION public.mikayala_is_couple_member(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mikayala_is_couple_member(text) TO authenticated;

CREATE OR REPLACE FUNCTION public.mikayala_is_partner_folder(p_user_folder text)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT p_user_folder = auth.uid()::text OR EXISTS (
    SELECT 1 FROM public.couples c
    WHERE c.status = 'paired'
      AND auth.uid() IN (c.user1_id, c.user2_id)
      AND p_user_folder IN (c.user1_id::text, c.user2_id::text)
  );
$$;
REVOKE ALL ON FUNCTION public.mikayala_is_partner_folder(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mikayala_is_partner_folder(text) TO authenticated;

-- messages-media : lire / écrire / remplacer dans le dossier de son couple
DROP POLICY IF EXISTS "mik messages-media select couple" ON storage.objects;
CREATE POLICY "mik messages-media select couple" ON storage.objects FOR SELECT TO authenticated
  USING (bucket_id = 'messages-media' AND public.mikayala_is_couple_member((storage.foldername(name))[1]));
DROP POLICY IF EXISTS "mik messages-media insert couple" ON storage.objects;
CREATE POLICY "mik messages-media insert couple" ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'messages-media' AND public.mikayala_is_couple_member((storage.foldername(name))[1]));
DROP POLICY IF EXISTS "mik messages-media update couple" ON storage.objects;
CREATE POLICY "mik messages-media update couple" ON storage.objects FOR UPDATE TO authenticated
  USING (bucket_id = 'messages-media' AND public.mikayala_is_couple_member((storage.foldername(name))[1]));

-- avatars : écrire dans son dossier ; lire le sien et celui du partenaire (URL signées)
DROP POLICY IF EXISTS "mik avatars select couple" ON storage.objects;
CREATE POLICY "mik avatars select couple" ON storage.objects FOR SELECT TO authenticated
  USING (bucket_id = 'avatars' AND public.mikayala_is_partner_folder((storage.foldername(name))[1]));
DROP POLICY IF EXISTS "mik avatars insert own" ON storage.objects;
CREATE POLICY "mik avatars insert own" ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);
DROP POLICY IF EXISTS "mik avatars update own" ON storage.objects;
CREATE POLICY "mik avatars update own" ON storage.objects FOR UPDATE TO authenticated
  USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);
DROP POLICY IF EXISTS "mik avatars delete own" ON storage.objects;
CREATE POLICY "mik avatars delete own" ON storage.objects FOR DELETE TO authenticated
  USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

-- chat_media : écriture par les membres du couple uniquement (lecture publique inchangée)
DROP POLICY IF EXISTS "mik chat_media insert couple" ON storage.objects;
CREATE POLICY "mik chat_media insert couple" ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'chat_media' AND public.mikayala_is_couple_member((storage.foldername(name))[1]));
DROP POLICY IF EXISTS "mik chat_media update couple" ON storage.objects;
CREATE POLICY "mik chat_media update couple" ON storage.objects FOR UPDATE TO authenticated
  USING (bucket_id = 'chat_media' AND public.mikayala_is_couple_member((storage.foldername(name))[1]));

-- ÉTAPE MANUELLE (après vérification, hors de ce fichier) : supprimer les politiques d'ÉCRITURE
-- existantes ouvertes à anon/public sur chat_media, vault_media, avatars et messages-media
-- (liste : requête 5). Les noter d'abord pour pouvoir revenir en arrière. Sans cette étape, les
-- politiques ci-dessus s'ajoutent (les politiques permissives se cumulent) et l'upload anonyme reste possible.

COMMIT;

-- ============================================================================
-- TESTS APRÈS APPLICATION (compte A et compte B du couple, compte C d'un autre couple)
--  A1. A enregistre son jeton (installation IA)            → 1 ligne, installation_hash renseigné
--  A2. A se déconnecte, B se connecte sur le même téléphone → même ligne, user_id = B
--  A3. C appelle la fonction avec le jeton de A et un autre secret → refus 42501
--  A4. Renouvellement du jeton sur IA                       → une seule ligne pour IA, nouveau jeton
--  A5. A sur deux téléphones                                → deux lignes, deux installation_hash
--  B1. Session sans authentification : rpc/mark_messages_read → refus ; avec session → OK
--  C1. A envoie une photo (messages-media/<couple>/photos)   → OK ; C vers le même chemin → refus
--  C2. A envoie sa photo de profil ; B obtient une URL signée → OK ; C → refus
--  C3. Ancien document chat_media (URL publique)            → toujours lisible
-- RETOUR ARRIÈRE :
--  DROP FUNCTION IF EXISTS public.register_device_push_token(text, text, text);
--  DROP POLICY IF EXISTS "mik ..." ON storage.objects;  (les 10 politiques ci-dessus)
--  DROP FUNCTION IF EXISTS public.mikayala_is_couple_member(text), public.mikayala_is_partner_folder(text);
--  (la colonne installation_hash et l'index peuvent rester : sans effet sur l'ancien fonctionnement)
-- ============================================================================
