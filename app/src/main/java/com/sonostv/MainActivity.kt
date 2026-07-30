package com.sonostv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonostv.ui.DemoNowPlayingScreen
import com.sonostv.ui.NowPlayingScreen
import com.sonostv.ui.PlayerActions
import com.sonostv.ui.SonosTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NowPlayingViewModel by viewModels()

    /**
     * Renders sample content instead of talking to the network, for inspecting the UI
     * without a Sonos system present:
     * `adb shell am start -n com.sonostv/.MainActivity --ez demo true`
     */
    private val demoMode: Boolean
        get() = intent?.getBooleanExtra("demo", false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            SonosTvTheme {
                if (demoMode) {
                    DemoNowPlayingScreen()
                } else {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    NowPlayingScreen(
                        state = state,
                        actions = PlayerActions(
                            onPlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            onSeek = viewModel::seekTo,
                            onVolumeChange = viewModel::adjustVolume,
                            onToggleMute = viewModel::toggleMute,
                            onPlayQueueItem = viewModel::playQueueItem,
                            onSelectGroup = viewModel::selectGroup,
                            onRetry = viewModel::retry,
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!demoMode) viewModel.start()
    }

    override fun onPause() {
        if (!demoMode) viewModel.stop()
        super.onPause()
    }

    /**
     * Route the remote's media and volume keys to Sonos rather than to this device,
     * so the app behaves like a real playback surface.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (demoMode) return super.dispatchKeyEvent(event)

        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.adjustVolume(VOLUME_STEP)
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.adjustVolume(-VOLUME_STEP)
                true
            }

            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.toggleMute()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.togglePlayPause()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.togglePlayPause()
                true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.next()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.previous()
                true
            }

            else -> false
        }

        return handled || super.dispatchKeyEvent(event)
    }

    private companion object {
        const val VOLUME_STEP = 4
    }
}
