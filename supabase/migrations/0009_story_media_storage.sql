-- Story uploads require a public media bucket and owner-scoped write access.
-- Staged migration: apply to the linked Supabase project before enabling story uploads.
-- Only the owner can upload or delete files; media is publicly readable for story playback.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'stories-media',
  'stories-media',
  true,
  52428800,
  array['image/jpeg','image/png','image/webp','image/heic','image/heif','video/mp4','video/webm','video/quicktime']::text[]
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "skillect_stories_public_read" on storage.objects;
create policy "skillect_stories_public_read"
on storage.objects
for select
to public
using (bucket_id = 'stories-media');

drop policy if exists "skillect_stories_owner_insert" on storage.objects;
create policy "skillect_stories_owner_insert"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'stories-media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "skillect_stories_owner_delete" on storage.objects;
create policy "skillect_stories_owner_delete"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'stories-media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);
