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
 * True when 2+ sessions are open at [at] — the highest-volume, highest-volatility windows.
 * With the standard hours in use, every adjacent pair overlaps somewhat (Sydney-Tokyo,
 * Tokyo-London, London-New York — corrected 2026-09-17; an earlier comment here claimed only
 * the latter two existed, which the timeline graphic's own on-device testing disproved). Drives
 * the header glyph's "worth a glance" dot — an overlap is the moment actually worth glancing at,
 * not just any single session being open (something is open most of the day regardless).
 */
fun isOverlapActiveAt(at: Instant): Boolean = allSessionStatesAt(at).count { it.isOpen } >= 2

/**
 * Every occurrence of this session whose window intersects [windowStart, windowEnd) at all, each
 * clipped to that range. A session recurs every 24h and lasts under 24h, so at most two
 * occurrences can overlap any 24h window — one already under way whose start precedes
 * [windowStart] (the previous cycle's tail) and/or one just starting whose end follows
 * [windowEnd] (the next cycle's head). This is exactly what happens when a session's own local
 * hours cross the reference window's day boundary (e.g. Sydney's evening-opening session,
 * viewed against most Western device timezones' midnight) — 2026-09-17 (Pieter's own catch: a
 * fixed calendar-day timeline needs this to draw that session as two segments, one at each edge
 * of the bar, instead of silently dropping the wrapped portion).
 */
fun TradingSession.occurrencesOverlapping(windowStart: Instant, windowEnd: Instant): List<Pair<Instant, Instant>> {
    val result = mutableListOf<Pair<Instant, Instant>>()
    var probe = windowStart
    var guard = 0
    while (probe.isBefore(windowEnd) && guard < 4) {
        val state = stateAt(probe)
        if (state.windowStart.isBefore(windowEnd) && state.windowEnd.isAfter(windowStart)) {
            result.add(state.windowStart to state.windowEnd)
        }
        probe = state.windowEnd.plusSeconds(1)
        guard++
    }
    return result
}

/**
 * The best instant to anchor a 24h timeline graphic near [near], chosen so as few sessions as
 * possible need drawing as two wrapped segments (occurrencesOverlapping's own "midnight-crossing"
 * case) — 2026-09-17, Pieter's own design catch: a linear 24h axis always has exactly one seam,
 * and whichever session is mid-session exactly there is the one that gets split; conventional FX
 * session-hours graphics dodge this by anchoring at a session HANDOFF (one closing as another
 * opens) instead of an arbitrary calendar midnight.
 *
 * With the standard hours in use, New York's close and Sydney's open land on the same instant
 * today — by design, that's the whole point of "the market never sleeps" — but this is NOT a
 * fixed, hardcodable fact: Sydney and New York observe daylight saving on opposite hemispheres'
 * schedules, so the exact handoff can drift by an hour or so at some points in the year. Rather
 * than hardcode "anchor at Sydney's open", this tries each session's own current-cycle open
 * instant as a candidate and picks whichever leaves the fewest OTHER sessions still open at that
 * exact moment — recomputed fresh every call, so it stays correct through any DST combination
 * without needing to know which pair of sessions happens to hand off cleanly today.
 */
fun bestTimelineAnchor(near: Instant): Instant {
    fun otherSessionsStraddling(candidate: Instant): Int =
        TradingSession.entries.count { session ->
            val state = session.stateAt(candidate)
            // Genuinely mid-session at `candidate` -- not just another session that ALSO happens
            // to open exactly there, which wouldn't need splitting either.
            state.isOpen && state.windowStart.isBefore(candidate)
        }
    val candidates = TradingSession.entries.map { it.stateAt(near).windowStart }
    return candidates.minByOrNull { otherSessionsStraddling(it) } ?: near
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
