# SKILLECT — System Architecture & Implementation Blueprint

**Tagline:** Connect. Create. Discover. Grow.  
**Audience:** Worldwide users aged 16+  
**Primary Experience:** Dark-first (Charcoal / Near-Black / Graphite / Electric Violet) with full Light Mode support.

---

## 1. Overall System Architecture
Skillect uses a **dual-backend architecture**:
- **Supabase (Primary System of Record):** Authentication, PostgreSQL database with Row Level Security (RLS), Storage buckets, Realtime WebSockets (chat, live notifications, feed updates), and Edge Functions (push dispatch, account deletion, moderation enforcement).
- **Firebase (Supporting Mobile Infrastructure):** Firebase Cloud Messaging (FCM) for mobile push notifications, Firebase Analytics for privacy-respecting telemetry, Crashlytics, and Performance Monitoring. Firebase never duplicates user accounts or application records; `user_device_tokens` maps FCM tokens to the canonical Supabase `auth.uid()`.

### Notification Flow
1. Application event occurs (like, comment, reply, mention, repost, follow, DM, community activity, project update).
2. PostgreSQL trigger / API inserts a canonical row in `public.notifications`.
3. Supabase Database Webhook invokes the trusted Edge Function `send-push-notification`.
4. Edge Function verifies recipient notification preferences, block status, and minor-safe restrictions, then dispatches an FCM HTTP v1 push using server-side Firebase service account credentials.
5. Supabase retains the in-app notification record (`read_at` synced across devices).

---

## 2. Technology Stack & Cross-Platform Strategy
- **Android Native Client:** Kotlin + Jetpack Compose + Material 3 + Navigation Compose (`@Serializable` routes) + Coroutines/Flow + Retrofit/OkHttp/Moshi + Media3 ExoPlayer + Coil + Jetpack DataStore.
- **Cross-Platform Parity (Web / iOS):** All business contracts live in versioned PostgreSQL schemas, PostgREST endpoints, Supabase Storage paths, and Edge Functions, allowing React/Next.js Web and SwiftUI iOS clients to share identical RLS policies, auth flows, and Realtime channels.
- **Why this fits Skillect:** Native Jetpack Compose delivers 60/120fps scrolling, smooth vertical video playback via Media3, zero-permission Android Photo Picker integration, and native adaptive layouts (`NavigationBar` on phones, `NavigationRail` on tablets/foldables) without WebView compromise.

---

## 3. Database Architecture & Existing Project Protection
Before running any migration on the existing Skillect Supabase project, run `supabase/migrations/0001_inspect_existing.sql` (read-only) to inspect:
1. Existing tables & columns in `public` and `auth`
2. Existing auth providers & triggers
3. Existing Storage buckets
4. Existing RLS policies
5. Existing PostgreSQL functions & triggers

