package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.BbD1
import com.pieter.atomfx.data.model.PercentBBoardBlock
import com.pieter.atomfx.ui.chart.PercentBOscillator
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * 2026-09-10 (Pieter's ask) — the per-pair Bollinger %B oscillator over `bb_touch.py`'s own
 * 12-period D1 bands (`bbD1.pctb`/`pctbSma`), computed fresh every scan — no on-device math.
 *
 * **Currently unused — deferred, deliberately NOT deleted (Pieter, 2026-09-18).** It last lived
 * on `ChartSheet`, which now shows the stock-standard 20-period %B instead as one of the four
 * glance-panel indicators; two %B charts on one sheet asking to be compared is worse than one.
 * This one is the chart of the BB *touch alert's* own bands — a different read, kept intact and
 * ready for the BB-touch-alert rework Pieter has flagged to discuss. Do not delete it as dead
 * code; that is a decision already taken the other way.
 */
@Composable
fun PercentBChart(bbD1: BbD1?, colors: AtomColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (bbD1 == null) {
            NotAvailableRow("Bollinger %B", colors)
            return@Column
        }
        PercentBOscillator(bbD1.pctb, bbD1.pctbSma, colors, dates = bbD1.pctbDates)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem("%B", colors.textSecondary, colors)
            LegendItem("Signal (SMA 12)", colors.watch, colors)
        }
    }
}

/**
 * A single currency's own `percent_b_currency` read — its %B sign-corrected across every pair it
 * trades, not just one (see `bb_touch.compute_currency_percent_b`'s own doc comment for why the
 * quote-side mirror matters). Same `PercentBOscillator` drawing as every other %B chart.
 *
 * Lives at the bottom of `CurrencyDetailSheet` as of 2026-09-18 (Pieter's call). It was
 * introduced on `ChartSheet` (2026-09-10) as a base/quote pair, sitting next to the pair's own %B
 * so "is USD stretched?" could be checked against "does this pair's other leg agree?" — once that
 * pair %B card was deferred, the comparison it existed for wasn't on that sheet any more, and a
 * currency-level read belongs on the currency's own surface. This is Currency %B's only UI
 * surface.
 */
@Composable
fun CurrencyPercentBCard(currency: String, block: PercentBBoardBlock?, colors: AtomColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (block == null || block.line.isEmpty()) {
            NotAvailableRow("$currency %B", colors)
            return@Column
        }
        // 2026-09-17 (Pieter's ask) — "NZD %B 14" read as one run; the currency code (the card's
        // own identity) and the %B reading (a supporting number, not the headline) split into two
        // styles so they're visually distinct, the same "identity primary, reading secondary"
        // pairing MetricCell's label/value split uses elsewhere.
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = currency, style = AtomType.Body.copy(color = colors.textPrimary))
            Text(text = "%B ${block.line.last().toInt()}", style = AtomType.Caption.copy(color = colors.textSecondary))
        }
        PercentBOscillator(block.line, block.signal, colors, dates = block.dates, modifier = Modifier.padding(top = 8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem("%B", colors.textSecondary, colors)
            LegendItem("Signal (SMA 12)", colors.watch, colors)
        }
        Text(
            text = "$currency's own %B, sign-corrected across every pair it trades — not just one.",
            style = AtomType.Caption.copy(color = colors.textMuted),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LegendItem(label: String, color: Color, colors: AtomColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).background(color, CircleShape))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
    }
}
