# AuthScreen onboarding integration

## Status on `fix/onboarding`

### Committed
- `SignUpDobCountrySection.kt` — Day/Month/Year + searchable flagged countries
- `OnboardingValidation.kt` — DOB composition, age errors, catalogs
- `AuthViewModel.kt` — DOB validation, `loadOnboardingSuggestions`, follow/join, **16–17 privacy defaults unchanged** on `completeOnboarding`
- `AgeValidationTest.kt` — unit tests for age helpers

### AuthScreen.kt
Full updated source is in the local build tree / agent artifacts as `AuthScreen_onboarding.kt`.

Apply the committed patch:

```bash
git checkout fix/onboarding
git apply docs/AuthScreen.onboarding.patch
# or replace AuthScreen.kt with the full file from the PR description artifacts
```

After apply, SIGN_UP uses:

```kotlin
SignUpDobCountrySection(
    birthDateIso = uiState.birthDateIso,
    selectedCountry = uiState.selectedCountry,
    onBirthDateIsoChange = viewModel::updateBirthDate,
    onCountryChange = viewModel::updateCountry,
)
```

Onboarding steps: 1 profile → 2 skills → 3 goals → 4 optional live suggestions (Skip) → 5 complete.
