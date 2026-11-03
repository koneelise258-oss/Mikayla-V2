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
