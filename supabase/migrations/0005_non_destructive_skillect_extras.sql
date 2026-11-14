-- STAGED ONLY — DO NOT AUTO-EXECUTE
-- Non-destructive additive migration for live Skillect Supabase database.
-- Preserves all existing website tables (profiles, posts, comments, communities,
-- community_members, projects, post_likes, follows, blocks, conversations,
-- conversation_participants, messages, notifications, saved_posts, reports,
-- moderation_appeals) and ONLY adds missing Android-optional tables, RPCs,
-- and Storage buckets safely with IF NOT EXISTS / CREATE OR REPLACE.

-- 1. Optional Stories table
create table if not exists public.stories (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(id) on delete cascade,
  media_url text not null,
  media_type text not null default 'image' check (media_type in ('image', 'video')),
  caption text not null default '',
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours')
);
alter table public.stories enable row level security;

revoke all on public.stories from anon;
grant select on public.stories to anon;
grant select, insert, delete on public.stories to authenticated;

drop policy if exists "stories_anon_public_select" on public.stories;
create policy "stories_anon_public_select" on public.stories
for select to anon
using (
  expires_at > now()
  and exists (
    select 1 from public.profiles pr
    where pr.id = stories.author_id and pr.is_private = false
  )
);

drop policy if exists "stories_auth_select" on public.stories;
create policy "stories_auth_select" on public.stories
for select to authenticated
using (
  expires_at > now()
  and (
    author_id = auth.uid()
    or exists (select 1 from public.profiles pr where pr.id = stories.author_id and pr.is_private = false)
    or exists (select 1 from public.follows f where f.follower_id = auth.uid() and f.following_id = stories.author_id)
  )
);

drop policy if exists "stories_auth_insert" on public.stories;
create policy "stories_auth_insert" on public.stories
for insert to authenticated
with check (auth.uid() = author_id);

drop policy if exists "stories_auth_delete" on public.stories;
create policy "stories_auth_delete" on public.stories
for delete to authenticated
using (auth.uid() = author_id);

-- 2. Optional Polls tables
create table if not exists public.poll_options (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  option_text text not null,
  votes_count int not null default 0,
  sort_order int not null default 0
);
alter table public.poll_options enable row level security;
grant select on public.poll_options to anon, authenticated;
grant insert on public.poll_options to authenticated;

drop policy if exists "poll_options_read" on public.poll_options;
create policy "poll_options_read" on public.poll_options
for select to anon, authenticated
using (true);

drop policy if exists "poll_options_insert_owner" on public.poll_options;
create policy "poll_options_insert_owner" on public.poll_options
for insert to authenticated
with check (
  exists (select 1 from public.posts p where p.id = poll_options.post_id and p.author_id = auth.uid())
);

create table if not exists public.poll_votes (
  post_id uuid not null references public.posts(id) on delete cascade,
  option_id uuid not null references public.poll_options(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);
alter table public.poll_votes enable row level security;
revoke all on public.poll_votes from anon;
grant select, insert on public.poll_votes to authenticated;

drop policy if exists "poll_votes_self" on public.poll_votes;
create policy "poll_votes_self" on public.poll_votes
for all to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

-- 3. Optional Experiences, Achievements, Mutes, Saved Collections, Device Tokens
create table if not exists public.experiences (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  title text not null,
  organization text not null,
  location_label text not null default '',
  start_date date not null,
  end_date date,
  is_current boolean not null default false,
  description text not null default ''
);
alter table public.experiences enable row level security;
grant select on public.experiences to anon, authenticated;
grant insert, update, delete on public.experiences to authenticated;

drop policy if exists "experiences_read" on public.experiences;
create policy "experiences_read" on public.experiences
for select to anon, authenticated
using (
  exists (select 1 from public.profiles pr where pr.id = experiences.user_id and pr.is_private = false)
  or auth.uid() = user_id
);

drop policy if exists "experiences_owner" on public.experiences;
create policy "experiences_owner" on public.experiences
for all to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create table if not exists public.achievements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  title text not null,
  issuer text not null default '',
  awarded_at date,
  description text not null default ''
);
alter table public.achievements enable row level security;
grant select on public.achievements to anon, authenticated;
grant insert, update, delete on public.achievements to authenticated;

