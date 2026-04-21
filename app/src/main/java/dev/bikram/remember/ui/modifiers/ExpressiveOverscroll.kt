@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.bikram.remember.ui.modifiers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Translation-based overscroll effect whose release uses the Material 3 Expressive
 * spatial spring, replacing the OEM stretch overscroll that snaps too abruptly on
 * the note / list editors.
 *
 * The pull is damped (scrollable-delta * [pullDamping]) so long drags don't fly the
 * content off the top/bottom, and the release is driven by
 * [androidx.compose.material3.MotionScheme.slowSpatialSpec] so the content settles
 * smoothly into place rather than cracking back. Drawn as a Y translation on the
 * wrapping modifier, so there's no RenderNode stretch fighting our spring.
 *
 * Wire-up: pass the effect to both the scroll feeder and the visual wrapper - e.g.
 * `Modifier.overscroll(effect).verticalScroll(scrollState, overscrollEffect = effect)`.
 * Wrap the scrolling column with `clipToBounds` to keep the translation from
 * painting outside the content frame during the bounce.
 */
@Composable
fun rememberExpressiveOverscrollEffect(
    pullDamping: Float = 0.45f,
): OverscrollEffect {
    val scope = rememberCoroutineScope()
    val releaseSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    return remember(scope, releaseSpec, pullDamping) {
        ExpressiveOverscrollEffect(scope, releaseSpec, pullDamping)
    }
}

private class ExpressiveOverscrollEffect(
    private val scope: CoroutineScope,
    private val releaseSpec: AnimationSpec<Float>,
    private val pullDamping: Float,
) : OverscrollEffect {
    // Raw offset state feeds the graphicsLayer translation every frame. We keep it
    // as a plain mutableFloatStateOf because snapTo on an Animatable is suspend -
    // we don't want to hop a coroutine on every single scroll delta.
    private val offsetState = mutableFloatStateOf(0f)

    // Tracks the currently-running spring release (if any) so a new user drag can
    // cancel it cleanly instead of fighting it.
    private var releaseJob: Job? = null

    override val isInProgress: Boolean
        get() = offsetState.floatValue != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // Any fresh scroll input cancels an in-flight spring release so we don't
        // double-animate when the user grabs the content mid-bounce.
        releaseJob?.cancel()
        releaseJob = null

        val current = offsetState.floatValue
        val dy = delta.y

        // Reducing existing overscroll: consume into the offset first, then pass any
        // remainder through so the scrollable keeps moving.
        if (current != 0f && sign(current) != sign(dy)) {
            val toZero = -current
            val absorbed = if (abs(dy) <= abs(toZero)) dy else toZero
            offsetState.floatValue = current + absorbed
            val remainder = dy - absorbed
            if (remainder != 0f) {
                val childConsumed = performScroll(Offset(0f, remainder))
                return Offset(0f, absorbed + childConsumed.y)
            }
            return Offset(0f, absorbed)
        }

        // Normal path: let the scrollable consume first. Anything it can't consume
        // becomes overscroll stretch (only for direct user drags - we don't stretch
        // on programmatic or nested scroll side-effects).
        val consumed = performScroll(delta)
        val remaining = dy - consumed.y
        if (remaining != 0f && source == NestedScrollSource.UserInput) {
            offsetState.floatValue = current + remaining * pullDamping
            return delta
        }
        return consumed
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        // Let the scrollable consume the fling against its own edges.
        performFling(velocity)
        // Settle any remaining stretch with the M3 Expressive spatial spring.
        if (offsetState.floatValue != 0f) {
            val releaseAnim = Animatable(offsetState.floatValue)
            releaseJob = scope.launch {
                releaseAnim.animateTo(0f, animationSpec = releaseSpec) {
                    offsetState.floatValue = value
                }
            }
            releaseJob?.join()
            releaseJob = null
        }
    }

    // Foundation 1.8+ replaced `effectModifier` with `node: DelegatableNode`. We
    // provide a LayoutModifierNode that measures the child and places it inside a
    // graphics layer whose translationY tracks [offsetState]. Reads of the state
    // inside the layer block are snapshot-observed, so Compose invalidates the
    // layer (not the whole measure pass) when the offset changes - cheaper than
    // triggering a re-measure each frame.
    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeRelativeWithLayer(0, 0) {
                    translationY = offsetState.floatValue
                }
            }
        }
    }
}
