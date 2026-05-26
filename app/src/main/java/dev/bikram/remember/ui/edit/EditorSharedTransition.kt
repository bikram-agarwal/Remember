package dev.bikram.remember.ui.edit

import androidx.compose.animation.BoundsTransform
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberEditorSharedBoundsModifier(noteId: Long?): Modifier {
    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedBoundsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Rect>())
    val sharedBoundsTransform = BoundsTransform { _, _ -> sharedBoundsSpec }
    return if (sharedScope != null && navScope != null && noteId != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note-card-$noteId"),
                animatedVisibilityScope = navScope,
                boundsTransform = sharedBoundsTransform,
            )
        }
    } else {
        Modifier
    }
}
