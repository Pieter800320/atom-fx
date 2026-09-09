package com.pieter.atomfx.ui.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.CalendarEvent
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// Item Library #05 — same slide-in side panel recipe Settings/Watchlist already use, matching
// Design §12's own original spec for this surface ("right edge panel") — it shipped as a bottom
// sheet instead (Phase 5 plan note), and Pieter's ask (2026-09-09) puts it back in line with that.
private const val PANEL_WIDTH_FRACTION = 0.82f

/**
 * Design §12's right edge panel — summoned from the header calendar glyph, on every tab, not a
 * ring/node/nucleus tap (so it's a sibling top-level panel to Settings/Watchlist now, not routed
 * through BottomSheetHost/SheetTarget any more — see MainActivity.kt's own calendarOpen state).
 *
 * 2026-09-04 (Pieter's ask) — the dedicated calendar surface: the near-identical inline copy in
 * Insights was removed (this was always one tap away via the gear bar's own calendar glyph, on
 * every tab, so it was pure duplication, not a second real surface). Events group under day
 * headers instead of a flat list, and each row gets a relative countdown ("in 3h", "Tomorrow")
 * computed from the `iso` timestamp the backend writes per event.
 */
@Composable
fun CalendarSheet(signals: Signals, colors: AtomColors, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220)) }
    val dismiss: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClose()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val panelWidthPx = with(density) { (maxWidth * PANEL_WIDTH_FRACTION).toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f * entrance.value))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = dismiss,
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(PANEL_WIDTH_FRACTION)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .graphicsLayer { translationX = (1f - entrance.value) * panelWidthPx }
                .background(colors.ground)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                CalendarContent(signals, colors, onClose)
            }
        }
    }
}

@Composable
private fun CalendarContent(signals: Signals, colors: AtomColors, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val events = signals.calendar?.events.orEmpty()

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "CALENDAR", style = AtomType.Title.copy(color = colors.textPrimary))
        Text(
            text = "Close",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.pressWash {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
        )
    }

    if (events.isEmpty()) {
        Text(
            text = "No upcoming events",
            style = AtomType.Body.copy(color = colors.textMuted),
        )
        return
    }

    // 2026-09-09 fix — found live: Thursday rendered before Wednesday. groupBy preserves
    // each key's FIRST-ENCOUNTER order in the source list, not calendar order — if a
    // Thursday event happened to appear earlier in the raw (unsorted) `events` list than any
    // Wednesday one, its day header won.
    //
    // Sorting by the parsed `iso` timestamp alone turned out to be a no-op against real data
    // (found on the same check): scan_news.py's AI-search calendar call returns `time` values
    // with a timezone abbreviation attached (e.g. "08:30 ET"), which breaks its naive
    // f"{date}T{time}:00+00:00" ISO construction for essentially every event — `iso` comes
    // through blank across the board, not just occasionally, so every event landed on the
    // same sentinel and the sort changed nothing. Primary key is the weekday name instead
    // (always present, and reliable here specifically because the backend's own prompt scopes
    // every event to one Monday-Friday window — no cross-week ambiguity to worry about); the
    // parsed iso is kept only as a same-day tiebreaker for whenever it does happen to parse.
    val weekdayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val sortedEvents = events.sortedWith(
        compareBy(
            { e -> e.day?.take(3)?.let { weekdayOrder.indexOf(it) }?.takeIf { it >= 0 } ?: Int.MAX_VALUE },
            { e -> e.iso?.let { iso -> runCatching { OffsetDateTime.parse(iso) }.getOrNull() } ?: OffsetDateTime.MAX },
        ),
    )
    val grouped = sortedEvents.groupBy { it.day?.takeIf { d -> d.isNotBlank() } ?: "—" }
    grouped.entries.forEachIndexed { groupIndex, (day, dayEvents) ->
        if (groupIndex > 0) SheetDivider(colors, modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = day.uppercase(),
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(top = if (groupIndex == 0) 0.dp else 4.dp, bottom = 6.dp),
        )
        dayEvents.forEachIndexed { index, event ->
            if (index > 0) SheetDivider(colors)
            CalendarEventRow(event, colors)
        }
    }
}

@Composable
internal fun CalendarEventRow(event: CalendarEvent, colors: AtomColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = event.currency ?: "—",
            style = AtomType.Caption.copy(color = colors.bear),
            modifier = Modifier
                .background(colors.bearSoft, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            // Name-first, matching InsightsScreen's own InsightsCalendarRow ordering — was
            // "day time · name", read as a timestamp-led list rather than an event-led one.
            Text(
                text = buildString {
                    append(event.name ?: "—")
                    val meta = listOfNotNull(event.day, event.time).joinToString(" ")
                    if (meta.isNotBlank()) append(" · $meta")
                },
                style = AtomType.Body.copy(color = colors.textPrimary),
            )
            val countdown = relativeCountdown(event.iso)
            if (countdown != null) {
                Text(text = countdown, style = AtomType.Caption.copy(color = colors.watch))
            }
            Text(
                text = "Forecast ${event.forecast ?: "—"} vs previous ${event.previous ?: "—"}",
                style = AtomType.Caption.copy(color = colors.textSecondary),
            )
            if (event.note != null) {
                Text(text = event.note, style = AtomType.Caption.copy(color = colors.textMuted))
            }
        }
    }
}

/** "in 3h" / "in 2d" / "Just happened" — null if `iso` is blank or unparseable (a normal state
 *  for an event whose date search didn't resolve cleanly, not an error to surface). */
private fun relativeCountdown(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val target = try {
        OffsetDateTime.parse(iso)
    } catch (e: DateTimeParseException) {
        return null
    }
    val minutes = Duration.between(OffsetDateTime.now(target.offset), target).toMinutes()
    return when {
        minutes < -30 -> "Just happened"
        minutes < 0 -> "Just happened"
        minutes < 60 -> "in ${minutes}m"
        minutes < 60 * 24 -> "in ${minutes / 60}h"
        else -> "in ${minutes / (60 * 24)}d"
    }
}
