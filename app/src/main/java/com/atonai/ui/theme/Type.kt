package com.atonai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Full Material 3 type scale (DESIGN_TOKENS.md §3). Body line height ≈1.45,
 * code ≈1.38. System sans/mono families are used, so no font assets ship.
 */
private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val AtonTypography = Typography(
    displayLarge = sans(44, 52, FontWeight.SemiBold),
    displayMedium = sans(36, 44, FontWeight.SemiBold),
    displaySmall = sans(28, 34, FontWeight.SemiBold),

    headlineLarge = sans(26, 32, FontWeight.SemiBold),
    headlineMedium = sans(24, 30, FontWeight.SemiBold),
    headlineSmall = sans(21, 27, FontWeight.SemiBold),

    titleLarge = sans(22, 28, FontWeight.SemiBold),
    titleMedium = sans(17, 23, FontWeight.SemiBold),
    titleSmall = sans(15, 20, FontWeight.Medium),

    bodyLarge = sans(15, 22),
    bodyMedium = sans(14, 20),
    bodySmall = sans(13, 18),

    labelLarge = sans(14, 19, FontWeight.Medium),
    labelMedium = sans(12, 17, FontWeight.Medium),
    labelSmall = sans(11, 16, FontWeight.Medium),
)

/** Monospace style used by code blocks, diffs and the file viewer. */
val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)