package com.sonostv.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest

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
    val context = LocalContext.current
    var palette by remember { mutableStateOf(AmbientPalette.Default) }

    LaunchedEffect(artUrl) {
        palette = if (artUrl == null) {
            AmbientPalette.Default
        } else {
            val request = ImageRequest.Builder(context)
                .data(artUrl)
                .size(SampleSize)
                .allowHardware(false)
                .build()
            val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            bitmap?.let(AmbientPalette::from) ?: AmbientPalette.Default
        }
    }

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

private const val SampleSize = 16

private data class AmbientPalette(
    val topLeft: Color,
    val topRight: Color,
    val bottomLeft: Color,
    val bottomRight: Color,
    val base: Color,
) {
    companion object {
        val Default = AmbientPalette(
            topLeft = Color(0xFF1A1A20),
            topRight = Color(0xFF141419),
            bottomLeft = Color(0xFF121217),
            bottomRight = Color(0xFF0C0C10),
            base = Color(0xFF0A0A0D),
        )

        fun from(bitmap: Bitmap): AmbientPalette {
            val small = runCatching {
                Bitmap.createScaledBitmap(bitmap, SampleSize, SampleSize, true)
            }.getOrNull() ?: return Default

            val half = SampleSize / 2
            return AmbientPalette(
                topLeft = small.averageColor(0, 0, half, half),
                topRight = small.averageColor(half, 0, SampleSize, half),
                bottomLeft = small.averageColor(0, half, half, SampleSize),
                bottomRight = small.averageColor(half, half, SampleSize, SampleSize),
                base = small.averageColor(0, 0, SampleSize, SampleSize, brightness = 0.32f),
            )
        }

        /**
         * Averages a region, then pushes it towards a saturated but dark tone: vivid enough
         * to feel like the artwork, dark enough to sit behind white type.
         */
        private fun Bitmap.averageColor(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            brightness: Float = 0.55f,
        ): Color {
            var r = 0L
            var g = 0L
            var b = 0L
            var count = 0
            for (x in left until right) {
                for (y in top until bottom) {
                    val pixel = getPixel(x, y)
                    r += (pixel shr 16) and 0xFF
                    g += (pixel shr 8) and 0xFF
                    b += pixel and 0xFF
                    count++
                }
            }
            if (count == 0) return Color(0xFF101014)

            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((r / count).toInt(), (g / count).toInt(), (b / count).toInt(), hsv)
            hsv[1] = (hsv[1] * 1.35f).coerceAtMost(0.85f)
            hsv[2] = brightness
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
}
