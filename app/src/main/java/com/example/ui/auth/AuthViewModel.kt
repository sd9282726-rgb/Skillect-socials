package com.example.ui.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.config.CountryOption
import com.example.core.network.ProfileDto
import com.example.core.network.ProfileUpsertDto
import com.example.data.repository.SkillectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP, RECOVER, RESET_PASSWORD }

data class RecoveryDeepLinkPayload(
    val accessToken: String? = null,
    val type: String? = null,
    val error: String? = null,
    val errorDescription: String? = null,
)

object AuthDeepLinkParser {
    fun parse(uriString: String?): RecoveryDeepLinkPayload? {
        if (uriString.isNullOrBlank()) return null
        val uri = Uri.parse(uriString)
        if (uri.scheme != "skillect" || uri.host != "auth-callback") return null

        val params = mutableMapOf<String, String>()
        // Parse fragment (#access_token=...&type=recovery)
        uri.fragment?.split("&")?.forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                params[Uri.decode(parts[0])] = Uri.decode(parts[1])
            }
        }
        // Parse query (?error=...&type=recovery)
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { value -> params[key] = value }
        }
        return RecoveryDeepLinkPayload(
            accessToken = params["access_token"],
            type = params["type"],
            error = params["error"],
            errorDescription = params["error_description"],
        )
    }
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val recoveryToken: String = "",
    val birthDateIso: String = "",
    val selectedCountry: CountryOption = AppConfig.supportedCountries.first(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val currentUserId: String = "",
    val needsOnboarding: Boolean = false,
    val currentProfile: ProfileDto? = null,
    val authSheetOpen: Boolean = false,
    val authPromptReason: String? = null,
    // Onboarding fields
    val displayName: String = "",
    val username: String = "",
    val usernameAvailable: Boolean? = null,
    val checkingUsername: Boolean = false,
    val bio: String = "",
    val headline: String = "",
    val selectedSkills: Set<String> = emptySet(),
    val selectedInterests: Set<String> = emptySet(),
)

