package dev.bikram.remember.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorAdjustmentsTest {
    @Test
    fun material_you_guard_separates_similar_secondary_container() {
        val sharedContainer = Color(0xFFE0E0E0)
        val original =
            lightColorScheme(
                primary = Color(0xFF305DA8),
                primaryContainer = Color(0xFFD8E2FF),
                onPrimaryContainer = Color(0xFF001A41),
                secondaryContainer = sharedContainer,
                onSecondaryContainer = Color(0xFF1A1C20),
                surfaceContainerHighest = sharedContainer,
            )

        val adjusted = original.separateMaterialYouSecondaryContainerWhenNeeded(dark = false)

        assertTrue(colorsTooSimilar(original.secondaryContainer, original.surfaceContainerHighest))
        assertNotEquals(original.secondaryContainer, adjusted.secondaryContainer)
    }

    @Test
    fun material_you_guard_preserves_distinct_secondary_container() {
        val original =
            lightColorScheme(
                secondaryContainer = Color.Black,
                surfaceContainerHighest = Color.White,
            )

        val adjusted = original.separateMaterialYouSecondaryContainerWhenNeeded(dark = false)

        assertEquals(original, adjusted)
    }
}
