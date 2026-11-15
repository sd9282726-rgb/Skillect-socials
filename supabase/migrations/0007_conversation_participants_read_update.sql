-- Restrict client read-cursor writes to the timestamp only.
-- conversation_reads is the canonical read-marker table used by the existing
-- Android inbox and already has participant-scoped SELECT/INSERT/UPDATE RLS policies.

REVOKE UPDATE ON TABLE public.conversation_reads FROM PUBLIC, anon, authenticated;
REVOKE UPDATE (conversation_id, user_id, last_read_at)
  ON TABLE public.conversation_reads FROM PUBLIC, anon, authenticated;
GRANT UPDATE (last_read_at)
  ON TABLE public.conversation_reads TO authenticated;
