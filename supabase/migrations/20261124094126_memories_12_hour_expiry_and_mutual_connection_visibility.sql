-- Memories expire after 12 hours and private accounts require mutual connections.
alter table public.stories
  alter column expires_at set default (now() + interval '12 hours');

-- Existing 24-hour Memories are shortened from their original creation time.
update public.stories
set expires_at = created_at + interval '12 hours'
where expires_at > created_at + interval '12 hours';

create index if not exists stories_expiry_idx
  on public.stories (expires_at);

drop policy if exists "skillect_stories_authenticated_read" on public.stories;
create policy "skillect_stories_authenticated_read"
on public.stories for select to authenticated
using (
  expires_at > now()
  and (
    author_id = (select auth.uid())
    or (
      moderation_status = 'approved'
      and not private.is_blocked_between(author_id)
      and exists (
        select 1
        from public.profiles p
        where p.id = stories.author_id
          and (
            p.is_private = false
            or (
              exists (
                select 1 from public.follows viewer_to_author
                where viewer_to_author.follower_id = (select auth.uid())
                  and viewer_to_author.following_id = stories.author_id
              )
              and exists (
                select 1 from public.follows author_to_viewer
                where author_to_viewer.follower_id = stories.author_id
                  and author_to_viewer.following_id = (select auth.uid())
              )
            )
          )
      )
    )
  )
);

drop policy if exists "skillect_stories_media_authenticated_read" on storage.objects;
create policy "skillect_stories_media_authenticated_read"
on storage.objects for select to authenticated
using (
  bucket_id = 'stories-media'
  and exists (
    select 1
    from public.stories s
    join public.profiles p on p.id = s.author_id
    where s.media_url = storage.objects.name
      and s.expires_at > now()
      and (
        s.author_id = (select auth.uid())
        or (
          s.moderation_status = 'approved'
          and not private.is_blocked_between(s.author_id)
          and (
            p.is_private = false
            or (
              exists (
                select 1 from public.follows viewer_to_author
                where viewer_to_author.follower_id = (select auth.uid())
                  and viewer_to_author.following_id = s.author_id
              )
              and exists (
                select 1 from public.follows author_to_viewer
                where author_to_viewer.follower_id = s.author_id
                  and author_to_viewer.following_id = (select auth.uid())
              )
            )
          )
        )
      )
  )
);