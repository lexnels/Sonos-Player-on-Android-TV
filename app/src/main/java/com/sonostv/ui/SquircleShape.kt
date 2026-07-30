package com.sonostv.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

/**
 * Continuous corners, the way Apple platforms draw them: the curvature eases into the
 * straight edges rather than meeting them abruptly as a circular arc does.
 *
 * [smoothing] of `0f` gives an ordinary rounded rectangle; around `0.6f` matches the iOS
 * look. The corner occupies `radius * (1 + smoothing)` along each edge.
 */
class SquircleShape(
    private val radius: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rounding = CornerRounding(
            radius = with(density) { radius.toPx() },
            smoothing = smoothing,
        )
        val path = RoundedPolygon
            .rectangle(width = size.width, height = size.height, rounding = rounding)
            .toPath()
            .asComposePath()

        // RoundedPolygon.rectangle is built around the origin, so shift it into place.
        path.translate(Offset(size.width / 2f, size.height / 2f))
        return Outline.Generic(path)
    }
}
