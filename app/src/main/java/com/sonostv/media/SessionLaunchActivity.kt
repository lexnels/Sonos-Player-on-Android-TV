package com.sonostv.media

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sonostv.MainActivity

/**
 * Open [PendingIntent] for the TV now-playing card.
 *
 * Open uses a broadcast [PendingIntent] with background-start opt-in because
 * `getActivity` alone is still blocked when invoked by the TV launcher on Android 14+.
 */
object SessionLaunchHelper {

    private const val TAG = "SonosTV/Open"
    private const val REQUEST_OPEN_APP = 102
    const val ACTION_OPEN_APP = "com.sonostv.action.OPEN_APP"

    /**
     * BAL mode for PendingIntent / activity starts from the now-playing Open action.
     * See [android.app.ActivityOptions] — ALLOW_ALWAYS (4) needs API 36+.
     */
    fun balStartMode(): Int = when {
        Build.VERSION.SDK_INT >= 36 -> 4 // MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        Build.VERSION.SDK_INT >= 35 -> 3 // MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        else -> 0
    }

    fun buildOpenIntent(context: Context): PendingIntent {
        val intent = Intent(context, OpenAppReceiver::class.java).apply {
            action = ACTION_OPEN_APP
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_OPEN_APP, intent, flags)
    }
}

/** Handles the TV now-playing card Open action. */
class OpenAppReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SessionLaunchHelper.ACTION_OPEN_APP) return
        Log.i(TAG, "OpenAppReceiver fired")
        NowPlayingService.startFromUser(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        }
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions.makeBasic().apply {
                setPendingIntentCreatorBackgroundActivityStartMode(SessionLaunchHelper.balStartMode())
            }.toBundle()
        } else {
            null
        }
        context.startActivity(launch, options)
    }

    private companion object {
        private const val TAG = "SonosTV/Open"
    }
}
