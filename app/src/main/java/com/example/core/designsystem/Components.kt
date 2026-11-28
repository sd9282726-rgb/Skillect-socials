package com.example.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R

val SkillectBrandGradient: Brush
    get() = Brush.linearGradient(
        colors = listOf(SkillectAccentAmber, SkillectAccentCoral, SkillectAccentViolet),
    )

val SkillectHorizontalBrandGradient: Brush
    get() = Brush.horizontalGradient(
        colors = listOf(SkillectAccentAmber, SkillectAccentCoral, SkillectVioletBright),
    )

@Composable
fun Modifier.skillectAtmosphericBackground(): Modifier {
    val bgColor = MaterialTheme.colorScheme.background
    val isDark = bgColor.luminance() < 0.5f
    val dotColor = if (isDark) Color.White.copy(alpha = 0.045f) else SkillectOrange.copy(alpha = 0.06f)
    val glowPrimary = SkillectOrange.copy(alpha = if (isDark) 0.15f else 0.075f)
    val glowSecondary = SkillectAccentAmber.copy(alpha = if (isDark) 0.055f else 0.035f)

    return this
        .background(bgColor)
        .drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowPrimary, Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.maxDimension * 0.65f,
                ),
                center = Offset(size.width * 0.8f, size.height * 0.2f),
                radius = size.maxDimension * 0.65f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowSecondary, Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.72f),
                    radius = size.maxDimension * 0.5f,
                ),
                center = Offset(size.width * 0.15f, size.height * 0.72f),
                radius = size.maxDimension * 0.5f,
            )
            val stepPx = 22.dp.toPx()
            val dotRadius = 1.dp.toPx()
            var y = stepPx * 0.5f
            while (y < size.height) {
                var x = stepPx * 0.5f
                while (x < size.width) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(x, y),
                    )
                    x += stepPx
                }
                y += stepPx
            }
        }
}

@Composable
fun SkillectBrandBadge(
    size: Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(size * 0.30f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = tileShape,
                ambientColor = SkillectOrange.copy(alpha = 0.34f),
                spotColor = SkillectOrangeDeep.copy(alpha = 0.30f),
            )
            .clip(tileShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(SkillectAccentAmber, SkillectOrange, SkillectOrangeDeep),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.13f)) {
            val scale = this.size.minDimension / 24f
            val white = Color.White
            drawRoundRect(
                color = white.copy(alpha = 0.96f),
                topLeft = Offset(4f * scale, 4f * scale),
                size = androidx.compose.ui.geometry.Size(9f * scale, 9f * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.8f * scale),
            )
            drawRoundRect(
                color = white.copy(alpha = 0.22f),
                topLeft = Offset(11f * scale, 11f * scale),
                size = androidx.compose.ui.geometry.Size(9f * scale, 9f * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.8f * scale),
            )
            drawRoundRect(
                color = white,
                topLeft = Offset(11f * scale, 11f * scale),
                size = androidx.compose.ui.geometry.Size(9f * scale, 9f * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.8f * scale),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f * scale),
            )
            drawCircle(
                color = white,
                radius = 2f * scale,
                center = Offset(17.5f * scale, 6.5f * scale),
            )
        }
    }
}

@Composable
fun SkillectGradientIconTile(
    icon: ImageVector,
    size: Dp = 74.dp,
    iconSize: Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = SkillectAccentCoral.copy(alpha = 0.45f),
                spotColor = SkillectViolet.copy(alpha = 0.5f),
            )
            .clip(RoundedCornerShape(22.dp))
            .background(SkillectBrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun SkillectCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = cardModifier,
        shape = shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SkillectAvatar(
    avatarUrl: String?,
    contentDescription: String,
    size: Dp = 44.dp,
    hasActiveStory: Boolean = false,
    showAddBadge: Boolean = false,
    showCameraBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val outerSize = if (hasActiveStory) size + 8.dp else size
    val clickableModifier = if (onClick != null) {
        modifier
            .size(outerSize)
            .clickable(onClick = onClick)
    } else {
        modifier.size(outerSize)
    }

    Box(modifier = clickableModifier, contentAlignment = Alignment.Center) {
        val frameModifier = if (hasActiveStory) {
            Modifier
                .size(outerSize)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = SkillectBrandGradient,
                    shape = CircleShape,
                )
                .padding(3.dp)
        } else {
            Modifier.size(size)
        }

        Box(modifier = frameModifier, contentAlignment = Alignment.Center) {
            if (avatarUrl.isNullOrBlank()) {
                Image(
                    painter = painterResource(id = R.drawable.img_avatar_placeholder),
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = contentDescription,
                    placeholder = painterResource(id = R.drawable.img_avatar_placeholder),
                    error = painterResource(id = R.drawable.img_avatar_placeholder),
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        if (showAddBadge || showCameraBadge) {
            val badgeSize = (size * 0.34f).coerceIn(20.dp, 30.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(
                        if (showCameraBadge) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.primary,
                    )
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (showCameraBadge) Icons.Rounded.PhotoCamera else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (showCameraBadge) MaterialTheme.colorScheme.background else Color.White,
                    modifier = Modifier.size(badgeSize * 0.56f),
                )
            }
        }
    }
}

@Composable
fun SkillectPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "primary_btn_scale",
    )

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SkillectOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "outlined_btn_scale",
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SkillectLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fieldModifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isNotBlank()) {
                {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
            } else null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = if (!supportingText.isNullOrBlank()) {
                { Text(supportingText) }
            } else null,
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = fieldModifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SkillChip(
    label: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    showCheckWhenSelected: Boolean = onClick != null,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else if (onClick != null) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "chip_bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "chip_fg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "chip_border",
    )

    Surface(
        modifier = if (onClick != null) modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected && showCheckWhenSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HashtagChip(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = if (tag.startsWith("#")) tag else "#$tag"
    Surface(
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Text(
            text = normalized,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun VerifiedCreatorBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.Verified,
        contentDescription = "Verified account",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(16.dp),
    )
}

@Composable
fun MinorSafeBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = SkillectEmerald.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, SkillectEmerald.copy(alpha = 0.36f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = SkillectEmerald,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Privacy-first defaults",
                style = MaterialTheme.typography.labelSmall,
                color = SkillectEmerald,
            )
        }
    }
}

@Composable
fun BackendConfigBanner(
    modifier: Modifier = Modifier,
    onOpenGuide: (() -> Unit)? = null,
) {
    SkillectCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("backend_config_banner"),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        onClick = onOpenGuide,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Supabase Project Credentials Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Set SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to connect live auth, feeds, storage, and realtime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SkeletonFeedCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .alpha(alpha)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .alpha(alpha)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkillectGradientIconTile(icon = icon)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(22.dp))
            SkillectPrimaryButton(
                text = actionLabel,
                onClick = onAction,
                shape = RoundedCornerShape(50),
            )
        }
    }
}

@Composable
fun ErrorRetryCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkillectCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SkillectOutlinedButton(
                text = "Retry",
                onClick = onRetry,
                leadingIcon = Icons.Rounded.Refresh,
            )
        }
    }
}

@Composable
fun SkillectSnackbarPill(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
