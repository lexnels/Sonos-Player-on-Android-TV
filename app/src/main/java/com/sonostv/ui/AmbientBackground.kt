package com.sonostv.ui

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * The soft, colour-washed backdrop derived from the album art. The image is deliberately
 * fetched at a tiny size and stretched, which produces a smooth gradient on every API level
 * (and costs almost nothing); on Android 12+ a real blur is layered on top.
 */
@Composable
fun AmbientBackground(artUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(SonosColors.Background)) {
        Crossfade(
            targetState = artUrl,
            animationSpec = tween(durationMillis = 900),
            label = "ambient",
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .size(28)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .fillMaxSize()
                        .blurCompat(48.dp),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.55f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.93f),
                    ),
                ),
        )
    }
}

fun Modifier.blurCompat(radius: androidx.compose.ui.unit.Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(radius) else this
