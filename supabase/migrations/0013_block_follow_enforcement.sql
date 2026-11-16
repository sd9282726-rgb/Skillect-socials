-- Enforce blocks at the relationship layer (not only UI).
-- 1) Prevent new follows between blocked accounts
-- 2) Remove existing follows when a block is created

create or replace function private.accounts_are_blocked(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path to ''
as $$
  select exists (
    select 1 from public.blocks
    where (blocker_id = a and blocked_id = b)
       or (blocker_id = b and blocked_id = a)
  );
$$;

create or replace function private.reject_follow_when_blocked()
returns trigger
language plpgsql
security definer
set search_path to ''
as $$
begin
  if private.accounts_are_blocked(new.follower_id, new.following_id) then
    raise exception 'Cannot follow a blocked account'
      using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists reject_follow_when_blocked on public.follows;
create trigger reject_follow_when_blocked
  before insert on public.follows
  for each row execute function private.reject_follow_when_blocked();

create or replace function private.cleanup_relationships_on_block()
returns trigger
language plpgsql
security definer
set search_path to ''
as $$
begin
  delete from public.follows
  where (follower_id = new.blocker_id and following_id = new.blocked_id)
     or (follower_id = new.blocked_id and following_id = new.blocker_id);
  return new;
end;
$$;

drop trigger if exists cleanup_relationships_on_block on public.blocks;
create trigger cleanup_relationships_on_block
  after insert on public.blocks
  for each row execute function private.cleanup_relationships_on_block();

drop policy if exists "Users can follow" on public.follows;
create policy "Users can follow"
  on public.follows
  for insert
  to authenticated
  with check (
    (select auth.uid()) = follower_id
    and private.is_profile_onboarded()
    and not private.accounts_are_blocked(follower_id, following_id)
  );
