package dev.bikram.remember.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.data.OnboardingState
import dev.bikram.remember.data.UpdatePreferencesState
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.update.AppReviewLauncher
import dev.bikram.remember.update.InAppRatingAutoPrompt
import kotlinx.coroutines.launch

/**
 * Play flavor: when eligible, requests Google's in-app review flow without a custom pre-prompt.
 */
@Composable
fun InAppRatingAutoPromptHost(
    onboardingState: OnboardingState,
    updateState: UpdatePreferencesState,
    activity: ComponentActivity,
    updatePrefs: UpdatePrefs,
    appReviewLauncher: AppReviewLauncher,
) {
    if (BuildConfig.FLAVOR != "playstore") return

    val context = LocalContext.current

    LaunchedEffect(
        onboardingState.hasSeenIntro,
        updateState.inAppReviewAutoNeverAskAgain,
        updateState.playAutoReviewPromptedForLastUpdateTime,
    ) {
        if (!onboardingState.hasSeenIntro) return@LaunchedEffect
        val lastUpdateMillis = InAppRatingAutoPrompt.packageLastUpdateTimeMillis(context)
        val nowMillis = System.currentTimeMillis()
        val debounceOk =
            nowMillis - InAppRatingAutoPrompt.SessionCoordination.lastInAppReviewAttemptWallClockMillis >=
                InAppRatingAutoPrompt.SessionCoordination.AUTO_VS_MANUAL_DEBOUNCE_MS
        val eligible =
            debounceOk &&
                InAppRatingAutoPrompt.isEligibleForAutoPrompt(
                    lastUpdateTimeMillis = lastUpdateMillis,
                    nowMillis = nowMillis,
                    neverAskAgain = updateState.inAppReviewAutoNeverAskAgain,
                    promptedForLastUpdateTimeMillis = updateState.playAutoReviewPromptedForLastUpdateTime,
                )
        if (!eligible) return@LaunchedEffect

        appReviewLauncher.tryLaunchInAppReview(activity) {
            activity.lifecycleScope.launch {
                updatePrefs.setPlayAutoReviewPromptedForLastUpdateTime(lastUpdateMillis)
            }
        }
    }
}
