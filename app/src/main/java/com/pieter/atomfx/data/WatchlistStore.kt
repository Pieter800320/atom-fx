package com.pieter.atomfx.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class WatchlistItem(
    val pair: String,
    val addedAt: Long,
)

/**
 * Signals Roadmap §5 (2026-09-09, Pieter's own design) — pairs flagged to track after a BB
 * touch, since a reversal (or its absence) usually develops over the following D1 sessions,
 * not instantly; the touch alert itself is deliberately not auto-confirmed. Same
 * `SharedPreferences` + JSON + hot `StateFlow` shape [NotificationHistoryStore] already uses —
 * a small, bounded list doesn't earn a new dependency (Room/DataStore) any more than that did.
 *
 * The change listener mirrors [NotificationHistoryStore]'s own for the same reason: more than
 * one instance of this class can exist at once (the pair sheet's watch toggle and the Watchlist
 * screen itself each construct their own via `remember`), and a write from one must be visible
 * to the other without a restart.
 */
class WatchlistStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atomfx_watchlist", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<List<WatchlistItem>> = _state.asStateFlow()

    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ITEMS) _state.value = readState()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readState(): List<WatchlistItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<WatchlistItem>>(raw) }.getOrDefault(emptyList())
    }

    private fun writeState(items: List<WatchlistItem>) {
        prefs.edit().putString(KEY_ITEMS, json.encodeToString(items)).apply()
        _state.value = items
    }

    fun isWatched(pair: String): Boolean = _state.value.any { it.pair == pair }

    /** Adds [pair] if not already watched, removes it if it is — the pair sheet's toggle button. */
    fun toggle(pair: String) {
        val current = _state.value
        writeState(
            if (current.any { it.pair == pair }) current.filterNot { it.pair == pair }
            else current + WatchlistItem(pair, System.currentTimeMillis()),
        )
    }

    fun remove(pair: String) {
        writeState(_state.value.filterNot { it.pair == pair })
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
