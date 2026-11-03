-- ============================================================================
-- PROPOSITION — NON APPLIQUÉE. Supprimer l'upload anonyme sans casser l'application.
-- ============================================================================
-- Constat côté app (avant la branche) : uploadChatMedia et uploadAvatar envoyaient la clé anon
-- comme jeton → les politiques Storage devaient autoriser le rôle anon en écriture.
-- Correction déjà faite dans l'app (branche) : la session utilisateur (JWT) est utilisée d'abord,
-- la clé anon n'est plus qu'un repli transitoire.
--
-- ORDRE OBLIGATOIRE :
--   1. Installer le nouvel APK sur les DEUX téléphones et vérifier que les envois marchent
--      (journal : "Upload OK (..., session)").
--   2. Lire les politiques existantes (requête 5 de docs/AUDIT_SUPABASE_REQUETES.sql) et les
--      noter pour pouvoir revenir en arrière.
--   3. Seulement ensuite : supprimer les politiques INSERT/UPDATE de storage.objects ouvertes à
--      anon/public sur chat_media, vault_media et avatars, puis créer celles-ci.
-- Les buckets restent publics en lecture pour l'instant (décision : pas de passage en privé).

-- Chemin utilisé par l'app : chat_media/<couple_id>/<fichier>
CREATE POLICY "chat_media insert couple members"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'chat_media'
    AND EXISTS (
      SELECT 1 FROM public.couples c
      WHERE c.id::text = (storage.foldername(name))[1]
        AND c.status = 'paired'
        AND auth.uid() IN (c.user1_id, c.user2_id)
    )
  );

-- x-upsert: true → l'upload d'un fichier existant passe par UPDATE
CREATE POLICY "chat_media update couple members"
  ON storage.objects FOR UPDATE TO authenticated
  USING (
    bucket_id = 'chat_media'
    AND EXISTS (
      SELECT 1 FROM public.couples c
      WHERE c.id::text = (storage.foldername(name))[1]
        AND c.status = 'paired'
        AND auth.uid() IN (c.user1_id, c.user2_id)
    )
  );

-- Chemin utilisé par l'app : avatars/<user_id>/<uuid>.webp
CREATE POLICY "avatars insert own folder"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

-- vault_media : l'application actuelle n'y envoie AUCUN fichier (le coffre ne stocke que des
-- métadonnées dans vault_items). Aucune écriture n'est donc nécessaire pour l'app : il suffit de
-- retirer toute politique d'écriture anon/public sur ce bucket.

-- Hypothèse à vérifier : couples.user1_id / user2_id de type uuid (sinon adapter la comparaison).
-- Retour arrière : DROP POLICY ... ; puis recréer les politiques notées à l'étape 2.
