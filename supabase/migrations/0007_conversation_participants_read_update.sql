-- Allow a participant to update only their own last_read_at for unread indicators.
-- DO NOT apply to production without explicit approval.
-- Android calls: PATCH /conversation_participants?conversation_id=eq.&user_id=eq. { last_read_at }

DROP POLICY IF EXISTS "conversation_participants_self_update_read" ON public.conversation_participants;
CREATE POLICY "conversation_participants_self_update_read"
  ON public.conversation_participants
  FOR UPDATE
  TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());
