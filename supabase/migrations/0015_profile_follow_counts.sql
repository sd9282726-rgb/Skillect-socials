-- Keep profile network totals available consistently across every app screen.
alter table public.profiles
  add column if not exists followers_count integer not null default 0,
  add column if not exists following_count integer not null default 0;

update public.profiles p
set followers_count = (
      select count(*)::integer from public.follows f where f.following_id = p.id
    ),
    following_count = (
      select count(*)::integer from public.follows f where f.follower_id = p.id
    );

create or replace function public.sync_profile_follow_counts()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    update public.profiles
      set following_count = following_count + 1
      where id = new.follower_id;
    update public.profiles
      set followers_count = followers_count + 1
      where id = new.following_id;
    return new;
  elsif tg_op = 'DELETE' then
    update public.profiles
      set following_count = greatest(following_count - 1, 0)
      where id = old.follower_id;
    update public.profiles
      set followers_count = greatest(followers_count - 1, 0)
      where id = old.following_id;
    return old;
  else
    if old.follower_id is distinct from new.follower_id then
      update public.profiles
        set following_count = greatest(following_count - 1, 0)
        where id = old.follower_id;
      update public.profiles
        set following_count = following_count + 1
        where id = new.follower_id;
    end if;
    if old.following_id is distinct from new.following_id then
      update public.profiles
        set followers_count = greatest(followers_count - 1, 0)
        where id = old.following_id;
      update public.profiles
        set followers_count = followers_count + 1
        where id = new.following_id;
    end if;
    return new;
  end if;
end;
$$;

revoke all on function public.sync_profile_follow_counts() from public, anon, authenticated;

drop trigger if exists sync_profile_follow_counts_insert on public.follows;
drop trigger if exists sync_profile_follow_counts_delete on public.follows;
drop trigger if exists sync_profile_follow_counts_update on public.follows;

create trigger sync_profile_follow_counts_insert
after insert on public.follows
for each row execute function public.sync_profile_follow_counts();

create trigger sync_profile_follow_counts_delete
after delete on public.follows
for each row execute function public.sync_profile_follow_counts();

create trigger sync_profile_follow_counts_update
after update of follower_id, following_id on public.follows
for each row execute function public.sync_profile_follow_counts();
