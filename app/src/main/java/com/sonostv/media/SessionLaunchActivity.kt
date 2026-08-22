package com.sonostv.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import com.sonostv.MainActivity

/**
 * Trampoline for the TV now-playing "Open" action, plus a transparent background
 * "keeper" window while music plays.
 *
 * Android 14+ blocks background activity launches when the app only has a foreground
 * service (`callingUidHasAnyVisibleWindow: false`). Stop/play work via [MediaSession]
 * callbacks; Open fires a [PendingIntent] to start an activity and is silently blocked.
 *
 * While the user is on the home screen we keep this activity alive in a separate,
 * touch-through, invisible task so the process retains a visible window. Open then
 * delivers [onNewIntent] here and we can start [MainActivity] from a foreground context.
 */
class SessionLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        if (intent.getBooleanExtra(EXTRA_KEEPER, false)) {
            Log.i(TAG, "keeper started")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            )
            return
        }
        openMain()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.getBooleanExtra(EXTRA_KEEPER, false)) {
            Log.i(TAG, "Open delivered via onNewIntent")
            openMain()
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
        finish()
    }

    companion object {
        private const val TAG = "SonosTV/Open"
        private const val EXTRA_KEEPER = "keeper"

        @Volatile
        private var instance: SessionLaunchActivity? = null

        val isKeeperRunning: Boolean get() = instance != null

        /** Only needed on API 34+ where TV launchers block background Open. */
        fun startKeeper(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            if (!NowPlayingService.shouldKeepSessionAlive) {
                Log.i(TAG, "keeper skipped: no session content")
                return
            }
            if (instance != null) return
            Log.i(TAG, "starting keeper")
            context.startActivity(
                Intent(context, SessionLaunchActivity::class.java).apply {
                    putExtra(EXTRA_KEEPER, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                },
            )
        }

        fun dismiss() {
            instance?.finish()
            instance = null
        }
    }
}
