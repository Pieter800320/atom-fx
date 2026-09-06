package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash

/** Design §13: "surface, 20px top corners, drag handle, a Title, then content. Numbers tabular."
 *
 * [trailingContent] (2026-09-04, the Reading Window's book+ entry point) is optional and defaults
 * to none — every existing caller keeps the plain centred title untouched; only a sheet that
 * offers a Reading Window opts in, right-aligned on the same row as the heading. */
@Composable
fun SheetTitle(text: String, colors: AtomColors, trailingContent: (@Composable () -> Unit)? = null) {
    if (trailingContent == null) {
        Text(
            text = text,
            style = AtomType.Title.copy(color = colors.textPrimary),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = AtomType.Title.copy(color = colors.textPrimary))
        trailingContent()
    }
}

/** A single aligned label/value row — Design §13: "no dense tables, use aligned rows." */
@Composable
fun SheetRow(label: String, value: String, colors: AtomColors, valueColor: Color = colors.textPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = AtomType.Body.copy(color = colors.textSecondary))
        Text(text = value, style = AtomType.Body.copy(color = valueColor))
    }
}

/** A quiet section separator between groups of rows. */
@Composable
fun SheetDivider(colors: AtomColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(colors.hairline),
    )
}

/** A small horizontal bar meter (Design §14.3 breadth bars, reused wherever a 0..1 fraction needs a visual). */
@Composable
fun BarMeter(fraction: Float, color: Color, colors: AtomColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(colors.hairline, RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
    }
}

/** Text shown wherever an EXTEND field this sheet wants isn't in the feed yet (Architecture §4.2 — normal, not a bug). */
@Composable
fun NotAvailableRow(label: String, colors: AtomColors) {
    SheetRow(label = label, value = "Not available yet", colors = colors, valueColor = colors.textMuted)
}

// = WheelScreen's own `TF_BUTTON_SHAPE` (matches StatusStrip's Summary-button CARD_SHAPE).
private val CONTROL_BUTTON_SHAPE = RoundedCornerShape(14.dp)

/**
 * HOME's own D1/H4/H1 button row look (WheelScreen's `TimeframeButtons`, moved here 2026-09-06 so
 * the Chart sheet's timeframe row can match it exactly rather than hand-copying the recipe):
 * equal-width pills, `controlBorder`, 14/12dp padding around a plain-weight Caption label, active
 * = textPrimary, inactive = textMuted. The active pill's fill is `controlSurfaceActive` (2026-09-06
 * follow-up, Pieter's ask — matches [RecommendationGlyph]'s own fill in light theme; a no-op in
 * dark theme, see that token's own doc comment), the inactive fill stays plain `controlSurface`.
 */
@Composable
fun ControlButtonRow(
    labels: List<String>,
    selected: Int,
    colors: AtomColors,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) colors.controlSurfaceActive else colors.controlSurface, CONTROL_BUTTON_SHAPE)
                    .border(1.dp, colors.controlBorder, CONTROL_BUTTON_SHAPE)
                    .pressWash(CONTROL_BUTTON_SHAPE) {
                        if (!active) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = AtomType.Caption.copy(
                        color = if (active) colors.textPrimary else colors.textMuted,
                        fontWeight = FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

// Exactly ScrollingPills' ELECTRIC_PILL_HEIGHT/SHAPE (matched there to the wheel's own Currency
// Flow Ticker chips) — Pieter, 2026-09-03 follow-up: "exactly the same size... text included," so
// this mirrors that recipe wholesale rather than approximating it with wrap-content padding.
private val SHEET_TAB_HEIGHT = 26.dp
private val SHEET_TAB_SHAPE = RoundedCornerShape(11.dp)

/**
 * Scrolling-pill-style tab row (Design §15). Horizontally scrollable — Phase 9 grew PairSheet
 * to 6 tabs, past what a fixed non-scrolling Row could fit without individual labels wrapping.
 *
 * Aesthetics pass, 2026-09-03 — control treatment (Color.kt): fill/border always
 * `controlSurface`/`controlBorder` (was `surfaceRaised`/`surface`, no border, full capsule), the
 * selected tab distinguished by text colour alone, same convention the wheel's own corner buttons
 * use since dropping their white selected-state border.
 */
@Composable
fun SheetTabs(tabs: List<String>, selected: Int, colors: AtomColors, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isOn = index == selected
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(SHEET_TAB_HEIGHT)
                    // Item Library #04 — pressWash() masks the ripple to this pill's own rounded
                    // shape instead of letting it bleed past the rounded corners as a rectangle.
                    .background(colors.controlSurface, SHEET_TAB_SHAPE)
                    .border(1.dp, colors.controlBorder, SHEET_TAB_SHAPE)
                    .pressWash(SHEET_TAB_SHAPE) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    }
                    // No vertical padding — ELECTRIC_PILL_HEIGHT already fixes the height;
                    // CenterVertically below centres the text within it (ScrollingPills' own fix
                    // for the same "8dp padding on top of a fixed height clips the text" bug).
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab,
                    style = AtomType.Caption.copy(
                        color = if (isOn) colors.textPrimary else colors.textSecondary,
                        fontWeight = FontWeight.Normal,
                    ),
                )
            }
        }
    }
}
