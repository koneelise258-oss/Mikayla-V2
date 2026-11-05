-- Requêtes d'audit EN LECTURE SEULE (SELECT uniquement). Aucune ne modifie la base.
-- À exécuter dans le SQL Editor Supabase (projet yrcnneyejqlkumjbetfh) ; coller les résultats à Claude.

-- 1. Fonctions SQL à search_path modifiable (attendu : 12)
SELECT n.nspname AS schema, p.proname AS fonction, pg_get_function_identity_arguments(p.oid) AS args,
       p.prosecdef AS security_definer
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND NOT EXISTS (SELECT 1 FROM unnest(coalesce(p.proconfig, '{}')) c WHERE c LIKE 'search_path=%')
ORDER BY 2;

-- 2. Fonctions SECURITY DEFINER exécutables par anon / authenticated / PUBLIC (attendu : 8 pour anon)
SELECT p.proname AS fonction, pg_get_function_identity_arguments(p.oid) AS args,
       has_function_privilege('anon', p.oid, 'EXECUTE') AS anon,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') AS authenticated
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.prosecdef
ORDER BY 1;

-- 3. Code source des fonctions SECURITY DEFINER (pour vérifier les contrôles auth.uid())
SELECT p.proname, pg_get_functiondef(p.oid)
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.prosecdef;

-- 4. Tables : RLS activée ? nombre de politiques (device_authorizations attendu : RLS sans politique)
SELECT c.relname AS table_name, c.relrowsecurity AS rls, c.relforcerowsecurity AS rls_forcee,
       (SELECT count(*) FROM pg_policies pp WHERE pp.schemaname = 'public' AND pp.tablename = c.relname) AS politiques
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r'
ORDER BY 1;

-- 5. Toutes les politiques (public + storage), dont menstrual_cycles et storage.objects
SELECT schemaname, tablename, policyname, cmd, roles, permissive, qual, with_check
FROM pg_policies
WHERE schemaname IN ('public', 'storage')
ORDER BY schemaname, tablename, policyname;

-- 6. menstrual_cycles en détail : colonnes + droits par rôle
SELECT column_name, data_type, is_nullable FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'menstrual_cycles' ORDER BY ordinal_position;
SELECT grantee, privilege_type FROM information_schema.role_table_grants
WHERE table_schema = 'public' AND table_name = 'menstrual_cycles' ORDER BY 1, 2;
-- Comptes anonymes : les politiques doivent exclure les sessions anonymes, par ex. via
-- coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) = false. Vérifier dans qual / with_check.

-- 7. Buckets
SELECT id, name, public, file_size_limit, allowed_mime_types FROM storage.buckets ORDER BY id;

-- 8. Export du schéma : colonnes, contraintes, index
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns WHERE table_schema = 'public' ORDER BY table_name, ordinal_position;
SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE connamespace = 'public'::regnamespace ORDER BY 1, 2;
SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' ORDER BY 1, 2;

-- Export complet recommandé (dans un environnement avec la CLI Supabase, sans rien appliquer) :
--   supabase db dump --schema public,storage --file supabase/migrations/<date>_schema_reel.sql
-- (lecture seule : crée un fichier local, ne modifie pas la base)

-- ============================================================================
-- COMPLÉMENTS (mission de mise en cohérence) — TOUJOURS EN LECTURE SEULE
-- Aucune requête n'affiche de valeur secrète : seulement des oui/non, des types et des emplacements.
-- ============================================================================

-- 9. Droits EXECUTE détaillés de TOUTES les fonctions public (qui peut appeler quoi)
SELECT p.oid::regprocedure AS fonction, p.prosecdef AS security_definer,
       coalesce(array_to_string(p.proconfig, ', '), '— search_path NON défini —') AS config,
       has_function_privilege('anon', p.oid, 'EXECUTE') AS anon,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') AS authenticated,
       EXISTS (SELECT 1 FROM pg_trigger t WHERE t.tgfoid = p.oid) AS utilisee_par_trigger
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
ORDER BY security_definer DESC, fonction;
-- Fonctions appelées par l'APK (doivent rester exécutables par authenticated) :
--   mark_messages_delivered, mark_messages_read, touch_user_activity
--   (+ register_device_push_token après la migration 20261105090000)

