package com.example.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.core.designsystem.SkillectOutlinedButton

@Composable
fun PasswordResetScreen(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkillectBrandBadge(size = 44.dp)
            Text(stringResource(R.string.password_reset_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.password_reset_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.newPassword,
                onValueChange = viewModel::updateNewPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password_reset_new)) },
                placeholder = { Text("At least 8 characters") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.confirmNewPassword,
                onValueChange = viewModel::updateConfirmNewPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password_reset_confirm)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            uiState.infoMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            SkillectPrimaryButton(
                text = stringResource(R.string.password_reset_update),
                onClick = viewModel::submitAuth,
                enabled = uiState.newPassword.length >= 8 &&
                    uiState.newPassword == uiState.confirmNewPassword &&
                    !uiState.isLoading,
                isLoading = uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            SkillectOutlinedButton(
                text = stringResource(R.string.password_reset_back),
                onClick = { viewModel.setMode(AuthMode.SIGN_IN) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
