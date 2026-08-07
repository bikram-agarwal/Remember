package dev.bikram.remember.ui.feedback

import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

// Keep in parity with FilePipe's ui/feedback/Haptics.kt. Haptics are centralised here and gated on
// LocalHapticEnabled rather than called ad hoc per screen, so that (a) the user's haptics
// preference actually turns every buzz off, and (b) a newly long-pressable surface inherits the
// feedback instead of silently shipping without it.
val LocalHapticEnabled = staticCompositionLocalOf { true }

/**
 * Heavier double-click haptic used for long-press actions (multi-select, copy-to-clipboard).
 * Distinct from the swipe-threshold haptic so the user can feel the difference.
 */
fun View.performLongPressHaptic() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
}

/**
 * Haptic when a swipe passes the commit threshold. Uses a stronger [View.performHapticFeedback]
 * than a bare tick, plus a heavier vibrator pulse when hardware supports it.
 */
fun View.performSwipeThresholdHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
}

fun View.performRejectHaptic() {
    performHapticFeedback(HapticFeedbackConstants.REJECT)
}

/**
 * Fires [performSwipeThresholdHaptic] once each time [isBeyondThreshold] flips from false to true,
 * respecting the user's haptics preference. Every swipe surface uses this instead of writing its own
 * effect, so the two apps cannot drift.
 */
@Composable
fun SwipeThresholdHaptic(isBeyondThreshold: () -> Boolean) {
    val view = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    // The effect deliberately does not re-key on the predicate or the preference: restarting it would
    // reset the flip state mid-drag and re-fire. Read both through snapshots so the effect stays
    // installed AND current - the same stale-lambda hazard the gesture handlers guard against.
    val currentIsBeyondThreshold by rememberUpdatedState(isBeyondThreshold)
    val currentHapticEnabled by rememberUpdatedState(hapticEnabled)
    LaunchedEffect(view) {
        var wasBeyondThreshold = false
        snapshotFlow { currentIsBeyondThreshold() }.collect { isBeyond ->
            if (isBeyond && !wasBeyondThreshold && currentHapticEnabled) {
                view.performSwipeThresholdHaptic()
            }
            wasBeyondThreshold = isBeyond
        }
    }
}