-- 10. Triggers : table, fonction appelée, et présence d'un JETON dans les arguments ou le code
--     (affiche uniquement « oui/non » et le type de jeton, jamais sa valeur)
SELECT c.relname AS table_name, t.tgname AS trigger_name, p.proname AS fonction,
       (encode(t.tgargs, 'escape') ~ 'eyJ[A-Za-z0-9_-]{10,}\.') AS jwt_dans_arguments,
       (p.prosrc ~ 'eyJ[A-Za-z0-9_-]{10,}\.') AS jwt_dans_code,
       (encode(t.tgargs, 'escape') ~* 'sb_secret_' OR p.prosrc ~* 'sb_secret_') AS cle_secrete_nouveau_format,
       (encode(t.tgargs, 'escape') ~* 'service_role' OR p.prosrc ~* 'service_role') AS mention_service_role,
       (p.prosrc ~* 'net\.http_post|http_request|supabase_functions\.http_request') AS appel_http
FROM pg_trigger t
JOIN pg_class c ON c.oid = t.tgrelid
JOIN pg_proc p ON p.oid = t.tgfoid
WHERE NOT t.tgisinternal
ORDER BY 1, 2;

-- 11. Rôle porté par un JWT trouvé dans un trigger (sans afficher le jeton) :
--     décode uniquement la partie centrale pour lire « role » et « exp » (date d'expiration).
WITH src AS (
  SELECT t.tgname, coalesce(substring(encode(t.tgargs, 'escape') from 'eyJ[A-Za-z0-9_-]+\.([A-Za-z0-9_-]+)\.'),
                            substring(p.prosrc from 'eyJ[A-Za-z0-9_-]+\.([A-Za-z0-9_-]+)\.')) AS payload
  FROM pg_trigger t JOIN pg_proc p ON p.oid = t.tgfoid WHERE NOT t.tgisinternal
)
SELECT tgname,
       (convert_from(decode(rpad(translate(payload, '-_', '+/'), ((length(payload) + 3) / 4) * 4, '='), 'base64'), 'UTF8')::json ->> 'role') AS role,
       to_timestamp((convert_from(decode(rpad(translate(payload, '-_', '+/'), ((length(payload) + 3) / 4) * 4, '='), 'base64'), 'UTF8')::json ->> 'exp')::bigint) AS expire_le
FROM src WHERE payload IS NOT NULL;
-- Interprétation : role = service_role → jeton privilégié ; à faire tourner selon docs/AUDIT_SUPABASE_FINAL.md §E.

-- 12. Webhooks de base de données (Database Webhooks) et secrets du Vault : noms seulement
SELECT id, hook_table_id::regclass AS table_name, hook_name, created_at FROM supabase_functions.hooks ORDER BY created_at;
SELECT name, description, created_at, updated_at FROM vault.secrets ORDER BY name;   -- jamais decrypted_secret

-- 13. Politiques de la table messages (les jeux du chat mettent à jour media_url) + publication temps réel
SELECT policyname, cmd, roles, qual, with_check FROM pg_policies WHERE schemaname = 'public' AND tablename = 'messages';
SELECT schemaname, tablename FROM pg_publication_tables WHERE pubname = 'supabase_realtime' ORDER BY 2;

-- 14. Colonnes de device_push_tokens et jetons en double / orphelins (comptes, pas de valeurs)
SELECT count(*) AS lignes, count(DISTINCT user_id) AS comptes, count(*) FILTER (WHERE last_seen_at < now() - interval '60 days') AS inactifs_60j
FROM public.device_push_tokens;

-- 15. Edge Function send-fcm : version déployée (à faire en ligne de commande, lecture seule)
--   supabase functions list --project-ref yrcnneyejqlkumjbetfh
--   supabase functions download send-fcm --project-ref yrcnneyejqlkumjbetfh   (dans un dossier temporaire)
--   puis comparer avec supabase/functions/send-fcm/{index.ts,payload.ts} du dépôt.
--   Secrets (noms uniquement) : supabase secrets list --project-ref yrcnneyejqlkumjbetfh
