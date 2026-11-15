alter table public.stories
    add column if not exists moderation_status text not null default 'pending';

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'stories_moderation_status_check'
          and conrelid = 'public.stories'::regclass
    ) then
        alter table public.stories
            add constraint stories_moderation_status_check
            check (moderation_status in ('pending', 'approved', 'rejected'));
    end if;
end $$;

drop policy if exists "skillect_stories_public_read" on public.stories;
create policy "skillect_stories_public_read"
on public.stories for select to anon
using (
    moderation_status = 'approved'
    and expires_at > now()
    and exists (
        select 1 from public.profiles p
        where p.id = stories.author_id and p.is_private = false
    )
);

drop policy if exists "skillect_stories_authenticated_read" on public.stories;
create policy "skillect_stories_authenticated_read"
on public.stories for select to authenticated
using (
    expires_at > now()
    and (
        author_id = (select auth.uid())
        or (
            moderation_status = 'approved'
            and (
                exists (
                    select 1 from public.profiles p
                    where p.id = stories.author_id and p.is_private = false
                )
                or exists (
                    select 1 from public.follows f
                    where f.follower_id = (select auth.uid())
                      and f.following_id = stories.author_id
                )
            )
            and not private.is_blocked_between(author_id)
        )
    )
);

drop policy if exists "skillect_stories_media_public_read" on storage.objects;
create policy "skillect_stories_media_public_read"
on storage.objects for select to anon
using (
    bucket_id = 'stories-media'
    and exists (
        select 1 from public.stories s
        join public.profiles p on p.id = s.author_id
        where s.media_url = storage.objects.name
          and s.moderation_status = 'approved'
          and s.expires_at > now()
          and p.is_private = false
    )
);

drop policy if exists "skillect_stories_media_authenticated_read" on storage.objects;
create policy "skillect_stories_media_authenticated_read"
on storage.objects for select to authenticated
using (
    bucket_id = 'stories-media'
    and exists (
        select 1 from public.stories s
        where s.media_url = storage.objects.name
          and s.expires_at > now()
          and (
              s.author_id = (select auth.uid())
              or (
                  s.moderation_status = 'approved'
                  and (
                      exists (
                          select 1 from public.profiles p
                          where p.id = s.author_id and p.is_private = false
                      )
                      or exists (
                          select 1 from public.follows f
                          where f.follower_id = (select auth.uid())
                            and f.following_id = s.author_id
                      )
                  )
                  and not private.is_blocked_between(s.author_id)
              )
          )
    )
);
