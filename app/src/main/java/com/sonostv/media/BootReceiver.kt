package com.sonostv.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the media session back without anyone opening the app, so the launcher can show
 * what Sonos is playing straight after the TV powers on or the app is updated.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> NowPlayingService.start(context)
        }
    }
}
