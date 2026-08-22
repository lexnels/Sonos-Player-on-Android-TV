package com.sonostv.media

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sonostv.SonosController
import com.sonostv.ui.ScreensaverScreen
import com.sonostv.ui.SonosTvTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The system screensaver, shown when the TV goes idle. It only claims the screen while
 * Sonos is actually playing; with the music stopped it steps aside so the TV does whatever
 * it would normally do when left alone.
 *
 * [DreamService] is a plain service, so the Compose plumbing an activity normally provides
 * — a lifecycle, a saved-state registry, a view-model store — has to be supplied by hand.
 */
class ScreensaverDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private var controller: SonosController? = null
    private var holding = false
    private var watchJob: Job? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isFullscreen = true
        isScreenBright = true
        // Non-interactive means the first press of any remote key dismisses the screensaver
        // and hands the user back to whatever they were doing.
        isInteractive = false

        val controller = SonosController.get(this).also { this.controller = it }

        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@ScreensaverDreamService)
                setViewTreeSavedStateRegistryOwner(this@ScreensaverDreamService)
                setViewTreeViewModelStoreOwner(this@ScreensaverDreamService)
                setContent {
                    SonosTvTheme {
                        val state by controller.state.collectAsStateWithLifecycle()
                        ScreensaverScreen(state)
                    }
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        controller?.acquire()
        holding = true
        watchJob = lifecycleScope.launch { leaveWhenNothingIsPlaying() }
    }

    override fun onDreamingStopped() {
        watchJob?.cancel()
        watchJob = null
        if (holding) {
            holding = false
            controller?.release()
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }

    /**
     * Discovery takes a few seconds from cold, so we give playback a window to appear
     * before giving up. Once it has appeared, a pause is only worth reacting to if it
     * lasts — skipping between tracks briefly reports a non-playing state.
     */
    private suspend fun leaveWhenNothingIsPlaying() {
        val playing = controller?.state
            ?.map { it.transport?.state?.isPlaying == true }
            ?.distinctUntilChanged()
            ?: return finish()

        val started = withTimeoutOrNull(StartTimeoutMs) { playing.first { it } }
        if (started == null) {
            finish()
            return
        }

        playing.collectLatest { isPlaying ->
            if (isPlaying) return@collectLatest
            delay(StopGraceMs)
            finish()
        }
    }

    private companion object {
        const val StartTimeoutMs = 15_000L
        const val StopGraceMs = 30_000L
    }
}
