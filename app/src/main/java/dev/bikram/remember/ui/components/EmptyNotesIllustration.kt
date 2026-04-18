package dev.bikram.remember.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.theme.RememberTheme

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
@Composable
fun EmptyNotesIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier.size(width = 180.dp, height = 180.dp)) {
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

            val nibPath = Path().apply {
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
