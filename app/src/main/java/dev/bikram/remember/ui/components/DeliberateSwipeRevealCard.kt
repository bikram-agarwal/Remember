package dev.bikram.remember.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.feedback.performSwipeThresholdHaptic
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SwipeRailCornerOverdraw = 32.dp

/**
 * Horizontal swipe with an explicit commit threshold (fraction of card width).
 */
@Composable
fun DeliberateSwipeRevealCard(
    commitThresholdFraction: Float,
    cardShape: Shape,
    onSwipeStartToEnd: () -> Unit,
    onSwipeEndToStart: () -> Unit,
    hapticEnabled: Boolean,
    backgroundContent: @Composable BoxScope.(draggingFromStart: Boolean, revealProgress: Float) -> Unit,
    modifier: Modifier = Modifier,
    allowSwipeStartToEnd: Boolean = true,
    allowSwipeEndToStart: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var laidOutWidthPx by remember { mutableFloatStateOf(0f) }
    val swipeSettleSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())

    BoxWithConstraints(modifier = modifier.clip(cardShape)) {
        val constraintWidthPx =
            if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat().coerceAtLeast(1f) else 0f
        val widthPx =
            when {
                laidOutWidthPx > 0f -> laidOutWidthPx
                constraintWidthPx > 0f -> constraintWidthPx
                else -> 0f
            }
        val dragClampPx = if (widthPx > 0f) widthPx else 10_000f
        val thresholdPx =
            if (widthPx > 0f) widthPx * commitThresholdFraction else Float.POSITIVE_INFINITY

        LaunchedEffect(hapticEnabled, widthPx, commitThresholdFraction) {
            var previousBeyond = false
            snapshotFlow {
                widthPx > 0f && abs(offsetX) >= thresholdPx
            }.collect { beyond ->
                if (beyond && !previousBeyond && hapticEnabled) {
                    view.performSwipeThresholdHaptic()
                }
                previousBeyond = beyond
            }
        }

        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    laidOutWidthPx = size.width.toFloat()
                },
        ) { layoutConstraints ->
            val foregroundMeasurable =
                subcompose("foreground") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .pointerInput(
                                dragClampPx,
                                thresholdPx,
                                allowSwipeStartToEnd,
                                allowSwipeEndToStart,
                            ) {
                                val minOffset = if (allowSwipeEndToStart) -dragClampPx else 0f
                                val maxOffset = if (allowSwipeStartToEnd) dragClampPx else 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        offsetX = (offsetX + dragAmount).coerceIn(minOffset, maxOffset)
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            when {
                                                allowSwipeStartToEnd && offsetX >= thresholdPx -> {
                                                    onSwipeStartToEnd()
                                                    offsetX = 0f
                                                }
                                                allowSwipeEndToStart && offsetX <= -thresholdPx -> {
                                                    onSwipeEndToStart()
                                                    offsetX = 0f
                                                }
                                                else -> {
                                                    val start = offsetX
                                                    val anim = Animatable(start)
                                                    anim.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = swipeSettleSpec,
                                                    ) {
                                                        offsetX = value
                                                    }
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        content()
                    }
                }.first()
            val foregroundPlaceable = foregroundMeasurable.measure(layoutConstraints)
            val cardWidth = foregroundPlaceable.width
            val cardHeight = foregroundPlaceable.height
            val fixed = Constraints.fixed(cardWidth, cardHeight)
            val backgroundMeasurable =
                subcompose("background") {
                    val revealProgress =
                        if (thresholdPx.isFinite() && thresholdPx > 0f) {
                            (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    Box(Modifier.fillMaxSize()) {
                        when {
                            offsetX > 4f -> backgroundContent(true, revealProgress)
                            offsetX < -4f -> backgroundContent(false, revealProgress)
                        }
                    }
                }.first()
            val backgroundPlaceable = backgroundMeasurable.measure(fixed)
            layout(cardWidth, cardHeight) {
                backgroundPlaceable.place(0, 0)
                foregroundPlaceable.place(0, 0)
            }
        }
    }
}

