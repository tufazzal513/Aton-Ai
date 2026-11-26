package com.atonai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Named colour tokens kept for readability in previews; the live app reads
 * [Palette] through the Material theme, never these directly.
 */
val AtonIndigo = Palette.primary
val AtonIndigoDark = Palette.primaryDark
val AtonTeal = Palette.accent
val AtonBackground = Palette.bgBaseDark
val AtonSurface = Palette.bgSurfaceDark
val AtonSurfaceVariant = Palette.bgSurfaceVariantDark
val AtonOnSurface = Palette.fgDefaultDark
val AtonOnSurfaceVariant = Palette.fgMutedDark
val AtonOutline = Palette.outlineDark
val AtonError = Palette.error
val AtonWarn = Palette.warn
val AtonOk = Palette.ok

/** Status palette for tool cards, shared by the chat and permission UIs. */
object StatusColors {
    val pending: Color = Palette.fgMutedDark
    val running: Color = Palette.accent
    val ok: Color = Palette.ok
    val error: Color = Palette.error
    val denied: Color = Palette.warn
}
