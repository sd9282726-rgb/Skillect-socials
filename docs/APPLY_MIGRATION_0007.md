# DM read cursor and conversation activity migrations

**Production status: applied and verified on 2026-11-15.**

## Migration 0007 — read-marker permissions
- Targets `public.conversation_reads`, the canonical read-marker table.
- Keeps the existing participant-scoped UPDATE RLS policy.
- Restricts authenticated clients to updating only `last_read_at`.
- Verified in production: authenticated has UPDATE on `last_read_at` only; anon and PUBLIC have no UPDATE grant.

## Migration 0008 — conversation activity timestamp
- Adds `conversations.updated_at`, backfilled from the latest message or conversation creation.
- Adds a trigger so each new message advances the parent conversation's activity timestamp.
- Verified in production: column exists and is NOT NULL with a `now()` default; message trigger and security-definer function exist.

## Android behavior
- Inbox fetch embeds the signed-in user's visible `conversation_reads` markers.
- Opening a chat creates the marker if missing, or updates only `last_read_at` if it already exists.
- The inbox uses `conversations.updated_at` to compare activity with the user's read marker.

Two-account end-to-end testing still requires real signed-in Android sessions.
