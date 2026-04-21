package com.jian.nemo.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3 shape scale for Nemo 2.0
 */
val NemoShapes = Shapes(
    // Extra Small - Used for small components like chips
    extraSmall = RoundedCornerShape(4.dp),

    // Small - Used for buttons and small cards
    small = RoundedCornerShape(8.dp),

    // Medium - Used for cards and dialogs
    medium = RoundedCornerShape(12.dp),

    // Large - Used for large cards and bottom sheets
    large = RoundedCornerShape(16.dp),

    // Extra Large - Used for modal bottom sheets
    extraLarge = RoundedCornerShape(28.dp)
)
