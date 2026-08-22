package com.sonostv.media

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.sonostv.MainActivity

/**
 * One-shot trampoline for the Google TV now-playing "Open" action.
 *
 * The launcher fires our session-activity [PendingIntent] while the app only has a
 * foreground service, and Android 14+ often blocks bringing an existing background
 * task forward (`balDontBringExistingBackgroundTaskStackToFg`). Starting this
 * activity in a separate task succeeds with the creator BAL opt-in, and from here
 * [MainActivity] is started from a visible foreground context.
 */
class SessionLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
