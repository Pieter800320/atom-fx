package com.pieter.atomfx.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }
private const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
private const val MAX_RECORDS = 200 // sanity cap — never realistically hit, these are all edge-triggered

@Serializable
data class NotificationRecord(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val deeplink: String? = null,
    val direction: String? = null,
    // Regime Playbook proof-of-concept, 2026-09-04 — archetype_change alerts carry this so a
    // history card can assemble its contextual explanation (RegimePlaybookContent.kt) without
    // re-deriving it. Null for every other alert type.
    val regimeCode: String? = null,
    // Technical Regime Playbook, part 2 (2026-09-04) — regime_flip's own equivalent of
    // regimeCode: the H4 regime name ("Risk-On"/"Risk-Off"/"Mixed"/"Ranging") this alert
    // flipped INTO, so a history card can show the playbook entry that actually fired, not
    // whatever's live by the time it's read. Null for every other alert type.
    val regimeFlipTo: String? = null,
    // Alert Playbook pass, 2026-09-04 — structure_event's own equivalent: "BOS" or "CHoCH",
    // keying STRUCTURE_EVENT_PLAYBOOK (AlertPlaybookContent.kt). Null for every other alert type.
    // gold_signal and conviction_extreme don't need an equivalent field — both already key their
    // own playbook maps off the existing `direction` field ("bull"/"bear"); volatility_spike and
    // tf_alignment need no key at all, each is a single static entry.
    val structureKind: String? = null,
    val timestamp: Long,
    val read: Boolean = false,
)

/**
 * A durable, on-device record of every push notification the app has itself built (see
 * AtomFxMessagingService — a data-only FCM message means that's now every notification, in
 * every app state, not just the foreground case). Same `SharedPreferences` + hot `StateFlow`
 * shape as [UserPreferences] — a bounded, small list doesn't earn a new dependency (Room/
 * DataStore) any more than the settings toggles did.
 *
 * Auto-pruned to the last 30 days on every write — no background job needed, since a write
 * only happens when a notification actually fires (these are all edge-triggered, so volume
 * stays low by construction).
 */
class NotificationHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atomfx_notification_history", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<List<NotificationRecord>> = _state.asStateFlow()

    // AtomFxMessagingService (a separate write path from any UI-held instance — it constructs
    // its own NotificationHistoryStore per message, since a background service can't share a
    // Composable's `remember`-scoped instance) writes here too. SharedPreferences notifies every
    // registered listener on the same file in-process regardless of which instance wrote —
    // exactly what a `remember`-held instance in MainActivity/SettingsScreen needs to pick up a
    // service-recorded notification live (the unread badge, an already-open History screen)
    // without a restart. The listener must be held as a strong reference — Android only keeps a
    // weak one internally.
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_RECORDS) _state.value = readState()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readState(): List<NotificationRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<NotificationRecord>>(raw) }.getOrDefault(emptyList())
    }

    private fun writeState(records: List<NotificationRecord>) {
        prefs.edit().putString(KEY_RECORDS, json.encodeToString(records)).apply()
        _state.value = records
    }

    fun record(
        type: String,
        title: String,
        body: String,
        deeplink: String?,
        direction: String?,
        regimeCode: String? = null,
        regimeFlipTo: String? = null,
        structureKind: String? = null,
    ) {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        val entry = NotificationRecord(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            body = body,
            deeplink = deeplink,
            direction = direction,
            regimeCode = regimeCode,
            regimeFlipTo = regimeFlipTo,
            structureKind = structureKind,
            timestamp = System.currentTimeMillis(),
        )
        val next = (listOf(entry) + _state.value)
            .filter { it.timestamp >= cutoff }
            .take(MAX_RECORDS)
        writeState(next)
    }

    fun markAllRead() {
        if (_state.value.none { !it.read }) return
        writeState(_state.value.map { it.copy(read = true) })
    }

    private companion object {
        const val KEY_RECORDS = "records"
    }
}
