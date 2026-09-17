package com.pieter.atomfx.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The four major FX trading sessions (Pieter's ask, 2026-09-17) — a pure clock feature, deliberately
 * with zero `signals.json`/backend involvement: session hours are a fixed calendar convention, not a
 * trading number, so there is nothing here for Rule #1 to guard.
 *
 * Each session's open/close is defined in that city's own real local time via a real IANA [ZoneId]
 * (never a fixed UTC offset) so the platform's own timezone database — not hand-rolled DST math —
 * decides how each session's UTC-equivalent hours shift across the year. Tokyo has no DST at all;
 * Sydney, London, and New York each observe their own region's DST on their own schedule, and this
 * is exactly why a fixed-UTC-band shortcut would quietly drift wrong twice a year.
 *
 * Hours are the commonly-cited retail convention (Pieter's call, 2026-09-17, after flagging that
 * "session hours" is itself a convention choice, not one universal fact) — tune here if a different
 * source's hours are ever preferred; nothing else needs to change.
 */
enum class TradingSession(val displayName: String, val zoneId: ZoneId, val openHour: Int, val closeHour: Int) {
    SYDNEY("Sydney", ZoneId.of("Australia/Sydney"), 7, 16),
    TOKYO("Tokyo", ZoneId.of("Asia/Tokyo"), 9, 18),
    LONDON("London", ZoneId.of("Europe/London"), 8, 17),
    NEW_YORK("New York", ZoneId.of("America/New_York"), 8, 17),
}

/**
 * [isOpen] — whether [session] is inside its own local trading hours at [at].
 * [windowStart]/[windowEnd] — the CURRENT window if open, else the NEXT upcoming window — always a
 * real, concrete pair of instants, so a caller never needs its own "is this today or tomorrow" logic.
 * [nextTransition] — [windowEnd] if open (when it closes), else [windowStart] (when it next opens).
 */
data class SessionState(
    val session: TradingSession,
    val isOpen: Boolean,
    val windowStart: Instant,
    val windowEnd: Instant,
    val nextTransition: Instant,
)

/** [at]'s state for this session — see [SessionState]'s own doc for what each field means. */
fun TradingSession.stateAt(at: Instant): SessionState {
    val local = at.atZone(zoneId)
    val todayOpen = local.withHour(openHour).withMinute(0).withSecond(0).withNano(0)
    val todayClose = local.withHour(closeHour).withMinute(0).withSecond(0).withNano(0)
    return when {
        !local.isBefore(todayOpen) && local.isBefore(todayClose) ->
            SessionState(this, true, todayOpen.toInstant(), todayClose.toInstant(), todayClose.toInstant())
        local.isBefore(todayOpen) ->
            SessionState(this, false, todayOpen.toInstant(), todayClose.toInstant(), todayOpen.toInstant())
        else -> { // already past today's close -> next window is tomorrow
            val tomorrowOpen = todayOpen.plusDays(1)
            val tomorrowClose = todayClose.plusDays(1)
            SessionState(this, false, tomorrowOpen.toInstant(), tomorrowClose.toInstant(), tomorrowOpen.toInstant())
        }
    }
}

/** All four sessions' state at [at], in [TradingSession] declaration order (Sydney→Tokyo→London→NY). */
fun allSessionStatesAt(at: Instant): List<SessionState> = TradingSession.entries.map { it.stateAt(at) }

/**
 * True when 2+ sessions are open at [at] — the highest-volume, highest-volatility windows
 * (London-NY and Tokyo-London are the only real overlaps this session set produces). Drives the
 * header glyph's "worth a glance" dot — Pieter's ask was "know when sessions start"; an overlap is
 * the moment that's most actionable to actually glance at, not just any single session being open.
 */
fun isOverlapActiveAt(at: Instant): Boolean = allSessionStatesAt(at).count { it.isOpen } >= 2

/**
 * The overlapping instant range between two windows, or null if they don't intersect at all.
 * Used to highlight overlap windows (the highest-volume, highest-volatility stretches) on the
 * timeline graphic — not just detect that one is active ([isOverlapActiveAt]'s job).
 */
fun overlapRange(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Pair<Instant, Instant>? {
    val start = if (aStart.isAfter(bStart)) aStart else bStart
    val end = if (aEnd.isBefore(bEnd)) aEnd else bEnd
    return if (start.isBefore(end)) start to end else null
}

/** "2h 14m" / "45m" style, matching CalendarSheet.kt's own relativeCountdown formatting. */
fun formatCountdown(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours <= 0 -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}
