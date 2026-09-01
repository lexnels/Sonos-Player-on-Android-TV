package com.sonostv.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import coil.imageLoader
import coil.request.ImageRequest
import com.sonostv.R
import com.sonostv.AppSettings
import com.sonostv.SonosController
import com.sonostv.sonos.NowPlaying
import com.sonostv.sonos.PlayState
import com.sonostv.ui.DemoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Publishes what Sonos is playing as an Android media session, so launchers and other
 * system surfaces (Projectivy's "now playing" panel, the Android TV remote app, and so on)
 * can show it and send transport commands back to us.
 *
 * The session mirrors a player that lives on the speakers rather than on this device, so
 * nothing is rendered locally — the notification exists to keep the session alive and
 * visible while the app is in the background.
 */
class NowPlayingService : Service() {

    private val settings by lazy { AppSettings.get(this) }
    private val controller by lazy { SonosController.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var session: MediaSessionCompat

    private var artUrl: String? = null
    private var artwork: Bitmap? = null
    private var artJob: Job? = null
    private var lastMetadataSignature: String? = null
    private var lastNotificationSignature: String? = null
    private var lastPlaybackState = PlaybackStateCompat.STATE_NONE
    private var lastPlaybackPosition = 0L
    private var lastPlaybackPublishedAt = 0L
    private var startedForeground = false
    private var sessionDemo = false
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "SonosTV").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(SessionCallback())
            isActive = true
        }

        if (!settings.isHomeCardStopped()) {
            ensureMonitoring()
        } else {
            session.isActive = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (settings.isHomeCardStopped()) {
            if (intent == null || intent.action == Intent.ACTION_MEDIA_BUTTON) {
                stopSelf()
                return START_NOT_STICKY
            }
            settings.setHomeCardStopped(false)
            ensureMonitoring()
        }

        handleMediaButtonIntent(intent)

        if (intent?.getBooleanExtra(EXTRA_SESSION_DEMO, false) == true) {
            sessionDemo = true
            settings.setHomeCardStopped(false)
            ensureMonitoring()
            publish(DemoData.nowPlaying)
        }

        if (!startedForeground && !settings.isHomeCardStopped()) {
            startForeground(buildNotification(
                if (sessionDemo) DemoData.nowPlaying else controller.state.value,
            ))
        }
        return if (settings.isHomeCardStopped()) START_NOT_STICKY else START_STICKY
    }

    /** [MediaButtonReceiver] throws if [Intent.EXTRA_KEY_EVENT] is missing — common after Stop. */
    private fun handleMediaButtonIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return
        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return
        MediaButtonReceiver.handleIntent(session, intent)
    }

    private fun ensureMonitoring() {
        controller.acquire()
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            controller.state.collect { state ->
                if (!sessionDemo) publish(state)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        controller.release()
        session.isActive = false
        session.release()
        shouldKeepSessionAlive = false
        super.onDestroy()
    }

    // ---- Session + notification --------------------------------------------

    private fun publish(state: NowPlaying) {
        if (settings.isHomeCardStopped()) return

        val transport = state.transport
        val track = transport?.track

        loadArtwork(track?.artUrl)

        val signature = "${track?.title}|${track?.artist}|${track?.album}|${transport?.durationMs}"
        if (signature != lastMetadataSignature) {
            lastMetadataSignature = signature
            setMetadata(state)
        }

        // Nothing loaded on the speakers means nothing to advertise; an inactive session
        // drops out of the launcher instead of showing an empty card.
        val hasContent = track?.isEmpty == false
        if (session.isActive != hasContent) session.isActive = hasContent
        shouldKeepSessionAlive = hasContent

        val playbackState = when (transport?.state) {
            PlayState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PlayState.TRANSITIONING -> PlaybackStateCompat.STATE_BUFFERING
            PlayState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        val position = transport?.positionMs ?: 0L
        val playing = transport?.state == PlayState.PLAYING
        val now = SystemClock.elapsedRealtime()

        // Controllers extrapolate position from the playback speed, so between real
        // changes we only need to correct them when they have drifted or gone stale.
        val elapsed = now - lastPlaybackPublishedAt
        val projected = lastPlaybackPosition + if (lastPlaybackState == PlaybackStateCompat.STATE_PLAYING) elapsed else 0L
        val drifted = kotlin.math.abs(position - projected) > POSITION_DRIFT_MS
        if (playbackState != lastPlaybackState || drifted || elapsed >= PLAYBACK_RESYNC_MS) {
            lastPlaybackState = playbackState
            lastPlaybackPosition = position
            lastPlaybackPublishedAt = now
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(ACTIONS)
                    .setState(playbackState, position, if (playing) 1f else 0f)
                    .build(),
            )
        }

        // Redraw only when something the user can see actually changed, not on every tick.
        val notificationSignature = "$signature|${transport?.state}|${state.group?.name}"
        if (notificationSignature != lastNotificationSignature) {
            lastNotificationSignature = notificationSignature
            session.setSessionActivity(buildOpenAppIntent())
            startForeground(buildNotification(state))
        }
    }

    private fun setMetadata(state: NowPlaying) {
        val transport = state.transport
        val track = transport?.track
        val title = track?.title ?: getString(R.string.app_name)
        val artist = track?.artist.orEmpty()
        val album = track?.album ?: transport?.source.orEmpty()

        session.setMetadata(
            MediaMetadataCompat.Builder().apply {
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, artist)
                // Some launchers read only the display fields, others only the ones above.
                putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, album)
                // A live stream has no meaningful length; -1 tells controllers not to draw progress.
                putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    if (transport == null || transport.isStream || transport.durationMs <= 0) -1L else transport.durationMs,
                )
                artwork?.let {
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                    putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
                }
            }.build(),
        )
    }

    /**
     * Fetching the cover outlives several state updates, so it runs on its own job and
     * republishes once the bitmap is in hand rather than holding up the rest of the session.
     */
    private fun loadArtwork(url: String?) {
        if (url == artUrl) return
        artUrl = url
        artJob?.cancel()

        if (url == null) {
            artwork = null
            return
        }

        artJob = scope.launch {
            val result = imageLoader.execute(
                ImageRequest.Builder(this@NowPlayingService)
                    .data(url)
                    .size(ART_SIZE)
                    .allowHardware(false)
                    .build(),
            )
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@launch
            artwork = bitmap
            if (!settings.isHomeCardStopped()) {
                setMetadata(controller.state.value)
                startForeground(buildNotification(controller.state.value))
            }
        }
    }

    private fun startForeground(notification: Notification) {
        // Android 14 refuses to start a mediaPlayback service while the boot broadcast is
        // being handled, which is exactly when we come back after a restart. The session is
        // still worth publishing then, so fall back to the unrestricted special-use type.
        for (type in foregroundTypes()) {
            val started = runCatching {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            }.isSuccess
            if (started) {
                startedForeground = true
                // Publish session activity only after the notification exists; TV launchers
                // read the PendingIntent from the posted notification / media session pair.
                session.setSessionActivity(buildOpenAppIntent())
                return
            }
        }
        stopSelf()
    }

    private fun foregroundTypes(): List<Int> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else -> listOf(0)
    }

    private fun buildNotification(state: NowPlaying): Notification {
        val transport = state.transport
        val playing = transport?.state?.isPlaying == true
        val track = transport?.track

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(listOfNotNull(track?.artist, track?.album ?: transport?.source).joinToString(" — "))
            .setSubText(state.group?.name)
            .setLargeIcon(artwork)
            .setContentIntent(buildOpenAppIntent())
            // No delete intent — swiping the notification or TV sleep must not send STOP to Sonos.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    getString(R.string.action_previous),
                    mediaAction(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    getString(if (playing) R.string.action_pause else R.string.action_play),
                    mediaAction(PlaybackStateCompat.ACTION_PLAY_PAUSE),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    getString(R.string.action_next),
                    mediaAction(PlaybackStateCompat.ACTION_SKIP_TO_NEXT),
                ),
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    /**
     * Pauses Sonos and tears down the media session. TV launchers only drop the home-screen
     * card when the session is gone — hiding metadata on a live session is not enough.
     */
    private fun handleUserStop() {
        Log.i(TAG, "handleUserStop")
        settings.setHomeCardStopped(true)
        controller.pause()
        session.isActive = false
        shouldKeepSessionAlive = false
        startedForeground = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun mediaAction(action: Long): PendingIntent =
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, action)

    private fun buildOpenAppIntent(): PendingIntent = SessionLaunchHelper.buildOpenIntent(this)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_now_playing),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private inner class SessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            settings.setHomeCardStopped(false)
            controller.play()
        }

        override fun onPause() {
            // Android TV home-screen Stop issues pause, not stop (see androidx/media#589).
            handleUserStop()
        }

        override fun onSkipToNext() = controller.next()
        override fun onSkipToPrevious() = controller.previous()
        override fun onSeekTo(pos: Long) = controller.seekTo(pos)

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            @Suppress("DEPRECATION")
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            } ?: return super.onMediaButtonEvent(mediaButtonEvent)
            if (keyEvent.action != KeyEvent.ACTION_DOWN) return true
            return when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                    controller.togglePlayPause()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    handleUserStop()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    settings.setHomeCardStopped(false)
                    controller.play()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    handleUserStop()
                    true
                }
                else -> super.onMediaButtonEvent(mediaButtonEvent)
            }
        }

        override fun onStop() = handleUserStop()
    }

    companion object {
        private const val CHANNEL_ID = "now_playing"
        private const val NOTIFICATION_ID = 1
        private const val ART_SIZE = 512
        private const val PLAYBACK_RESYNC_MS = 5_000L
        private const val POSITION_DRIFT_MS = 2_000L

        /** True while the session advertises playable content (now-playing card visible). */
        @Volatile
        var shouldKeepSessionAlive: Boolean = false
            private set

        private const val ACTIONS = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        /** After boot or app update — not when the user pressed Stop on the home-screen card. */
        fun start(context: Context) {
            if (!AppSettings.get(context).backgroundNowPlayingEnabled()) return
            if (AppSettings.get(context).isHomeCardStopped()) return
            val intent = Intent(context, NowPlayingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** User opened the app — re-enable the home-screen card. */
        fun startFromUser(context: Context) {
            if (!AppSettings.get(context).backgroundNowPlayingEnabled()) return
            AppSettings.get(context).setHomeCardStopped(false)
            val intent = Intent(context, NowPlayingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NowPlayingService::class.java))
        }

        /** Emulator/debug: fake now-playing metadata without Sonos on the network. */
        fun startSessionDemo(context: Context) {
            val intent = Intent(context, NowPlayingService::class.java)
                .putExtra(EXTRA_SESSION_DEMO, true)
            AppSettings.get(context).setHomeCardStopped(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private const val EXTRA_SESSION_DEMO = "session_demo"
        private const val TAG = "SonosTV/Session"
    }
}
