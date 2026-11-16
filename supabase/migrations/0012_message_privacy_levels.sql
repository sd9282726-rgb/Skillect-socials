-- Expand DM privacy beyond allow_message_requests boolean.
-- Values: everyone | following | nobody

alter table public.user_preferences
  add column if not exists message_privacy text not null default 'everyone';

alter table public.user_preferences
  drop constraint if exists user_preferences_message_privacy_check;

alter table public.user_preferences
  add constraint user_preferences_message_privacy_check
  check (message_privacy in ('everyone', 'following', 'nobody'));

update public.user_preferences
set message_privacy = case
  when allow_message_requests is false then 'nobody'
  else 'everyone'
end
where message_privacy = 'everyone' and allow_message_requests is false;

comment on column public.user_preferences.message_privacy is
  'Who may start a DM: everyone, following (recipient follows sender), nobody';
