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

**Local changes prepared** in working tree for AuthScreen, FeedScreen, PostCard, ProfileScreen. Push of large Kotlin files may require phone/GitHub UI if MCP payload limits block full-file updates.

## Messaging (code review)

| Feature | Code status | Live verified |
|---------|-------------|---------------|
| startDirectConversation (self-guard, existing/created) | Implemented | No |
| fetchConversations / fetchMessages | Implemented | No |
| sendMessage via moderateText edge path | Implemented | No |
| Media in DMs | Explicitly disabled | N/A |
| Realtime messages channel | WebSocket client joins `realtime:public:messages` | No |
| Mark conversation read / DM unread counts | **Gap**: `last_read_at` on model but no repository mark-read on open chat | No |
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

**Live project `chohswyxsckpwyzlbzav`**: not fully audited against production without Management API/service role (not used; secrets not exposed). Edge functions listed under `supabase/functions/` — deploy parity not confirmed.

## Build / tests

| Check | Result |
|-------|--------|
| Full Gradle release compile | **Not run** (host ~1.2GB RAM) |
| Unit tests (AgeValidation etc.) | Present; not executed this session |
| Live journey 1–16 from audit brief | **Not executed** |

## Priority remaining work

1. Push Networks terminology string fixes to branch (or apply via GitHub mobile editor).
2. Implement `markConversationRead` when opening chat; surface DM unread badges.
3. Live verify messaging realtime + RLS with two test accounts.
4. Live verify likes/comments counters match DB after refresh.
5. Confirm Storage policies for avatars/media on production.
6. Confirm Edge Function deploy parity for moderateText / start conversation RPC.
7. Release Gradle build on higher-RAM CI.

## Release readiness

**Not release-ready** until critical journeys (auth → post → like → comment → connect → message → notify → restart) are live-verified.
