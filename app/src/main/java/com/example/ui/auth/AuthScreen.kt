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
 * TEMPORARY compiling stub.
 * Full integration (64992 bytes) is verified in agent artifact:
 *   AuthScreen_onboarding.kt
 * Push with: export GITHUB_TOKEN=... ; ./push_authscreen.sh AuthScreen_onboarding.kt
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
            text = "AuthScreen full integration pending push. Use AuthScreen_onboarding.kt artifact.",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = onContinueGuestExplorer,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
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
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Onboarding full UI is in AuthScreen_onboarding.kt (not yet on this path).",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
