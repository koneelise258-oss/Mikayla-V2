# Changements backend proposés (NON déployés)

## 1. send-fcm (code dans supabase/functions/send-fcm, non déployé)
- `payload.ts` : liste blanche stricte pour `cycle` (aucune donnée de santé), types autorisés, TTL court pour les appels, champ `recipientId` ajouté.
- Risque : faible. Les anciens APK ignorent `recipientId`. Le nouvel APK reste compatible avec l'ancienne fonction (sans `recipientId`, il ne bloque rien).
- Déploiement (à valider par vous) : `supabase functions deploy send-fcm` (le dossier contient index.ts + payload.ts).

## 2. Reprise d'un token après changement de compte (migration à écrire, non appliquée)
Constat : `device_push_tokens` a une contrainte UNIQUE(fcm_token) et une politique UPDATE `auth.uid() = user_id`.
Si l'ancien compte n'a pas pu supprimer son token (déconnexion forcée sans réseau), le nouveau compte ne peut
pas le reprendre : l'upsert est refusé par RLS. L'appareil reste lié à l'ancien compte.
Atténuation déjà côté app : la notification n'est affichée que si `recipientId` == utilisateur connecté.
Correction proposée : fonction `claim_device_push_token(p_token text)` SECURITY DEFINER, `SET search_path = public`,
EXECUTE accordé à `authenticated` seulement (révoqué à `anon`/`public`), qui fait
`INSERT ... ON CONFLICT (fcm_token) DO UPDATE SET user_id = auth.uid(), updated_at = now(), last_seen_at = now()`.
Le client appellerait `rpc/claim_device_push_token` au lieu de l'upsert REST. À valider avec votre audit Supabase avant toute écriture.