data class SwipeRevealTile(
    val key: String,
    @param:androidx.annotation.StringRes val labelRes: Int,
    val symbolName: String,
    val backgroundColor: Color,
    val contentColor: Color,
    val filled: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Horizontal swipe that opens a capped action rail. Releasing the card never executes an
 * action; users tap one of the revealed tiles.
 */
@Composable
fun MultiActionSwipeRevealCard(
    startActions: List<SwipeRevealTile>,
    endActions: List<SwipeRevealTile>,
    cardShape: Shape,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
    maxRevealWidth: Dp = 240.dp,
    revealKey: Any? = null,
    activeRevealKey: Any? = null,
    onRevealStarted: (() -> Unit)? = null,
    onRevealClosed: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var laidOutWidthPx by remember { mutableFloatStateOf(0f) }
    val swipeSettleSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val maxRevealWidthPx =
        with(androidx.compose.ui.platform.LocalDensity.current) {
            maxRevealWidth.toPx()
        }

    BoxWithConstraints(modifier = modifier.clip(cardShape)) {
        val constraintWidthPx =
            if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat().coerceAtLeast(1f) else 0f
        val widthPx =
            when {
                laidOutWidthPx > 0f -> laidOutWidthPx
                constraintWidthPx > 0f -> constraintWidthPx
                else -> 0f
            }
        val startRevealPx =
            if (startActions.isEmpty()) {
                0f
            } else {
                maxRevealWidthPx.coerceAtMost(widthPx)
            }
        val endRevealPx =
            if (endActions.isEmpty()) {
                0f
            } else {
                maxRevealWidthPx.coerceAtMost(widthPx)
            }
        val openThresholdPx = (maxRevealWidthPx * 0.22f).coerceAtLeast(36f)

        LaunchedEffect(hapticEnabled, startRevealPx, endRevealPx) {
            var previousBeyond = false
            snapshotFlow {
                val beyondStart = startRevealPx > 0f && offsetX >= openThresholdPx
                val beyondEnd = endRevealPx > 0f && offsetX <= -openThresholdPx
                beyondStart || beyondEnd
            }.collect { beyond ->
                if (beyond && !previousBeyond && hapticEnabled) {
                    view.performSwipeThresholdHaptic()
                }
                previousBeyond = beyond
            }
        }

        LaunchedEffect(activeRevealKey, revealKey) {
            if (activeRevealKey != revealKey && offsetX != 0f) {
                val anim = Animatable(offsetX)
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = swipeSettleSpec,
                ) {
                    offsetX = value
                }
            }
        }

        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    laidOutWidthPx = size.width.toFloat()
                },
        ) { layoutConstraints ->
            val foregroundMeasurable =
                subcompose("foreground") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .pointerInput(startRevealPx, endRevealPx, openThresholdPx) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        onRevealStarted?.invoke()
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        offsetX = (offsetX + dragAmount).coerceIn(-endRevealPx, startRevealPx)
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            val target =
                                                when {
                                                    offsetX >= openThresholdPx && startRevealPx > 0f -> startRevealPx
                                                    offsetX <= -openThresholdPx && endRevealPx > 0f -> -endRevealPx
                                                    else -> 0f
                                                }
                                            if (target == 0f) {
                                                onRevealClosed?.invoke()
                                            } else {
                                                onRevealStarted?.invoke()
                                            }
                                            val anim = Animatable(offsetX)
                                            anim.animateTo(
                                                targetValue = target,
                                                animationSpec = swipeSettleSpec,
                                            ) {
                                                offsetX = value
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        content()
                    }
                }.first()
            val foregroundPlaceable = foregroundMeasurable.measure(layoutConstraints)
            val cardWidth = foregroundPlaceable.width
            val cardHeight = foregroundPlaceable.height
            val fixed = Constraints.fixed(cardWidth, cardHeight)
            val backgroundMeasurable =
                subcompose("background") {
                    val revealProgress = (abs(offsetX) / maxRevealWidthPx).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxSize()) {
                        if (offsetX > 4f && startActions.isNotEmpty()) {
                            SwipeActionRail(
                                actions = startActions,
                                revealWidth = maxRevealWidth,
                                revealProgress = revealProgress,
                                alignToStart = true,
                                onActionClick = {
                                    scope.launch {
                                        offsetX = 0f
                                        onRevealClosed?.invoke()
                                    }
                                },
                            )
                        } else if (offsetX < -4f && endActions.isNotEmpty()) {
                            SwipeActionRail(
                                actions = endActions,
                                revealWidth = maxRevealWidth,
                                revealProgress = revealProgress,
                                alignToStart = false,
                                onActionClick = {
                                    scope.launch {
                                        offsetX = 0f
                                        onRevealClosed?.invoke()
                                    }
                                },
                            )
                        }
                    }
                }.first()
            val backgroundPlaceable = backgroundMeasurable.measure(fixed)
            layout(cardWidth, cardHeight) {
                backgroundPlaceable.place(0, 0)
                foregroundPlaceable.place(0, 0)
            }
        }
    }
}

@Composable
private fun BoxScope.SwipeActionRail(
    actions: List<SwipeRevealTile>,
    revealWidth: Dp,
    revealProgress: Float,
    alignToStart: Boolean,
    onActionClick: () -> Unit,
) {
    val nearestCardColor =
        if (alignToStart) {
            actions.last().backgroundColor
        } else {
            actions.first().backgroundColor
        }
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(revealWidth + SwipeRailCornerOverdraw)
                .align(if (alignToStart) Alignment.CenterStart else Alignment.CenterEnd),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(SwipeRailCornerOverdraw)
                    .align(if (alignToStart) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(nearestCardColor),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(revealWidth)
                    .align(if (alignToStart) Alignment.CenterStart else Alignment.CenterEnd),
            horizontalArrangement = Arrangement.Start,
        ) {
            actions.forEach { action ->
                SwipeActionTile(
                    action = action,
                    revealProgress = revealProgress,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    onActionClick = onActionClick,
                )
            }
        }
    }
}

@Composable
private fun SwipeActionTile(
    action: SwipeRevealTile,
    revealProgress: Float,
    modifier: Modifier,
    onActionClick: () -> Unit,
) {
    val tileScale = 0.88f + 0.12f * revealProgress
    Box(
        modifier =
            modifier
                .background(action.backgroundColor)
                .tapSoundClickable {
                    onActionClick()
                    action.onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier.graphicsLayer {
                    alpha = revealProgress
                    scaleX = tileScale
                    scaleY = tileScale
                },
        ) {
            RememberMaterialRoundedSymbol(
                name = action.symbolName,
                filled = action.filled,
                size = 22.dp,
                tint = action.contentColor,
                weight = FontWeight.Medium,
            )
            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = action.contentColor,
                maxLines = 1,
            )
        }
    }
}
