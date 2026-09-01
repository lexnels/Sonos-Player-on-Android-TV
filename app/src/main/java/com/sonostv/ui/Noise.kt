package com.sonostv.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.random.Random

/**
 * Fine grain over the colour wash. 8-bit TV panels posterise smooth gradients;
 * a little noise breaks the bands up without reading as texture from across the room.
 *
 * Pass a changing [frame] on animated backgrounds so the grain jumps every
 * redraw. Temporal dither averages toward more bits; a static tile leaves the
 * same bands sitting there frame after frame.
 *
 * Drawn with SrcOver rather than Overlay/Softlight: those advanced blend modes go
 * through GL_KHR_blend_equation_advanced, which crashes the Android emulator's
 * gfxstream backend when the lava-lamp animation invalidates every frame.
 */
internal fun DrawScope.drawNoise(frame: Int = 0) {
    val brush = NoiseBrushes[frame and (NoisePatternCount - 1)]
    if (frame == 0) {
        drawRect(brush = brush)
        return
    }
    // Large coprime steps so the tile scrambles in place rather than crawling.
    val ox = ((frame * 73) and (NoiseTileSize - 1)).toFloat()
    val oy = ((frame * 47) and (NoiseTileSize - 1)).toFloat()
    val pad = NoiseTileSize.toFloat()
    translate(-ox, -oy) {
        drawRect(
            brush = brush,
            size = Size(size.width + pad, size.height + pad),
        )
    }
}

/** Grain over a drop shadow — clipped to the blurred halo around an offset shape. */
internal fun DrawScope.drawShadowNoise(
    shapePath: Path,
    offsetY: Float,
    spread: Float,
) {
    val bounds = shapePath.getBounds()
    val shadowBounds = Rect(
        left = bounds.left - spread,
        top = bounds.top + offsetY - spread,
        right = bounds.right + spread,
        bottom = bounds.bottom + offsetY + spread,
    )
    clipPath(Path().apply { addRect(shadowBounds) }) {
        drawNoise()
    }
}

/** Grain confined to a soft radial blob — matches lava-lamp / corner washes. */
internal fun DrawScope.drawRadialNoise(
    center: Offset,
    radius: Float,
    frame: Int = 0,
) {
    val bounds = Rect(
        left = center.x - radius,
        top = center.y - radius,
        right = center.x + radius,
        bottom = center.y + radius,
    )
    clipPath(Path().apply { addOval(bounds) }) {
        drawNoise(frame)
    }
}

private const val GrainAlpha = 3
private const val NoiseTileSize = 256
private const val GrainSize = 2
private const val NoisePatternCount = 8

private val NoiseBrushes: Array<ShaderBrush> by lazy {
    Array(NoisePatternCount) { i ->
        ShaderBrush(
            ImageShader(
                createNoiseTile(NoiseTileSize, GrainSize, seed = 0x5EED + i * 997),
                TileMode.Repeated,
                TileMode.Repeated,
            ),
        )
    }
}

private fun createNoiseTile(size: Int, grainSize: Int, seed: Int): ImageBitmap {
    val grid = size / grainSize
    val pixels = IntArray(size * size)
    val random = Random(seed)
    for (gy in 0 until grid) {
        for (gx in 0 until grid) {
            // Faint white or black so the wash is dithered without a net tint.
            val color = if (random.nextBoolean()) {
                (GrainAlpha shl 24) or 0x00FFFFFF
            } else {
                GrainAlpha shl 24
            }
            val x0 = gx * grainSize
            val y0 = gy * grainSize
            for (y in y0 until y0 + grainSize) {
                val row = y * size
                for (x in x0 until x0 + grainSize) {
                    pixels[row + x] = color
                }
            }
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }.asImageBitmap()
}
