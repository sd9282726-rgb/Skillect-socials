package com.example.ui.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.config.CountryOption
import com.example.core.network.CommunityDto
import com.example.core.network.AccountEnforcementDto
import com.example.core.network.ModerationAppealDto
import com.example.core.network.ProfileDto
import com.example.core.network.ProfileUpsertDto
import com.example.data.repository.SkillectRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
        uri.fragment?.split("&")?.forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                params[Uri.decode(parts[0])] = Uri.decode(parts[1])
            }
        }
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
    val isRestoringSession: Boolean = true,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val currentUserId: String = "",
    val needsOnboarding: Boolean = false,
    val currentProfile: ProfileDto? = null,
    val accountEnforcement: AccountEnforcementDto? = null,
    val accountAppeal: ModerationAppealDto? = null,
    val isCheckingAccountStatus: Boolean = false,
    val accountAppealSubmitting: Boolean = false,
    val authSheetOpen: Boolean = false,
    val authPromptReason: String? = null,
    val displayName: String = "",
    val username: String = "",
    val usernameAvailable: Boolean? = null,
    val checkingUsername: Boolean = false,
    val bio: String = "",
    val headline: String = "",
    val selectedSkills: Set<String> = emptySet(),
    val selectedInterests: Set<String> = emptySet(),
    val suggestedProfiles: List<ProfileDto> = emptyList(),
    val suggestedCommunities: List<CommunityDto> = emptyList(),
    val loadingSuggestions: Boolean = false,
)

