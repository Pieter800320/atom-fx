package com.pieter.atomfx.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Functional Spec §9 / Architecture §8.1 — the stored `system | dark | light` override. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

const val DEFAULT_SIGNALS_URL =
    "https://raw.githubusercontent.com/Pieter800320/atom-fx/main/data/signals.json"
const val DEFAULT_REFRESH_MINUTES = 15

data class NotificationPrefs(
    val enabled: Boolean = true,
    val goldSignal: Boolean = true,
    val levelAlerts: Boolean = true,
)

data class UserPrefsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notifications: NotificationPrefs = NotificationPrefs(),
    val signalsUrl: String = DEFAULT_SIGNALS_URL,
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
)

/**
 * A plain `SharedPreferences` wrapper (Functional Spec §9's settings are a handful of small
 * key/value toggles — DataStore would be a new dependency for no real benefit at this size).
 * Exposes [state] as a hot [StateFlow] so Compose (and `WheelViewModel`'s refresh loop) react
 * live to a change instead of needing a restart.
 */
class UserPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atomfx_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<UserPrefsState> = _state.asStateFlow()

    private fun readState() = UserPrefsState(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
        notifications = NotificationPrefs(
            enabled = prefs.getBoolean(KEY_NOTIF_ENABLED, true),
            goldSignal = prefs.getBoolean(KEY_NOTIF_GOLD, true),
            levelAlerts = prefs.getBoolean(KEY_NOTIF_LEVEL, true),
        ),
        signalsUrl = prefs.getString(KEY_URL, null) ?: DEFAULT_SIGNALS_URL,
        refreshMinutes = prefs.getInt(KEY_REFRESH_MIN, DEFAULT_REFRESH_MINUTES),
    )

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(enabled = enabled))
    }

    fun setGoldSignalEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_GOLD, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(goldSignal = enabled))
    }

    fun setLevelAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_LEVEL, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(levelAlerts = enabled))
    }

    fun setSignalsUrl(url: String) {
        val resolved = url.ifBlank { DEFAULT_SIGNALS_URL }
        prefs.edit().putString(KEY_URL, resolved).apply()
        _state.value = _state.value.copy(signalsUrl = resolved)
    }

    fun setRefreshMinutes(minutes: Int) {
        val resolved = minutes.coerceIn(5, 120)
        prefs.edit().putInt(KEY_REFRESH_MIN, resolved).apply()
        _state.value = _state.value.copy(refreshMinutes = resolved)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_NOTIF_ENABLED = "notif_enabled"
        const val KEY_NOTIF_GOLD = "notif_gold_signal"
        const val KEY_NOTIF_LEVEL = "notif_level_alerts"
        const val KEY_URL = "signals_url"
        const val KEY_REFRESH_MIN = "refresh_minutes"
    }
}
