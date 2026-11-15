# Apply migration 0007 (DM last_read_at)

**Approved:** 2026-11-15 (owner requested all remaining steps)

## What it does
- Lets authenticated users **UPDATE only** `conversation_participants.last_read_at` on **their own** row
- Required for persistent DM unread badges after app restart

## How to apply (Supabase Dashboard)
1. Open project `chohswyxsckpwyzlbzav`
2. SQL Editor → New query
3. Paste the full contents of `supabase/migrations/0007_conversation_participants_read_update.sql`
4. Run
5. Confirm policy exists:
   `select * from pg_policies where tablename = 'conversation_participants';`

## Verify from the app
1. Account A messages Account B
2. B opens the chat (marks read)
3. Force-stop app, reopen inbox → conversation should not show unread for B
4. A sends again → B should see unread until open

## Agent limitation
This environment has no Supabase service role / CLI login, so the SQL cannot be executed from the agent.