class AuthViewModel(
    private val repository: SkillectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Restore the saved identity before waiting for any profile/network request.
        // This prevents a returning member from briefly landing in guest mode.
        viewModelScope.launch {
            repository.sessionFlow.distinctUntilChangedBy { it.userId }.collect { session ->
                val authenticated = session.isAuthenticated
                val cachedProfile = if (authenticated) {
                    repository.sessionStore.cachedProfile(session.userId)
                } else {
                    null
                }
                _uiState.update { state ->
                    state.copy(
                        isRestoringSession = false,
                        isAuthenticated = authenticated,
                        currentUserId = if (authenticated) session.userId else "",
                        currentProfile = if (authenticated) cachedProfile else null,
                        needsOnboarding = cachedProfile?.let {
                            AppConfig.isSupabaseConfigured && !it.onboarded
                        } ?: false,
                    )
                }

                if (authenticated) {
                    repository.realtimeClient.connect(session.userId)
                    repository.syncTelemetryAndPushToken(session.userId)
                    val (profile, enforcement) = coroutineScope {
                        val profileTask = async { repository.fetchMyProfile().getOrNull() }
                        val enforcementTask = async { repository.fetchMyEnforcement().getOrNull() }
                        profileTask.await() to enforcementTask.await()
                    }
                    if (profile != null) repository.sessionStore.saveCachedProfile(profile)
                    if (profile == null) hydrateMetadataIfMissing()
                    val latestAppeal = if (enforcement?.appealAllowed == true) {
                        repository.fetchModerationAppeals().getOrDefault(emptyList())
                            .firstOrNull { it.userId == session.userId }
                    } else null
                    _uiState.update { state ->
                        if (state.currentUserId != session.userId) state else {
                            val resolvedProfile = profile ?: state.currentProfile
                            state.copy(
                                currentProfile = resolvedProfile,
                                needsOnboarding = AppConfig.isSupabaseConfigured &&
                                    (resolvedProfile == null || !resolvedProfile.onboarded),
                                accountEnforcement = enforcement,
                                accountAppeal = latestAppeal,
                                isCheckingAccountStatus = false,
                                authSheetOpen = false,
                                authPromptReason = null,
                            )
                        }
                    }
                } else {
                    repository.sessionStore.clearCachedProfile()
                    _uiState.update {
                        it.copy(
                            currentProfile = null,
                            accountEnforcement = null,
                            accountAppeal = null,
                            isCheckingAccountStatus = false,
                            needsOnboarding = false,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.realtimeClient.events.collect { event ->
                if (event is com.example.core.network.RealtimeEvent.AccountStatusChanged) {
                    refreshAccountEnforcement()
                }
            }
        }
        // Refresh in the background; do not hold the first authenticated frame for it.
        viewModelScope.launch {
            repository.refreshSessionIfNeeded()
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
                ?: "This authentication link is invalid or has expired. Please request a new link."
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
        if (payload.type == "recovery") {
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
        } else if (!payload.type.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    mode = AuthMode.SIGN_IN,
                    authSheetOpen = true,
                    infoMessage = if (payload.type == "signup") {
                        "Email confirmation links are not enabled in this app yet. Skillect currently expects instant sign-in after registration."
                    } else {
                        "This authentication link type is not supported. Please sign in or request a password reset."
                    },
                    errorMessage = null,
                )
            }
        }
    }

    fun refreshAccountEnforcement() {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) return@launch
            repository.fetchMyEnforcement().fold(
                onSuccess = { enforcement ->
                    val appeal = if (enforcement?.appealAllowed == true) {
                        repository.fetchModerationAppeals().getOrDefault(emptyList())
                            .firstOrNull { it.userId == uid }
                    } else null
                    _uiState.update { it.copy(accountEnforcement = enforcement, accountAppeal = appeal, isCheckingAccountStatus = false) }
                },
                onFailure = { _uiState.update { it.copy(isCheckingAccountStatus = false) } },
            )
        }
    }

    fun submitAccountAppeal(reason: String) {
        if (_uiState.value.accountAppealSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(accountAppealSubmitting = true, errorMessage = null, infoMessage = null) }
            repository.submitModerationAppeal(reason).fold(
                onSuccess = { appeal ->
                    _uiState.update { it.copy(accountAppealSubmitting = false, accountAppeal = appeal, infoMessage = "Your appeal has been submitted for review.") }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(accountAppealSubmitting = false, errorMessage = error.message ?: "Your appeal could not be submitted.") }
                },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut().fold(
                onSuccess = {
                    _uiState.update {
                        AuthUiState(
                            isRestoringSession = false,
                            infoMessage = "You have been signed out.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Could not sign out.") }
                },
            )
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
        if (state.mode == AuthMode.SIGN_UP) {
            if (state.birthDateIso.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Select your date of birth (day, month and year). You must be 16 or older.")
                }
                return
            }
            if (!AppConfig.isEligibleAge16Plus(state.birthDateIso)) {
                _uiState.update {
                    it.copy(errorMessage = "Skillect requires users to be aged 16 or older.")
                }
                return
            }
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
                            } else null
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

    fun loadOnboardingSuggestions() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingSuggestions = true, errorMessage = null) }
            val interests = _uiState.value.selectedInterests.ifEmpty { _uiState.value.selectedSkills }
            val query = interests.firstOrNull().orEmpty()
            val profilesResult = if (query.isNotBlank()) {
                repository.searchProfiles(query)
            } else {
                repository.searchProfiles("a")
            }
            val communitiesResult = repository.fetchCommunities(
                category = interests.firstOrNull(),
                query = "",
            )
            val profiles = profilesResult.getOrElse { emptyList() }
                .filter { it.id != _uiState.value.currentUserId }
                .take(8)
            val communities = communitiesResult.getOrElse { emptyList() }.take(8)
            _uiState.update {
                it.copy(
                    loadingSuggestions = false,
                    suggestedProfiles = profiles,
                    suggestedCommunities = communities,
                    errorMessage = when {
                        profilesResult.isFailure && communitiesResult.isFailure ->
                            profilesResult.exceptionOrNull()?.message
                                ?: communitiesResult.exceptionOrNull()?.message
                        else -> null
                    },
                )
            }
        }
    }

    fun followSuggestedCreator(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            repository.toggleFollow(userId).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            suggestedProfiles = state.suggestedProfiles.filterNot { it.id == userId },
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                },
            )
        }
    }

    fun joinSuggestedCommunity(communityId: String) {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            repository.joinCommunity(communityId).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            suggestedCommunities = state.suggestedCommunities.filterNot { it.id == communityId },
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                },
            )
        }
    }
}
