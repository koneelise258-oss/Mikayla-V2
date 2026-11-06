-- ============================================================================
-- Mikayala V2 — Coffre réellement privé : vault_items réservé aux 2 membres du couple
-- (proposée, NON appliquée)
-- ============================================================================
-- Ce que l'APK fait (après la phase 3) :
--   SELECT  vault_items?couple_id=eq.<couple>
--   INSERT  id (uuid texte), couple_id, title, type, media_type, category, caption, media_url, added_by
--   DELETE  vault_items?id=eq.<id>   (Prefer: return=representation, succès seulement si une ligne revient)
-- Les fichiers du coffre sont dans le bucket PRIVÉ messages-media, dossier <couple>/vault/ ; leurs droits
-- relèvent de la partie C de la migration 20261105090000 (écritures réservées aux membres).
--
-- AVANT D'APPLIQUER : lister les politiques existantes. En PostgreSQL, les politiques PERMISSIVES se
-- cumulent (OU) : une ancienne politique « tout le monde » rendrait celles-ci inutiles. Noter puis
-- supprimer à la main toute politique trop large, après vérification :
--   SELECT policyname, cmd, roles, qual, with_check FROM pg_policies
--    WHERE schemaname = 'public' AND tablename = 'vault_items';
--   SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns
--    WHERE table_schema = 'public' AND table_name = 'vault_items' ORDER BY ordinal_position;
--
-- Non destructive : aucune donnée ni colonne modifiée ; RLS activée (sans effet si déjà active).
-- Comparaisons en ::text pour fonctionner que les colonnes soient uuid ou text.
-- ============================================================================

ALTER TABLE public.vault_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "mik vault select couple" ON public.vault_items;
CREATE POLICY "mik vault select couple" ON public.vault_items
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.couples c
     WHERE c.id::text = vault_items.couple_id::text
       AND c.status = 'paired'
       AND auth.uid()::text IN (c.user1_id::text, c.user2_id::text)
  ));

DROP POLICY IF EXISTS "mik vault insert couple" ON public.vault_items;
CREATE POLICY "mik vault insert couple" ON public.vault_items
  FOR INSERT TO authenticated
  WITH CHECK (
    added_by::text = auth.uid()::text
    AND EXISTS (
      SELECT 1 FROM public.couples c
       WHERE c.id::text = vault_items.couple_id::text
         AND c.status = 'paired'
         AND auth.uid()::text IN (c.user1_id::text, c.user2_id::text)
    )
  );

DROP POLICY IF EXISTS "mik vault delete couple" ON public.vault_items;
CREATE POLICY "mik vault delete couple" ON public.vault_items
  FOR DELETE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.couples c
     WHERE c.id::text = vault_items.couple_id::text
       AND c.status = 'paired'
       AND auth.uid()::text IN (c.user1_id::text, c.user2_id::text)
  ));

REVOKE ALL ON public.vault_items FROM anon;

-- Si la colonne added_by n'existe pas dans le schéma réel, remplacer la condition de la politique
-- d'insertion par la seule vérification d'appartenance au couple.
--
-- TESTS (A et B même couple, C autre compte) :
--  V1. A ajoute une note → B la voit après réouverture du coffre.
--  V2. B supprime → l'élément disparaît chez A à la synchronisation suivante.
--  V3. C : SELECT vault_items?couple_id=eq.<couple de A> → [] ; INSERT / DELETE → refusés.
--  V4. Sans session (clé anon) → refus.
-- RETOUR ARRIÈRE :
--  DROP POLICY IF EXISTS "mik vault select couple" ON public.vault_items;
--  DROP POLICY IF EXISTS "mik vault insert couple" ON public.vault_items;
--  DROP POLICY IF EXISTS "mik vault delete couple" ON public.vault_items;
