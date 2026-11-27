package com.atonai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.atonai.data.prefs.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Palette.primary,
    onPrimary = Color.White,
    primaryContainer = Palette.primaryContainer,
    onPrimaryContainer = Palette.onPrimaryContainer,
    secondary = Palette.accent,
    onSecondary = Color.Black,
    background = Palette.bgBaseDark,
    onBackground = Palette.fgDefaultDark,
    surface = Palette.bgSurfaceDark,
    onSurface = Palette.fgDefaultDark,
    surfaceVariant = Palette.bgSurfaceVariantDark,
    onSurfaceVariant = Palette.fgMutedDark,
    // Screenshot dialogs sit on a raised #191D26-like panel. UNSURE exactness.
    surfaceContainer = Palette.bgSurfaceVariantDark,
    surfaceContainerHigh = Palette.bgSurfaceDark,
    outline = Palette.outlineDark,
    outlineVariant = Palette.outlineStrongDark,
    error = Palette.error,
    onError = Color.White,
    tertiary = Palette.warn,
)

private val LightScheme = lightColorScheme(
    primary = Palette.primary,
    onPrimary = Color.White,
    primaryContainer = Palette.bgSurfaceVariantLight,
    onPrimaryContainer = Palette.fgDefaultLight,
    secondary = Palette.accent,
    background = Palette.bgBaseLight,
    onBackground = Palette.fgDefaultLight,
    surface = Palette.bgSurfaceLight,
    onSurface = Palette.fgDefaultLight,
    surfaceVariant = Palette.bgSurfaceVariantLight,
    onSurfaceVariant = Palette.fgMutedLight,
    outline = Palette.outlineLight,
    error = Palette.error,
    tertiary = Palette.warn,
)

/**
 * Resolves [mode] to a boolean dark flag, keeping system-follow as the default.
 */
@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AtonTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = mode.resolveDark()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AtonTypography,
        shapes = AtonShapes,
        content = content,
    )
}