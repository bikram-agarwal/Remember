package dev.bikram.remember.update

import androidx.activity.ComponentActivity

/**
 * Play flavor launches Google Play In-App Review; GitHub flavor is a no-op.
 * [onFlowFinished] runs after request/launch completes, whether successful or not.
 */
fun interface AppReviewLauncher {
    fun tryLaunchInAppReview(
        activity: ComponentActivity,
        onFlowFinished: () -> Unit,
    )
}