**Non-Destructive Guarantee:** `0002_skillect_core_schema.sql` uses `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, and `CREATE OR REPLACE` exclusively—never `DROP TABLE`, `TRUNCATE`, or `RESET`.

---

## 4. Authentication & 16+ Age Eligibility Architecture
- Supabase Auth (`/auth/v1/*`) handles Sign Up, Login, Email Verification, Password Reset, Session Refresh, and Account Deletion (via Edge Function calling `auth.admin.deleteUser`).
- **Age Eligibility Gate (16+):** Registration validates `birth_date <= current_date - interval '16 years'` on both client and PostgreSQL `CHECK` constraint / trigger.
- **Minor-Safe Defaults (Ages 16–17):** Automatically initializes `allow_dms_from = 'following_only'`, `discoverable_by_phone = false`, and stricter content filtering.
- **International Phone / Country Support:** E.164 country dialing codes, ISO 3166-1 country codes, IANA timezones, and Unicode/RTL-safe fields.

---

## 5. Firebase Integration Architecture
- Android registers `SkillectFirebaseMessagingService` for FCM token refresh and message handling across dedicated channels (`skillect_messages`, `skillect_social`, `skillect_safety`).
- Token is upserted into `public.user_device_tokens` with `(user_id, fcm_token, platform = 'android', updated_at)`.
- Firebase Analytics logs sanitized screen/engagement events without PII.

---

## 6. Storage Architecture
Supabase Storage buckets with RLS & MIME/size constraints:
- `avatars` (public read, owner write, max 5 MB, image/*)
- `covers` (public read, owner write, max 8 MB, image/*)
- `post-media` (authenticated/public read per post visibility, owner write, max 15 MB images / 100 MB videos)
- `stories-media` (24-hour active story objects, owner write, max 50 MB)
- `chat-media` (private bucket — signed URLs only for conversation participants, max 10 MB)
- `project-covers` (public read, owner write, max 8 MB)

---

## 7. Security, Safety & Moderation Model
- **Zero-Trust RLS:** Every table has `ENABLE ROW LEVEL SECURITY`.
- **Blocking & Muting:** Blocked users cannot view profiles, posts, stories, or send DMs (`NOT EXISTS (SELECT 1 FROM blocks ...)`).
- **Zero-Tolerance Safety Policy:** Prohibits CSAM, sexual exploitation, grooming, harassment, threats, hate speech, explicit sexual content, scams, spam, impersonation, and malicious links.
- **Server-Side Admin Role:** `profiles.role IN ('member', 'moderator', 'admin')` is protected from client-side updates via RLS `WITH CHECK` / column privilege revocation. Admin dashboard actions execute via RLS-verified queries or `SECURITY DEFINER` RPCs.

---

## 8. Feature Module Breakdown
- `core/config`: BuildConfig secrets, international formatting, 16+ age calculator
- `core/designsystem`: Dark-first & Light color schemes, Plus Jakarta Sans / Inter / JetBrains Mono typography, reusable M3 components
- `core/network`: Auth, PostgREST, Storage, Realtime WebSocket client, DataStore session store
- `core/media`: Photo Picker helper, JPEG compression, upload progress/cancel/retry, Media3 video player
- `core/notifications`: FCM service, notification channels, Analytics wrapper
- `ui/auth`: Welcome, Sign In, Sign Up (16+ DOB + country code), Recovery, Onboarding
- `ui/feed`: Following / Recommended / Discover feeds, Stories bar & 24h viewer, Vertical Short Videos, Post cards, Polls, Threaded Comments, Repost/Quote
- `ui/explore`: Global search (People, Posts, Hashtags, Communities, Projects, Skills) with filters & sorting
- `ui/composer`: Multi-media post composer, polls, link previews, skill tags, project links
- `ui/communities`: 12 global categories, create/join/leave, member roles, community posts & moderation
- `ui/messages`: Real-time DM inbox & chat threads, image sharing, delivery/read states, block/report
- `ui/notifications`: Real-time alert center, mark read / mark all read, deep links
- `ui/profile`: Social + Professional identity (skills, projects, experience, education, achievements, portfolio), saved collections
- `ui/settings`: Account, Privacy, Security, Blocked/Muted lists, Language/Theme preferences, Safety guidelines, Admin Moderation Dashboard

---

## 9. Navigation Structure
- **Adaptive Shell:**
  - `Compact` (< 600dp): Material 3 `NavigationBar` with 5 primary destinations (`Feed`, `Explore`, `Create`, `Messages`, `Profile`) + top-bar access to `Shorts`, `Communities`, `Notifications`, and `Settings/Moderation`.
  - `Medium` / `Expanded` (>= 600dp): Material 3 `NavigationRail` on the start edge + centered max-width feed / split list-detail panes.

---

## 10. Credentials & External Deployment Checklist
1. Add `SUPABASE_URL` and `SUPABASE_ANON_KEY` in the **AI Studio Secrets panel**.
2. Inspect your existing Supabase project with `supabase/migrations/0001_inspect_existing.sql`.
3. Apply `supabase/migrations/0002_skillect_core_schema.sql` via Supabase SQL Editor or `supabase db push`.
4. Create the Storage buckets (`avatars`, `covers`, `post-media`, `stories-media`, `chat-media`, `project-covers`) and enable Realtime on `messages`, `notifications`, `posts`, and `post_comments`.
5. Place your Firebase `google-services.json` in `app/google-services.json` for FCM & Analytics, and deploy `supabase/functions/send-push-notification/index.ts` with `FIREBASE_SERVICE_ACCOUNT_JSON` set in Supabase Edge Function secrets.
