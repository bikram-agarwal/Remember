package dev.bikram.remember.ui.common

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun noteMosaicColumnCountUsesWidthBreakpoints() {
        assertEquals(1, noteMosaicColumnCount(320.dp))
        assertEquals(2, noteMosaicColumnCount(340.dp))
        assertEquals(2, noteMosaicColumnCount(559.dp))
        assertEquals(2, noteMosaicColumnCount(760.dp))
        assertEquals(2, noteMosaicColumnCount(959.dp))
        assertEquals(3, noteMosaicColumnCount(960.dp))
        assertEquals(3, noteMosaicColumnCount(1279.dp))
        assertEquals(4, noteMosaicColumnCount(1280.dp))
    }
}
