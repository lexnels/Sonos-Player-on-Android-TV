package com.sonostv.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonostv.sonos.NowPlaying

/** How far the content wanders, as a fraction of the screen. */
private const val DriftExtent = 0.05f
private const val DriftXMs = 47_000
private const val DriftYMs = 71_000

/**
 * The system screensaver's face: artwork, title, artist, and nothing else. Everything
 * drifts slowly so a static panel never holds the same pixels for long.
 *
 * Until the first track arrives only the background is drawn, so the handful of seconds
 * spent finding the Sonos system doesn't flash placeholder text across the screen.
 */
@Composable
fun ScreensaverScreen(state: NowPlaying, modifier: Modifier = Modifier) {
    val track = state.transport?.track

    Box(modifier.fillMaxSize()) {
        AmbientBackground(track?.artUrl)

        if (track == null || track.isEmpty) return@Box

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val artSize = (maxHeight * 0.44f).coerceAtMost(340.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drift()
                    .padding(horizontal = 80.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AlbumArt(artUrl = track.artUrl, size = artSize, contentDescription = null)

                Spacer(Modifier.height(28.dp))

                Text(
                    text = track.title ?: "",
                    style = SonosText.Title,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (track.artist != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = track.artist,
                        style = SonosText.Artist,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.group != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.group.name.uppercase(),
                        style = SonosText.Eyebrow,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Two slow, mismatched cycles, so the path never repeats itself quickly. */
@Composable
private fun Modifier.drift(): Modifier {
    val transition = rememberInfiniteTransition(label = "drift")
    val spec = { durationMillis: Int ->
        infiniteRepeatable<Float>(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        )
    }
    val x by transition.animateFloat(-1f, 1f, spec(DriftXMs), label = "driftX")
    val y by transition.animateFloat(1f, -1f, spec(DriftYMs), label = "driftY")

    return graphicsLayer {
        translationX = x * size.width * DriftExtent
        translationY = y * size.height * DriftExtent
    }
}
