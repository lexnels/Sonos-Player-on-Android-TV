package com.sonostv.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonostv.AppSettings
import com.sonostv.BackgroundStyle
import com.sonostv.sonos.ConnectionState
import com.sonostv.sonos.NowPlaying
import com.sonostv.sonos.Transport
import com.sonostv.sonos.formatDuration
import kotlinx.coroutines.delay

private enum class Panel { None, Queue, Rooms, Settings }

private const val IdleTimeoutMs = 10_000L

@Composable
fun NowPlayingScreen(
    state: NowPlaying,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    var panel by remember { mutableStateOf(Panel.None) }
    val playButton = remember { FocusRequester() }
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val prefs by settings.value.collectAsStateWithLifecycle()

    // After a spell with no input the controls fade away, leaving just the artwork.
    var wakeCount by remember { mutableStateOf(0) }
    var idle by remember { mutableStateOf(false) }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (idle) 0f else 1f,
        animationSpec = tween(durationMillis = if (idle) 900 else 220),
        label = "chrome",
    )

    LaunchedEffect(wakeCount, panel) {
        idle = false
        if (panel != Panel.None) return@LaunchedEffect
        delay(IdleTimeoutMs)
        idle = true
    }

    fun closePanel() {
        panel = Panel.None
        runCatching { playButton.requestFocus() }
    }

    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler(enabled = panel != Panel.None) {
            if (panel == Panel.Settings) {
                panel = Panel.Rooms
            } else {
                closePanel()
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val wasIdle = idle
                wakeCount++
                // The keypress that wakes the screen shouldn't also press a hidden button.
                wasIdle
            },
    ) {
        when (prefs.backgroundStyle) {
            BackgroundStyle.Ambient -> AmbientBackground(state.transport?.track?.artUrl)
            BackgroundStyle.LavaLamp -> LavaLampBackground(state.transport?.track?.artUrl)
        }

        when {
            state.connectionState is ConnectionState.Failed && state.group == null ->
                StatusMessage(
                    message = (state.connectionState as ConnectionState.Failed).message,
                    detail = "Make sure this device is on the same network as your Sonos system.",
                    onRetry = actions.onRetry,
                )

            state.group == null -> StatusMessage(message = "Looking for your Sonos…", detail = null, onRetry = null)

            else -> PlayerContent(
                state = state,
                actions = actions,
                playButton = playButton,
                chromeAlpha = chromeAlpha,
                onOpenQueue = { panel = Panel.Queue },
                onOpenRooms = { panel = Panel.Rooms },
            )
        }

        SidePanel(
            visible = panel == Panel.Queue,
            header = "Up Next",
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            QueueList(
                tracks = state.queue,
                currentPosition = state.transport?.track?.queuePosition ?: 0,
                isPlayingWithoutQueue = state.transport?.track?.isEmpty == false,
                onSelect = { index ->
                    actions.onPlayQueueItem(index)
                    closePanel()
                },
            )
        }

        SidePanel(
            visible = panel == Panel.Rooms,
            header = "Rooms",
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            RoomList(
                groups = state.groups,
                selectedId = state.group?.id,
                onSelect = { group ->
                    actions.onSelectGroup(group)
                    closePanel()
                },
                onOpenSettings = { panel = Panel.Settings },
            )
        }

        SidePanel(
            visible = panel == Panel.Settings,
            header = "Settings",
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SettingsPanel(
                groups = state.groups,
                prefs = prefs,
                settings = settings,
            )
        }
    }
}

@Composable
private fun PlayerContent(
    state: NowPlaying,
    actions: PlayerActions,
    playButton: FocusRequester,
    chromeAlpha: Float,
    onOpenQueue: () -> Unit,
    onOpenRooms: () -> Unit,
) {
    val transport = state.transport
    val track = transport?.track

    LaunchedEffect(Unit) {
        runCatching { playButton.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artSize = (maxHeight * 0.58f).coerceAtMost(300.dp)

        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(artUrl = track?.artUrl, size = artSize)

                Spacer(Modifier.width(36.dp))

                // Sized to the control row, so the block is symmetric within the screen
                // and the artwork doesn't shift as track titles change length.
                Column(Modifier.width(IntrinsicSize.Min)) {
                    Text(
                        text = eyebrow(state),
                        style = SonosText.Eyebrow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(chromeAlpha),
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = track?.title ?: "Nothing playing",
                        style = SonosText.Title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (track?.artist != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = track.artist,
                            style = SonosText.Artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (track?.album != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.album,
                            style = SonosText.Album,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    // Intrinsic sizing ties the progress bar to the width of the button row.
                    Column(Modifier.width(IntrinsicSize.Min)) {
                        ProgressSection(
                            transport = transport,
                            onSeek = actions.onSeek,
                            timecodeAlpha = chromeAlpha,
                        )

                        Spacer(Modifier.height(18.dp))

                        TransportControls(
                            actions = actions,
                            state = state,
                            playButton = playButton,
                            onOpenQueue = onOpenQueue,
                            onOpenRooms = onOpenRooms,
                            modifier = Modifier.graphicsLayer {
                                alpha = chromeAlpha
                                clip = false
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(transport: Transport?, onSeek: (Long) -> Unit, timecodeAlpha: Float) {
    val seekable = transport != null && !transport.isStream && transport.durationMs > 0

    Column(Modifier.fillMaxWidth()) {
        Scrubber(
            positionMs = transport?.positionMs ?: 0L,
            durationMs = transport?.durationMs ?: 0L,
            enabled = seekable,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(2.dp))

        Row(
            Modifier.fillMaxWidth().alpha(timecodeAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (seekable) formatDuration(transport.positionMs) else "",
                style = SonosText.Timecode,
            )
            Text(
                text = when {
                    transport?.isStream == true -> "LIVE"
                    seekable -> "-" + formatDuration(transport.durationMs - transport.positionMs)
                    else -> ""
                },
                style = SonosText.Timecode,
            )
        }
    }
}

@Composable
private fun TransportControls(
    actions: PlayerActions,
    state: NowPlaying,
    playButton: FocusRequester,
    onOpenQueue: () -> Unit,
    onOpenRooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = state.transport?.state?.isPlaying == true

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvIconButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous track",
            onClick = actions.onPrevious,
        )
        TvIconButton(
            icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            onClick = actions.onPlayPause,
            focusRequester = playButton,
            idleBackground = SonosColors.ControlEmphasis,
        )
        TvIconButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next track",
            onClick = actions.onNext,
        )

        Spacer(Modifier.width(6.dp))
        ControlDivider()
        Spacer(Modifier.width(6.dp))

        TvIconButton(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "Show queue",
            onClick = onOpenQueue,
        )
        TvIconButton(
            icon = Icons.Rounded.Speaker,
            contentDescription = "Choose room",
            onClick = onOpenRooms,
        )
    }
}

@Composable
private fun StatusMessage(message: String, detail: String?, onRetry: (() -> Unit)?) {
    val retryFocus = remember { FocusRequester() }

    LaunchedEffect(onRetry) {
        if (onRetry != null) runCatching { retryFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onRetry == null) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(18.dp))
        }

        Text(text = message, style = SonosText.Artist.copy(color = SonosColors.Primary))

        if (detail != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = detail, style = SonosText.Album)
        }

        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            TvIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Search again",
                onClick = onRetry,
                focusRequester = retryFocus,
            )
        }
    }
}

private fun eyebrow(state: NowPlaying): String {
    val room = state.group?.name?.uppercase().orEmpty()
    val source = state.transport?.source?.uppercase()
    return if (source != null) "$room  ·  $source" else room
}
