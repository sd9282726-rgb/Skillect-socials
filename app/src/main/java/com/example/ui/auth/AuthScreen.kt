package com.example.ui.auth

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.config.AppConfig
import com.example.core.designsystem.BackendConfigBanner
import com.example.core.designsystem.MinorSafeBadge
import com.example.core.designsystem.SkillChip
import com.example.core.designsystem.SkillectAccentAmber
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectCard
import com.example.core.designsystem.SkillectEmerald
import com.example.core.designsystem.SkillectGradientIconTile
import com.example.core.designsystem.SkillectHorizontalBrandGradient
import com.example.core.designsystem.SkillectLabeledField
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.core.designsystem.skillectAtmosphericBackground
import com.example.core.media.MediaUploadManager
import com.example.core.media.UploadProgressState
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
    onContinueGuestExplorer: () -> Unit,
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var countryMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .skillectAtmosphericBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            // Top Brand Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkillectBrandBadge(size = 34.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "skillect",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            contentDescription = "Toggle theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onContinueGuestExplorer,
                        modifier = Modifier.testTag("auth_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Continue as guest",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Hero Headline with Gradient Second Line
            Text(
                text = "Show what",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "you can do.",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    brush = SkillectHorizontalBrandGradient,
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Connect, share projects, discover creators, and grow across every skill worldwide.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!uiState.authPromptReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                SkillectCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guest_restriction_prompt"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(
                        text = "Account required",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = uiState.authPromptReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (!AppConfig.isSupabaseConfigured) {
                Spacer(modifier = Modifier.height(14.dp))
                BackendConfigBanner()
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Action Card
            SkillectCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                contentPadding = PaddingValues(20.dp),
            ) {
                if (uiState.mode != AuthMode.RESET_PASSWORD && uiState.mode != AuthMode.VERIFY_RECOVERY_OTP) {
                    // Pill Segmented Mode Selector
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(
                                AuthMode.SIGN_IN to "Sign In",
                                AuthMode.SIGN_UP to "Sign Up",
                                AuthMode.RECOVER to "Recover",
                            ).forEach { (mode, label) ->
                                val selected = uiState.mode == mode
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setMode(mode) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                } else if (uiState.mode == AuthMode.RESET_PASSWORD) {
                    Text("Set a New Password", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose a strong password (8+ characters) for your Skillect account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    Text("Enter your 6-digit code", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "We sent a password reset code to ${uiState.email}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (uiState.mode == AuthMode.RESET_PASSWORD) {
                    SkillectLabeledField(
                        label = "New password",
                        value = uiState.newPassword,
                        onValueChange = viewModel::updateNewPassword,
                        placeholder = "At least 8 characters",
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        fieldModifier = Modifier.testTag("reset_new_password_input"),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SkillectLabeledField(
                        label = "Confirm new password",
                        value = uiState.confirmNewPassword,
                        onValueChange = viewModel::updateConfirmNewPassword,
                        placeholder = "Re-enter new password",
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        fieldModifier = Modifier.testTag("reset_confirm_password_input"),
                    )
                } else if (uiState.mode == AuthMode.VERIFY_RECOVERY_OTP) {
                    SkillectLabeledField(
                        label = "6-digit verification code",
                        value = uiState.recoveryToken,
                        onValueChange = viewModel::updateRecoveryToken,
                        placeholder = "123456",
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        fieldModifier = Modifier.testTag("recovery_otp_input"),
                    )
                } else {
                    SkillectLabeledField(
                        label = "Email address",
                        value = uiState.email,
                        onValueChange = viewModel::updateEmail,
                        placeholder = "you@example.com",
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        fieldModifier = Modifier.testTag("auth_email_input"),
                    )
                }

                if (uiState.mode == AuthMode.SIGN_IN || uiState.mode == AuthMode.SIGN_UP) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SkillectLabeledField(
                        label = "Password",
                        value = uiState.password,
                        onValueChange = viewModel::updatePassword,
                        placeholder = "8+ characters",
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        fieldModifier = Modifier.testTag("auth_password_input"),
                    )
                }

                if (uiState.mode == AuthMode.SIGN_IN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { viewModel.setMode(AuthMode.RECOVER) },
                            modifier = Modifier.testTag("forgot_password_button"),
                        ) {
                            Text("Forgot password?")
                        }
                    }
                }

                if (uiState.mode == AuthMode.SIGN_UP) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SignUpDobCountrySection(
                        birthDateIso = uiState.birthDateIso,
                        selectedCountry = uiState.selectedCountry,
                        onBirthDateIsoChange = viewModel::updateBirthDate,
                        onCountryChange = viewModel::updateCountry,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MinorSafeBadge()
                }

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("auth_error_text"),
                    )
                }

                if (uiState.infoMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.infoMessage,
                        color = SkillectEmerald,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("auth_info_text"),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                SkillectPrimaryButton(
                    text = when (uiState.mode) {
                        AuthMode.SIGN_IN -> "Sign In to Skillect"
                        AuthMode.SIGN_UP -> "Create Account"
                        AuthMode.RECOVER -> "Send Reset Code"
                        AuthMode.VERIFY_RECOVERY_OTP -> "Verify Code"
                        AuthMode.RESET_PASSWORD -> "Update Password"
                    },
                    onClick = viewModel::submitAuth,
                    isLoading = uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_submit_button"),
                )

                if (uiState.mode == AuthMode.VERIFY_RECOVERY_OTP || uiState.mode == AuthMode.RESET_PASSWORD) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.setMode(AuthMode.RECOVER) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Need a new code? Change email or resend")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // OR Divider matching welcome card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "OR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onContinueGuestExplorer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_workspace_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = SkillectAccentAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue browsing as Guest",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A global community to learn, share your work, and grow together.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private data class OnboardingGoalItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
    mediaUploadManager: MediaUploadManager,
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(1) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var localPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var uploadMessage by remember { mutableStateOf<String?>(null) }
    var skillSearch by remember { mutableStateOf("") }
    var localStepError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentStep > 1) {
        localStepError = null
        currentStep = (currentStep - 1).coerceAtLeast(1)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val uid = (uiState.currentProfile?.id ?: uiState.currentUserId).trim()
            if (uid.isBlank()) {
                uploadMessage = "Sign in required to upload an avatar."
                return@rememberLauncherForActivityResult
            }
            localPreviewUri = uri
            scope.launch {
                mediaUploadManager.uploadMedia(uri, "avatars", uid).collect { state ->
                    when (state) {
                        is UploadProgressState.Uploading -> uploadMessage = "Uploading avatar ${(state.progress * 100).toInt()}%"
                        is UploadProgressState.Success -> {
                            avatarUrl = state.publicUrl
                            uploadMessage = "Avatar uploaded"
                        }
                        is UploadProgressState.Failed -> uploadMessage = state.message
                        else -> Unit
                    }
                }
            }
        }
    }

    val categorizedSkills = OnboardingSkillCatalog.categories

    val onboardingGoals = remember {
        listOf(
            OnboardingGoalItem("Technology", "Show my work", "Build a portfolio of projects, code, and prototypes people discover", Icons.Rounded.AutoAwesome),
            OnboardingGoalItem("Education", "Learn new skills", "Connect with mentors, creators, and peers across disciplines", Icons.Rounded.School),
            OnboardingGoalItem("Entrepreneurship", "Collaborate", "Team up on builds, launches, hackathons, and ventures", Icons.Rounded.RocketLaunch),
            OnboardingGoalItem("Design", "Visual & Design Craft", "Explore typography, interfaces, photography, and digital art", Icons.Rounded.Brush),
            OnboardingGoalItem("Creative Arts", "Media & Storytelling", "Share short videos, filmmaking, writing, and audio", Icons.Rounded.MovieCreation),
            OnboardingGoalItem("Science", "Research & Discovery", "Discuss science, engineering, and open knowledge globally", Icons.Rounded.Science),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .skillectAtmosphericBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize(),
        ) {
            // Segmented 4-Step Progress Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentStep > 1 && currentStep < 5) {
                    IconButton(
                        onClick = {
                            localStepError = null
                            currentStep--
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous step",
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkillectBrandBadge(size = 32.dp)
                        Spacer(modifier = Modifier.width(9.dp))
                        Column {
                            Text(
                                text = "skillect",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.4).sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "YOUR NEXT CHAPTER",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (stepIdx in 1..5) {
                        val targetFill = if (stepIdx <= currentStep) 1f else 0f
                        val fillFraction by animateFloatAsState(
                            targetValue = targetFill,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "step_progress_$stepIdx",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fillFraction)
                                    .clip(RoundedCornerShape(50))
                                    .background(SkillectHorizontalBrandGradient),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onToggleTheme, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = "Toggle theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "onboarding_step_transition",
            ) { step ->
                when (step) {
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                        ) {
                            Text(
                                text = "Step 1 of 5",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Set up your profile",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This is how creators and peers worldwide discover you on Skillect.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(modifier = Modifier.height(22.dp))

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                SkillectAvatar(
                                    avatarUrl = localPreviewUri?.toString() ?: avatarUrl,
                                    contentDescription = "Profile avatar preview",
                                    size = 92.dp,
                                    hasActiveStory = true,
                                    showCameraBadge = true,
                                    onClick = {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                )
                            }
                            if (uploadMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uploadMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            SkillectLabeledField(
                                label = "Display name",
                                value = uiState.displayName,
                                onValueChange = {
                                    localStepError = null
                                    viewModel.updateOnboardingFields(displayName = it)
                                },
                                placeholder = "Your name or creator alias",
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Spacer(modifier = Modifier.height(14.dp))
                            SkillectLabeledField(
                                label = "Headline (optional)",
                                value = uiState.headline,
                                onValueChange = { viewModel.updateOnboardingFields(headline = it) },
                                placeholder = "e.g. Mobile Engineer & Visual Storyteller",
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            SkillectLabeledField(
                                label = "Bio",
                                value = uiState.bio,
                                onValueChange = { viewModel.updateOnboardingFields(bio = it) },
                                placeholder = "What are you building, creating, or learning?",
                                singleLine = false,
                                minLines = 3,
                            )

                            val step1Err = localStepError ?: uiState.errorMessage
                            if (step1Err != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = step1Err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Spacer(modifier = Modifier.height(22.dp))
                            SkillectPrimaryButton(
                                text = "Continue",
                                onClick = {
                                    if (uiState.displayName.isBlank()) {
                                        localStepError = "Enter your display name to continue."
                                    } else {
                                        localStepError = null
                                        currentStep = 2
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    2 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = "Step 2 of 5",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "What are you into?",
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pick your skills and craft areas. We'll tailor your feed and creators.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = skillSearch,
                                    onValueChange = { skillSearch = it },
                                    placeholder = { Text("Search skills like Kotlin or Photography") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(50),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                categorizedSkills.forEach { (category, skills) ->
                                    val filtered = if (skillSearch.isBlank()) skills
                                    else skills.filter { it.contains(skillSearch, ignoreCase = true) }

                                    if (filtered.isNotEmpty()) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            filtered.forEach { skill ->
                                                SkillChip(
                                                    label = skill,
                                                    selected = skill in uiState.selectedSkills,
                                                    onClick = { viewModel.toggleSkill(skill) },
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }

                            SkillectPrimaryButton(
                                text = if (uiState.selectedSkills.isEmpty()) "Continue"
                                else "Continue (${uiState.selectedSkills.size} selected)",
                                onClick = { currentStep = 3 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }

                    3 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = "Step 3 of 5",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "What brings you here?",
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Choose as many as you like.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(18.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    onboardingGoals.forEach { goal ->
                                        val selected = goal.key in uiState.selectedInterests
                                        val borderColor by animateColorAsState(
                                            targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            label = "goal_border",
                                        )
                                        val iconBg by animateColorAsState(
                                            targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            label = "goal_icon_bg",
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .clickable { viewModel.toggleInterest(goal.key) },
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                            ) {
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Top,
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(CircleShape)
                                                                .background(iconBg),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(
                                                                imageVector = goal.icon,
                                                                contentDescription = null,
                                                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                        if (selected) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.primary),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Check,
                                                                    contentDescription = "Selected",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(15.dp),
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(14.dp))
                                                    Text(
                                                        text = goal.title,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = goal.subtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            SkillectPrimaryButton(
                                text = "Continue",
                                onClick = { currentStep = 4 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }

                    4 -> {
                        LaunchedEffect(step) {
                            viewModel.loadOnboardingSuggestions()
                        }
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        ) {
                            Text(
                                text = "Step 4 of 5",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Find your people",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Discover creators and communities related to your interests. Connecting and joining are optional.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("CREATORS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (uiState.loadingSuggestions) {
                                    Text("Finding creators and communities…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (uiState.suggestedProfiles.isEmpty() && !uiState.loadingSuggestions) {
                                    Text("No creator suggestions yet. You can skip this step and explore later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                uiState.suggestedProfiles.forEach { profile ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(profile.displayName.ifBlank { "Skillect Member" }, style = MaterialTheme.typography.titleSmall)
                                                if (profile.bio.isNotBlank()) Text(profile.bio, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            TextButton(onClick = { viewModel.followSuggestedCreator(profile.id) }) { Text("Connect") }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("COMMUNITIES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (uiState.suggestedCommunities.isEmpty() && !uiState.loadingSuggestions) {
                                    Text("No community suggestions yet. More communities will appear as the network grows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                uiState.suggestedCommunities.forEach { community ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(community.name, style = MaterialTheme.typography.titleSmall)
                                                Text(community.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (community.description.isNotBlank()) Text(community.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            TextButton(onClick = { viewModel.joinSuggestedCommunity(community.id) }) { Text("Join") }
                                        }
                                    }
                                }
                                if (uiState.errorMessage != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            SkillectPrimaryButton(
                                text = "Continue",
                                onClick = { currentStep = 5 },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                            TextButton(onClick = { currentStep = 5 }, modifier = Modifier.fillMaxWidth()) {
                                Text("Skip for now")
                            }
                        }
                    }

                    else -> {
                        val firstName = uiState.displayName.trim().split(" ").firstOrNull().orEmpty()
                        val tunedTopics = (uiState.selectedSkills + uiState.selectedInterests).take(3)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SkillectGradientIconTile(
                                icon = Icons.Rounded.Celebration,
                                size = 84.dp,
                                iconSize = 38.dp,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = if (firstName.isNotBlank()) "You're in, $firstName!" else "You're in!",
                                style = MaterialTheme.typography.displaySmall,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (tunedTopics.isNotEmpty()) {
                                    "Your Skillect profile and feed are tuned to ${tunedTopics.joinToString(", ")} and more."
                                } else {
                                    "Your global Skillect identity is ready. Discover creators, communities, and projects."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )

                            if (uiState.errorMessage != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = uiState.errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))
                            SkillectPrimaryButton(
                                text = "Start exploring",
                                onClick = { viewModel.completeOnboarding(avatarUrl) },
                                isLoading = uiState.isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { currentStep = 1 }) {
                                Text("Review profile details")
                            }
                        }
                    }
                }
            }
        }
    }
}
