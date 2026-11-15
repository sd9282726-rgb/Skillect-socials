-- Private story media with viewer-scoped access and short-lived signed playback URLs.
create table if not exists public.stories (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.profiles(id) on delete cascade,
    media_url text not null,
    media_type text not null default 'image' check (media_type in ('image', 'video')),
    moderation_status text not null default 'pending' check (moderation_status in ('pending', 'approved', 'rejected')),
    caption text not null default '' check (char_length(caption) <= 280),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '24 hours'),
    constraint stories_media_path_owned_by_author
        check (split_part(media_url, '/', 1) = author_id::text)
);

create index if not exists stories_active_by_author_created
    on public.stories (author_id, created_at desc);

alter table public.stories enable row level security;
grant select on public.stories to anon, authenticated;
grant insert, delete on public.stories to authenticated;

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

drop policy if exists "skillect_stories_owner_insert" on public.stories;
create policy "skillect_stories_owner_insert"
on public.stories for insert to authenticated
with check (
    author_id = (select auth.uid())
    and private.is_profile_onboarded()
    and split_part(media_url, '/', 1) = (select auth.uid())::text
);

drop policy if exists "skillect_stories_owner_delete" on public.stories;
create policy "skillect_stories_owner_delete"
on public.stories for delete to authenticated
using (author_id = (select auth.uid()));

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'stories-media', 'stories-media', false, 52428800,
    array['image/jpeg','image/png','image/webp','image/heic','image/heif','video/mp4','video/webm','video/quicktime']::text[]
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "skillect_stories_media_owner_insert" on storage.objects;
create policy "skillect_stories_media_owner_insert"
on storage.objects for insert to authenticated
with check (
    bucket_id = 'stories-media'
    and (storage.foldername(name))[1] = (select auth.uid())::text
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

drop policy if exists "skillect_stories_media_owner_delete" on storage.objects;
create policy "skillect_stories_media_owner_delete"
on storage.objects for delete to authenticated
using (
    bucket_id = 'stories-media'
    and (storage.foldername(name))[1] = (select auth.uid())::text
);
