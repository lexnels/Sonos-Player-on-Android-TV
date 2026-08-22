package com.sonostv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Diffuse lava-lamp wash: one sampled colour fills the screen, three others drift
 * around as soft radial blobs. Drawn on a single canvas so nothing gets clipped
 * to square layout bounds (which is what makes `Modifier.blur` look blocky).
 */
@Composable
fun LavaLampBackground(artUrl: String?, modifier: Modifier = Modifier) {
    val palette = rememberArtPalette(artUrl)
    val spec = tween<Color>(durationMillis = 1200)
    val base by animateColorAsState(palette.base, spec, label = "base")
    val blob1 by animateColorAsState(palette.topLeft, spec, label = "blob1")
    val blob2 by animateColorAsState(palette.topRight, spec, label = "blob2")
    val blob3 by animateColorAsState(palette.bottomLeft, spec, label = "blob3")

    val transition = rememberInfiniteTransition(label = "lava")
    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t1",
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(25_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t2",
    )
    val t3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t3",
    )

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)

                val blobRadius = size.maxDimension * 0.62f
                drawSoftBlob(
                    blob1,
                    blobCenter(t1 * 2f * Math.PI.toFloat(), 0.35f, 0.38f, 0.28f, 0.22f),
                    blobRadius,
                )
                drawSoftBlob(
                    blob2,
                    blobCenter(t2 * 2f * Math.PI.toFloat() + 2.1f, 0.68f, 0.55f, 0.24f, 0.26f),
                    blobRadius,
                )
                drawSoftBlob(
                    blob3,
                    blobCenter(t3 * 2f * Math.PI.toFloat() + 4.3f, 0.48f, 0.72f, 0.30f, 0.20f),
                    blobRadius,
                )

                drawReadabilityOverlay()
            },
    )
}

private fun DrawScope.blobCenter(
    phase: Float,
    centerX: Float,
    centerY: Float,
    orbitX: Float,
    orbitY: Float,
): Offset = Offset(
    x = size.width * (centerX + orbitX * sin(phase)),
    y = size.height * (centerY + orbitY * cos(phase * 0.85f + 0.7f)),
)

private fun DrawScope.drawSoftBlob(color: Color, center: Offset, radius: Float) {
    val c = color.copy(alpha = 0.92f)
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to c,
                0.30f to c.copy(alpha = c.alpha * 0.65f),
                0.60f to c.copy(alpha = c.alpha * 0.25f),
                1f to c.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
    )
}

private fun DrawScope.drawReadabilityOverlay() {
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.22f),
            0.55f to Color.Black.copy(alpha = 0.38f),
            1f to Color.Black.copy(alpha = 0.72f),
        ),
    )
}
