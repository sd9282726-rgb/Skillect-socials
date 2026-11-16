# Final UI fixes (2026-11-16)

Committed on `fix/onboarding`:

1. **Feed header** — removed oversized hero banner; compact spacing only.
2. **Timestamps** — `AppConfig.parseIsoTimestamp` handles microsecond ISO (`...618962+00:00`) so Messages/Profile show `Just now` / `5m` instead of raw strings.
3. **Publish button** — primary button uses `maxLines=1` + `softWrap=false`; composer header gives title `weight(1f)` so Publish is not clipped vertically.
4. **Explore** — never lists the signed-in user in people/suggestions; shorter search debounce (120ms).
5. **English only** — language picker removed from Settings.
6. **Messaging** — friendly errors for 503/403/401 instead of technical HTTP text.
7. **SkillChip / PrimaryButton** — no vertical text wrapping.

Still recommended follow-ups (already partially present in codebase):
- Notification permission launcher exists in SkillectApp + Settings.
- `deletePost` + confirmation path exists in Feed.
- Private profile / follow_requests backend migration 0014 live.
- Media aspect ratios partially handled in PostCard (ContentScale.Crop / aspectRatio).
