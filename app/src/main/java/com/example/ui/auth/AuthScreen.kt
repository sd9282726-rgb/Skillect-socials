package com.example.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.media.MediaUploadManager

/**
 * TEMPORARY stub — full integration is in docs/onboarding/AuthScreen.part{1,2,3}.kt
 * and agent artifact AuthScreen_onboarding.kt. Run scripts/assemble_authscreen.sh to install.
 */
@Composable
fun AuthScreen(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
    onContinueGuestExplorer: () -> Unit,
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Auth UI update in progress on fix/onboarding. Assemble from docs/onboarding parts or use AuthScreen_onboarding.kt artifact.",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onContinueGuestExplorer, modifier = Modifier.align(Alignment.BottomCenter)) {
            Text("Continue as guest")
        }
    }
}

@Composable
fun OnboardingScreen(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
    mediaUploadManager: MediaUploadManager,
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Onboarding full integration: run scripts/assemble_authscreen.sh after parts are present.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
