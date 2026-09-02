package com.pieter.atomfx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Tint
import com.pieter.atomfx.ui.wheel.WheelUiState
import com.pieter.atomfx.ui.wheel.tintColor
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Design §9 / mockup `header()` (`atom-fx-screen-kit.html`) — two rows plus the flow sub-line:
 * row 1 is the wordmark and the panel affordances (mockup's `ah-tools` slot — no gear/search
 * icon exists yet, so "brief"/"events" stand in as plain text chips); row 2 is the regime +
 * direction arrow (tinted per Design §2.5) on the left and `Updated HH:mm` + freshness dot
 * (+ `DATA STALE`, Design §8) on the right, mirroring the mockup's `ah-regime`/`ah-updated`
 * pairing. "events" opens the Calendar panel (Design §12 right edge panel); "brief" opens the
 * Recommendation panel (Design §12 left edge panel). Design §6.5: when `recommendation.headline`
 * exists, it also gets a quiet line of its own beneath the flow line, truncated, tappable
 * straight into the same Recommendation panel.
 */
@Composable
fun HeaderBar(
    state: WheelUiState,
    updated: String?,
    isFresh: Boolean,
    colors: AtomColors,
    onCalendarClick: () -> Unit,
    onRecommendationClick: () -> Unit,
    recommendationHeadline: String?,
    modifier: Modifier = Modifier,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        action()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Row 1 — wordmark + panel affordances (mockup ah-r1 #1 / ah-tools).
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
                        text = "brief",
                        style = AtomType.Caption.copy(color = colors.textSecondary),
                        modifier = Modifier.clickable { tap(onRecommendationClick) },
                    )
                    Text(
                        text = "  events",
                        style = AtomType.Caption.copy(color = colors.textSecondary),
                        modifier = Modifier.clickable { tap(onCalendarClick) },
                    )
                }
            }
            // Row 2 — regime + arrow (left, tinted) / Updated + freshness dot (right, mockup ah-r1 #2).
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.nucleus.regimeLabel} ${regimeArrow(state.nucleus.tint)}",
                    style = AtomType.Title.copy(color = tintColor(state.nucleus.tint, colors)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isFresh) {
                        Text(
                            text = "DATA STALE  ",
                            style = AtomType.Caption.copy(color = colors.bear),
                        )
                    }
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
                }
            }
            // Sub-line — currency-flow one-liner (mockup ah-sub).
            Text(
                text = state.nucleus.flowLine,
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!recommendationHeadline.isNullOrBlank()) {
                Text(
                    text = recommendationHeadline,
                    style = AtomType.Caption.copy(color = colors.textMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { tap(onRecommendationClick) }.padding(top = 2.dp),
                )
            }
        }
        // app-head border-bottom (mockup .app-head).
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
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
