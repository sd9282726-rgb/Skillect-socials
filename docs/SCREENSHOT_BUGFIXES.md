# Screenshot bugfixes (2026-11-15)

## Vertical skill text ("Business strategy" letter-by-letter)
**Cause:** Skill chips sat in a tight `Row` next to the "0 networks" button; Compose wrapped each letter vertically.
**Fix:** Move skill chips to a full-width `FlowRow` under the person row; `softWrap = false` + ellipsis on tags.

## Publish button "Pu / bli / sh"
**Cause:** Header `SpaceBetween` squeezed the Publish button; label wrapped vertically.
**Fix:** `SkillectPrimaryButton` / Outlined: `maxLines = 1`, `softWrap = false`; composer title `weight(1f)` so Publish keeps width.

## "Skillect Member" on empty profiles
**Fix:** Prefer `displayName` → `username` → `Loading…` / `Member`. Never show product jargon as a name.

## Message when Connected still blocked
**Server:** `start-conversation` returns `request_required` until a message request is accepted.
**App copy (friendlier):** "Send a quick hello first. They'll need to accept before you can chat."
**Follow-up:** wire `create-message-request` so Message auto-sends a hello instead of a dead-end error.

## Skill tags comma field
**Fix:** Multi-select skill chips + optional custom skill (max 8).

## Media publish "safety screening not configured"
Requires Supabase Edge secrets `SIGHTENGINE_API_USER` + `SIGHTENGINE_API_SECRET`.

Local fixed sources also under agent workspace for Components, Explore, Composer, Profile, Repository.
