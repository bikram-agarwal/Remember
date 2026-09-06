package dev.bikram.remember.ui.nav

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

/**
 * One card <-> editor container transform for notes and lists, with or without cover art.
 * Keep the photo, scrim and text inside this layer so they share its scale, clip and fade.
 * The default ScaleToBounds preserves each endpoint's text layout during the morph.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.noteMorphContainer(noteId: Long?): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current
    val boundsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Rect>())
    val enterSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val exitSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    return if (sharedScope != null && navScope != null && noteId != null) {
        with(sharedScope) {
            this@noteMorphContainer.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note-card-$noteId"),
                animatedVisibilityScope = navScope,
                enter = fadeIn(animationSpec = enterSpec),
                exit = fadeOut(animationSpec = exitSpec),
                boundsTransform = BoundsTransform { _, _ -> boundsSpec },
                clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.medium),
            )
        }
    } else {
        this
    }
}
