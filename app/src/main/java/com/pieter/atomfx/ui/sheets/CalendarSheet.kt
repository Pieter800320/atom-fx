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

/** Design §12's right edge panel, built as a sheet like everything else (Phase 5 plan note). */
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

        events.forEachIndexed { index, event ->
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
