package dev.bikram.remember.ui.common

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable

@Composable
fun RememberPredictiveBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        progress.collect {
            // Collect progress so Android 14+ can drive predictive-back preview.
        }
        onBack()
    }
}
