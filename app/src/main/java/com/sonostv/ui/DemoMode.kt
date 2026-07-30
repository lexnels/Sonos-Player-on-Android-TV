package com.sonostv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.sonostv.sonos.ConnectionState
import com.sonostv.sonos.NowPlaying
import com.sonostv.sonos.PlayState
import com.sonostv.sonos.SonosGroup
import com.sonostv.sonos.SonosPlayer
import com.sonostv.sonos.Track
import com.sonostv.sonos.Transport

/**
 * Sample data so the interface can be inspected without a Sonos system on the network,
 * either through `@Preview` or by launching the app with the `demo` intent extra.
 */
object DemoData {

    private const val COVER_1 = "android.resource://com.sonostv/drawable/demo_cover_1"
    private const val COVER_2 = "android.resource://com.sonostv/drawable/demo_cover_2"
    private const val COVER_3 = "android.resource://com.sonostv/drawable/demo_cover_3"

    private val livingRoom = SonosGroup(
        id = "RINCON_DEMO1:1",
        coordinator = SonosPlayer("RINCON_DEMO1", "Living Room", "192.168.1.20"),
        members = listOf(
            SonosPlayer("RINCON_DEMO1", "Living Room", "192.168.1.20"),
            SonosPlayer("RINCON_DEMO2", "Kitchen", "192.168.1.21"),
        ),
    )

    val groups = listOf(
        livingRoom,
        SonosGroup(
            id = "RINCON_DEMO3:1",
            coordinator = SonosPlayer("RINCON_DEMO3", "Bedroom", "192.168.1.22"),
            members = listOf(SonosPlayer("RINCON_DEMO3", "Bedroom", "192.168.1.22")),
        ),
        SonosGroup(
            id = "RINCON_DEMO4:1",
            coordinator = SonosPlayer("RINCON_DEMO4", "Study", "192.168.1.23"),
            members = listOf(SonosPlayer("RINCON_DEMO4", "Study", "192.168.1.23")),
        ),
    )

    val queue = listOf(
        Track("Midnight Coast", "Hollow Coves", "Blue Hour", COVER_1),
        Track("Slow Tide", "Hollow Coves", "Blue Hour", COVER_1),
        Track("Paper Lanterns", "Aurora Falls", "Long Way Home", COVER_2),
        Track("Glass Harbour", "Aurora Falls", "Long Way Home", COVER_2),
        Track("Ember", "The Quiet Season", "Northerly", COVER_3),
        Track("Weathervane", "The Quiet Season", "Northerly", COVER_3),
        Track("Salt & Pine", "Marrow Hill", "Field Notes", COVER_1),
    )

    val nowPlaying = NowPlaying(
        groups = groups,
        group = livingRoom,
        transport = Transport(
            state = PlayState.PLAYING,
            track = queue[0].copy(queuePosition = 1),
            positionMs = 98_000,
            durationMs = 252_000,
            isStream = false,
            source = "Spotify",
        ),
        volume = 34,
        muted = false,
        queue = queue,
        connectionState = ConnectionState.Connected,
    )
}

/**
 * A self-contained, interactive stand-in for the real player: the controls change local
 * state so the focus behaviour, panels and transport can be tried out end to end.
 */
@Composable
fun DemoNowPlayingScreen() {
    var state by remember { mutableStateOf(DemoData.nowPlaying) }

    fun moveTo(index: Int) {
        val bounded = index.coerceIn(DemoData.queue.indices)
        state = state.copy(
            transport = state.transport?.copy(
                track = DemoData.queue[bounded].copy(queuePosition = bounded + 1),
                positionMs = 0,
                durationMs = 200_000L + (bounded * 23_000L),
                state = PlayState.PLAYING,
            ),
        )
    }

    NowPlayingScreen(
        state = state,
        actions = PlayerActions(
            onPlayPause = {
                val transport = state.transport ?: return@PlayerActions
                state = state.copy(
                    transport = transport.copy(
                        state = if (transport.state.isPlaying) PlayState.PAUSED else PlayState.PLAYING,
                    ),
                )
            },
            onNext = { moveTo((state.transport?.track?.queuePosition ?: 1)) },
            onPrevious = { moveTo((state.transport?.track?.queuePosition ?: 1) - 2) },
            onSeek = { position ->
                state = state.copy(transport = state.transport?.copy(positionMs = position))
            },
            onVolumeChange = { delta ->
                state = state.copy(volume = (state.volume + delta).coerceIn(0, 100), muted = false)
            },
            onToggleMute = { state = state.copy(muted = !state.muted) },
            onPlayQueueItem = { index -> moveTo(index) },
            onSelectGroup = { group -> state = state.copy(group = group) },
            onRetry = {},
        ),
    )
}

@Preview(device = Devices.TV_1080p, showBackground = true)
@Composable
private fun NowPlayingPreview() {
    SonosTvTheme { DemoNowPlayingScreen() }
}
