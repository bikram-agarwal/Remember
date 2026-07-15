package dev.bikram.remember.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Adapts normalized MaterialShapes polygons to Compose's Shape contract so they can be
 * used with clipping, borders, and touch targets like regular M3 shape tokens.
 */
internal class RoundedPolygonShape(
    private val polygon: RoundedPolygon,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = polygon.toPath().asComposePath()
        path.transform(pathBoundsMatrix(path, size))
        return Outline.Generic(path)
    }
}

internal class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress = progress).asComposePath()
        path.transform(pathBoundsMatrix(path, size))
        return Outline.Generic(path)
    }
}

private fun pathBoundsMatrix(
    path: Path,
    size: Size,
): Matrix {
    val bounds = path.getBounds()
    val matrix = Matrix()
    matrix.scale(size.width / bounds.width, size.height / bounds.height)
    matrix.translate(-bounds.left, -bounds.top)
    return matrix
}
