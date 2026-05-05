package dev.bikram.remember.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.bikram.remember.ui.theme.LocalReducedMotion
import dev.bikram.remember.ui.theme.MorphPolygonShape
import dev.bikram.remember.ui.theme.RememberTheme
import dev.bikram.remember.ui.theme.RoundedPolygonShape

private const val ARTBOARD_W = 220f
private const val ARTBOARD_H = 220f
private const val PAD_W = 124f
private const val PAD_H = 160f

/**
 * Empty notes tab: portrait notepad with spiral binding on top, ruled lines, and a pen
 * angled diagonally with its nib pointing down-left into the page.
 *
 * The notepad is horizontally centered in the canvas; the pen is allowed to extend into
 * the upper-right empty space without shifting the visual center off the pad.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyNotesIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(width = 180.dp, height = 180.dp)) {
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 12.dp)
                    .size(72.dp),
            polygon = MaterialShapes.Clover4Leaf,
            morphTo = MaterialShapes.Cookie9Sided,
            color = scheme.tertiaryContainer.copy(alpha = 0.42f),
        )
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 10.dp)
                    .size(58.dp),
            polygon = MaterialShapes.Cookie9Sided,
            morphTo = MaterialShapes.Clover4Leaf,
            color = scheme.primaryContainer.copy(alpha = 0.46f),
        )
        Canvas(Modifier.matchParentSize()) {
            val scaleX = size.width / ARTBOARD_W
            val scaleY = size.height / ARTBOARD_H

            fun x(value: Float) = value * scaleX

            fun y(value: Float) = value * scaleY

            val padW = x(PAD_W)
            val padH = y(PAD_H)
            val padLeft = (size.width - padW) / 2f
            val padTop = y(34f)
            val padCorner = CornerRadius(x(10f), y(10f))
            val paperFill = scheme.surfaceContainerHigh
            val paperOutline = scheme.outline.copy(alpha = 0.5f)
            val ruleColor = scheme.onSurfaceVariant.copy(alpha = 0.25f)
            val bindBand = scheme.surfaceVariant
            val coilColor = scheme.outline.copy(alpha = 0.85f)

            drawRoundRect(
                color = scheme.scrim.copy(alpha = 0.10f),
                topLeft = Offset(padLeft + x(4f), padTop + y(7f)),
                size = Size(padW, padH),
                cornerRadius = padCorner,
                style = Fill,
            )

            drawRoundRect(
                color = paperFill,
                topLeft = Offset(padLeft, padTop),
                size = Size(padW, padH),
                cornerRadius = padCorner,
                style = Fill,
            )
            drawRoundRect(
                color = paperOutline,
                topLeft = Offset(padLeft, padTop),
                size = Size(padW, padH),
                cornerRadius = padCorner,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            val bindH = y(22f)
            drawRoundRect(
                color = bindBand,
                topLeft = Offset(padLeft, padTop),
                size = Size(padW, bindH),
                cornerRadius = padCorner,
                style = Fill,
            )
            drawLine(
                color = paperOutline,
                start = Offset(padLeft + x(2f), padTop + bindH),
                end = Offset(padLeft + padW - x(2f), padTop + bindH),
                strokeWidth = 1.dp.toPx(),
            )

            val coilCount = 6
            val coilSpacing = padW / (coilCount + 1)
            val coilRadius = y(4.5f)
            repeat(coilCount) { index ->
                val centerX = padLeft + coilSpacing * (index + 1)
                val centerY = padTop + bindH * 0.5f
                drawCircle(
                    color = coilColor,
                    radius = coilRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.6.dp.toPx()),
                )
            }

            val ruleLeft = padLeft + x(12f)
            val ruleRight = padLeft + padW - x(12f)
            val ruleStroke = 1.dp.toPx()
            var ruleY = padTop + bindH + y(20f)
            val ruleGap = y(18f)
            repeat(6) {
                drawLine(
                    color = ruleColor,
                    start = Offset(ruleLeft, ruleY),
                    end = Offset(ruleRight, ruleY),
                    strokeWidth = ruleStroke,
                    cap = StrokeCap.Round,
                )
                ruleY += ruleGap
            }

            val nibTip = Offset(padLeft + padW * 0.55f, padTop + padH * 0.62f)
            rotate(degrees = -38f, pivot = nibTip) {
                val nibLen = x(14f)
                val barrelLen = x(96f)
                val barrelThick = y(13f)
                val barrelLeftEdge = nibTip.x + nibLen
                val barrelTop = nibTip.y - barrelThick / 2f

                drawRoundRect(
                    color = scheme.scrim.copy(alpha = 0.18f),
                    topLeft = Offset(barrelLeftEdge + x(2f), barrelTop + y(3f)),
                    size = Size(barrelLen, barrelThick),
                    cornerRadius = CornerRadius(barrelThick / 2f, barrelThick / 2f),
                    style = Fill,
                )

                drawRoundRect(
                    color = scheme.primary,
                    topLeft = Offset(barrelLeftEdge, barrelTop),
                    size = Size(barrelLen, barrelThick),
                    cornerRadius = CornerRadius(barrelThick / 2f, barrelThick / 2f),
                    style = Fill,
                )

                val bandW = x(7f)
                drawRoundRect(
                    color = scheme.onPrimary.copy(alpha = 0.32f),
                    topLeft = Offset(barrelLeftEdge + x(10f), barrelTop),
                    size = Size(bandW, barrelThick),
                    cornerRadius = CornerRadius(y(1.5f), y(1.5f)),
                    style = Fill,
                )

                val capW = x(12f)
                drawRoundRect(
                    color = scheme.tertiary,
                    topLeft = Offset(barrelLeftEdge + barrelLen - capW, barrelTop),
                    size = Size(capW, barrelThick),
                    cornerRadius = CornerRadius(barrelThick / 2f, barrelThick / 2f),
                    style = Fill,
                )

                val nibPath =
                    Path().apply {
                        moveTo(barrelLeftEdge, barrelTop + y(1f))
                        lineTo(nibTip.x, nibTip.y)
                        lineTo(barrelLeftEdge, barrelTop + barrelThick - y(1f))
                        close()
                    }
                drawPath(nibPath, color = scheme.onSurface.copy(alpha = 0.92f), style = Fill)

                drawLine(
                    color = scheme.surface.copy(alpha = 0.5f),
                    start = Offset(barrelLeftEdge + x(1f), barrelTop + barrelThick / 2f),
                    end = Offset(nibTip.x + x(2f), nibTip.y),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Decorative backdrop that slowly morphs between [polygon] and [morphTo] on a 5-second
 * loop. Each empty illustration pairs its two backdrops so they trade silhouettes — the
 * page feels alive without distracting from the foreground artwork. Honors
 * [LocalReducedMotion]: when the user opts out, the shape stays fixed at [polygon].
 */
