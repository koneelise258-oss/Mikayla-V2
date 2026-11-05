Retour de Claude (côté application Android) pour la partie Supabase de Mikayala V2 — projet `yrcnneyejqlkumjbetfh`.
Branche GitHub : `claude/mikayala-v2-audit-dev-ryddh8`. Rien n'a été déployé ni modifié sur Supabase par Claude.

1. Fonction Edge `send-fcm` — nouvelle version prête, NON déployée
- Fichiers : `supabase/functions/send-fcm/index.ts` + nouveau `payload.ts` (tests : `payload.test.ts`, 7/7 OK en CI).
- Changements : liste blanche des types (ajout `calendar_event`, `cycle`, `call_declined`, `call_cancelled`, `call_ended`) ;
  pour `cycle`, aucun champ client n'est transmis (texte neutre « Vous avez une nouvelle notification ») ;
  champ `recipientId` ajouté au payload ; TTL 30 s pour `call`, `call_cancelled`, `call_ended`.
- À faire : relire puis déployer les DEUX fichiers (`supabase functions deploy send-fcm`), garder `verify_jwt = true`,
  vérifier que le secret `FCM_SERVICE_ACCOUNT_JSON` correspond au projet Firebase `gen-lang-client-0988385912`
  (l'app Android s'appelle maintenant `com.mikayla.app`).
- Compatibilité : l'ancien APK fonctionne avec la nouvelle fonction, et le nouvel APK avec l'ancienne.

2. Table `device_push_tokens` — problème au changement de compte
- UNIQUE(fcm_token) + politique UPDATE `auth.uid() = user_id` : si l'ancien compte n'a pas supprimé son token,
  le nouveau compte du même téléphone ne peut pas le reprendre (upsert REST `on_conflict=fcm_token` refusé par RLS).
- Proposition : fonction `public.claim_device_push_token(p_token text, p_label text default 'Android')`,
  SECURITY DEFINER, `SET search_path = public`, `REVOKE ALL ... FROM public, anon`, `GRANT EXECUTE ... TO authenticated`,
  qui refuse si `auth.uid()` est null puis fait
  `INSERT (user_id, fcm_token, platform, device_label) VALUES (auth.uid(), p_token, 'android', p_label)
   ON CONFLICT (fcm_token) DO UPDATE SET user_id = auth.uid(), updated_at = now(), last_seen_at = now()`.
- Merci de dire si vous la créez : Claude modifiera alors l'app pour appeler `rpc/claim_device_push_token`.

3. Points d'audit à vérifier sur la base réelle (non vérifiables depuis le dépôt, une seule migration y est versionnée)
- Buckets `chat_media` et `vault_media` publics : l'app utilise `publicUrl` pour `chat_media`. Passer en privé
  imposerait des URLs signées côté app → à coordonner AVANT de changer le bucket, sinon les médias ne s'affichent plus.
- Fonctions SQL à `search_path` mutable, fonctions SECURITY DEFINER exécutables par `anon`, table `device_authorizations`
  avec RLS sans politique.
- Données de cycle menstruel : RLS limitée aux deux membres du couple, aucune exposition publique.
- Exporter le schéma (tables, RLS, buckets, fonctions) dans `supabase/migrations` pour que l'app et la base restent alignées.

4. Ce dont Claude a besoin en retour
- Confirmation du déploiement de `send-fcm` (ou des corrections demandées).
- Décision sur `claim_device_push_token`.
- Décision et calendrier pour les buckets privés.
