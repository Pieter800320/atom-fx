package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.CalendarEvent
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Design §12's right edge panel, built as a sheet like everything else (Phase 5 plan note).
 *
 * 2026-09-04 (Pieter's ask) — now the dedicated calendar surface: the near-identical inline copy
 * in Insights was removed (this sheet was always one tap away via the gear bar's own calendar
 * glyph, on every tab, so it was pure duplication, not a second real surface). Expanded to make
 * use of that: events group under day headers instead of a flat list, and each row gets a
 * relative countdown ("in 3h", "Tomorrow") computed from the `iso` timestamp the backend already
 * writes per event but the app never read before — no new data, just using what's there.
 */
@Composable
fun CalendarSheet(signals: Signals, colors: AtomColors) {
    val events = signals.calendar?.events.orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle("CALENDAR", colors)

        if (events.isEmpty()) {
            Text(
                text = "No upcoming events",
                style = AtomType.Body.copy(color = colors.textMuted),
            )
            return@Column
        }

        val grouped = events.groupBy { it.day?.takeIf { d -> d.isNotBlank() } ?: "—" }
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
