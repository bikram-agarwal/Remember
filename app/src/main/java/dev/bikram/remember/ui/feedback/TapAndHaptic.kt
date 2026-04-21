package dev.bikram.remember.ui.feedback

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }
}

fun View.performSaveHaptic() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
