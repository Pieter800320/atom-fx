package com.pieter.atomfx.data

import android.content.Context
import com.pieter.atomfx.push.CROWD_MIN_LEVEL_CHOICES
import com.pieter.atomfx.push.CROWD_MIN_LEVEL_D1_DEFAULT
import com.pieter.atomfx.push.CROWD_MIN_LEVEL_H4_DEFAULT
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
    // Signals Roadmap §2 (Phase 1) — Structure covers both new BOS and CHoCH events (one
    // toggle, per Pieter's call); Regime covers both an H4 regime flip and a Macro Archetype
    // change (again one toggle — same "the backdrop changed" register). Setup alerts
    // (`potential_state`) retired 2026-09-17 — redundant with Recommendation alerts (below):
    // Setup fired on a pair's own cont >= 45 alone, the loosest single-factor bar in the app,
    // while Recommendation fires on the full weighted composite clearing 6.5/10 — strictly
    // richer evidence for the same "this pair just got interesting" story. Checked live before
    // removing: 9/12 pairs cleared Setup's gate in one real scan vs. only 3/12 for
    // Recommendation's, confirming Setup was the systematically noisier duplicate.
    val structureAlerts: Boolean = true,
    val regimeAlerts: Boolean = true,
    val volatilityAlerts: Boolean = true,
    val alignmentAlerts: Boolean = true,
    // Signals Roadmap §4 (Phase 3) — the conviction_extreme alert.
    val positioningAlerts: Boolean = true,
    // Signals Roadmap §5 (Phase 4, 2026-09-09) — the bb_touch alert.
    val bbTouchAlerts: Boolean = true,
    // Signals Roadmap §1 (2026-09-16) — the edge-triggered "recommendation" alert (a pair
    // newly enters the hourly-refreshed ranked.top, or flips direction while staying in it).
    val recommendationAlerts: Boolean = true,
    // Signals Roadmap §5c Phase 2 (2026-09-19) — the Crowd score alert. OFF by default, unlike every toggle above:
    // the studies behind the score found only a small D1 tendency (docs/RESEARCH_LOG.md), so it is opt-in context, not
    // a default interruption. The two minimum levels (0 = any) are applied to the push by CrowdAlertFilter.kt.
    val crowdScoreAlerts: Boolean = false,
    val crowdMinLevelD1: Int = CROWD_MIN_LEVEL_D1_DEFAULT,
    val crowdMinLevelH4: Int = CROWD_MIN_LEVEL_H4_DEFAULT,
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
            structureAlerts = prefs.getBoolean(KEY_NOTIF_STRUCTURE, true),
            regimeAlerts = prefs.getBoolean(KEY_NOTIF_REGIME, true),
            volatilityAlerts = prefs.getBoolean(KEY_NOTIF_VOLATILITY, true),
            alignmentAlerts = prefs.getBoolean(KEY_NOTIF_ALIGNMENT, true),
            positioningAlerts = prefs.getBoolean(KEY_NOTIF_POSITIONING, true),
            bbTouchAlerts = prefs.getBoolean(KEY_NOTIF_BB_TOUCH, true),
            recommendationAlerts = prefs.getBoolean(KEY_NOTIF_RECOMMENDATION, true),
            crowdScoreAlerts = prefs.getBoolean(KEY_NOTIF_CROWD, false),
            crowdMinLevelD1 = prefs.getInt(KEY_CROWD_MIN_D1, CROWD_MIN_LEVEL_D1_DEFAULT).takeIf { it in CROWD_MIN_LEVEL_CHOICES }
                ?: CROWD_MIN_LEVEL_D1_DEFAULT,
            crowdMinLevelH4 = prefs.getInt(KEY_CROWD_MIN_H4, CROWD_MIN_LEVEL_H4_DEFAULT).takeIf { it in CROWD_MIN_LEVEL_CHOICES }
                ?: CROWD_MIN_LEVEL_H4_DEFAULT,
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

    fun setStructureAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_STRUCTURE, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(structureAlerts = enabled))
    }

    fun setRegimeAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_REGIME, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(regimeAlerts = enabled))
    }

    fun setVolatilityAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_VOLATILITY, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(volatilityAlerts = enabled))
    }

    fun setAlignmentAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_ALIGNMENT, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(alignmentAlerts = enabled))
    }

    fun setPositioningAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_POSITIONING, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(positioningAlerts = enabled))
    }

    fun setBbTouchAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_BB_TOUCH, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(bbTouchAlerts = enabled))
    }

    fun setRecommendationAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_RECOMMENDATION, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(recommendationAlerts = enabled))
    }

    fun setCrowdScoreAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_CROWD, enabled).apply()
        _state.value = _state.value.copy(notifications = _state.value.notifications.copy(crowdScoreAlerts = enabled))
    }

    /** [level] must be one of [CROWD_MIN_LEVEL_CHOICES] (0 = any); anything else is ignored. */
    fun setCrowdMinLevel(timeframe: String, level: Int) {
        if (level !in CROWD_MIN_LEVEL_CHOICES) return
        when (timeframe) {
            "d1" -> {
                prefs.edit().putInt(KEY_CROWD_MIN_D1, level).apply()
                _state.value = _state.value.copy(notifications = _state.value.notifications.copy(crowdMinLevelD1 = level))
            }
            "h4" -> {
                prefs.edit().putInt(KEY_CROWD_MIN_H4, level).apply()
                _state.value = _state.value.copy(notifications = _state.value.notifications.copy(crowdMinLevelH4 = level))
            }
        }
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
        const val KEY_NOTIF_STRUCTURE = "notif_structure_alerts"
        const val KEY_NOTIF_REGIME = "notif_regime_alerts"
        const val KEY_NOTIF_VOLATILITY = "notif_volatility_alerts"
        const val KEY_NOTIF_ALIGNMENT = "notif_alignment_alerts"
        const val KEY_NOTIF_POSITIONING = "notif_positioning_alerts"
        const val KEY_NOTIF_BB_TOUCH = "notif_bb_touch_alerts"
        const val KEY_NOTIF_RECOMMENDATION = "notif_recommendation_alerts"
        const val KEY_NOTIF_CROWD = "notif_crowd_score_alerts"
        const val KEY_CROWD_MIN_D1 = "crowd_min_level_d1"
        const val KEY_CROWD_MIN_H4 = "crowd_min_level_h4"
        const val KEY_URL = "signals_url"
        const val KEY_REFRESH_MIN = "refresh_minutes"
    }
}
