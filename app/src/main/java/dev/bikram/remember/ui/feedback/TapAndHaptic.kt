package dev.bikram.remember.ui.feedback

import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTapSound = staticCompositionLocalOf<() -> Unit> { { } }
val LocalHapticEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberPlayTapSound(): () -> Unit = LocalTapSound.current

fun View.playTapSound() {
    if (isShown) {
        playSoundEffect(SoundEffectConstants.CLICK)
    }
}

/**
 * Heavier feedback for long-press actions (e.g. save attachment to Downloads).
 * Distinct from the swipe-threshold haptic on note cards.
 */
fun View.performLongPressHaptic() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
}

fun View.performSaveHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
}

fun View.performRejectHaptic() {
    performHapticFeedback(HapticFeedbackConstants.REJECT)
}
