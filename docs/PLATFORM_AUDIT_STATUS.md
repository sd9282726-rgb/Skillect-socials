# Skillect platform audit status (fix/onboarding)

Date: 2026-11-15  
Branch: `fix/onboarding`  
Scope: Android app source plus live Supabase schema/policies inspected for the messaging fixes.

## Rules followed

- Website untouched
- No merge to `main`
- No secrets exposed
- Release JKS untouched
- No destructive production data operations
- Existing RLS policies retained; no RLS weakening

## Auth / onboarding

| Item | Status |
|------|--------|
| AuthScreen integration | Present on branch; DOB, country selection, onboarding steps and suggestions |
| AuthViewModel privacy defaults | Present |
| Live account creation / onboarding E2E | Not verified; no interactive Android session available |

## Networks terminology

User-facing copy uses Networks / Connect / Connected in the profile, feed, post cards and onboarding suggestions. Internal schema names such as `follows` and `following_id` remain unchanged.

## Messaging

| Feature | Code / database status | Live verified |
|---------|------------------------|---------------|
| Start direct conversation | Edge Function exists; checks participant restrictions and creates participants | No |
| Fetch conversations and messages | Implemented | No |
| Send message through moderation path | Implemented | No |
| Realtime new messages | Implemented in Android client | No two-account test |
| Read markers | Android now reads/writes `conversation_reads`; creates a missing marker or updates only `last_read_at` | Database permissions verified; app E2E pending |
| Inbox activity timestamp | `conversations.updated_at` added; new-message trigger advances it | Schema/trigger verified; app E2E pending |
| RLS | Existing own-marker UPDATE policy retained | Policy inspected |

### Production database changes applied and verified

- Migration 0007: authenticated UPDATE grant restricted to `conversation_reads.last_read_at`; no UPDATE grants remain for anon or PUBLIC.
- Migration 0008: `conversations.updated_at` added, backfilled, NOT NULL with `now()` default; message-insert trigger and security-definer function installed.
- Production has no conversation rows yet, so no existing read-marker data was changed.
- No production messages or user records were created for testing.

## Composer media

`ComposerMediaAttachmentPanel.kt` is wired into `ComposerScreen.kt`. The former inline preview/progress UI was removed. Remove, cancel and retry callbacks are connected to the existing upload job and state.

## Feed / profile / communities

| Area | Code status | Live verified |
|------|-------------|---------------|
| Feed tabs, likes, comments, saves | Implemented | No |
| Profile editing and avatar/cover upload | Implemented | No |
| Explore and community flows | Implemented | No |
| Notifications and push registration | Implemented | No end-to-end test |

## Build and release verification

- Latest source commits retain `[skip ci]`.
- Current HEAD has no CI check run. The GitHub Actions workflow can be dispatched, but this agent connection exposes read-only workflow inspection and no workflow-dispatch action.
- A prior manual release build succeeded at commit `387e804` on 2026-11-14; that run predates the current changes and does not validate this HEAD.
- No current APK is claimed from this patch set.

## Remaining verification

1. Android unit tests and debug APK build on current HEAD.
2. Two-account app test: create conversation, send/receive, unread clears, restart persists, realtime arrives without refresh.
3. Feed photo/video, likes/comments and counter persistence.
4. Profile/avatar persistence.
5. Storage and moderation paths end-to-end.

**Release readiness: not yet verified.** The source and database fixes above are implemented, but the latest Android build and device-level journeys remain untested.

## Latest DM correction

- Production schema confirms `conversation_participants` contains only `id`, `conversation_id`, and `user_id`; read timestamps belong in `conversation_reads`.
- Corrected `start-conversation` to insert participant rows without the nonexistent `last_read_at` column and create both read markers separately.
- Deployed production Edge Function version 6 with `verify_jwt=true`; fetched the deployed source back and verified the participant payload and both read markers.
- Database read-marker grants and participant-scoped RLS policies were checked directly.
- Two-account runtime messaging and latest Android build are still not verified.

## Final verification snapshot — 2026-11-15

- Production migrations for read-marker column permissions and conversation activity timestamps are present in migration history.
- Production schema confirms read timestamps are stored in `conversation_reads`; `conversation_participants` has no `last_read_at` column.
- Production `start-conversation` Edge Function is version 6 (`verify_jwt=true)); deployed source verified to insert only valid participant columns and initialize both read markers.
- Read-marker RLS policies and least-privilege UPDATE grant were checked against production.
- Realtime publication includes `messages`, `notifications`, and `posts`; Android client has bounded reconnect and stale-connection guards.
- Composer media attachment panel is connected to the active composer flow.
- Current branch source has **not** had an Android build/test run. All commits intentionally include `[skip ci]`; no workflow-dispatch action is available in this connection.
- No two-account device test was run. The production database had no conversation rows during the audit, and no safe test accounts/device session are available here.
- Do not mark release-ready until a current Android build and two-account app test pass.

