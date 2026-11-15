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
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import kotlinx.coroutines.delay

@Composable
fun AccountRestrictedScreen(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
) {
    var explanation by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshAccountEnforcement()
        while (true) {
            delay(15_000)
            viewModel.refreshAccountEnforcement()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EmptyStateCard(
                icon = Icons.Rounded.Gavel,
                title = "Account temporarily restricted",
                subtitle = uiState.accountEnforcement?.reason?.takeIf { it.isNotBlank() }
                    ?: "Your account access has been restricted following a safety review. You can review the decision and submit an appeal below.",
            )

            if (uiState.isCheckingAccountStatus) {
                CircularProgressIndicator()
            }

            val appeal = uiState.accountAppeal
            if (appeal != null) {
                Text("Appeal status  ${appeal.status.replace('_', ' ')}", style = MaterialTheme.typography.titleMedium)
                Text(appeal.reason, style = MaterialTheme.typography.bodyMedium)
                appeal.reviewerNote?.takeIf { it.isNotBlank() }?.let {
                    Text("Review update  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "This page checks for updates automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.accountEnforcement?.appealAllowed == true) {
                Text("Request a review", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Explain why you believe this decision should be reviewed. Include the relevant context so the moderation team can assess it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text("Your explanation") },
                    supportingText = { Text("${explanation.trim().length} of 20 minimum characters") },
                )
                SkillectPrimaryButton(
                    text = if (uiState.accountAppealSubmitting) "Submitting" else "Submit appeal",
                    onClick = { viewModel.submitAccountAppeal(explanation) },
                    enabled = explanation.trim().length >= 20 && !uiState.accountAppealSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.accountAppealSubmitting,
                )
            } else {
                Text(
                    "Appeals are not available for this restriction. Contact Skillect support if you believe this is an error.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            uiState.infoMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Spacer(Modifier.height(4.dp))
            SkillectOutlinedButton(
                text = "Sign out",
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
