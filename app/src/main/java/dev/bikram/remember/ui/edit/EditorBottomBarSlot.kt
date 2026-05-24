package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

private enum class EditorBottomSlot { Format, Action, None }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditorBottomBarSlot(
    isEditMode: Boolean,
    actionBarVisible: Boolean,
    actionContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    formatContent: (@Composable () -> Unit)? = null,
) {
    val bottomSlot: EditorBottomSlot =
        when {
            isEditMode && formatContent != null -> EditorBottomSlot.Format
            actionBarVisible -> EditorBottomSlot.Action
            else -> EditorBottomSlot.None
        }
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    AnimatedContent(
        targetState = bottomSlot,
        modifier = modifier,
        label = "EditorBottomBarSlot",
        transitionSpec = {
            (
                slideInVertically(animationSpec = spatialSpec) { it } +
                    fadeIn(animationSpec = fadeInSpec)
            ) togetherWith (
                slideOutVertically(animationSpec = spatialSpec) { it } +
                    fadeOut(animationSpec = fadeOutSpec)
            )
        },
    ) { currentSlot ->
        when (currentSlot) {
            EditorBottomSlot.Format -> formatContent?.invoke() ?: Box(Modifier.fillMaxWidth())
            EditorBottomSlot.Action -> actionContent()
            EditorBottomSlot.None -> Box(Modifier.fillMaxWidth())
        }
    }
}
