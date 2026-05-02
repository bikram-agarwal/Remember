package dev.bikram.remember.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

@Composable
fun <T> reducedMotionAwareSpec(defaultSpec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> {
    val reducedMotion = LocalReducedMotion.current
    return if (reducedMotion) {
        tween(durationMillis = 0)
    } else {
        defaultSpec
    }
}

@Composable
fun reducedMotionEnterTransition(defaultTransition: EnterTransition): EnterTransition {
    val reducedMotion = LocalReducedMotion.current
    return if (reducedMotion) {
        EnterTransition.None
    } else {
        defaultTransition
    }
}

@Composable
fun reducedMotionExitTransition(defaultTransition: ExitTransition): ExitTransition {
    val reducedMotion = LocalReducedMotion.current
    return if (reducedMotion) {
        ExitTransition.None
    } else {
        defaultTransition
    }
}
