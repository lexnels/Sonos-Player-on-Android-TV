package com.sonostv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BackgroundStyle {
    Ambient,
    LavaLamp,
}

data class UiPrefs(
    val uiScale: Float = 1f,
    val cornerRadiusDp: Float = 18f,
    /** Null means follow the last room the user selected. */
    val defaultGroupUuid: String? = null,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.LavaLamp,
)

class AppSettings private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _value = MutableStateFlow(load())
    val value: StateFlow<UiPrefs> = _value.asStateFlow()

    private fun load(): UiPrefs = UiPrefs(
        uiScale = prefs.getFloat(KEY_UI_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE),
        cornerRadiusDp = prefs.getFloat(KEY_CORNER_RADIUS, 18f).coerceIn(MIN_RADIUS, MAX_RADIUS),
        defaultGroupUuid = prefs.getString(KEY_DEFAULT_GROUP, null),
        backgroundStyle = BackgroundStyle.entries.getOrElse(
            prefs.getInt(KEY_BACKGROUND_STYLE, BackgroundStyle.LavaLamp.ordinal),
        ) { BackgroundStyle.LavaLamp },
    )

    fun setUiScale(scale: Float) {
        val clamped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        prefs.edit().putFloat(KEY_UI_SCALE, clamped).apply()
        _value.update { it.copy(uiScale = clamped) }
    }

    fun setCornerRadius(dp: Float) {
        val clamped = dp.coerceIn(MIN_RADIUS, MAX_RADIUS)
        prefs.edit().putFloat(KEY_CORNER_RADIUS, clamped).apply()
        _value.update { it.copy(cornerRadiusDp = clamped) }
    }

    fun setDefaultGroupUuid(uuid: String?) {
        prefs.edit().putString(KEY_DEFAULT_GROUP, uuid).apply()
        _value.update { it.copy(defaultGroupUuid = uuid) }
    }

    fun setBackgroundStyle(style: BackgroundStyle) {
        prefs.edit().putInt(KEY_BACKGROUND_STYLE, style.ordinal).apply()
        _value.update { it.copy(backgroundStyle = style) }
    }

    companion object {
        const val PREFS_NAME = "sonos_tv"
        const val KEY_LAST_GROUP = "last_group_uuid"
        const val KEY_DEFAULT_GROUP = "default_group_uuid"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_CORNER_RADIUS = "corner_radius_dp"
        const val KEY_BACKGROUND_STYLE = "background_style"

        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 1.5f
        const val MIN_RADIUS = 0f
        const val MAX_RADIUS = 40f

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
