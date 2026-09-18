package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.BollingerSeries
import com.pieter.atomfx.data.model.MomentumSeries
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.chart.BandWidthChart
import com.pieter.atomfx.ui.chart.MacdOscillator
import com.pieter.atomfx.ui.chart.PercentBOscillator
import com.pieter.atomfx.ui.chart.RsiOscillator
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

// Same card shape/fill Pair sheet's own Spark3Row cards use.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

// Pieter, 2026-09-06 — "let the sheet come up slightly higher, maybe 8mm": ModalBottomSheet sizes
// itself to content (BottomSheetHost's own doc comment), so there's no separate "sheet height" to
// set — padding the content out is how you make it rise further. 8mm ≈ 50dp at the density-
// independent 160dp/inch dp is defined against (25.4mm/inch ÷ 160dp/inch).
private val EXTRA_RISE = 50.dp

/**
 * Long-press a wheel node → the per-pair technical view (Design §19.4b).
 *
 * **2026-09-18 — the glance panel is now complete and this sheet holds only it.** Pieter's own
 * four standard indicators, "each gives one a different view on a certain market dimension," all
 * driven by one shared D1/H4/H1 row: **%B** (where price sits in its bands), **BandWidth** (how
 * wide those bands are), **RSI** (momentum extremity) and **MACD** (momentum direction/turn).
 * %B and BandWidth complete the set; RSI/MACD shipped first (2026-09-17) so the visual result
 * could be judged before the rest was built.
 *
 * Two things moved OFF this sheet in the same change, both Pieter's call:
 *
 * - **The pair's own 12-period D1 %B card is deferred, not discarded.** It is the chart of the BB
 *   *touch alert's* own bands (`bb_touch.py`, 12-period by Pieter's explicit spec), a different
 *   read from the stock-standard 20-period %B a glance panel should show, and two %B charts on
 *   one sheet asking to be compared is worse than one. The backend key (`bbD1`) and its chart
 *   (`PercentBChart.kt`) are both left intact and untouched — Pieter has a BB-touch-alert rework
 *   to discuss, and that is where this card is expected to come back.
 * - **The base/quote Currency %B cards moved to `CurrencyDetailSheet`**, at the bottom, one card
 *   on each currency's own sheet. They were added here (2026-09-10) so a "is USD stretched?" read
 *   sat next to the pair's own %B; with the pair %B card deferred, the comparison they existed
 *   for isn't on this sheet any more, and a currency-level read belongs on the currency's own
 *   surface. That is now Currency %B's only UI surface.
 *
 * Every chart reads its series straight from `signals.json` — no on-device indicator math
 * (Architecture §8.3). `pair` is always a plain 6-char code throughout this app.
 */
@Composable
fun ChartSheet(pair: String, signals: Signals, colors: AtomColors) {
    // Hoisted above every card — one D1/H4/H1 choice drives all four indicators together.
    var tf by remember { mutableIntStateOf(1) } // H4 default, matching the CSM strip's own default
    val tfKey = TF_KEYS[tf]
    val momentum = signals.pairs[pair]?.momentumSeries.orEmpty()[tfKey]
    val bollinger = signals.pairs[pair]?.bollingerSeries.orEmpty()[tfKey]

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(pair, colors)

        // 2026-09-18 (Pieter's restyle ask) — a bare full-width row, equal thirds, no card
        // wrapper of its own; same look HOME's own D1/H4/H1 row has below the wheel.
        ControlButtonRow(
            labels = TF_LABELS,
            selected = tf,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            onSelect = { tf = it },
        )

        PercentBCard(pair, bollinger, colors, modifier = Modifier.padding(top = 10.dp))
        BandWidthCard(bollinger, colors, modifier = Modifier.padding(top = 10.dp))
        RsiCard(momentum, colors, modifier = Modifier.padding(top = 10.dp))
        MacdCard(momentum, colors, modifier = Modifier.padding(top = 10.dp))

        Spacer(modifier = Modifier.height(EXTRA_RISE))
    }
}

private val TF_LABELS = listOf("D1", "H4", "H1")
private val TF_KEYS = listOf("d1", "h4", "h1")

