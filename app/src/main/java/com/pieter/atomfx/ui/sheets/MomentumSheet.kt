package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Momentum
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.DarkColors
import com.pieter.atomfx.ui.theme.lighten

/**
 * Design §14.4 — `pairs.<PAIR>.mom` is frozen, so this tab is always fully populated.
 *
 * Redesigned 2026-09-03 (Pieter's ask) — was three plain SheetRow lines plus a separate large
 * "CMP 26 / BEARISH MOMENTUM" block below; now four identically-sized bars in a row (D1, H4, H1,
 * then CMP set apart by a wider gap since it's the composite, not a fourth timeframe), each a
 * direction-washed squircle carrying its own label, value, and (for D1/H4/H1) a coloured delta —
 * the wash itself now carries what "BEARISH MOMENTUM" used to spell out in words.
 *
 * Follow-up, same day: the timeframe label moved outside and above the square (centred), the
 * square itself now just centres a white value with a smaller coloured delta beneath, no arrow
 * glyph, and the wash uses the subtler evidence-style lerp instead of the pill's alpha wash.
 *
 * 2026-09-09 (Pieter's ask, Pair Sheet reorg follow-up) — "style the Momentum pills like the
 * technical pills now for uniformity, but... keep the text like MOM's pills are now": bars moved
 * onto the shared `SmallPillCell` (SheetComponents.kt) — same shape/height/wash as Breakdown's
 * ALIGNMENT pills directly above this section — and the delta moved inline beside the value
 * (same two font styles/colours as before, just side by side instead of stacked) so it still fits
 * the smaller shared height. Value stays plain `textPrimary` exactly as before; only the delta
 * gets the electric-pill's dark-mode `lighten()` contrast fix, since it now sits on the same
 * near-black alpha wash the old opaque lerp fill didn't need it against.
 *
 * Same day, found live (Pieter's own catch, "should it be red if the value is 58?") — the pill's
 * wash/hue was driven by the delta's sign, not the value: 58 (bullish territory, >= the 50
 * neutral line) with a negative delta rendered as a bear-red pill, contradicting this same
 * sheet's Overview tab (`overviewRows`' momentumRow: `tint = if (momentum >= 50) BULL else BEAR`)
 * and the wheel's own Momentum wing (`WheelCanvas.modeHue`: same `>= 50` rule) — both of which
 * read this identical number as bullish. Now the pill wash/value colour is value-based (matching
 * both), and only the small delta number keeps its own delta-sign colour — still shows
 * strengthening/weakening without contradicting the headline read.
 */
@Composable
fun MomentumTabContent(mom: Momentum?, colors: AtomColors) {
    if (mom == null) {
        NotAvailableRow("Momentum", colors)
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        MomBar("D1", mom.d1, mom.dd1, colors, Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        MomBar("H4", mom.h4, mom.dh4, colors, Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        MomBar("H1", mom.h1, mom.dh1, colors, Modifier.weight(1f))
        // A little more room before CMP — it's the composite, not a fourth timeframe reading.
        Spacer(modifier = Modifier.width(16.dp))
        CmpBar(mom.cmp, colors, Modifier.weight(1f))
    }
}

@Composable
private fun MomBar(label: String, value: Int?, delta: Int?, colors: AtomColors, modifier: Modifier = Modifier) {
    val hue = valueHue(value, colors)
    val isDark = colors == DarkColors
    val deltaHue = directionHue(delta, colors)
    val deltaColor = if (isDark) lighten(deltaHue, 0.45f) else deltaHue
    SmallPillCell(label, hue, colors, modifier) {
        Text(text = value?.toString() ?: "—", style = AtomType.Body.copy(color = colors.textPrimary))
        if (delta != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = deltaText(delta), style = AtomType.Caption.copy(color = deltaColor))
        }
    }
}

@Composable
private fun CmpBar(cmp: Int?, colors: AtomColors, modifier: Modifier = Modifier) {
    val hue = cmpColor(cmp, colors)
    SmallPillCell("CMP", hue, colors, modifier) {
        Text(text = cmp?.toString() ?: "—", style = AtomType.Body.copy(color = colors.textPrimary))
    }
}

// Value-based, matching this same sheet's Overview tab (`overviewRows`' momentumRow) and the
// wheel's own Momentum wing (`WheelCanvas.modeHue`) — the pill's headline colour, not the delta's.
private fun valueHue(value: Int?, colors: AtomColors): Color = when {
    value == null -> colors.neutral
    value >= 50 -> colors.bull
    else -> colors.bear
}

// Delta-sign-based — used only for the small delta number's own colour (strengthening/weakening),
// never for the pill's headline wash any more (see this file's own doc comment).
private fun directionHue(delta: Int?, colors: AtomColors): Color = when {
    delta == null -> colors.neutral
    delta > 0 -> colors.bull
    delta < 0 -> colors.bear
    else -> colors.neutral
}

private fun deltaText(delta: Int): String {
    val sign = if (delta > 0) "+" else ""
    return "$sign$delta"
}

private fun cmpColor(cmp: Int?, colors: AtomColors) = when {
    cmp == null -> colors.textMuted
    cmp >= 60 -> colors.bull
    cmp <= 40 -> colors.bear
    else -> colors.watch
}
