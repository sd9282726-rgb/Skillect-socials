# SKILLECT — Live Supabase Compatibility, Auth & RLS Audit Report

**Status:** Audited live Supabase project (`https://chohswyxsckpwyzlbzav.supabase.co`) via read-only HTTP probes. Android source code updated for full production compatibility. **No existing production tables, users, content, policies, or website workflows were modified.**

---

## 1. Live Supabase Audit Findings

### A. Authentication & Key Behavior (`/auth/v1/settings`)
- **Auto-Confirm Already Enabled (`mailer_autoconfirm: true`)**: New 16+ users signing up via `POST /auth/v1/signup` receive an active `access_token` + `refresh_token` immediately without email verification blockers.
- **Anonymous Auth Disabled (`anonymous_users: false`)**: Matches Skillect's strict zero-fake-user Guest Mode rule.
- **Publishable Key (`sb_publishable_...`)**: Live Auth/PostgREST endpoints require `sb_publishable_...` ONLY in the `apikey` header (sending it in `Authorization: Bearer` fails with `401 no_authorization`). `SupabaseClientFactory` and `SupabaseRealtimeClient` now omit `Authorization` when unauthenticated with `sb_publishable_...` and inject `Authorization: Bearer <user_jwt>` once signed in.
- **Trigger-Created Profile Stubs**: Signup triggers on the live DB auto-create a `profiles` stub row with `onboarded = false` and nullable `country`.

### B. Live Production Schema Mapping (Preserving Existing Website)
Read-only REST inspection identified schema differences between the initial Android spec and the live production website database:
- **`profiles`**: Uses `location`, `country` (nullable), `date_of_birth`, `website`, `skills`, `interests`, `onboarded`, `is_private` (no `headline`, `country_code`, `region_label`, `allow_dms_from`, `followers_count`).
- **`posts`**: Uses `image_url`, `category`, `tags`, `community_id`, `likes_count`, `comments_count` (no `post_type`, `media_urls`, `poll_options` embed).
- **`comments`**: Table is `comments` (not `post_comments`) with `parent_id`.
- **`communities` & `community_members`**: `communities` uses `cover_url`, `created_by` (nullable), `member_count`, `topics`; `community_members` uses `created_at` and `status`.
- **`projects`**: Uses `author_id`, `image_url`, `status`.
- **`notifications`**: Uses `user_id`, `message`, `read` (no `title`, `recipient_id`, `is_read`).
- **`saved_posts`**: Uses `user_id`, `post_id`, `created_at` (no `collection_id`).
- **`moderation_appeals`**: Uses `explanation`, `moderator_note`, `status`.
- **`conversations`, `conversation_participants`, `messages`, `post_likes`, `follows`, `blocks`, `reports`**: Verified present and aligned.

### C. Optional Tables / RPCs / Storage Not Yet Present on Live Instance
- Returned `404` on live DB: `stories`, `poll_options`, `poll_votes`, `mutes`, `skills`, `user_skills`, `experiences`, `achievements`, `saved_collections`, `moderation_audit_logs`, `user_device_tokens`, `create_or_get_direct_conversation`, `upsert_my_device_token`, `request_account_deletion`, and Storage buckets (`GET /storage/v1/bucket` returned `[]`).
- **Android Resilience Applied**: `SkillectRepository` handles `404` responses gracefully for optional sections (`stories`, `poll_votes`, `skills` falls back to curated global skill catalog, `experiences`, `achievements`, `saved_collections`, `moderation_audit_logs`, and `startDirectConversation` falls back to checking existing DM threads).

---

## 2. Android Compatibility Fixes Applied
1. **`SupabaseModels.kt` & `SupabaseApiService.kt`**: Aligned DTO field names (`@Json`), query selects, filter parameters, and sort orders with the live production tables while keeping UI compatibility getters (`countryCode`, `regionLabel`, `birthDate`, `creatorId`, `ownerId`, `coverUrl`).
2. **`SupabaseClientFactory.kt` & `SupabaseRealtimeClient.kt`**: Omit `Authorization: Bearer` when the key starts with `sb_publishable_` and no user session is active; attach user JWT once authenticated.
3. **`SkillectRepository.kt`, `AuthViewModel.kt`, `ProfileSettingsViewModel.kt`, `FeedViewModel.kt`, `ExploreScreen.kt`, `NotificationsScreen.kt`**:
   - Trigger onboarding when `profile == null || !profile.onboarded`, and save `onboarded = true` + `skills` on completion.
   - Normalize `image_url`/`tags`/`category` into feed media/badges and preserve repost attribution + images.
   - Map profile edits (`location`, `website`, `is_private`, `skills`) cleanly without referencing missing columns.
4. **Tests (`SkillectUnitAndRobolectricTest.kt`)**: Verified Guest Mode navigation, auth recovery deep-link parsing, `sb_publishable_` header behavior, privileged-column stripping, and live production JSON deserialization.

---

## 3. Remaining External Configuration (Optional / Manual Approval)

1. **AI Studio Secrets Panel (Required for APK Runtime)**:
   Add these keys in the **AI Studio Secrets** panel (do **not** use `local.properties`):
   - `SUPABASE_URL` = `https://chohswyxsckpwyzlbzav.supabase.co`
   - `SUPABASE_ANON_KEY` = `sb_publishable__1Bd6TEnLWDtTdT4U4e-oQ_0MeY2E17`
2. **Auth Deep-Link Redirect URL**:
   In **Supabase Dashboard -> Authentication -> URL Configuration -> Redirect URLs**, ensure `skillect://auth-callback` is listed for in-app password reset redirects.
3. **Optional Additive SQL Migration (`0005_non_destructive_skillect_extras.sql`)**:
   Do **NOT** run `0002_skillect_core_schema.sql` against the live DB (it targets an older table shape). Instead, if you want to enable Stories, Polls, Portfolio Experiences/Achievements, Mutes, Saved Collections, DM auto-creation RPC, FCM device tokens, and media upload buckets (`post-media`, `avatars`, `stories-media`, `chat-media`), review and run `supabase/migrations/0005_non_destructive_skillect_extras.sql`. It uses only `IF NOT EXISTS` and leaves all existing website tables untouched.
