# Onboarding AuthScreen integration

The complete `AuthScreen.kt` (DOB Day/Month/Year dropdowns, searchable country picker, 5-step onboarding with skippable live suggestions) is provided as:

1. Agent artifact: `AuthScreen_onboarding.kt`
2. This branch: apply via `scripts/assemble_authscreen.sh` once `AuthScreen.full.kt` is present

## ViewModel (already on branch)

- `loadOnboardingSuggestions()` — real Supabase searchProfiles + fetchCommunities
- `followSuggestedCreator` / `joinSuggestedCommunity`
- `completeOnboarding` — 16–17 privacy defaults intact

## Verify after install

```bash
grep -n SignUpDobCountrySection app/src/main/java/com/example/ui/auth/AuthScreen.kt
grep -n 'Skip for now' app/src/main/java/com/example/ui/auth/AuthScreen.kt
grep -n 'Step 4 of 5' app/src/main/java/com/example/ui/auth/AuthScreen.kt
```
