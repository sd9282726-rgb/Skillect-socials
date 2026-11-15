package com.example.ui.composer

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.media.UploadProgressState

@Composable
fun ComposerMediaAttachmentPanel(
    selectedUris: List<Uri>,
    uploadState: UploadProgressState,
    onRemove: () -> Unit,
    onCancelUpload: () -> Unit,
    onRetryUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedUris.isEmpty() && uploadState is UploadProgressState.Idle) return

    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (selectedUris.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val firstUri = selectedUris.first()
                        val isVideo = context.contentResolver.getType(firstUri)?.startsWith("video/") == true
                        Text(
                            text = if (isVideo) "Video attachment" else "Photo attachment",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(onClick = onRemove) { Text("Remove") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(selectedUris) { _, uri ->
                            val isVideoItem = context.contentResolver.getType(uri)?.startsWith("video/") == true
                            if (isVideoItem) {
                                Box(
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Rounded.VideoLibrary,
                                            contentDescription = "Selected video",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Video",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected photo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }
                    Text(
                        text = if (context.contentResolver.getType(selectedUris.first())?.startsWith("video/") == true)
                            "Videos upload securely before publishing. Keep this screen open until the progress finishes."
                        else
                            "Photos are screened and published when you tap Publish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        when (val state = uploadState) {
            is UploadProgressState.Validating -> {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Validating & compressing ${state.fileName}...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is UploadProgressState.Uploading -> {
                Spacer(modifier = Modifier.height(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Uploading ${state.fileName} · ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCancelUpload) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "Cancel upload")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            is UploadProgressState.Failed -> {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRetryUpload) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry upload")
                    }
                }
            }
            is UploadProgressState.Success -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Video uploaded securely. Ready to publish.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            UploadProgressState.Idle -> Unit
        }
    }
}
