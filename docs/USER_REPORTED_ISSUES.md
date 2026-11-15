# User-reported issues (2026-11-15)

Tested against APK feedback + codebase review on `fix/onboarding`.

## 1. Profile shows "Skillect Member" briefly
**Cause:** UI uses placeholder while profile is still loading.
**App fix:** Prefer `Loading…` while `uiState.isLoading`, then real `displayName`.
**File:** `ProfileScreen.kt` (apply locally if not yet on remote).

## 2. Messages HTTP 403
**Cause:** Send path goes through `functions/v1/moderate-text` (type=message). 403 also returned when account is restricted (`account_enforcements`) or RLS blocks conversation access.
**Not fixed by UI alone.** Check Supabase edge function logs and RLS on `messages` / participants.

## 3. Network / Connected not visible
**Cause:** Follow edges + counters depend on `follows` table RLS and successful `toggleFollow`.
**App labels:** already Connect / Connected on PostCard and Profile.
**Verify:** signed-in user can INSERT into `follows`; counters RPC readable.

## 4. Avatar / DP not saving
**Path:** upload to storage → save profile via `moderate-text` with `avatar_url`.
Fails if: storage RLS blocks upload, or moderate-text secrets missing, or save never tapped after "photo ready".

## 5. Explore skills vertical
Skill directory already uses `FlowRow`. Long single-word labels truncated via SkillChip `maxLines=1`.

## 6. Publish fails: "Media safety screening is not configured"
**Exact server error from** `supabase/functions/moderate-media/index.ts`:
```
if (!base || !anon || !service || !sightUser || !sightSecret)
  return 503 "Media safety screening is not configured"
```
**Fix in Supabase project secrets (Dashboard → Edge Functions → Secrets):**
- `SIGHTENGINE_API_USER`
- `SIGHTENGINE_API_SECRET`
- `SUPABASE_SERVICE_ROLE_KEY` (usually auto)

Text posts use `moderate-text` which needs the same service role + optional Sightengine for non-profile types.

## 7. Skill tags comma input
**Fixed in app:** multi-select skill chips + optional custom skill (commit on branch).

## Live device testing
Agent cannot install the attached APK on a physical phone. Code + edge-function review only.
