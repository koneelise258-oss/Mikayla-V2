-- Appliquée le 25/11/2026 (trace). Voir migration du même nom dans Supabase.
-- 1) Messages : aucun droit UPDATE/DELETE => réactions, coches, jeux, épingler, modifier, supprimer refusés (403).
alter table public.messages add column if not exists is_viewed boolean default false;
grant update (reactions, status, delivered_at, read_at, media_url, is_pinned, is_starred,
              content, is_edited, edited_at, deleted_for_everyone, is_deleted_for_everyone,
              deleted_at, deleted_for_users, is_viewed)
  on public.messages to authenticated;
grant delete on public.messages to authenticated;
-- Garde-fou : seul l'expéditeur modifie/supprime le texte ; il ne peut pas marquer son message lu.
-- (fonction public.mik_guard_message_update + déclencheur mik_messages_guard_update)
-- 2) call_signals : statuts « cancelled » et « accepted » autorisés.
-- 3) Déclencheur send_chat_notification (smart-api, notifications web) supprimé : toujours en 401.
