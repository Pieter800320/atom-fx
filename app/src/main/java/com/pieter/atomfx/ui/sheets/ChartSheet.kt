package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.pieter.atomfx.data.model.MomentumSeries
import com.pieter.atomfx.data.model.PercentBBoardBlock
import com.pieter.atomfx.data.model.Signals
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
 * 2026-09-10 (Pieter's ask) — long-press a wheel node now opens the per-pair %B oscillator here,
 * not the D1/H4/H1 close-price line this sheet showed before. Price itself isn't lost — Pair
 * Sheet's own header (`Spark3Row`) already shows D1/H4/H1 mini sparklines, always visible, no tap
 * needed — this sheet's own bigger LineChart was a genuine duplicate of that, just larger; %B was
 * three taps deep (tap pair → Breakdown tab → scroll) and is a chart-shaped read, a better fit for
 * the wheel's own "long-press for the technical view" gesture. No D1/H4/H1 switcher any more —
 * %B is D1-only by design (`bb_touch.py`'s own 12-period config), unlike price; keeping a
 * switcher pointing at nothing would be worse than removing it.
 *
 * 2026-09-10 (2nd, Pieter's ask) — below the pair's own %B, two more compact cards: the base and
 * quote currencies' own `percentBCurrency` reads. Pieter's own workflow, talked through first: a
 * currency-level "USD looks fragile" read (Currency %B rolling over + CSM weakening) only becomes
 * an actual trade candidate once you check whether THIS pair's other leg agrees — so putting both
 * currencies' own %B right on the pair sheet, next to the pair's own %B, is where that comparison
 * actually happens. Deliberately three SEPARATE small charts, not one merged chart — pair %B
 * (price position within THIS pair's own bands) and currency %B (each currency's own stretch
 * across ALL its pairs) are related but different reads; overlaying up to 6 lines (2 per chart)
 * onto one chart would trade away the "did it cross its own signal" readability that makes any of
 * this useful. `pair` is always a plain 6-char code (`"EURUSD"`, no slash) throughout this app, so
 * `take(3)`/`takeLast(3)` recovers base/quote with no new parsing logic needed.
 */
@Composable
fun ChartSheet(pair: String, signals: Signals, colors: AtomColors) {
    val bbD1 = signals.pairs[pair]?.bbD1
    val base = pair.take(3)
    val quote = pair.takeLast(3)

    // Hoisted above both cards below — one D1/H4/H1 choice drives RSI and MACD together.
    var momentumTf by remember { mutableIntStateOf(1) } // H4 default, matching the CSM strip's own default
    val momentumSeries = signals.pairs[pair]?.momentumSeries.orEmpty()[MOMENTUM_TF_KEYS[momentumTf]]

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(pair, colors)

        if (bbD1 == null || bbD1.pctb.isEmpty()) {
            NotAvailableRow("Bollinger %B", colors)
        } else {
            Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // 2026-09-17 (2nd, Pieter's ask) — same split as the currency cards below:
                    // the pair (this card's own identity) stays the bigger, primary run; the %B
                    // reading drops to Caption/textSecondary so the two don't read as one run.
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = pair, style = AtomType.Body.copy(color = colors.textPrimary))
                        Text(text = "%B ${bbD1.pctb.last().toInt()}", style = AtomType.Caption.copy(color = colors.textSecondary))
                    }
                    val touchWord = when (bbD1.touching) {
                        "upper" -> "Still touching upper"
                        "lower" -> "Still touching lower"
                        else -> null
                    }
                    if (touchWord != null) {
                        Text(text = touchWord, style = AtomType.Body.copy(color = touchColor(bbD1.touching, colors)))
                    }
                }
                PercentBChart(bbD1, colors, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // 2026-09-18 (Pieter's restyle ask) — a bare full-width row, equal thirds, no card
        // wrapper of its own; same look HOME's own D1/H4/H1 row has below the wheel.
        ControlButtonRow(
            labels = MOMENTUM_TF_LABELS,
            selected = momentumTf,
            colors = colors,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            onSelect = { momentumTf = it },
        )
        RsiCard(momentumSeries, colors, modifier = Modifier.padding(top = 10.dp))
        MacdCard(momentumSeries, colors, modifier = Modifier.padding(top = 10.dp))

        CurrencyPercentBCard(base, signals.percentBCurrency[base], colors, modifier = Modifier.padding(top = 10.dp))
        CurrencyPercentBCard(quote, signals.percentBCurrency[quote], colors, modifier = Modifier.padding(top = 10.dp))

        Spacer(modifier = Modifier.height(EXTRA_RISE))
    }
}

@Composable
private fun CurrencyPercentBCard(currency: String, block: PercentBBoardBlock?, colors: AtomColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 14.dp)) {
        if (block == null || block.line.isEmpty()) {
            NotAvailableRow("$currency %B", colors)
            return@Column
        }
        // 2026-09-17 (Pieter's ask) — "NZD %B 14" read as one run; the currency code (the card's
        // own identity) and the %B reading (a supporting number, not the headline) now split into
        // two styles so they're visually distinct at a glance, same "identity primary, reading
        // secondary" pairing MetricCell's label/value split uses elsewhere.
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
            text = "$currency's own %B, sign-corrected across every pair it trades — not just this one.",
            style = AtomType.Caption.copy(color = colors.textMuted),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private val MOMENTUM_TF_LABELS = listOf("D1", "H4", "H1")
private val MOMENTUM_TF_KEYS = listOf("d1", "h4", "h1")

/**
 * 2026-09-17 (Pieter's ask) — RSI, one of an eventual four glance-panel indicators (%B/BandWidth
 * to join once they're computed at H4/H1 too, not just today's D1-only 12-period alert bands).
 * Driven by [ChartSheet]'s own shared D1/H4/H1 row above both this and [MacdCard], not a picker
 * of its own. Restyled 2026-09-18 (Pieter's ask) into its own card, same style the %B card above
 * uses, rather than stacked with MACD under one shared "Momentum" heading.
 */
@Composable
private fun RsiCard(series: MomentumSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 14.dp)) {
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
    Column(modifier = modifier.fillMaxWidth().background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 14.dp)) {
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

@Composable
private fun LegendItem(label: String, color: Color, colors: AtomColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).background(color, CircleShape))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
    }
}

// Same "small local copy, not shared" house style StatusStrip.kt/WatchlistScreen.kt's own
// directionColor uses. "upper" touch reads bear (price stretched up, due to revert), "lower" bull.
private fun touchColor(touching: String?, colors: AtomColors): Color = when (touching) {
    "upper" -> colors.bear
    "lower" -> colors.bull
    else -> colors.textSecondary
}
