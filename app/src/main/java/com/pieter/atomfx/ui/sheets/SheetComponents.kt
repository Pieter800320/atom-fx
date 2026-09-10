package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

// 2026-09-09 (Pieter's ask) — the label-above/small-wash-squircle shape shared by Breakdown's
// ALIGNMENT pills (TfAlignmentStrip) and MOMENTUM bars (MomentumSheet), so the two rows read as
// one visual family: "style the Momentum pills like the technical pills now for uniformity."
// Same size/corner-radius/wash either way; content stays per-caller (RowScope) since Alignment
// shows one value and MOM shows a value plus a differently-coloured delta inline beside it — only
// the shape/size/wash needed to match, not the text itself ("keep the text like MOM's pills are
// now" — MOM's own value/delta styling is untouched, just laid out inside this shared shell).
//
// 2026-09-10 (Pieter's ask) — this is now the app's one standard "non-scrolling pill" shape,
// corner radius nudged from 11dp to 8dp (slightly squarer, still clearly rounded). Every caller —
// MomentumSheet's D1/H4/H1/CMP bars, TfAlignmentStrip, CurrencyDetailSheet's CcyTfSquare, and
// RegimeSheet's own D1/H4/H1 squares (migrated onto this shared cell the same day, see
// RegimeSheet.kt's own note) — picks this up automatically from the one definition here.
//
// Same day, text-colour convention (Pieter's ask, after seeing RegimeSheet's pills and liking the
// look): a pill's main/headline text adopts the wash's own `tint` colour directly (no lighten,
// no textPrimary) — RegimeSheet already did this from the start, and it's now the rule for every
// caller above too. A pill with a secondary delta number (Momentum, Currency strength) is the one
// exception spelled out per-caller — the delta keeps its own separate delta-sign colour, since it
// answers a different question ("strengthening/weakening") than the headline value does.
private val SMALL_PILL_HEIGHT = 26.dp
private val SMALL_PILL_SHAPE = RoundedCornerShape(8.dp)

@Composable
fun SmallPillCell(
    label: String,
    tint: Color,
    colors: AtomColors,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SMALL_PILL_HEIGHT)
                .background(tint.copy(alpha = 0.18f), SMALL_PILL_SHAPE),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
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
                    // 2026-09-10 — the standard button style (AtomType.Button's own doc comment)
                    // — was AtomType.Caption; labels here (D1/H4/H1) are already all-caps, so
                    // .uppercase() is a harmless no-op, kept for consistency with every other
                    // call site of this style.
                    text = label.uppercase(),
                    style = AtomType.Button.copy(color = if (active) colors.textPrimary else colors.textMuted),
                )
            }
        }
    }
}

private val SHEET_TAB_SHAPE = RoundedCornerShape(11.dp)

/**
 * 2026-09-09 (Pieter's ask, Pair Sheet reorg) — restyled to match Settings' `ThemeControl`
 * exactly, not the old scrolling-pill treatment: PairSheet is back down to 3 fixed tabs
 * (Overview/Breakdown/Correlation — Momentum/Structure/Entry/Macro folded into Breakdown back on
 * 2026-09-05), so an equal-width Row needs no horizontal scroll any more. This was also part of
 * the "everything reads as an undifferentiated pill" complaint that started the reorg — a real
 * segmented control here now visually distinguishes an actual tap target from the small read-only
 * pills now living inside Breakdown's ALIGNMENT section (`TfAlignmentStrip`).
 */
@Composable
fun SheetTabs(tabs: List<String>, selected: Int, colors: AtomColors, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) colors.controlSurfaceActive else colors.controlSurface, SHEET_TAB_SHAPE)
                    .border(1.dp, colors.controlBorder, SHEET_TAB_SHAPE)
                    .pressWash(SHEET_TAB_SHAPE) {
                        if (!active) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // 2026-09-10 — the standard button style (AtomType.Button's own doc comment)
                    // — was AtomType.Body ("Overview").
                    text = tab.uppercase(),
                    style = AtomType.Button.copy(color = if (active) colors.textPrimary else colors.textMuted),
                    maxLines = 1,
                )
            }
        }
    }
}
