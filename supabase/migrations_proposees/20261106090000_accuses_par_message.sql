-- ============================================================================
-- Mikayala V2 — Accusés de réception MESSAGE PAR MESSAGE (proposée, NON appliquée)
-- ============================================================================
-- Pourquoi : mark_messages_delivered / mark_messages_read (arguments : target_couple_id) marquent
-- d'un coup TOUS les messages du couple. Une notification pour un seul message marquait donc
-- « reçu » des messages que le téléphone n'avait pas encore. Ces deux fonctions ne touchent que
-- les identifiants envoyés par l'application (ceux réellement reçus / affichés).
--
-- Non destructive : aucune table, colonne, politique ni fonction existante n'est modifiée.
-- L'application continue d'utiliser les anciennes fonctions tant que celles-ci n'existent pas.
-- Types : comparaisons en ::text pour fonctionner que les colonnes soient uuid ou text.
--
-- Sécurité : SECURITY DEFINER + search_path fixe ; refus sans session ; uniquement les messages
-- d'un couple « paired » dont l'appelant est membre ET dont il n'est PAS l'expéditeur ;
-- l'état ne recule jamais (lu reste lu) ; EXECUTE réservé à authenticated.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.mark_messages_delivered_ids(message_ids text[])
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  n integer;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Non authentifié';
  END IF;
  IF message_ids IS NULL OR array_length(message_ids, 1) IS NULL THEN
    RETURN 0;
  END IF;
  UPDATE public.messages m
     SET status = 'delivered',
         delivered_at = COALESCE(m.delivered_at, now())
   WHERE m.id::text = ANY (message_ids)
     AND m.sender_id::text <> auth.uid()::text
     AND m.status = 'sent'
     AND EXISTS (
       SELECT 1 FROM public.couples c
        WHERE c.id::text = m.couple_id::text
          AND c.status = 'paired'
          AND auth.uid()::text IN (c.user1_id::text, c.user2_id::text)
     );
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN n;
END $$;

CREATE OR REPLACE FUNCTION public.mark_messages_read_ids(message_ids text[])
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  n integer;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Non authentifié';
  END IF;
  IF message_ids IS NULL OR array_length(message_ids, 1) IS NULL THEN
    RETURN 0;
  END IF;
  UPDATE public.messages m
     SET status = 'read',
         delivered_at = COALESCE(m.delivered_at, now()),
         read_at = COALESCE(m.read_at, now())
   WHERE m.id::text = ANY (message_ids)
     AND m.sender_id::text <> auth.uid()::text
     AND m.status IN ('sent', 'delivered')
     AND EXISTS (
       SELECT 1 FROM public.couples c
        WHERE c.id::text = m.couple_id::text
          AND c.status = 'paired'
          AND auth.uid()::text IN (c.user1_id::text, c.user2_id::text)
     );
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN n;
END $$;

REVOKE ALL ON FUNCTION public.mark_messages_delivered_ids(text[]) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.mark_messages_read_ids(text[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_messages_delivered_ids(text[]) TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_messages_read_ids(text[]) TO authenticated;

-- TESTS (après application, avec deux comptes A et B du même couple) :
--  1. A envoie 3 messages ; B appelle mark_messages_delivered_ids([id1]) → 1 ; seul id1 est « delivered ».
--  2. B appelle mark_messages_read_ids([id1, id2]) → 2 ; id3 reste « sent ».
--  3. A appelle mark_messages_read_ids([id1]) sur SON message → 0 (on ne marque jamais ses propres messages).
--  4. Compte C (hors couple) → 0 ; sans session → erreur « Non authentifié ».
-- RETOUR ARRIÈRE :
--  DROP FUNCTION IF EXISTS public.mark_messages_delivered_ids(text[]);
--  DROP FUNCTION IF EXISTS public.mark_messages_read_ids(text[]);
