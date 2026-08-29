package com.pieter.atomfx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Tint
import com.pieter.atomfx.ui.wheel.WheelUiState
import com.pieter.atomfx.ui.wheel.tintColor
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Design §9 — wordmark, regime + direction arrow, the flow one-liner (computed since
 * Phase 3's `WheelMapper`, shown for the first time here), `Updated HH:mm`, and a freshness
 * dot. No gear icon — Settings doesn't exist yet, and a dead tap target isn't worth adding.
 * The small "events" affordance on the right opens the Calendar panel (Design §12's right
 * edge panel); there's no gear/nav to hang it off yet, so a plain header chip summons it.
 */
@Composable
fun HeaderBar(
    state: WheelUiState,
    updated: String?,
    isFresh: Boolean,
    colors: AtomColors,
    onCalendarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ATOM FX",
                style = AtomType.Caption.copy(color = colors.textSecondary),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Updated ${formatUpdated(updated)}",
                    style = AtomType.Caption.copy(color = colors.textMuted),
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(7.dp)
                        .background(if (isFresh) colors.bull else colors.bear, CircleShape),
                )
                Text(
                    text = "  events",
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                    modifier = Modifier.clickable(onClick = onCalendarClick),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = "${state.nucleus.regimeLabel} ${regimeArrow(state.nucleus.tint)}",
                style = AtomType.Title.copy(color = tintColor(state.nucleus.tint, colors)),
            )
        }
        Text(
            text = state.nucleus.flowLine,
            style = AtomType.Caption.copy(color = colors.textSecondary),
        )
    }
}

private fun regimeArrow(tint: Tint): String = when (tint) {
    Tint.BULL -> "↑"
    Tint.BEAR -> "↓"
    Tint.WATCH, Tint.NEUTRAL -> "→"
}

private fun formatUpdated(updated: String?): String {
    val timestamp = updated ?: return "—"
    return runCatching { OffsetDateTime.parse(timestamp).format(DateTimeFormatter.ofPattern("HH:mm")) }
        .getOrDefault("—")
}
