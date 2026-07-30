package com.sonostv

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonostv.sonos.ConnectionState
import com.sonostv.sonos.NowPlaying
import com.sonostv.sonos.PlayState
import com.sonostv.sonos.SonosClient
import com.sonostv.sonos.SonosDiscovery
import com.sonostv.sonos.SonosGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {

    private val client = SonosClient()
    private val discovery = SonosDiscovery(application)
    private val prefs = application.getSharedPreferences("sonos_tv", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var tickJob: Job? = null
    private var consecutiveFailures = 0
    private var pollCycle = 0
    private var lastQueueSignature: String? = null

    init {
        start()
    }

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch { pollLoop() }
        tickJob = viewModelScope.launch { tickLoop() }
    }

    fun stop() {
        pollJob?.cancel()
        tickJob?.cancel()
        pollJob = null
        tickJob = null
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    // ---- Polling -----------------------------------------------------------

    private suspend fun pollLoop() {
        while (viewModelScope.isActive) {
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

    /** Prefer the room the user last used, then any room that is actually playing something. */
    private suspend fun chooseGroup(groups: List<SonosGroup>): SonosGroup {
        val remembered = prefs.getString(KEY_LAST_GROUP, null)
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
        while (viewModelScope.isActive) {
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
        viewModelScope.launch { runCatching { block(host) } }
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
        command(
            optimistic = { current ->
                val transport = current.transport
                if (transport == null) {
                    current
                } else {
                    current.copy(
                        transport = transport.copy(state = if (playing) PlayState.PAUSED else PlayState.PLAYING),
                    )
                }
            },
        ) { host ->
            if (playing) client.pause(host) else client.play(host)
        }
    }

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

    fun skip(deltaMs: Long) {
        val transport = _state.value.transport ?: return
        if (transport.isStream || transport.durationMs <= 0) return
        seekTo((transport.positionMs + deltaMs).coerceIn(0, transport.durationMs))
    }

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
        prefs.edit().putString(KEY_LAST_GROUP, group.coordinator.uuid).apply()
        lastQueueSignature = null
        pollCycle = 0
        _state.update { it.copy(group = group, transport = null, queue = emptyList()) }
        viewModelScope.launch { runCatching { refresh(group) } }
    }

    fun retry() {
        _state.update { it.copy(group = null, connectionState = ConnectionState.Searching) }
        start()
    }

    private companion object {
        const val POLL_PLAYING_MS = 1_500L
        const val POLL_IDLE_MS = 3_000L
        const val RETRY_DELAY_MS = 4_000L
        const val TICK_MS = 500L
        const val COMMAND_SETTLE_MS = 350L
        const val RESTART_THRESHOLD_MS = 5_000L
        const val MAX_FAILURES = 3
        const val VOLUME_EVERY = 4
        const val TOPOLOGY_EVERY = 10
        const val KEY_LAST_GROUP = "last_group_uuid"
    }
}
