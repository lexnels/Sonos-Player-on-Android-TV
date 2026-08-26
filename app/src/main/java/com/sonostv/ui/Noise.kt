package com.sonostv.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * Fine grain over the colour wash. 8-bit TV panels posterise smooth gradients;
 * a little noise breaks the bands up without reading as texture from across the room.
 *
 * Drawn with SrcOver rather than Overlay/Softlight: those advanced blend modes go
 * through GL_KHR_blend_equation_advanced, which crashes the Android emulator's
 * gfxstream backend when the lava-lamp animation invalidates every frame.
 */
internal fun DrawScope.drawNoise() {
    drawRect(brush = NoiseBrush)
}

private const val GrainAlpha = 3
private const val NoiseTileSize = 256
private const val GrainSize = 2

private val NoiseBrush: ShaderBrush by lazy {
    ShaderBrush(
        ImageShader(
            createNoiseTile(NoiseTileSize, GrainSize),
            TileMode.Repeated,
            TileMode.Repeated,
        ),
    )
}

private fun createNoiseTile(size: Int, grainSize: Int): ImageBitmap {
    val grid = size / grainSize
    val pixels = IntArray(size * size)
    val random = Random(0x5EED)
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
