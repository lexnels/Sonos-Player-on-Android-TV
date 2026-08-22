package com.sonostv.media

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.Rational
import com.sonostv.MainActivity

/**
 * PiP keeper and Open [PendingIntent] for the TV now-playing card.
 *
 * On API 34–35 the launcher blocks background activity starts unless the app has a
 * visible window. TV only allows PiP for approved categories (we use `ticker` for
 * now-playing); [MainActivity] auto-enters PiP on Home so Open can expand the tile.
 *
 * Open uses a broadcast [PendingIntent] with background-start opt-in because
 * `getActivity` alone is still blocked when invoked by the TV launcher.
 */
object SessionLaunchHelper {

    private const val TAG = "SonosTV/Open"
    private const val REQUEST_OPEN_APP = 102
    const val ACTION_OPEN_APP = "com.sonostv.action.OPEN_APP"

    /** True while [MainActivity] is in PiP keeping a visible window for BAL on API 34–35. */
    @Volatile
    var pipKeeperActive: Boolean = false

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

    fun configurePip(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Log.w(TAG, "PiP not supported on this device")
            return
        }
        try {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(true)
                        setSeamlessResizeEnabled(true)
                    }
                }
                .build()
            activity.setPictureInPictureParams(params)
            Log.i(TAG, "PiP params set (auto-enter=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.S})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set PiP params", e)
        }
    }

    fun enterPipKeeper(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (Build.VERSION.SDK_INT >= 36) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!NowPlayingService.shouldKeepSessionAlive) {
            Log.d(TAG, "skip PiP: no session content")
            return
        }
        if (pipKeeperActive || activity.isInPictureInPictureMode) return
        if (!activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Log.w(TAG, "skip PiP: FEATURE_PICTURE_IN_PICTURE missing")
            return
        }
        try {
            Log.i(TAG, "entering PiP keeper")
            val entered = activity.enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setAutoEnterEnabled(true)
                        }
                    }
                    .build(),
            )
            if (entered) {
                pipKeeperActive = true
            } else {
                Log.w(TAG, "enterPictureInPictureMode returned false")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "PiP keeper unavailable", e)
        }
    }

    fun exitPipKeeper() {
        pipKeeperActive = false
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
