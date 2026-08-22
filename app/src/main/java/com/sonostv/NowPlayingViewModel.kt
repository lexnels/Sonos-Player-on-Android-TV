package com.sonostv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sonostv.sonos.SonosGroup

/** Thin bridge between the now-playing screen and the process-wide [SonosController]. */
class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = SonosController.get(application)
    private var holding = false

    val state = controller.state

    /** Keeps polling alive while the screen is visible. */
    fun start() {
        if (holding) return
        holding = true
        controller.acquire()
    }

    fun stop() {
        if (!holding) return
        holding = false
        controller.release()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun adjustVolume(delta: Int) = controller.adjustVolume(delta)
    fun toggleMute() = controller.toggleMute()
    fun playQueueItem(index: Int) = controller.playQueueItem(index)
    fun selectGroup(group: SonosGroup) = controller.selectGroup(group)
    fun retry() = controller.retry()
}
