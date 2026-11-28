package com.example.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.core.config.ThemeMode

private val SkillectDarkColorScheme = darkColorScheme(
    primary = SkillectOrange,
    onPrimary = Color.White,
    primaryContainer = SkillectOrangeContainer,
    onPrimaryContainer = SkillectOrangeSoft,
    secondary = SkillectOrangeSoft,
    onSecondary = SkillectNearBlack,
    secondaryContainer = SkillectGraphiteElevated,
    onSecondaryContainer = SkillectWhite,
    tertiary = SkillectOrangeSoft,
    onTertiary = SkillectNearBlack,
    background = SkillectNearBlack,
    onBackground = SkillectWhite,
    surface = SkillectCharcoal,
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
    onError = Color.White,
)

private val SkillectLightColorScheme = lightColorScheme(
    primary = SkillectLightPrimary,
    onPrimary = Color.White,
    primaryContainer = SkillectLightPrimaryContainer,
    onPrimaryContainer = SkillectOrangeDim,
    secondary = SkillectOrange,
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
    surfaceContainerHigh = Color(0xFFDFDAD0),
    outline = SkillectLightBorder,
    outlineVariant = Color(0x14202228),
    error = SkillectCrimson,
    onError = Color.White,
)

val SkillectShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
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
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SkillectTypography,
        shapes = SkillectShapes,
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
            content()
        }
    }
}