class AuthViewModel(
    private val repository: SkillectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refreshSessionIfNeeded()
        }
        viewModelScope.launch {
            repository.sessionFlow.collect { session ->
                if (session.isAuthenticated) {
                    repository.realtimeClient.connect(session.userId)
                    repository.syncTelemetryAndPushToken(session.userId)
                    val profile = repository.fetchMyProfile().getOrNull()
                    if (profile == null) {
                        hydrateMetadataIfMissing()
                    }
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            currentUserId = session.userId,
                            currentProfile = profile,
                            needsOnboarding = AppConfig.isSupabaseConfigured && (profile == null || !profile.onboarded),
                            authSheetOpen = false,
                            authPromptReason = null,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = false,
                            currentUserId = "",
                            currentProfile = null,
                            needsOnboarding = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun hydrateMetadataIfMissing() {
        val meta = repository.fetchCurrentAuthUserMetadata()
        val birth = meta["birth_date"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val countryIso = meta["country_code"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val matchedCountry = countryIso?.let { iso ->
            AppConfig.supportedCountries.firstOrNull { it.isoCode.equals(iso, ignoreCase = true) }
        }
        if (birth != null || matchedCountry != null) {
            _uiState.update { state ->
                state.copy(
                    birthDateIso = if (state.birthDateIso.isBlank() && birth != null) birth else state.birthDateIso,
                    selectedCountry = matchedCountry ?: state.selectedCountry,
                )
            }
        }
    }

    fun openAuthPrompt(mode: AuthMode = AuthMode.SIGN_IN, reason: String? = null) {
        _uiState.update {
            it.copy(
                mode = mode,
                authSheetOpen = true,
                authPromptReason = reason,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun closeAuthPrompt() {
        _uiState.update {
            it.copy(
                authSheetOpen = false,
                authPromptReason = null,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun handleDeepLinkUri(uriString: String?) {
        val payload = AuthDeepLinkParser.parse(uriString) ?: return
        if (!payload.error.isNullOrBlank() || !payload.errorDescription.isNullOrBlank()) {
            val friendly = payload.errorDescription
                ?: "This password reset link is invalid or has expired. Request a new one below."
            _uiState.update {
                it.copy(
                    mode = AuthMode.RECOVER,
                    authSheetOpen = true,
                    errorMessage = friendly,
                    infoMessage = null,
                )
            }
            return
        }
        if (payload.type == "recovery" || !payload.accessToken.isNullOrBlank()) {
            val token = payload.accessToken.orEmpty()
            if (token.isBlank()) {
                _uiState.update {
                    it.copy(
                        mode = AuthMode.RECOVER,
                        authSheetOpen = true,
                        errorMessage = "Recovery token missing or expired. Please request a new reset email.",
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        mode = AuthMode.RESET_PASSWORD,
                        recoveryToken = token,
                        newPassword = "",
                        confirmNewPassword = "",
                        authSheetOpen = true,
                        errorMessage = null,
                        infoMessage = "Enter a new password for your Skillect account.",
                    )
                }
            }
        }
    }

    fun setMode(mode: AuthMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null, infoMessage = null) }
    }

    fun updateEmail(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun updateNewPassword(value: String) = _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    fun updateConfirmNewPassword(value: String) = _uiState.update { it.copy(confirmNewPassword = value, errorMessage = null) }
    fun updateBirthDate(value: String) = _uiState.update { it.copy(birthDateIso = value, errorMessage = null) }
    fun updateCountry(country: CountryOption) = _uiState.update { it.copy(selectedCountry = country) }

    fun submitAuth() {
        val state = _uiState.value
        if (state.mode == AuthMode.RESET_PASSWORD) {
            if (state.newPassword.length < 8) {
                _uiState.update { it.copy(errorMessage = "New password must be at least 8 characters.") }
                return
            }
            if (state.newPassword != state.confirmNewPassword) {
                _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
                repository.completePasswordRecovery(state.recoveryToken, state.newPassword).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mode = AuthMode.SIGN_IN,
                                recoveryToken = "",
                                newPassword = "",
                                confirmNewPassword = "",
                                infoMessage = "Password updated! You can now sign in with your new password.",
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                    },
                )
            }
            return
        }

        if (state.email.isBlank() || !state.email.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }
        if (state.mode != AuthMode.RECOVER && state.password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters.") }
            return
        }
        if (state.mode == AuthMode.SIGN_UP && !AppConfig.isEligibleAge16Plus(state.birthDateIso)) {
            _uiState.update {
                it.copy(errorMessage = "Skillect requires users to be aged 16 or older (enter YYYY-MM-DD).")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (state.mode) {
                AuthMode.SIGN_IN -> {
                    repository.signIn(state.email, state.password).fold(
                        onSuccess = { session ->
                            val profile = repository.fetchMyProfile().getOrNull()
                            if (profile == null) {
                                hydrateMetadataIfMissing()
                            }
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isAuthenticated = true,
                                    currentUserId = session.userId,
                                    currentProfile = profile,
                                    needsOnboarding = profile == null || !profile.onboarded,
                                    authSheetOpen = false,
                                    authPromptReason = null,
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                        },
                    )
                }
                AuthMode.SIGN_UP -> {
                    repository.signUp(
                        email = state.email,
                        password = state.password,
                        birthDateIso = state.birthDateIso,
                        countryCode = state.selectedCountry.isoCode,
                    ).fold(
                        onSuccess = { session ->
                            val msg = if (!session.isAuthenticated) {
                                "Account created, but email confirmation is still enabled on this Supabase project. Disable 'Confirm email' in Supabase Auth settings for instant sign-in."
                            } else {
                                null
                            }
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isAuthenticated = session.isAuthenticated,
                                    currentUserId = session.userId,
                                    needsOnboarding = session.isAuthenticated,
                                    authSheetOpen = !session.isAuthenticated,
                                    infoMessage = msg,
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                        },
                    )
                }
                AuthMode.RECOVER -> {
                    repository.recoverPassword(state.email).fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    infoMessage = "Password reset link sent to ${state.email}. Open the link in your email to create a new password.",
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                        },
                    )
                }
                AuthMode.RESET_PASSWORD -> Unit
            }
        }
    }

    fun updateOnboardingFields(
        displayName: String = _uiState.value.displayName,
        username: String = _uiState.value.username,
        bio: String = _uiState.value.bio,
        headline: String = _uiState.value.headline,
    ) {
        _uiState.update {
            it.copy(
                displayName = displayName,
                username = username,
                bio = bio,
                headline = headline,
                usernameAvailable = if (username != it.username) null else it.usernameAvailable,
                errorMessage = null,
            )
        }
    }

    fun toggleSkill(skill: String) {
        _uiState.update { state ->
            val updated = state.selectedSkills.toMutableSet()
            if (!updated.add(skill)) updated.remove(skill)
            state.copy(selectedSkills = updated)
        }
    }

    fun toggleInterest(interest: String) {
        _uiState.update { state ->
            val updated = state.selectedInterests.toMutableSet()
            if (!updated.add(interest)) updated.remove(interest)
            state.copy(selectedInterests = updated)
        }
    }

    fun checkUsernameAvailability() {
        val candidate = _uiState.value.username.trim().lowercase()
        if (!candidate.matches(Regex("^[a-z0-9_.]{3,30}$"))) {
            _uiState.update {
                it.copy(
                    usernameAvailable = false,
                    errorMessage = "Username must be 3–30 chars (letters, numbers, _ or .).",
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(checkingUsername = true, errorMessage = null) }
            repository.checkUsernameAvailable(candidate).fold(
                onSuccess = { available ->
                    _uiState.update { it.copy(checkingUsername = false, usernameAvailable = available) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(checkingUsername = false, errorMessage = err.message) }
                },
            )
        }
    }

    fun completeOnboarding(avatarUrl: String? = null) {
        val state = _uiState.value
        val cleanUsername = state.username.trim().lowercase()
        if (state.displayName.isBlank() || cleanUsername.length < 3) {
            _uiState.update { it.copy(errorMessage = "Display name and valid username are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val session = repository.sessionStore.currentSession()
            val isMinor = if (state.birthDateIso.isNotBlank()) {
                AppConfig.isMinor16To17(state.birthDateIso)
            } else {
                true
            }
            val dto = ProfileUpsertDto(
                id = session.userId,
                username = cleanUsername,
                displayName = state.displayName.trim(),
                bio = state.bio.trim(),
                headline = state.headline.trim(),
                avatarUrl = avatarUrl,
                countryCode = state.selectedCountry.isoCode,
                regionLabel = state.selectedCountry.name,
                timezone = state.selectedCountry.defaultTimezone,
                birthDate = state.birthDateIso.ifBlank { null },
                isMinorSafeDefaults = isMinor,
                allowDmsFrom = if (isMinor) "following_only" else "everyone",
                onboarded = true,
                skills = state.selectedSkills.toList(),
                interests = state.selectedInterests.toList(),
            )
            repository.upsertProfile(dto).fold(
                onSuccess = { saved ->
                    if (state.selectedSkills.isNotEmpty()) {
                        repository.syncUserSkills(state.selectedSkills.toList())
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentProfile = saved,
                            needsOnboarding = false,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                },
            )
        }
    }
}
