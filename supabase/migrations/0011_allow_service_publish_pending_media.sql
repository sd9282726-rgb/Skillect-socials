-- Allow edge functions (service_role) to publish media posts immediately while
-- background safety screening continues. Does not weaken end-user RLS.
-- Users still cannot insert posts with unapproved media from the client.

create or replace function private.require_approved_post_media()
returns trigger
language plpgsql
security definer
set search_path to ''
as $function$
begin
  if new.image_url is not null
     and (tg_op = 'INSERT' or new.image_url is distinct from old.image_url)
     and not exists (
       select 1
       from public.media_assets a
       where a.owner_id = new.author_id
         and a.public_url = new.image_url
         and (
           a.status = 'approved'
           or (a.status = 'pending' and new.moderation_hidden = true)
           -- service_role edge functions may publish pending media visibly for async screening
           or (a.status = 'pending' and coalesce(auth.role(), '') = 'service_role')
         )
     ) then
    raise exception 'Media must have a pending safety record and remain hidden until approval.'
      using errcode = '42501';
  end if;
  return new;
end;
$function$;

create or replace function private.guard_post_moderation_visibility()
returns trigger
language plpgsql
security definer
set search_path to ''
as $function$
begin
  if new.moderation_hidden is distinct from old.moderation_hidden
     and not private.is_moderator()
     and coalesce(auth.role(), '') <> 'service_role' then
    raise exception 'Only moderators can change post visibility.' using errcode = '42501';
  end if;
  return new;
end;
$function$;