drop policy if exists "achievements_read" on public.achievements;
create policy "achievements_read" on public.achievements
for select to anon, authenticated
using (
  exists (select 1 from public.profiles pr where pr.id = achievements.user_id and pr.is_private = false)
  or auth.uid() = user_id
);

drop policy if exists "achievements_owner" on public.achievements;
create policy "achievements_owner" on public.achievements
for all to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create table if not exists public.mutes (
  muter_id uuid not null references public.profiles(id) on delete cascade,
  muted_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (muter_id, muted_id)
);
alter table public.mutes enable row level security;
revoke all on public.mutes from anon;
grant select, insert, delete on public.mutes to authenticated;

drop policy if exists "mutes_owner" on public.mutes;
create policy "mutes_owner" on public.mutes
for all to authenticated
using (auth.uid() = muter_id)
with check (auth.uid() = muter_id);

create table if not exists public.saved_collections (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  name text not null,
  is_private boolean not null default true,
  created_at timestamptz not null default now()
);
alter table public.saved_collections enable row level security;
revoke all on public.saved_collections from anon;
grant select, insert, update, delete on public.saved_collections to authenticated;

drop policy if exists "saved_collections_owner" on public.saved_collections;
create policy "saved_collections_owner" on public.saved_collections
for all to authenticated
using (auth.uid() = owner_id)
with check (auth.uid() = owner_id);

create table if not exists public.user_device_tokens (
  user_id uuid not null references public.profiles(id) on delete cascade,
  fcm_token text not null,
  platform text not null default 'android',
  updated_at timestamptz not null default now(),
  primary key (user_id, fcm_token)
);
alter table public.user_device_tokens enable row level security;
revoke all on public.user_device_tokens from anon;
grant select, insert, update, delete on public.user_device_tokens to authenticated;

drop policy if exists "device_tokens_owner" on public.user_device_tokens;
create policy "device_tokens_owner" on public.user_device_tokens
for all to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

-- 4. Helper RPCs for Direct Messaging & Device Push Tokens
create or replace function public.create_or_get_direct_conversation(peer_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
  v_conv uuid;
begin
  if v_me is null then
    raise exception 'Authentication required';
  end if;
  if v_me = peer_id then
    raise exception 'Cannot message yourself';
  end if;

  select cp1.conversation_id into v_conv
  from public.conversation_participants cp1
  join public.conversation_participants cp2 on cp1.conversation_id = cp2.conversation_id
  where cp1.user_id = v_me and cp2.user_id = peer_id
  limit 1;

  if v_conv is not null then
    return v_conv;
  end if;

  insert into public.conversations default values returning id into v_conv;
  insert into public.conversation_participants (conversation_id, user_id)
  values (v_conv, v_me), (v_conv, peer_id);

  return v_conv;
end;
$$;

revoke all on function public.create_or_get_direct_conversation(uuid) from anon;
grant execute on function public.create_or_get_direct_conversation(uuid) to authenticated;

create or replace function public.upsert_my_device_token(p_fcm_token text, p_platform text default 'android')
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;
  insert into public.user_device_tokens (user_id, fcm_token, platform, updated_at)
  values (auth.uid(), p_fcm_token, coalesce(nullif(p_platform, ''), 'android'), now())
  on conflict (user_id, fcm_token) do update
    set platform = excluded.platform, updated_at = now();
end;
$$;

revoke all on function public.upsert_my_device_token(text, text) from anon;
grant execute on function public.upsert_my_device_token(text, text) to authenticated;

-- 5. Media Storage Buckets (idempotent)
insert into storage.buckets (id, name, public)
values
  ('post-media', 'post-media', true),
  ('avatars', 'avatars', true),
  ('stories-media', 'stories-media', true),
  ('chat-media', 'chat-media', false)
on conflict (id) do nothing;
