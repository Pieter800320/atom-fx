package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * quote-side mirror matters). Same `PercentBOscillator` drawing as every other %B chart, and
 * (2026-09-18, unlike ChartSheet's own %B(20) card) still draws its own real signal line — that
 * removal was specific to the glance panel, not asked for here.
 *
 * Lives at the bottom of `CurrencyDetailSheet` as of 2026-09-18 (Pieter's call). It was
 * introduced on `ChartSheet` (2026-09-10) as a base/quote pair, sitting next to the pair's own %B
 * so "is USD stretched?" could be checked against "does this pair's other leg agree?" — once that
 * pair %B card was deferred, the comparison it existed for wasn't on that sheet any more, and a
 * currency-level read belongs on the currency's own surface. This is Currency %B's only UI
 * surface.
 *
 * **2026-09-18 (2nd) — "the same makeover" (Pieter's own words) as ChartSheet's own four cards**:
 * the shared `IndicatorCard` shell (`SheetComponents.kt`) — black plot area, grey header strip
 * reading `%B (12) <value>` (the currency code dropped, the exact same reasoning that dropped the
 * pair name from ChartSheet's %B card — `CurrencyDetailSheet`'s own title already names the
 * currency, directly above this card), and a grey footer holding the %B/Signal legend (unchanged
 * content, just restyled into the new footer panel). The old sentence-style explainer ("USD's own
 * %B, sign-corrected across every pair it trades...") is gone outright, not moved — Pieter's
 * explicit ask, "remove the explanatory text at the bottom completely."
 */
@Composable
fun CurrencyPercentBCard(currency: String, block: PercentBBoardBlock?, colors: AtomColors, modifier: Modifier = Modifier) {
    val reading = block?.line?.lastOrNull()?.let { "(12) ${it.toInt()}" } ?: "(12)"
    val hasData = block != null && block.line.isNotEmpty()
    IndicatorCard(
        name = "%B",
        reading = reading,
        colors = colors,
        modifier = modifier,
        footer = if (hasData) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem("%B", colors.textSecondary, colors)
                    LegendItem("Signal (SMA 12)", colors.watch, colors)
                }
            }
        } else null,
    ) {
        if (!hasData || block == null) {
            NotAvailableRow("$currency %B", colors)
        } else {
            PercentBOscillator(block.line, block.signal, colors, dates = block.dates)
        }
    }
}