@Composable
private fun ExpressiveEmptyBackdrop(
    modifier: Modifier,
    polygon: RoundedPolygon,
    morphTo: RoundedPolygon,
    color: Color,
) {
    val reducedMotion = LocalReducedMotion.current
    val shape: Shape =
        if (reducedMotion) {
            remember(polygon) { RoundedPolygonShape(polygon) }
        } else {
            val morph = remember(polygon, morphTo) { Morph(polygon, morphTo) }
            val transition = rememberInfiniteTransition(label = "emptyBackdropMorph")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 2_500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "emptyBackdropMorphProgress",
            )
            MorphPolygonShape(morph, progress)
        }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(color),
    )
}

/**
 * Empty archive tab: a three-drawer filing cabinet. Reads as long-term storage rather
 * than a folder, so it pairs better with the "stays searchable, never auto-deleted"
 * subtitle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyArchiveIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(width = 132.dp, height = 132.dp)) {
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .size(58.dp),
            polygon = MaterialShapes.Cookie6Sided,
            morphTo = MaterialShapes.Clover4Leaf,
            color = scheme.primaryContainer.copy(alpha = 0.50f),
        )
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(48.dp),
            polygon = MaterialShapes.Clover4Leaf,
            morphTo = MaterialShapes.Cookie6Sided,
            color = scheme.secondaryContainer.copy(alpha = 0.56f),
        )
        Canvas(Modifier.matchParentSize()) {
            val scaleX = size.width / ARTBOARD_W
            val scaleY = size.height / ARTBOARD_H
            val strokeWidth = 1.4.dp.toPx()
            val outlineColor = scheme.outline.copy(alpha = 0.55f)
            val drawerSeam = scheme.outline.copy(alpha = 0.45f)
            val handleColor = scheme.onSecondaryContainer.copy(alpha = 0.72f)
            val cabinetFill = scheme.secondaryContainer

            // Cabinet body geometry - drawn first so the crown can sit visually on top.
            val cabLeft = 60f * scaleX
            val cabTop = 36f * scaleY
            val cabWidth = 100f * scaleX
            val cabHeight = 150f * scaleY
            val cabCorner = CornerRadius(7f * scaleX, 7f * scaleY)

            // Soft drop shadow under the body.
            translate(left = 4f * scaleX, top = 6f * scaleY) {
                drawRoundRect(
                    color = scheme.scrim.copy(alpha = 0.10f),
                    topLeft = Offset(cabLeft, cabTop),
                    size = Size(cabWidth, cabHeight),
                    cornerRadius = cabCorner,
                    style = Fill,
                )
            }

            // Crown plate - slightly wider than the body, visible at the very top.
            drawRoundRect(
                color = cabinetFill,
                topLeft = Offset(56f * scaleX, 28f * scaleY),
                size = Size(108f * scaleX, 8f * scaleY),
                cornerRadius = CornerRadius(3f * scaleX, 3f * scaleY),
                style = Fill,
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(56f * scaleX, 28f * scaleY),
                size = Size(108f * scaleX, 8f * scaleY),
                cornerRadius = CornerRadius(3f * scaleX, 3f * scaleY),
                style = Stroke(width = strokeWidth),
            )

            // Cabinet body.
            drawRoundRect(
                color = cabinetFill,
                topLeft = Offset(cabLeft, cabTop),
                size = Size(cabWidth, cabHeight),
                cornerRadius = cabCorner,
                style = Fill,
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(cabLeft, cabTop),
                size = Size(cabWidth, cabHeight),
                cornerRadius = cabCorner,
                style = Stroke(width = strokeWidth),
            )

            // Drawer seams - two horizontal lines splitting the body into three drawers.
            val drawerHeight = cabHeight / 3f
            drawLine(
                color = drawerSeam,
                start = Offset(cabLeft + 4f * scaleX, cabTop + drawerHeight),
                end = Offset(cabLeft + cabWidth - 4f * scaleX, cabTop + drawerHeight),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = drawerSeam,
                start = Offset(cabLeft + 4f * scaleX, cabTop + drawerHeight * 2f),
                end = Offset(cabLeft + cabWidth - 4f * scaleX, cabTop + drawerHeight * 2f),
                strokeWidth = strokeWidth,
            )

            // Drawer handles - one short pill horizontally centered in each drawer.
            val handleWidth = 30f * scaleX
            val handleHeight = 5f * scaleY
            val handleLeft = cabLeft + (cabWidth - handleWidth) / 2f
            val handleCorner = CornerRadius(handleHeight / 2f, handleHeight / 2f)
            for (index in 0..2) {
                val handleTop = cabTop + drawerHeight * (index + 0.5f) - handleHeight / 2f
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset(handleLeft, handleTop),
                    size = Size(handleWidth, handleHeight),
                    cornerRadius = handleCorner,
                    style = Fill,
                )
            }

            // Two short feet at the bottom edge - reinforce the "real cabinet" silhouette.
            val footY = cabTop + cabHeight + 2f * scaleY
            val footWidth = 14f * scaleX
            val footHeight = 5f * scaleY
            val footCorner = CornerRadius(2f * scaleX, 2f * scaleY)
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(cabLeft + 12f * scaleX, footY),
                size = Size(footWidth, footHeight),
                cornerRadius = footCorner,
                style = Fill,
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(cabLeft + cabWidth - 12f * scaleX - footWidth, footY),
                size = Size(footWidth, footHeight),
                cornerRadius = footCorner,
                style = Fill,
            )
        }
    }
}

/**
 * Empty trash tab: a clean, empty bin (lid + handle, slight taper, subtle ribs) with three
 * 4-point sparkles around it to read as "freshly emptied" rather than "trash sitting here".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyTrashIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(width = 132.dp, height = 132.dp)) {
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(56.dp),
            polygon = MaterialShapes.Sunny,
            morphTo = MaterialShapes.Cookie9Sided,
            color = scheme.errorContainer.copy(alpha = 0.42f),
        )
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .size(46.dp),
            polygon = MaterialShapes.Cookie9Sided,
            morphTo = MaterialShapes.Sunny,
            color = scheme.tertiaryContainer.copy(alpha = 0.52f),
        )
        Canvas(Modifier.matchParentSize()) {
            val scaleX = size.width / ARTBOARD_W
            val scaleY = size.height / ARTBOARD_H
            val strokeWidth = 1.4.dp.toPx()
            val outlineColor = scheme.outline.copy(alpha = 0.55f)
            val ribColor = scheme.onSurfaceVariant.copy(alpha = 0.30f)
            val shadowColor = scheme.scrim.copy(alpha = 0.10f)

            // Bin body - wide and slightly squat, with a small inward taper toward the bottom.
            // Top opening 116 wide, base 100 wide, height 126 (about 0.92 width-to-height
            // ratio so it reads as a real trash can rather than a tall mailing tube).
            val binPath =
                Path().apply {
                    moveTo(52f * scaleX, 66f * scaleY)
                    lineTo(168f * scaleX, 66f * scaleY)
                    lineTo(162f * scaleX, 184f * scaleY)
                    quadraticTo(160f * scaleX, 192f * scaleY, 154f * scaleX, 192f * scaleY)
                    lineTo(66f * scaleX, 192f * scaleY)
                    quadraticTo(60f * scaleX, 192f * scaleY, 58f * scaleX, 184f * scaleY)
                    close()
                }
            translate(left = 4f * scaleX, top = 6f * scaleY) {
                drawPath(binPath, color = shadowColor, style = Fill)
            }
            drawPath(binPath, color = scheme.surfaceContainerHigh, style = Fill)
            drawPath(binPath, color = outlineColor, style = Stroke(width = strokeWidth))

            // Three subtle vertical ribs to give the bin volume - track the taper.
            drawLine(
                color = ribColor,
                start = Offset(86f * scaleX, 82f * scaleY),
                end = Offset(88f * scaleX, 180f * scaleY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ribColor,
                start = Offset(110f * scaleX, 80f * scaleY),
                end = Offset(110f * scaleX, 182f * scaleY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ribColor,
                start = Offset(134f * scaleX, 82f * scaleY),
                end = Offset(132f * scaleX, 180f * scaleY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            // Lid sits flush on top of the bin opening, ~10 units wider on each side.
            drawRoundRect(
                color = scheme.errorContainer.copy(alpha = 0.78f),
                topLeft = Offset(44f * scaleX, 52f * scaleY),
                size = Size(132f * scaleX, 14f * scaleY),
                cornerRadius = CornerRadius(7f * scaleX, 7f * scaleY),
                style = Fill,
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(44f * scaleX, 52f * scaleY),
                size = Size(132f * scaleX, 14f * scaleY),
                cornerRadius = CornerRadius(7f * scaleX, 7f * scaleY),
                style = Stroke(width = strokeWidth),
            )

            // Lid handle - small pill centered on top of the lid.
            drawRoundRect(
                color = scheme.onErrorContainer.copy(alpha = 0.55f),
                topLeft = Offset(98f * scaleX, 42f * scaleY),
                size = Size(24f * scaleX, 10f * scaleY),
                cornerRadius = CornerRadius(5f * scaleX, 5f * scaleY),
                style = Fill,
            )

            // Sparkles - 4-pointed twinkles signalling "freshly cleaned". Tucked into the
            // corners outside the lid so they read as motion rather than overlapping the bin.
            drawSparkle(Offset(192f * scaleX, 56f * scaleY), 9f * scaleX, scheme.tertiary)
            drawSparkle(Offset(28f * scaleX, 78f * scaleY), 6f * scaleX, scheme.primary.copy(alpha = 0.78f))
            drawSparkle(Offset(200f * scaleX, 138f * scaleY), 5f * scaleX, scheme.tertiary.copy(alpha = 0.70f))
        }
    }
}

/**
 * Draws a 4-pointed sparkle (concave-sided diamond) centred at [center] with arms of
 * length [radius]. Used by the trash empty state.
 */
