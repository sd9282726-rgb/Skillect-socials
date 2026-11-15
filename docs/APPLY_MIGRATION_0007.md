# Apply migration 0007 (DM read cursor)

Migration 0007 now targets `public.conversation_reads`, the existing canonical read-marker table.

It preserves the existing participant-scoped UPDATE RLS policy and restricts authenticated clients to updating only `last_read_at`. It does not alter `conversation_participants`.

The Android client reads the embedded `conversation_reads` markers in the inbox and updates the current user's marker when a chat is opened or a message is sent.

