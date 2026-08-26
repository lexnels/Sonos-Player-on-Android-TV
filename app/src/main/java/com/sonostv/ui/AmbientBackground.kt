package com.sonostv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The colour wash behind the player.
 *
 * Rather than blurring a scaled-up thumbnail — which needs `Modifier.blur` (Android 12+) to
 * hide the interpolation grid, and shows a cross-hatch pattern without it — this samples a
 * handful of colours from the artwork and paints them as gradients. That is resolution
 * independent, so it looks the same on every device and costs nothing to draw.
 */
@Composable
fun AmbientBackground(artUrl: String?, modifier: Modifier = Modifier) {
    val palette = rememberArtPalette(artUrl)

    val spec = tween<Color>(durationMillis = 1200)
    val base by animateColorAsState(palette.base, spec, label = "base")
    val topLeft by animateColorAsState(palette.topLeft, spec, label = "topLeft")
    val topRight by animateColorAsState(palette.topRight, spec, label = "topRight")
    val bottomLeft by animateColorAsState(palette.bottomLeft, spec, label = "bottomLeft")
    val bottomRight by animateColorAsState(palette.bottomRight, spec, label = "bottomRight")

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)

                val radius = size.maxDimension * 0.85f
                drawCorner(topLeft, Offset(0f, 0f), radius)
                drawCorner(topRight, Offset(size.width, 0f), radius)
                drawCorner(bottomLeft, Offset(0f, size.height), radius)
                drawCorner(bottomRight, Offset(size.width, size.height), radius)

                // Settle the whole thing down so white text stays comfortably readable.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.55f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                )

                drawNoise()
            },
    )
}

private fun DrawScope.drawCorner(
    color: Color,
    center: Offset,
    radius: Float,
) {
    drawRect(
        // Fading to a transparent copy of the same colour rather than Color.Transparent
        // avoids the grey halo you get from interpolating towards transparent black.
        Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
    )
}
