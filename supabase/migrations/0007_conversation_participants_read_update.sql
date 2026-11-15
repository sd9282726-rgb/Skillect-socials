-- Allow a participant to update only their own last_read_at cursor.
-- Column privileges are deliberately restricted: the mobile client must not be able
-- to rewrite conversation_id or user_id via this endpoint.
-- Approved for production apply by project owner (2026-11-15).
-- Apply via Supabase SQL Editor on project chohswyxsckpwyzlbzav if not yet applied.
-- Agent host has no Supabase service-role credentials; apply from the dashboard.

REVOKE UPDATE ON TABLE public.conversation_participants FROM PUBLIC, anon, authenticated;
REVOKE UPDATE (conversation_id, user_id, last_read_at)
  ON TABLE public.conversation_participants FROM PUBLIC, anon, authenticated;
GRANT UPDATE (last_read_at)
  ON TABLE public.conversation_participants TO authenticated;

DROP POLICY IF EXISTS "conversation_participants_self_update_read" ON public.conversation_participants;
CREATE POLICY "conversation_participants_self_update_read"
  ON public.conversation_participants
  FOR UPDATE
  TO authenticated
  USING (user_id = (SELECT auth.uid()))
  WITH CHECK (user_id = (SELECT auth.uid()));
