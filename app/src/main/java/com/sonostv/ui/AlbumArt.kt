package com.sonostv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

private const val ArtFadeMs = 700

/**
 * Cover art that keeps the last successful bitmap on screen until the next URL has
 * decoded, then crossfades. The note icon is only used when nothing has ever loaded.
 */
@Composable
fun AlbumArt(
    artUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(radius = LocalCornerRadius.current, smoothing = 0.6f),
    contentDescription: String? = "Album artwork",
) {
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(artUrl) {
        val url = artUrl ?: return@LaunchedEffect
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build(),
        )
        val drawable = (result as? SuccessResult)?.drawable ?: return@LaunchedEffect
        shown = drawable.toBitmap().asImageBitmap()
    }

    Box(
        modifier
            .size(size)
            .graphicsLayer { clip = false }
            .drawBehind {
                val outline = shape.createOutline(this.size, layoutDirection, this)
                if (outline !is Outline.Generic) return@drawBehind

                val path = outline.path.asAndroidPath()
                val offsetY = 22.dp.toPx()

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.TRANSPARENT
                    }

                    // Wide ambient halo, then a tighter drop beneath the cover.
                    paint.setShadowLayer(72.dp.toPx(), 0f, offsetY, android.graphics.Color.argb(48, 0, 0, 0))
                    canvas.nativeCanvas.drawPath(path, paint)

                    paint.setShadowLayer(40.dp.toPx(), 0f, offsetY, android.graphics.Color.argb(100, 0, 0, 0))
                    canvas.nativeCanvas.drawPath(path, paint)
                }
            }
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = shown,
            animationSpec = tween(ArtFadeMs),
            modifier = Modifier.fillMaxSize(),
            label = "albumArt",
        ) { cover ->
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (artUrl == null) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(size * 0.28f),
                )
            }
        }
    }
}
