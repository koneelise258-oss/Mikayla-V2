-- Espace couple partagé : agenda, cycle, « temps ensemble », jeux (coupons, quiz, défis,
-- liste de rêves, dilemmes, capsules, étapes). Chaque élément est visible ET modifiable
-- par les deux membres du couple. Les suppressions sont gardées (deleted = true) pour que
-- l'autre téléphone les applique aussi.
create table if not exists public.couple_items (
  couple_id  uuid        not null references public.couples(id) on delete cascade,
  item_id    text        not null check (char_length(item_id) between 1 and 80),
  kind       text        not null check (kind in (
                 'event', 'cycle', 'love_time', 'coupon', 'bucket', 'quiz',
                 'dilemma', 'truth_dare', 'milestone', 'capsule')),
  payload    jsonb       not null default '{}'::jsonb,
  deleted    boolean     not null default false,
  updated_by uuid        not null default auth.uid(),
  updated_at timestamptz not null default now(),
  primary key (couple_id, item_id)
);

create index if not exists couple_items_couple_updated_idx on public.couple_items (couple_id, updated_at);

alter table public.couple_items enable row level security;

drop policy if exists couple_items_select on public.couple_items;
create policy couple_items_select on public.couple_items
  for select to authenticated using (public.is_couple_member(couple_id));

drop policy if exists couple_items_insert on public.couple_items;
create policy couple_items_insert on public.couple_items
  for insert to authenticated
  with check (public.is_couple_member(couple_id) and updated_by = auth.uid());

drop policy if exists couple_items_update on public.couple_items;
create policy couple_items_update on public.couple_items
  for update to authenticated
  using (public.is_couple_member(couple_id))
  with check (public.is_couple_member(couple_id) and updated_by = auth.uid());

-- Pas de DELETE direct : on marque deleted = true (l'autre téléphone doit le voir)

-- Horodatage toujours fixé par le serveur (pas par l'horloge du téléphone)
create or replace function public.mik_couple_items_touch()
returns trigger language plpgsql set search_path = public, pg_temp as $$
begin
  new.updated_at := now();
  new.updated_by := auth.uid();
  return new;
end $$;

drop trigger if exists couple_items_touch on public.couple_items;
create trigger couple_items_touch before insert or update on public.couple_items
  for each row execute function public.mik_couple_items_touch();

grant select, insert, update on public.couple_items to authenticated;
