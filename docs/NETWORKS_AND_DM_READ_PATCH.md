# Networks and DM read-state patch status

Branch: `fix/onboarding`

## Networks terminology

Applied on the branch:

- `PostCard.kt`: Connect / Connected
- `ProfileScreen.kt`: Connect / Connected and Connected count label
- `FeedScreen.kt`: Network tab and Connect wording in guest prompts
- `AuthScreen.kt`: Connect action for onboarding suggestions

Internal database and Kotlin identifiers such as `follows`, `followingIds`, and `toggleFollow` remain unchanged intentionally.

## DM read state

Implemented on the branch:

- `ConversationReadRepository.kt`: create a missing marker in `conversation_reads`; if it already exists, PATCH only `last_read_at`.
- `SupabaseApiService.kt`: embed `conversation_reads` in conversation inbox responses.
- `SupabaseModels.kt`: add the `ConversationReadDto` and read-marker list.
- `MessagesNotificationsViewModel.kt`: update the local marker when a chat opens or the user sends a message.
- `MessagesScreen.kt`: compare the newest conversation activity with the current user's marker.
- `0007_conversation_participants_read_update.sql`: corrected to restrict UPDATE on the canonical `conversation_reads` table.

## Conversation activity timestamp

- `0008_conversation_activity_timestamp.sql` adds `conversations.updated_at`.
- A database trigger advances it when a new message is inserted.
- This fixes the inbox's activity comparison; the live `conversations` table previously had no `updated_at` column.

## Conversation creation function

- The branch version of `start-conversation` now creates read markers for both participants at conversation creation and removes a duplicated existing-conversation lookup.
- This Edge Function source change is **not deployed** to production; the deployed function remains unchanged until deployment is explicitly authorized.

## Production verification

Both migrations were applied to project `chohswyxsckpwyzlbzav`.

Verified:
- Authenticated UPDATE permission is limited to `conversation_reads.last_read_at`.
- The existing participant-scoped UPDATE RLS policy remains in place.
- `conversations.updated_at` exists as NOT NULL with a `now()` default.
- The message activity trigger and its function exist.

No two-account live messaging test has been performed. The production database currently has zero conversation participant rows, so there was no existing DM data to validate or migrate.

## Composer media

`ComposerMediaAttachmentPanel.kt` is now connected in `ComposerScreen.kt`; remove, cancel and retry are wired to the upload state and job.

## Realtime reliability follow-up

The Android WebSocket client now retries after unexpected close/failure with bounded backoff and cancels pending retries on explicit disconnect. This is source-reviewed but not yet verified by a connected-device interruption test.

## Production deployment verification

- The production `start-conversation` Edge Function is now version 6 with JWT verification still enabled.
- Its participant insert now writes only the live `conversation_participants` columns (`conversation_id`, `user_id`).
- It creates read markers for both participants in `conversation_reads`.
- The deployed function source was fetched back and checked after deployment.
- A real two-account conversation test is still outstanding; the production project had no existing conversation rows during the schema audit.
