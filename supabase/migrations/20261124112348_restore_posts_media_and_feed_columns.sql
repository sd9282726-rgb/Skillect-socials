-- Restore the current post media/feed contract on projects upgraded from the legacy posts schema.
alter table public.posts
  add column if not exists post_type text not null default 'text',
  add column if not exists media_urls text[] not null default '{}'::text[],
  add column if not exists media_types text[] not null default '{}'::text[],
  add column if not exists hashtags text[] not null default '{}'::text[],
  add column if not exists mentions text[] not null default '{}'::text[],
  add column if not exists skill_tags text[] not null default '{}'::text[],
  add column if not exists link_url text,
  add column if not exists link_title text,
  add column if not exists project_id uuid,
  add column if not exists reposted_post_id uuid,
  add column if not exists quoted_post_id uuid,
  add column if not exists reposts_count integer not null default 0,
  add column if not exists moderation_status text not null default 'approved',
  add column if not exists is_edited boolean not null default false;

-- Preserve legacy media already stored in image_url/media_type.
update public.posts
set media_urls = case
      when coalesce(cardinality(media_urls), 0) = 0 and image_url is not null and btrim(image_url) <> ''
        then array[image_url]::text[]
      else media_urls
    end,
    media_types = case
      when coalesce(cardinality(media_types), 0) = 0 and media_type in ('image', 'video')
        then array[media_type]::text[]
      else media_types
    end,
    post_type = case
      when media_type = 'video' then 'video'
      when media_type = 'image' or (image_url is not null and btrim(image_url) <> '') then 'image'
      else coalesce(nullif(post_type, ''), 'text')
    end
where coalesce(cardinality(media_urls), 0) = 0
   or coalesce(cardinality(media_types), 0) = 0
   or post_type is null
   or post_type = '';

notify pgrst, 'reload schema';