/**
 * %B — the stock-standard 20-period read (`scanner/extend/bollinger_series.py`), NOT the BB touch
 * alert's own 12-period bands; see this file's own doc comment for why the two coexist. Same
 * `PercentBOscillator` drawing every %B chart in the app shares.
 */
@Composable
private fun PercentBCard(pair: String, series: BollingerSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    IndicatorCard(colors, modifier) {
        // Same "identity primary, reading secondary" split the currency cards use — the pair is
        // this card's own identity, the %B number a supporting reading, so they don't read as one
        // run (Pieter's ask, 2026-09-17).
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = pair, style = AtomType.Body.copy(color = colors.textPrimary))
            Text(
                text = "%B (20)" + (series?.pctb?.lastOrNull()?.let { " ${it.toInt()}" } ?: ""),
                style = AtomType.Caption.copy(color = colors.textSecondary),
            )
        }
        if (series == null || series.pctb.isEmpty()) {
            NotAvailableRow("Bollinger %B", colors)
        } else {
            PercentBOscillator(series.pctb, series.pctbSma, colors, dates = series.dates, modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem("%B", colors.textSecondary, colors)
                LegendItem("Signal (SMA 20)", colors.watch, colors)
            }
        }
    }
}

/**
 * BandWidth — the volatility view: how wide the same 20-period bands are, as a series rather than
 * `bb_d1`'s single `width_pct` number plus an expanding/converging word. Squeeze bars (lowest
 * BandWidth in 125 bars, Bollinger's own definition) are flagged by the backend and marked on the
 * chart; the header says so in words when the current bar is one.
 */
@Composable
private fun BandWidthCard(series: BollingerSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    IndicatorCard(colors, modifier) {
        val squeezedNow = series?.squeeze?.lastOrNull() == true
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "BandWidth", style = AtomType.Body.copy(color = colors.textPrimary))
                Text(
                    text = series?.bandwidth?.lastOrNull()?.let { "%.2f%%".format(java.util.Locale.US, it) } ?: "",
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                )
            }
            if (squeezedNow) {
                Text(text = "In squeeze", style = AtomType.Body.copy(color = colors.watch))
            }
        }
        if (series == null || series.bandwidth.isEmpty()) {
            NotAvailableRow("BandWidth", colors)
        } else {
            BandWidthChart(series.bandwidth, colors, squeeze = series.squeeze, dates = series.dates, modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem("BandWidth", colors.textSecondary, colors)
                LegendItem("Squeeze (125-bar low)", colors.watch, colors)
            }
        }
    }
}

/**
 * RSI (14). 2026-09-17 (Pieter's ask); restyled 2026-09-18 into its own card. Driven by
 * [ChartSheet]'s own shared D1/H4/H1 row, not a picker of its own.
 */
@Composable
private fun RsiCard(series: MomentumSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    IndicatorCard(colors, modifier) {
        Text(
            text = "RSI (14)" + (series?.rsi?.lastOrNull()?.let { " ${it.toInt()}" } ?: ""),
            style = AtomType.Body.copy(color = colors.textPrimary),
        )
        if (series == null || series.rsi.isEmpty()) {
            NotAvailableRow("RSI", colors)
        } else {
            RsiOscillator(series.rsi, colors, dates = series.dates, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** MACD sibling to [RsiCard] — see that card's own doc comment. */
@Composable
private fun MacdCard(series: MomentumSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    IndicatorCard(colors, modifier) {
        Text(text = "MACD (12, 26, 9)", style = AtomType.Body.copy(color = colors.textPrimary))
        if (series == null || series.macdHistogram.isEmpty()) {
            NotAvailableRow("MACD", colors)
        } else {
            MacdOscillator(series.macdLine, series.macdSignal, series.macdHistogram, colors, dates = series.dates, modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem("MACD", colors.textPrimary, colors)
                LegendItem("Signal", colors.watch, colors)
                LegendItem("Histogram", colors.bull, colors)
            }
        }
    }
}

/** The one card shell all four glance-panel indicators share — same surface/shape/padding, so
 * they read as one panel rather than four separately-styled charts. */
@Composable
private fun IndicatorCard(colors: AtomColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
private fun LegendItem(label: String, color: Color, colors: AtomColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).background(color, CircleShape))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
    }
}