private fun DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val waist = radius * 0.32f
    val sparkle =
        Path().apply {
            moveTo(center.x, center.y - radius)
            quadraticTo(center.x + waist, center.y - waist, center.x + radius, center.y)
            quadraticTo(center.x + waist, center.y + waist, center.x, center.y + radius)
            quadraticTo(center.x - waist, center.y + waist, center.x - radius, center.y)
            quadraticTo(center.x - waist, center.y - waist, center.x, center.y - radius)
            close()
        }
    drawPath(sparkle, color = color, style = Fill)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyFilterIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(width = 132.dp, height = 132.dp)) {
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .size(56.dp),
            polygon = MaterialShapes.Clover4Leaf,
            morphTo = MaterialShapes.Cookie9Sided,
            color = scheme.tertiaryContainer.copy(alpha = 0.56f),
        )
        ExpressiveEmptyBackdrop(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(44.dp),
            polygon = MaterialShapes.Cookie9Sided,
            morphTo = MaterialShapes.Clover4Leaf,
            color = scheme.primaryContainer.copy(alpha = 0.58f),
        )
        Canvas(Modifier.matchParentSize()) {
            val scaleX = size.width / ARTBOARD_W
            val scaleY = size.height / ARTBOARD_H
            val strokeWidth = 1.4.dp.toPx()
            val outlineColor = scheme.outline.copy(alpha = 0.55f)
            val quietLineColor = scheme.onSurfaceVariant.copy(alpha = 0.28f)

            drawRoundRect(
                color = scheme.scrim.copy(alpha = 0.11f),
                topLeft = Offset(45f * scaleX, 47f * scaleY),
                size = Size(120f * scaleX, 120f * scaleY),
                cornerRadius = CornerRadius(18f * scaleX, 18f * scaleY),
                style = Fill,
            )
            drawRoundRect(
                color = scheme.surfaceContainerHigh,
                topLeft = Offset(40f * scaleX, 42f * scaleY),
                size = Size(120f * scaleX, 120f * scaleY),
                cornerRadius = CornerRadius(18f * scaleX, 18f * scaleY),
                style = Fill,
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(40f * scaleX, 42f * scaleY),
                size = Size(120f * scaleX, 120f * scaleY),
                cornerRadius = CornerRadius(18f * scaleX, 18f * scaleY),
                style = Stroke(width = strokeWidth),
            )

            drawRoundRect(
                color = scheme.primaryContainer,
                topLeft = Offset(58f * scaleX, 62f * scaleY),
                size = Size(62f * scaleX, 18f * scaleY),
                cornerRadius = CornerRadius(9f * scaleX, 9f * scaleY),
                style = Fill,
            )
            drawRoundRect(
                color = scheme.tertiaryContainer,
                topLeft = Offset(58f * scaleX, 91f * scaleY),
                size = Size(80f * scaleX, 18f * scaleY),
                cornerRadius = CornerRadius(9f * scaleX, 9f * scaleY),
                style = Fill,
            )
            drawRoundRect(
                color = scheme.surfaceVariant,
                topLeft = Offset(58f * scaleX, 120f * scaleY),
                size = Size(52f * scaleX, 18f * scaleY),
                cornerRadius = CornerRadius(9f * scaleX, 9f * scaleY),
                style = Fill,
            )
            drawCircle(
                color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
                radius = 4f * scaleX,
                center = Offset(75f * scaleX, 71f * scaleY),
                style = Fill,
            )
            drawCircle(
                color = scheme.onTertiaryContainer.copy(alpha = 0.68f),
                radius = 4f * scaleX,
                center = Offset(122f * scaleX, 100f * scaleY),
                style = Fill,
            )
            drawCircle(
                color = scheme.onSurfaceVariant.copy(alpha = 0.54f),
                radius = 4f * scaleX,
                center = Offset(87f * scaleX, 129f * scaleY),
                style = Fill,
            )

            drawCircle(
                color = scheme.surface.copy(alpha = 0.78f),
                radius = 33f * scaleX,
                center = Offset(131f * scaleX, 129f * scaleY),
                style = Fill,
            )
            drawCircle(
                color = scheme.onSurface.copy(alpha = 0.86f),
                radius = 34f * scaleX,
                center = Offset(131f * scaleX, 129f * scaleY),
                style = Stroke(width = 4.dp.toPx()),
            )
            drawLine(
                color = scheme.onSurface.copy(alpha = 0.86f),
                start = Offset(154f * scaleX, 153f * scaleY),
                end = Offset(179f * scaleX, 178f * scaleY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = quietLineColor,
                start = Offset(116f * scaleX, 129f * scaleY),
                end = Offset(146f * scaleX, 129f * scaleY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 240, heightDp = 260, name = "Empty notes illustration")
@Composable
private fun EmptyNotesIllustrationPreview() {
    RememberTheme {
        Surface {
            Box(Modifier.padding(24.dp)) {
                EmptyNotesIllustration()
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 180, name = "Secondary empty illustrations")
@Composable
private fun SecondaryEmptyIllustrationsPreview() {
    RememberTheme {
        Surface {
            androidx.compose.foundation.layout.Row(Modifier.padding(24.dp)) {
                EmptyArchiveIllustration()
                EmptyTrashIllustration()
                EmptyFilterIllustration()
            }
        }
    }
}
