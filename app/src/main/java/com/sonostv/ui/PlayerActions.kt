package com.sonostv.ui

import com.sonostv.sonos.SonosGroup

/** Everything the now-playing screen can ask the player to do. */
data class PlayerActions(
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onVolumeChange: (Int) -> Unit,
    val onToggleMute: () -> Unit,
    val onPlayQueueItem: (Int) -> Unit,
    val onSelectGroup: (SonosGroup) -> Unit,
    val onRetry: () -> Unit,
)
