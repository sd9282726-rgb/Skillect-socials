# Feature status (code on fix/onboarding)

Updated: 2026-11-15. **Code review only** unless marked live-verified.

| Feature | Code on GitHub | Notes |
|---------|----------------|-------|
| Networks terminology | Yes | Connect / Connected / Network (not Followers) |
| Likes | Yes | Pending guard, count delta on success |
| Comments | Yes | Via moderate-text gateway |
| Feed | Yes | Recommended / Network tabs, refresh |
| Shorts | Yes | Vertical videos, like/comment/share |
| Communities | Yes | Join/leave/create |
| Profile edit + save | Yes | moderate-text + re-fetch verification |
| Avatar / cover | Yes | uploadProfileImage then updateProfile |
| Messaging | Yes | startConversation, send, list |
| DM read cursor | Yes | ConversationReadRepository + optimistic UI |
| DM unread badge | Yes | last_read_at vs updatedAt |
| Realtime messages | Yes | WS channel messages |
| Realtime notifications | Yes | WS channel notifications |
| Realtime feed | Yes | posts → FeedUpdated |
| Composer photo/video | Yes | Progress, cancel, retry, attachment card |
| Migration 0007 (read RLS) | In repo only | **Not applied to production without approval** |

Live E2E against Supabase still required for release.
