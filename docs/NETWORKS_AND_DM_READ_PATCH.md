# Networks and DM read-state patch status

Branch: `fix/onboarding`  
Latest implementation: `e735eb92a4d579566e633d9a2f58a38cebdfa2a4`

## Networks terminology

Applied to the remote branch in:

- `PostCard.kt`: Connect / Connected action
- `ProfileScreen.kt`: Connect / Connected action and Connected count label
- `FeedScreen.kt`: Network tab and Connect wording in guest prompts
- `AuthScreen.kt`: Connect action for onboarding suggestions

Internal database and Kotlin identifiers such as `follows`, `followingIds`, and `toggleFollow` remain unchanged intentionally.

## DM read state

Implemented in code on `fix/onboarding`:

- `ConversationReadRepository.kt`: authenticated PATCH of the current user's `last_read_at`, with representation verification
- `MessagesNotificationsViewModel.kt`: persist read state when a conversation opens; show an error and refresh the inbox if the server rejects the update
- `MessagesScreen.kt`: show a New marker when conversation activity is newer than the current user's read cursor
- `0007_conversation_participants_read_update.sql`: staged RLS policy plus column-level UPDATE grant for `last_read_at` only

The extension uses the repository's existing session store and Supabase client configuration. It does not expose service-role credentials.

## Production and verification status

- Migration `0007` has **not** been applied to production.
- The live migration inventory already contains a migration named `conversation_read_markers_for_unread_counts`. Inspect the current production columns, grants, and RLS policies before considering `0007`; do not apply it blindly.
- No live messaging E2E has been run with two accounts.
- No Android compile or unit-test run has been completed for these changes.

The Android CI workflow now supports manual dispatch. In GitHub Actions, select **Android CI**, choose branch `fix/onboarding`, and run the workflow. It executes unit tests and assembles a debug APK on a GitHub-hosted runner. The workflow needs the repository secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY`.

All source commits retain `[skip ci]`; manual dispatch is therefore the intended way to run CI for this patch set.
