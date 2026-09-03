package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Momentum
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

// Matches ScrollingPills' ELECTRIC_PILL_SHAPE — same squircle, just twice the height (a bar has
// two lines of text to carry, a pill has one).
private val MOM_BAR_SHAPE = RoundedCornerShape(11.dp)
private val MOM_BAR_HEIGHT = 52.dp
// Same "evidence" wash formula as Macro's EvidenceAxes / PairSheet's WhyChecklist — an opaque
// subtle lerp toward the hue, not an alpha-composited wash — Pieter, 2026-09-03: "make the wash
// subtler, like the treatment you gave the evidence bars in Macro."
private const val MOM_LIT_AMOUNT = 0.08f

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
    val hue = directionHue(delta, colors)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(MOM_BAR_HEIGHT)
                .background(lerp(colors.surfaceRaised, hue, MOM_LIT_AMOUNT), MOM_BAR_SHAPE)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value?.toString() ?: "—", style = AtomType.Body.copy(color = colors.textPrimary))
            if (delta != null) {
                Text(text = deltaText(delta), style = AtomType.Caption.copy(color = hue))
            }
        }
    }
}

@Composable
private fun CmpBar(cmp: Int?, colors: AtomColors, modifier: Modifier = Modifier) {
    val hue = cmpColor(cmp, colors)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "CMP", style = AtomType.Caption.copy(color = colors.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(MOM_BAR_HEIGHT)
                .background(lerp(colors.surfaceRaised, hue, MOM_LIT_AMOUNT), MOM_BAR_SHAPE)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = cmp?.toString() ?: "—", style = AtomType.Body.copy(color = colors.textPrimary))
        }
    }
}

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
