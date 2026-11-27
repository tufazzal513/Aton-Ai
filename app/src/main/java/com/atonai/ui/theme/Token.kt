package com.atonai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AtonAI palette.
 *
 * Values marked SAMPLED were read from the reference screenshots with
 * `img_mcp_image_analyze` (see workplace/plan/index.tsv and DESIGN_TOKENS.md).
 * Image analysis returns broad clusters, so component-level precision is not
 * claimed; ambiguous values are marked UNSURE in the docs rather than guessed
 * here. Nothing in this file comes from the extracted reference source.
 */
object Palette {
    // Brand blue. The reference UI clearly uses a blue accent (Sessions FAB,
    // dialog actions, segmented controls). The user states the main colour is
    // about #2F80FF; the image quantizer did not isolate it, so exactness is
    // UNSURE. The filled container below is a real sampled dark blue.
    val primary = Color(0xFF2F80FF)          // UNSURE exactness, user-stated blue
    val primaryDark = Color(0xFF1F6BE0)      // pressed/light-mode variant, UNSURE
    val primaryContainer = Color(0xFF0A2D63) // SAMPLED (10,45,99) deep blue
    val onPrimaryContainer = Color(0xFFE8F0FF)
    val accent = Color(0xFF2F80FF)

    // Dark neutrals — SAMPLED image clusters.
    val bgBaseDark = Color(0xFF060709)        // SAMPLED (6,7,9)
    val bgSurfaceDark = Color(0xFF11141B)     // SAMPLED (17,20,27)
    val bgSurfaceVariantDark = Color(0xFF191D26) // SAMPLED (25,29,38)
    val bgSurfaceHighDark = Color(0xFF0D0E12) // SAMPLED (13,14,18)

    val fgDefaultDark = Color(0xFFF3F3F5)     // SAMPLED near-white text
    val fgMutedDark = Color(0xFFA5A5A5)       // SAMPLED (165,165,165)
    val outlineDark = Color(0xFF282D3A)       // SAMPLED border cluster
    val outlineStrongDark = Color(0xFF394150) // SAMPLED stronger border (dialog)

    // Light neutrals — SAMPLED from the single light capture.
    val bgBaseLight = Color(0xFFEFF4FA)       // SAMPLED (239,244,250)
    val bgSurfaceLight = Color(0xFFF6F9FE)    // SAMPLED (246,249,254)
    val bgSurfaceVariantLight = Color(0xFFDAE6FC) // SAMPLED (218,230,252)
    val fgDefaultLight = Color(0xFF11141B)    // UNSURE (inverse of sampled dark)
    val fgMutedLight = Color(0xFF5B6472)      // UNSURE
    val outlineLight = Color(0xFFC6D3E4)      // UNSURE

    // State colours. Red sampled from the provider-limit notice screen.
    val error = Color(0xFF93000A)             // SAMPLED red tone
    val warn = Color(0xFFFFB020)              // UNSURE
    val ok = Color(0xFF37D67A)                // UNSURE

    val toolPending = fgMutedDark
    val toolRunning = primary
    val toolOk = ok
    val toolError = error
    val toolDenied = warn

    // Diff surfaces (not sampled; kept for patch rendering).
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

/** Spacing scale — use only these values. */
object Space {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

/** Corner radii and touch target. Geometry is estimated; see DESIGN_TOKENS.md. */
object Radius {
    val bubble: Dp = 16.dp
    val card: Dp = 14.dp
    val sheet: Dp = 24.dp
    val pill: Dp = 28.dp
    val minTouch: Dp = 48.dp
}

/** Motion durations. Streaming text never animates; only chrome does. */
object Motion {
    const val fast = 150
    const val medium = 260
    const val logo = 1200
}