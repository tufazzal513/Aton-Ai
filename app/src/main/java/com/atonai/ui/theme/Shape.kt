package com.atonai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner shapes for every Material 3 slot (DESIGN_TOKENS.md §4). Named roles
 * come from [Radius] so component code and theme never disagree.
 */
val AtonShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.bubble),
    extraLarge = RoundedCornerShape(Radius.sheet),
)

/** Elevation steps used by cards, sheets and the app bar. */
object Elevation {
    val flat = 0.dp
    val raised = 1.dp
    val card = 2.dp
    val sheet = 3.dp
    val dialog = 6.dp
}
