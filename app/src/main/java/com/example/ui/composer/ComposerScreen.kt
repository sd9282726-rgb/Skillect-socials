package com.example.ui.composer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.config.AppConfig
import com.example.core.designsystem.SkillChip
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectCard
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.core.media.MediaUploadManager
import com.example.core.media.UploadProgressState
import com.example.core.network.CreatePostRequest
import com.example.data.repository.SkillectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposerScreen(
    currentUserId: String,
    communityId: String?,
    repository: SkillectRepository,
    mediaUploadManager: MediaUploadManager,
    onPostCreated: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val postTypes = listOf(
        "text" to "Post",
        "long_discussion" to "Discussion",
        "project_update" to "Project",
        "professional_update" to "Career",
        "poll" to "Poll",
    )
    var selectedType by remember { mutableStateOf("text") }
    var content by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var linkTitle by remember { mutableStateOf("") }
    var skillInput by remember { mutableStateOf("") }
    var showLinkFields by remember { mutableStateOf(false) }

    val pollOptions = remember { mutableStateListOf("", "") }
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var videoStoragePath by remember { mutableStateOf<String?>(null) }
    var uploadState by remember { mutableStateOf<UploadProgressState>(UploadProgressState.Idle) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun startUploadForUris(uris: List<Uri>) {
        val uri = uris.firstOrNull() ?: return
        uploadJob?.cancel()
        videoStoragePath = null
        uploadJob = scope.launch {
            mediaUploadManager.uploadMedia(uri, "media-quarantine", currentUserId).collect { state ->
                uploadState = state
                if (state is UploadProgressState.Success) videoStoragePath = state.storagePath
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            uploadJob?.cancel()
            selectedUris.clear()
            selectedUris.add(uri)
            selectedType = "text"
            videoStoragePath = null
            uploadState = UploadProgressState.Idle
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            selectedUris.clear()
            selectedUris.add(uri)
            selectedType = "text"
            startUploadForUris(listOf(uri))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    SkillectBrandBadge(size = 30.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (communityId != null) "Create in community" else "Create a post",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (communityId != null) "Share with your community" else "Share ideas, work and moments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val isUploadBusy = uploadState is UploadProgressState.Validating || uploadState is UploadProgressState.Uploading
                SkillectPrimaryButton(
                    text = if (isUploadBusy) "Uploading..." else "Publish",
                    isLoading = isSubmitting,
                    shape = RoundedCornerShape(50),
                    enabled = !isUploadBusy && !isSubmitting && (content.isNotBlank() || selectedUris.isNotEmpty()),
                    modifier = Modifier.testTag("publish_post_button"),
                    onClick = {
                        if (currentUserId.isBlank()) {
                            errorMessage = "Please sign in with your Supabase credentials to publish."
                            return@SkillectPrimaryButton
                        }
                        val validPolls = if (selectedType == "poll") {
                            pollOptions.map { it.trim() }.filter { it.isNotBlank() }
                        } else {
                            emptyList()
                        }
                        if (selectedType == "poll" && validPolls.size < 2) {
                            errorMessage = "Polls must include at least 2 non-empty options."
                            return@SkillectPrimaryButton
                        }
                        val cleanLink = linkUrl.trim().takeIf { it.isNotBlank() }
                        if (cleanLink != null && !AppConfig.isValidHttpUrl(cleanLink)) {
                            errorMessage = "Link URL must be a valid http:// or https:// address."
                            return@SkillectPrimaryButton
                        }

                        scope.launch {
                            isSubmitting = true
                            errorMessage = null
                            val hashtags = Regex("#([A-Za-z0-9_]+)")
                                .findAll(content)
                                .map { it.groupValues[1].lowercase() }
                                .distinct()
                                .toList()
                            val mentions = Regex("@([A-Za-z0-9_.]+)")
                                .findAll(content)
                                .map { it.groupValues[1].lowercase() }
                                .distinct()
                                .toList()
                            val skills = skillInput.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }

                            val selectedUri = selectedUris.firstOrNull()
                            val selectedMime = selectedUri?.let { context.contentResolver.getType(it).orEmpty() }.orEmpty()
                            val mediaTags = (hashtags + skills).distinct().take(10)
                            val category = when {
                                selectedMime.startsWith("video/") && selectedType == "text" -> "video"
                                selectedMime.startsWith("image/") && selectedType == "text" -> "image"
                                showLinkFields && cleanLink != null && selectedType == "text" -> "link"
                                else -> selectedType
                            }

                            if (selectedMime.startsWith("video/")) {
                                val storagePath = videoStoragePath
                                if (storagePath.isNullOrBlank()) {
                                    isSubmitting = false
                                    errorMessage = "Wait for the secure video upload to finish before publishing."
                                    return@launch
                                }
                                repository.createVideoPost(storagePath, content.trim(), mediaTags, category, communityId).fold(
                                    onSuccess = {
                                        isSubmitting = false
                                        onPostCreated()
                                    },
                                    onFailure = { err ->
                                        isSubmitting = false
                                        errorMessage = err.message
                                    },
                                )
                            } else if (selectedMime.startsWith("image/") && selectedUri != null) {
                                mediaUploadManager.uploadImagePost(selectedUri, content.trim(), mediaTags, category, communityId).fold(
                                    onSuccess = {
                                        isSubmitting = false
                                        onPostCreated()
                                    },
                                    onFailure = { err ->
                                        isSubmitting = false
                                        errorMessage = err.message
                                    },
                                )
                            } else {
                                val req = CreatePostRequest(
                                    authorId = currentUserId,
                                    postType = selectedType,
                                    content = content.trim(),
                                    linkUrl = cleanLink,
                                    linkTitle = linkTitle.trim().takeIf { it.isNotBlank() },
                                    hashtags = hashtags,
                                    mentions = mentions,
                                    skillTags = skills,
                                    communityId = communityId,
                                )
                                repository.createPost(req, validPolls).fold(
                                    onSuccess = {
                                        isSubmitting = false
                                        onPostCreated()
                                    },
                                    onFailure = { err ->
                                        isSubmitting = false
                                        errorMessage = err.message
                                    },
                                )
                            }
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_community_banner),
                    contentDescription = "Skillect creative workspace",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.08f)),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        "CREATE SOMETHING MEANINGFUL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your next idea starts here.",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("Choose a format", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                postTypes.forEach { (key, label) ->
                    SkillChip(
                        label = label,
                        selected = selectedType == key,
                        onClick = { selectedType = key },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SkillectCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkillectAvatar(
                        avatarUrl = null,
                        contentDescription = "Your profile",
                        size = 42.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Your post", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Make it useful, personal or inspiring",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("What would you like to share?") },
                    minLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composer_content_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "${content.length}/${AppConfig.MAX_POST_LENGTH}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Add to your post", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = skillInput,
                    onValueChange = { skillInput = it },
                    label = { Text("Skill tags (comma-separated, e.g. Kotlin, UI Design)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkillectOutlinedButton(
                        text = "Photos",
                        leadingIcon = Icons.Rounded.Collections,
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                    SkillectOutlinedButton(
                        text = "Video",
                        leadingIcon = Icons.Rounded.VideoLibrary,
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                    )
                    IconButton(onClick = { showLinkFields = !showLinkFields }) {
                        Icon(Icons.Rounded.Link, contentDescription = "Attach link preview")
                    }
                    IconButton(onClick = { selectedType = "poll" }) {
                        Icon(Icons.Rounded.Poll, contentDescription = "Create poll")
                    }
                }

                ComposerMediaAttachmentPanel(
                    selectedUris = selectedUris.toList(),
                    uploadState = uploadState,
                    onRemove = {
                        uploadJob?.cancel()
                        selectedUris.clear()
                        videoStoragePath = null
                        uploadState = UploadProgressState.Idle
                    },
                    onCancelUpload = {
                        uploadJob?.cancel()
                        selectedUris.clear()
                        videoStoragePath = null
                        uploadState = UploadProgressState.Idle
                    },
                    onRetryUpload = { startUploadForUris(selectedUris.toList()) },
                )

                if (showLinkFields) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("Link URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = linkTitle,
                        onValueChange = { linkTitle = it },
                        label = { Text("Link preview title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (selectedType == "poll") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Poll Options", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    pollOptions.forEachIndexed { index, option ->
                        OutlinedTextField(
                            value = option,
                            onValueChange = { pollOptions[index] = it },
                            label = { Text("Option ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        )
                    }
                    if (pollOptions.size < 4) {
                        TextButton(onClick = { pollOptions.add("") }) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add poll option")
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
