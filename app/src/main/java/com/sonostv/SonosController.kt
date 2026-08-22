package com.sonostv

import android.content.Context
import com.sonostv.sonos.ConnectionState
import com.sonostv.sonos.NowPlaying
import com.sonostv.sonos.PlayState
import com.sonostv.sonos.SonosClient
import com.sonostv.sonos.SonosDiscovery
import com.sonostv.sonos.SonosGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Owns the connection to Sonos for the whole process, so that the on-screen UI and the
 * media session published to the system share one set of polling loops. Polling runs for
 * as long as at least one caller holds it open with [acquire].
 */
class SonosController private constructor(context: Context) {

    private val client = SonosClient()
    private val discovery = SonosDiscovery(context)
    private val prefs = context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var tickJob: Job? = null
    private var holders = 0
    private var consecutiveFailures = 0
    private var pollCycle = 0
    private var lastQueueSignature: String? = null

    /** Call from the main thread when a screen or service starts needing live state. */
    fun acquire() {
        holders++
        if (pollJob?.isActive == true) return
        pollJob = scope.launch { pollLoop() }
        tickJob = scope.launch { tickLoop() }
    }

    /** Call from the main thread once that screen or service is done. */
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        pollJob?.cancel()
        tickJob?.cancel()
        pollJob = null
        tickJob = null
    }

    // ---- Polling -----------------------------------------------------------

    private suspend fun pollLoop() {
        while (coroutineContext.isActive) {
            val group = _state.value.group
            if (group == null) {
                connect()
                if (_state.value.group == null) delay(RETRY_DELAY_MS)
                continue
            }

            val success = refresh(group)
            if (!success) {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_FAILURES) {
                    _state.update { it.copy(group = null, connectionState = ConnectionState.Searching) }
                    consecutiveFailures = 0
                }
                delay(RETRY_DELAY_MS)
                continue
            }

            consecutiveFailures = 0
            delay(if (_state.value.transport?.state?.isPlaying == true) POLL_PLAYING_MS else POLL_IDLE_MS)
        }
    }

    private suspend fun connect() {
        _state.update { it.copy(connectionState = ConnectionState.Searching) }

        val host = discovery.findAnyPlayer()
        if (host == null) {
            _state.update {
                it.copy(connectionState = ConnectionState.Failed("No Sonos players found on this network"))
            }
            return
        }

        val groups = runCatching { client.fetchGroups(host) }.getOrDefault(emptyList())
        if (groups.isEmpty()) {
            _state.update {
                it.copy(connectionState = ConnectionState.Failed("Found a player but could not read the system topology"))
            }
            return
        }

        _state.update { it.copy(groups = groups, group = chooseGroup(groups), connectionState = ConnectionState.Connected) }
    }

    /** Prefer a pinned default room, then the last used one, then anything currently playing. */
    private suspend fun chooseGroup(groups: List<SonosGroup>): SonosGroup {
        val pinned = prefs.getString(AppSettings.KEY_DEFAULT_GROUP, null)
        groups.firstOrNull { it.coordinator.uuid == pinned }?.let { return it }

        val remembered = prefs.getString(AppSettings.KEY_LAST_GROUP, null)
        groups.firstOrNull { it.coordinator.uuid == remembered }?.let { return it }

        val playing = groups.firstOrNull { group ->
            runCatching { client.fetchTransport(group.coordinator.host).state.isPlaying }.getOrDefault(false)
        }
        return playing ?: groups.first()
    }

    private suspend fun refresh(group: SonosGroup): Boolean {
        val host = group.coordinator.host

        val transport = runCatching { client.fetchTransport(host) }.getOrElse { return false }
        _state.update { it.copy(transport = transport, connectionState = ConnectionState.Connected) }

        if (pollCycle % VOLUME_EVERY == 0) {
            runCatching { client.fetchVolume(host) }.onSuccess { (volume, muted) ->
                _state.update { it.copy(volume = volume, muted = muted) }
            }
        }

        if (pollCycle % TOPOLOGY_EVERY == 0) {
            runCatching { client.fetchGroups(host) }.onSuccess { groups -> mergeGroups(groups) }
        }

        // The queue only needs refetching when the content actually changed.
        val signature = "${group.id}|${transport.track.title}|${transport.track.queuePosition}"
        if (signature != lastQueueSignature) {
            lastQueueSignature = signature
            runCatching { client.fetchQueue(host) }.onSuccess { queue ->
                _state.update { it.copy(queue = queue) }
            }
        }

        pollCycle++
        return true
    }

    private fun mergeGroups(groups: List<SonosGroup>) {
        if (groups.isEmpty()) return
        _state.update { current ->
            val selected = groups.firstOrNull { it.coordinator.uuid == current.group?.coordinator?.uuid }
                ?: groups.firstOrNull { it.id == current.group?.id }
                ?: groups.first()
            current.copy(groups = groups, group = selected)
        }
    }

    /** Keeps the progress bar moving smoothly between polls. */
    private suspend fun tickLoop() {
        while (coroutineContext.isActive) {
            delay(TICK_MS)
            _state.update { current ->
                val transport = current.transport ?: return@update current
                if (!transport.state.isPlaying || transport.durationMs <= 0) return@update current
                current.copy(
                    transport = transport.copy(
                        positionMs = (transport.positionMs + TICK_MS).coerceAtMost(transport.durationMs),
                    ),
                )
            }
        }
    }

    private fun withCoordinator(block: suspend (String) -> Unit) {
        val host = _state.value.group?.coordinator?.host ?: return
        scope.launch { runCatching { block(host) } }
    }

    /** Applies an optimistic state change, then re-reads the player shortly after. */
    private fun command(optimistic: (NowPlaying) -> NowPlaying = { it }, block: suspend (String) -> Unit) {
        _state.update(optimistic)
        withCoordinator { host ->
            block(host)
            delay(COMMAND_SETTLE_MS)
            _state.value.group?.let { refresh(it) }
        }
    }

    // ---- Actions -----------------------------------------------------------

    fun togglePlayPause() {
        val playing = _state.value.transport?.state?.isPlaying == true
        if (playing) pause() else play()
    }

    fun play() = command(
        optimistic = { current ->
            current.transport?.let { current.copy(transport = it.copy(state = PlayState.PLAYING)) } ?: current
        },
    ) { host -> client.play(host) }

    fun pause() = command(
        optimistic = { current ->
            current.transport?.let { current.copy(transport = it.copy(state = PlayState.PAUSED)) } ?: current
        },
    ) { host -> client.pause(host) }

    fun next() = command(
        optimistic = { current ->
            current.transport?.let { current.copy(transport = it.copy(state = PlayState.TRANSITIONING)) } ?: current
        },
    ) { host -> client.next(host) }

    /**
     * Matches the behaviour of every other music player: restart the track if we are more
     * than a few seconds in, otherwise jump to the previous one.
     */
    fun previous() {
        val transport = _state.value.transport
        if (transport != null && !transport.isStream && transport.positionMs > RESTART_THRESHOLD_MS) {
            seekTo(0)
            return
        }
        command(
            optimistic = { current ->
                current.transport?.let { current.copy(transport = it.copy(state = PlayState.TRANSITIONING)) } ?: current
            },
        ) { host -> client.previous(host) }
    }

    fun seekTo(positionMs: Long) = command(
        optimistic = { current ->
            current.transport?.let { current.copy(transport = it.copy(positionMs = positionMs)) } ?: current
        },
    ) { host -> client.seekToMillis(host, positionMs) }

    fun adjustVolume(delta: Int) {
        val optimistic = (_state.value.volume + delta).coerceIn(0, 100)
        _state.update { it.copy(volume = optimistic, muted = false) }
        withCoordinator { host ->
            val actual = client.adjustVolume(host, delta)
            _state.update { it.copy(volume = actual) }
        }
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        _state.update { it.copy(muted = muted) }
        withCoordinator { host -> client.setMute(host, muted) }
    }

    /** [index] is the zero-based position in the queue as displayed. */
    fun playQueueItem(index: Int) {
        if (index !in _state.value.queue.indices) return
        command { host ->
            client.seekToQueuePosition(host, index + 1)
            client.play(host)
        }
    }

    fun selectGroup(group: SonosGroup) {
        prefs.edit().putString(AppSettings.KEY_LAST_GROUP, group.coordinator.uuid).apply()
        lastQueueSignature = null
        pollCycle = 0
        _state.update { it.copy(group = group, transport = null, queue = emptyList()) }
        scope.launch { runCatching { refresh(group) } }
    }

    fun retry() {
        _state.update { it.copy(group = null, connectionState = ConnectionState.Searching) }
    }

    companion object {
        private const val POLL_PLAYING_MS = 1_500L
        private const val POLL_IDLE_MS = 3_000L
        private const val RETRY_DELAY_MS = 4_000L
        private const val TICK_MS = 500L
        private const val COMMAND_SETTLE_MS = 350L
        private const val RESTART_THRESHOLD_MS = 5_000L
        private const val MAX_FAILURES = 3
        private const val VOLUME_EVERY = 4
        private const val TOPOLOGY_EVERY = 10
        @Volatile
        private var instance: SonosController? = null

        fun get(context: Context): SonosController =
            instance ?: synchronized(this) {
                instance ?: SonosController(context.applicationContext).also { instance = it }
            }
    }
}
