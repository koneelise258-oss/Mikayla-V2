-- ============================================================================
-- PROPOSITION — NON APPLIQUÉE. Dossier volontairement hors de supabase/migrations
-- pour qu'aucun `supabase db push` ne l'exécute par erreur. À valider avant tout déploiement.
-- ============================================================================
-- Objectif : qu'un appareil passé d'un compte A à un compte B puisse rattacher son token FCM
-- au compte B, même si A n'a pas pu supprimer son token (déconnexion forcée sans réseau).
-- Sans cette fonction, l'upsert REST est refusé par la politique UPDATE (auth.uid() = user_id).
--
-- Pourquoi SECURITY DEFINER : il faut mettre à jour une ligne appartenant à un autre utilisateur,
-- ce que RLS interdit au rôle authenticated. La fonction est limitée à la seule ligne dont
-- fcm_token = p_token, ne renvoie aucune donnée et force user_id = auth.uid().
-- Risque résiduel : quelqu'un qui connaîtrait le token FCM d'un autre appareil pourrait se
-- l'attribuer (il recevrait alors les notifications destinées à cet appareil). Un token FCM
-- n'est connu que de son appareil ; ce risque est jugé faible mais doit être accepté explicitement.

CREATE OR REPLACE FUNCTION public.claim_device_push_token(p_token text, p_label text DEFAULT 'Android')
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'authentication required' USING ERRCODE = '42501';
  END IF;
  IF p_token IS NULL OR length(btrim(p_token)) < 20 OR length(p_token) > 4096 THEN
    RAISE EXCEPTION 'invalid token' USING ERRCODE = '22023';
  END IF;

  INSERT INTO public.device_push_tokens (user_id, fcm_token, platform, device_label, updated_at, last_seen_at)
  VALUES (v_uid, btrim(p_token), 'android', left(coalesce(p_label, 'Android'), 100), now(), now())
  ON CONFLICT (fcm_token) DO UPDATE
    SET user_id      = EXCLUDED.user_id,
        device_label = EXCLUDED.device_label,
        platform     = 'android',
        updated_at   = now(),
        last_seen_at = now();
END;
$$;

REVOKE ALL ON FUNCTION public.claim_device_push_token(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.claim_device_push_token(text, text) FROM anon;
GRANT EXECUTE ON FUNCTION public.claim_device_push_token(text, text) TO authenticated;

-- Vérifications après application (à exécuter avec un compte de test) :
--  1. anon : POST /rest/v1/rpc/claim_device_push_token → 401/permission denied
--  2. compte A : claim(token_X) → ligne token_X avec user_id = A
--  3. compte B : claim(token_X) → même ligne, user_id = B (une seule ligne, contrainte UNIQUE)
--  4. compte B : SELECT sur device_push_tokens ne montre jamais les autres tokens de A (RLS inchangée)
-- Retour arrière : DROP FUNCTION public.claim_device_push_token(text, text);
-- (l'application se replie automatiquement sur l'ancien upsert si la fonction n'existe pas)
