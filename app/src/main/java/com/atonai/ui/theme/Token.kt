package com.atonai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AtonAI brand palette — our own, defined in 05_UI_MAP.md §11.
 * Named by role, not by hue, so themes can be swapped without touching UI code.
 */
object Palette {
    // brand
    val primary = Color(0xFF5B6BFF)
    val primaryDark = Color(0xFF3A44D6)
    val accent = Color(0xFF00BFA5)

    // dark neutrals (default)
    val bgBaseDark = Color(0xFF0F1116)
    val bgSurfaceDark = Color(0xFF171A21)
    val bgSurfaceVariantDark = Color(0xFF22262F)
    val fgDefaultDark = Color(0xFFE6E8EF)
    val fgMutedDark = Color(0xFF9BA3B4)
    val outlineDark = Color(0xFF2E3340)

    // light neutrals
    val bgBaseLight = Color(0xFFF7F8FB)
    val bgSurfaceLight = Color(0xFFFFFFFF)
    val bgSurfaceVariantLight = Color(0xFFEDEFF5)
    val fgDefaultLight = Color(0xFF1A1C22)
    val fgMutedLight = Color(0xFF5B6270)
    val outlineLight = Color(0xFFD5D9E2)

    // state
    val error = Color(0xFFFF6B6B)
    val warn = Color(0xFFFFB020)
    val ok = Color(0xFF37D67A)

    // diff surfaces (DESIGN_TOKENS.md — patch added/removed lines)
    val diffAddDark = Color(0xFF12301F)
    val diffAddLight = Color(0xFFE6F7EC)
    val diffDelDark = Color(0xFF3A1B1B)
    val diffDelLight = Color(0xFFFDECEC)
}

/** Line-level colours for patch/diff blocks, resolved for the active theme. */
object DiffColors {
    val add: Color
        @Composable get() = if (isSystemInDarkTheme()) Palette.diffAddDark else Palette.diffAddLight
    val del: Color
        @Composable get() = if (isSystemInDarkTheme()) Palette.diffDelDark else Palette.diffDelLight
}

/** Spacing scale — use only these values (05_UI_MAP.md §11). */
object Space {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

/** Corner radii and touch target (05_UI_MAP.md §11). */
object Radius {
    val bubble: Dp = 16.dp
    val card: Dp = 14.dp
    val sheet: Dp = 20.dp
    val minTouch: Dp = 48.dp
}

/** Motion durations. Streaming text never animates; only chrome does. */
object Motion {
    const val fast = 150
    const val medium = 260
    const val logo = 1200
}
