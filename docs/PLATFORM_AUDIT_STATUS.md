# Skillect platform audit status (fix/onboarding)

Date: 2026-11-15  
Branch: `fix/onboarding`  
Scope: Android app + Supabase schema/migrations in repo (live Supabase project not fully exercised without authenticated device session).

## Rules followed

- No website changes
- No merge to main
- No secrets exposed
- No JKS regeneration
- No production destructive DB ops
- No RLS weakening

## Auth / onboarding

| Item | Status |
|------|--------|
| AuthScreen full integration | Present on branch (~63KB), DOB Day/Month/Year, country picker, 5-step flow, suggestions + Skip |
| AuthViewModel minor privacy defaults | Present (`following_only` for 16–17) |
| Live account create / onboarding E2E | **Not verified in this session** (no interactive device + credentials) |

## Terminology: Networks (not Followers)

User-facing copy should say Networks / Connect / Connected.

| Location | Was | Should be |
|----------|-----|-----------|
| Profile followers counter | Networks | Networks (already) |
| Profile following counter | Following | Connected |
| Profile CTA | Follow / Following | Connect / Connected |
| Post card CTA | Follow / Following | Connect / Connected |
| Feed tab | Following | Network |
| Guest prompts | follow creators | connect with creators |
| Onboarding suggestions | Follow | Connect |

Internal schema remains `follows` / follower_id / following_id (correct).

**Applied to the remote `fix/onboarding` branch** in commits `5d5936a`, `5d110e3`, and `e735eb9`. This includes AuthScreen, FeedScreen, PostCard, ProfileScreen, MessagesScreen, and the DM read-state implementation.

## Messaging (code review)

| Feature | Code status | Live verified |
|---------|-------------|---------------|
| startDirectConversation (self-guard, existing/created) | Implemented | No |
| fetchConversations / fetchMessages | Implemented | No |
| sendMessage via moderateText edge path | Implemented | No |
| Media in DMs | Explicitly disabled | N/A |
| Realtime messages channel | WebSocket client joins `realtime:public:messages` | No |
| Mark conversation read / DM unread counts | Implemented in branch: authenticated read-cursor update on open + inbox New marker | **Not live-tested** |
| RLS for messages | Migrations enable RLS; production policies need live check | No |

## Feed (code review)

| Feature | Code status | Live verified |
|---------|-------------|---------------|
| Tabs recommended / following | Implemented | No |
| toggleLike with pending state | Implemented | No |
| Comments load/add | Implemented | No |
| Save posts | Implemented | No |
| Stories | fetchActiveStories | No |
| Pull-to-refresh / pagination | Present in ViewModel refresh patterns | No |
| Duplicate like prevention | Pending like set in UI | No |

## Profile

| Feature | Code status | Live verified |
|---------|-------------|---------------|
| Follow toggle pending while loading | Recent fix `[skip ci] Prevent follow action during profile loading` | No |
| Avatar/cover upload | MediaUploadManager + storage migrations | No |
| Edit profile fields | updateProfileFields / upsertProfile | No |

## Explore / Communities / Notifications

| Area | Code status | Live verified |
|------|-------------|---------------|
| Search people/posts | ExploreScreen + repository search* | No |
| Communities join/leave | Repository + CommunitiesScreen | No |
| Notifications list / mark read | Implemented for notifications table | No |
| Push FCM | FirebaseMessagingService present; end-to-end not verified | No |

## Supabase (repo migrations only)

Migrations present:

- `0001_inspect_existing.sql`
- `0002_skillect_core_schema.sql` (profiles, posts, likes, follows, conversations, messages, RLS enable)
- `0003_guest_rls_and_auth_audit.sql`
- `0004_counters_storage_and_dm_rpc.sql`
- `0005_non_destructive_skillect_extras.sql`
- `0006_profile_avatar_storage.sql`
- `0007_conversation_participants_read_update.sql` (staged only; not applied to production)

**Live project `chohswyxsckpwyzlbzav`**: not fully audited against production without Management API/service role (not used; secrets not exposed). Edge functions listed under `supabase/functions/` — deploy parity not confirmed.

## Build / tests

| Check | Result |
|-------|--------|
| Android unit tests + debug compile | **Not run**; manual `workflow_dispatch` was added to `.github/workflows/android-ci.yml` for a GitHub-hosted runner |
| Unit tests (AgeValidation etc.) | Present; not executed this session |
| Live journey 1–16 from audit brief | **Not executed** |

## Priority remaining work

1. Run the manual Android CI workflow on `fix/onboarding` (unit tests + debug APK build); no run has been triggered yet.
2. Inspect production conversation-participant columns, grants, and RLS before deciding whether migration `0007` is needed. The live migration inventory already lists `conversation_read_markers_for_unread_counts`.
3. Two-account live verify messaging, persisted read state, realtime, and RLS after the production policy path is confirmed.
4. Live verify likes/comments counters after refresh.
5. Confirm Storage policies for avatars/media on production.
6. Confirm Edge Function deploy parity for moderateText / start conversation RPC.
7. Run the complete release build after CI passes; no release APK is claimed from this patch set.

## Release readiness

**Not release-ready** until critical journeys (auth → post → like → comment → connect → message → notify → restart) are live-verified.
