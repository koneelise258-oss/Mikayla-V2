-- Appliquée le 25/11/2026 (trace). Appels audio/vidéo WebRTC.
alter table public.call_signals add column if not exists caller_id uuid default auth.uid();
alter table public.call_signals add column if not exists offer_sdp text;
alter table public.call_signals add column if not exists answer_sdp text;
alter table public.call_signals add column if not exists answered_at bigint;
alter table public.call_signals add column if not exists ended_at bigint;
grant insert (caller_id, offer_sdp) on public.call_signals to authenticated;
grant update (offer_sdp, answer_sdp, answered_at, ended_at) on public.call_signals to authenticated;
-- caller_id toujours fixé par le serveur (déclencheur mik_call_signals_set_caller)
