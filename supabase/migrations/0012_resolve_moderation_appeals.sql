create or replace function public.resolve_moderation_appeal(
    p_appeal_id uuid,
    p_accept boolean,
    p_moderator_note text default ''
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    appeal_row public.moderation_appeals%rowtype;
begin
    if auth.uid() is null or not private.is_moderator() then
        raise exception 'Moderator access required' using errcode = '42501';
    end if;

    select *
      into appeal_row
      from public.moderation_appeals
     where id = p_appeal_id
     for update;

    if not found then
        raise exception 'Appeal not found' using errcode = 'P0002';
    end if;

    if appeal_row.status not in ('pending', 'under_review') then
        raise exception 'This appeal has already been resolved' using errcode = '55000';
    end if;

    update public.moderation_appeals
       set status = case when p_accept then 'accepted' else 'rejected' end,
           moderator_note = left(coalesce(p_moderator_note, ''), 1000),
           reviewed_by = auth.uid(),
           reviewed_at = now()
     where id = p_appeal_id;

    if p_accept then
        delete from public.account_enforcements
         where user_id = appeal_row.enforcement_user_id;
    end if;
end;
$$;

revoke all on function public.resolve_moderation_appeal(uuid, boolean, text) from public, anon;
grant execute on function public.resolve_moderation_appeal(uuid, boolean, text) to authenticated;
