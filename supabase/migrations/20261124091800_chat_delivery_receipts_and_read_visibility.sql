alter table public.conversation_reads
  add column if not exists last_delivered_at timestamptz;

revoke update on table public.conversation_reads from public, anon, authenticated;
revoke update (conversation_id, user_id, last_read_at, last_delivered_at)
  on table public.conversation_reads from public, anon, authenticated;
grant update (last_read_at, last_delivered_at)
  on table public.conversation_reads to authenticated;

drop policy if exists "Users view own conversation read markers"
  on public.conversation_reads;
drop policy if exists "Conversation participants view read markers"
  on public.conversation_reads;
create policy "Conversation participants view read markers"
  on public.conversation_reads
  for select
  to authenticated
  using (private.is_conversation_participant(conversation_id));
