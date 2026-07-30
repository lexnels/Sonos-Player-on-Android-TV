package com.sonostv.sonos

data class SonosPlayer(
    val uuid: String,
    val name: String,
    val host: String,
)

data class SonosGroup(
    val id: String,
    val coordinator: SonosPlayer,
    val members: List<SonosPlayer>,
) {
    val name: String
        get() = when (members.size) {
            0, 1 -> coordinator.name
            2 -> "${coordinator.name} + 1"
            else -> "${coordinator.name} + ${members.size - 1}"
        }
}

enum class PlayState {
    PLAYING,
    PAUSED,
    STOPPED,
    TRANSITIONING;

    val isPlaying: Boolean get() = this == PLAYING || this == TRANSITIONING

    companion object {
        fun from(raw: String?): PlayState = when (raw) {
            "PLAYING" -> PLAYING
            "PAUSED_PLAYBACK" -> PAUSED
            "TRANSITIONING" -> TRANSITIONING
            else -> STOPPED
        }
    }
}

data class Track(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artUrl: String?,
    val queuePosition: Int = 0,
) {
    val isEmpty: Boolean get() = title == null && artist == null && album == null
}

data class Transport(
    val state: PlayState,
    val track: Track,
    val positionMs: Long,
    val durationMs: Long,
    /** True for radio and other endless streams, where seeking and duration are meaningless. */
    val isStream: Boolean,
    val source: String?,
)

data class NowPlaying(
    val groups: List<SonosGroup> = emptyList(),
    val group: SonosGroup? = null,
    val transport: Transport? = null,
    val volume: Int = 0,
    val muted: Boolean = false,
    val queue: List<Track> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Searching,
)

sealed interface ConnectionState {
    data object Searching : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}
