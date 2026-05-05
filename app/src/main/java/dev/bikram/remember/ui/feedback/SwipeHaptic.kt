package dev.bikram.remember.ui.feedback

import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

fun View.performSwipeThresholdHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
}
