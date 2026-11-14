# AuthScreen integration (Create Account)

## Status on branch `fix/onboarding`

### Done
- `OnboardingValidation.kt` — compose Day/Month/Year → ISO date, age 16+ errors, flag emoji helper, interest/goal catalogs
- `SignUpDobCountrySection.kt` — Day/Month/Year dropdowns + searchable country list with flags
- `AuthViewModel.kt` — clearer blank vs under-16 DOB errors on sign-up
- `AgeValidationTest.kt` — unit tests for `AppConfig` age helpers

### Required AuthScreen.kt change (one block)

Replace the free-text DOB field and country menu inside `if (uiState.mode == AuthMode.SIGN_UP)` with:

```kotlin
if (uiState.mode == AuthMode.SIGN_UP) {
    Spacer(modifier = Modifier.height(12.dp))
    SignUpDobCountrySection(
        birthDateIso = uiState.birthDateIso,
        selectedCountry = uiState.selectedCountry,
        onBirthDateIsoChange = viewModel::updateBirthDate,
        onCountryChange = viewModel::updateCountry,
    )
    Spacer(modifier = Modifier.height(12.dp))
    // … keep any fields that followed the old country dropdown
}
```

Remove the local `countryMenuOpen` state if it becomes unused.

### Onboarding flow already in repo
`OnboardingScreen` already implements a multi-step flow after sign-up:
1. Profile (display name, username + availability, avatar, bio)
2. Skills
3. Goals / interests
4. Completion → `completeOnboarding()` → Supabase profile upsert with minor-safe defaults for ages 16–17

### Not claimed verified
- Full Gradle assemble / APK on this agent host (1.2 GiB RAM)
- Live Supabase register against production project from this environment
- Interrupted onboarding resume on a physical device
