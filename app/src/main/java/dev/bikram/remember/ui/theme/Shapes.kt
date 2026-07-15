package dev.bikram.remember.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val pillShape = RoundedCornerShape(percent = 50)
val compactControlShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(24.dp),
        largeIncreased = RoundedCornerShape(22.dp),
        extraLargeIncreased = RoundedCornerShape(28.dp),
        extraExtraLarge = RoundedCornerShape(36.dp),
    )
