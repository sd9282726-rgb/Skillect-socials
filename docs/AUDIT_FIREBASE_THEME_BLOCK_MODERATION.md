# Audit — Firebase, Light theme, Block/Unblock, Moderation Hub

Date: 2026-11-16  
Branch: `fix/onboarding`  
Live project: `chohswyxsckpwyzlbzav`

## 1. Firebase / FCM

### Verified (source + live data)
- `app/google-services.json` package = `com.skillect.app` (matches `applicationId`)
- Firebase BOM + messaging/analytics/crashlytics/perf in `app/build.gradle.kts`
- `SkillectFirebaseMessagingService` registered; token refresh path present
- `AnalyticsTracker` fetches FCM token and repository upserts into `public.device_tokens`
- Live: `device_tokens` has **3** rows; RLS policy `Users manage their own device tokens`
- Edge function `send-push` uses `FIREBASE_SERVICE_ACCOUNT_JSON` (secret present in project)
- Secret list includes: `FIREBASE_SERVICE_ACCOUNT_JSON`, `PUSH_WEBHOOK_SECRET`

### Not device-verified here
- Actual notification delivery on a physical phone (no emulator in this environment)
- Permission prompt UX on Android 13+

### Auth note
- App auth is **Supabase**, not Firebase Auth. Firebase is used for push/analytics/crashlytics/perf only. That is correct for this architecture.

## 2. Light theme readability

### Theme tokens
- Dedicated `SkillectLightColorScheme` with dark on-surface (`#111118`) on light surfaces
- Primary buttons use `onPrimary` (white on violet) — correct
- Outlined buttons use `onSurface` — correct
- Profile header icons use white on **dark translucent circle** — readable on light covers
- Shorts/story overlays intentionally use white on dark media — correct

### Fix applied
- `SkillectLightMutedText` darkened from `#343447` → `#2A2A3A` for stronger secondary contrast

### Limitation
- Pixel-perfect contrast on every screen requires device screenshots in light mode; code-level theme tokens are consistent. No pure white-on-white MaterialTheme text paths found in primary components.

## 3. Block / Unblock

### Already live (RLS)
- `blocks`: insert/delete/select by blocker only
- Hide content between blocked accounts on: `posts`, `comments`, `messages`, `profiles`, `stories`
- Messaging edge function also checks `blocks`

### Gap fixed (migration 0013 — **applied live**)
- Follow insert did **not** check blocks (only onboarded check)
- Now: trigger + RLS `with check` reject follows when either direction is blocked
- On block insert: trigger deletes both follow edges
- Client: best-effort unfollow after successful block (CI inject via privacy script)

### UI
- Profile menu: Block account
- Settings: Blocked accounts list + Unblock button

### Live counts
- `blocks` rows: **0** (no active blocks in production data at audit time)

## 4. Moderation Hub + Community Guidelines

### Community Guidelines
- Settings entry + dialog using `community_guidelines_title` / `community_guidelines_body` / `understood`
- Strings present in `values/strings.xml`

### Moderation Hub
- Route `SkillectRoute.ModerationAdmin` + `ModerationAdminScreen`
- Gate: `uiState.isModerator` from JWT `app_metadata.role` in (`moderator`,`admin`) via `private.is_moderator()`
- Non-moderators see empty state only — **no admin actions exposed**
- Moderators: reports, media queue, appeals with approve/reject paths
- Reports RLS: users create; moderators read/update
- Live: `reports` count **0**

### Not device-tested
- Full moderator workflow with a real moderator JWT account

## Verification summary

| Area | Source reviewed | Live backend checked | Device tested |
|------|-----------------|----------------------|---------------|
| Firebase config | Yes | Yes (tokens=3, secrets present) | No |
| Light theme tokens | Yes | N/A | No |
| Block RLS + follow gap | Yes | Yes (0013 applied) | No |
| Moderation hub gate | Yes | Yes (is_moderator, reports policies) | No |
| Guidelines UI | Yes | N/A | No |
