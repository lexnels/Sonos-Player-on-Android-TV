package com.sonostv.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest

const val ArtSampleSize = 16

data class ArtPalette(
    val topLeft: Color,
    val topRight: Color,
    val bottomLeft: Color,
    val bottomRight: Color,
    val base: Color,
) {
    companion object {
        val Default = ArtPalette(
            topLeft = Color(0xFF1A1A20),
            topRight = Color(0xFF141419),
            bottomLeft = Color(0xFF121217),
            bottomRight = Color(0xFF0C0C10),
            base = Color(0xFF0A0A0D),
        )

        fun from(bitmap: Bitmap): ArtPalette {
            val small = runCatching {
                Bitmap.createScaledBitmap(bitmap, ArtSampleSize, ArtSampleSize, true)
            }.getOrNull() ?: return Default

            val half = ArtSampleSize / 2
            return ArtPalette(
                topLeft = small.averageColor(0, 0, half, half),
                topRight = small.averageColor(half, 0, ArtSampleSize, half),
                bottomLeft = small.averageColor(0, half, half, ArtSampleSize),
                bottomRight = small.averageColor(half, half, ArtSampleSize, ArtSampleSize),
                base = small.averageColor(0, 0, ArtSampleSize, ArtSampleSize, brightness = 0.32f),
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

@Composable
fun rememberArtPalette(artUrl: String?): ArtPalette {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(ArtPalette.Default) }

    LaunchedEffect(artUrl) {
        palette = if (artUrl == null) {
            ArtPalette.Default
        } else {
            val request = ImageRequest.Builder(context)
                .data(artUrl)
                .size(ArtSampleSize)
                .allowHardware(false)
                .build()
            val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            bitmap?.let(ArtPalette::from) ?: ArtPalette.Default
        }
    }

    return palette
}
