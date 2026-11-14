package com.example.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.core.config.ThemeMode

private val SkillectDarkColorScheme = darkColorScheme(
    primary = SkillectVioletBright,
    onPrimary = SkillectWhite,
    primaryContainer = SkillectVioletContainer,
    onPrimaryContainer = SkillectLavender,
    secondary = SkillectPurple,
    onSecondary = SkillectNearBlack,
    secondaryContainer = SkillectGraphiteElevated,
    onSecondaryContainer = SkillectWhite,
    tertiary = SkillectLavender,
    onTertiary = SkillectNearBlack,
    background = SkillectNearBlack,
    onBackground = SkillectWhite,
    surface = SkillectNearBlack,
    onSurface = SkillectWhite,
    surfaceVariant = SkillectGraphite,
    onSurfaceVariant = SkillectMutedGrey,
    surfaceContainerLowest = SkillectNearBlack,
    surfaceContainerLow = SkillectCharcoal,
    surfaceContainer = SkillectCharcoal,
    surfaceContainerHigh = SkillectGraphite,
    outline = SkillectBorderDark,
    outlineVariant = SkillectHairlineDark,
    error = SkillectCrimson,
    onError = SkillectWhite,
)

private val SkillectLightColorScheme = lightColorScheme(
    primary = SkillectLightPrimary,
    onPrimary = Color.White,
    primaryContainer = SkillectLightPrimaryContainer,
    onPrimaryContainer = SkillectVioletDim,
    secondary = SkillectViolet,
    onSecondary = Color.White,
    secondaryContainer = SkillectLightCard,
    onSecondaryContainer = SkillectLightOnText,
    background = SkillectLightBg,
    onBackground = SkillectLightOnText,
    surface = SkillectLightSurface,
    onSurface = SkillectLightOnText,
    surfaceVariant = SkillectLightCard,
    onSurfaceVariant = SkillectLightMutedText,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SkillectLightBg,
    surfaceContainer = SkillectLightCard,
    surfaceContainerHigh = Color(0xFFE7E5F5),
    outline = SkillectLightBorder,
    outlineVariant = Color(0xFFECEBF6),
    error = SkillectCrimson,
    onError = Color.White,
)

val SkillectShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SkillectTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) SkillectDarkColorScheme else SkillectLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SkillectTypography,
        shapes = SkillectShapes,
        content = content,
    )
}
