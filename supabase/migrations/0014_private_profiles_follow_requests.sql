-- Private profiles: approved followers may view; others request access.

create table if not exists public.follow_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles(id) on delete cascade,
  target_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending', 'accepted', 'rejected')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint follow_requests_no_self check (requester_id <> target_id),
  constraint follow_requests_unique_pair unique (requester_id, target_id)
);

create index if not exists follow_requests_target_pending_idx
  on public.follow_requests (target_id)
  where status = 'pending';

create index if not exists follow_requests_requester_idx
  on public.follow_requests (requester_id);

alter table public.follow_requests enable row level security;

drop policy if exists "Users can read own follow requests" on public.follow_requests;
create policy "Users can read own follow requests"
  on public.follow_requests for select to authenticated
  using (requester_id = (select auth.uid()) or target_id = (select auth.uid()) or private.is_moderator());

drop policy if exists "Users can create follow requests" on public.follow_requests;
create policy "Users can create follow requests"
  on public.follow_requests for insert to authenticated
  with check (
    requester_id = (select auth.uid())
    and private.is_profile_onboarded()
    and not private.accounts_are_blocked(requester_id, target_id)
  );

drop policy if exists "Targets can update follow requests" on public.follow_requests;
create policy "Targets can update follow requests"
  on public.follow_requests for update to authenticated
  using (target_id = (select auth.uid()) or private.is_moderator())
  with check (target_id = (select auth.uid()) or private.is_moderator());

drop policy if exists "Users can delete own outgoing requests" on public.follow_requests;
create policy "Users can delete own outgoing requests"
  on public.follow_requests for delete to authenticated
  using (requester_id = (select auth.uid()) or target_id = (select auth.uid()) or private.is_moderator());

create or replace function private.is_profile_public_or_self(p_profile_id uuid)
returns boolean
language sql
stable
security definer
set search_path to ''
as $function$
  select exists (
    select 1
    from public.profiles p
    where p.id = p_profile_id
      and (
        p.is_private = false
        or p.id = (select auth.uid())
        or exists (
          select 1 from public.follows f
          where f.follower_id = (select auth.uid())
            and f.following_id = p_profile_id
        )
      )
  );
$function$;

drop policy if exists "Public profiles are viewable by everyone" on public.profiles;
drop policy if exists "Profiles visible by privacy rules" on public.profiles;
create policy "Profiles visible by privacy rules"
  on public.profiles for select to authenticated, anon
  using (
    private.is_moderator()
    or id = (select auth.uid())
    or private.is_profile_public_or_self(id)
  );

create or replace function private.accept_follow_request(p_request_id uuid)
returns void
language plpgsql
security definer
set search_path to ''
as $function$
declare
  r public.follow_requests%rowtype;
begin
  select * into r from public.follow_requests where id = p_request_id for update;
  if not found then
    raise exception 'Request not found' using errcode = 'P0002';
  end if;
  if r.target_id is distinct from (select auth.uid()) and not private.is_moderator() then
    raise exception 'Only the recipient can accept' using errcode = '42501';
  end if;
  if private.accounts_are_blocked(r.requester_id, r.target_id) then
    raise exception 'Blocked accounts cannot connect' using errcode = '42501';
  end if;
  update public.follow_requests
    set status = 'accepted', updated_at = now()
    where id = p_request_id;
  insert into public.follows (follower_id, following_id)
  values (r.requester_id, r.target_id)
  on conflict do nothing;
end;
$function$;

revoke all on function private.accept_follow_request(uuid) from public;
grant execute on function private.accept_follow_request(uuid) to authenticated;

create or replace function public.accept_follow_request(request_id uuid)
returns void
language sql
security invoker
set search_path to public, private
as $$
  select private.accept_follow_request(request_id);
$$;

grant execute on function public.accept_follow_request(uuid) to authenticated;

create or replace function public.reject_follow_request(request_id uuid)
returns void
language plpgsql
security definer
set search_path to ''
as $function$
declare
  r public.follow_requests%rowtype;
begin
  select * into r from public.follow_requests where id = request_id for update;
  if not found then
    raise exception 'Request not found' using errcode = 'P0002';
  end if;
  if r.target_id is distinct from (select auth.uid()) and not private.is_moderator() then
    raise exception 'Only the recipient can reject' using errcode = '42501';
  end if;
  update public.follow_requests set status = 'rejected', updated_at = now() where id = request_id;
end;
$function$;

grant execute on function public.reject_follow_request(uuid) to authenticated;
