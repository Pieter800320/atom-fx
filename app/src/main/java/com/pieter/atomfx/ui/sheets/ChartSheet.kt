package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.pieter.atomfx.ui.theme.pressWash
import kotlin.math.abs

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
 * driven by one shared D1/H4/H1/M15 row: **%B** (where price sits in its bands), **BandWidth**
 * (how wide those bands are), **RSI** (momentum extremity) and **MACD** (momentum direction/turn).
 *
 * Two things moved OFF this sheet in an earlier change, both Pieter's call:
 *
 * - **The pair's own 12-period D1 %B card is deferred, not discarded.** It is the chart of the BB
 *   *touch alert's* own bands (`bb_touch.py`, 12-period by Pieter's explicit spec), a different
 *   read from the stock-standard 20-period %B a glance panel should show, and two %B charts on
 *   one sheet asking to be compared is worse than one. The backend key (`bbD1`) and its chart
 *   (`PercentBChart.kt`) are both left intact and untouched — Pieter has a BB-touch-alert rework
 *   to discuss, and that is where this card is expected to come back.
 * - **The base/quote Currency %B cards moved to `CurrencyDetailSheet`**, at the bottom, one card
 *   on each currency's own sheet. A currency-level read belongs on the currency's own surface,
 *   not this pair-specific one; that is now Currency %B's only UI surface. It got the exact same
 *   card shell/makeover as this sheet's own four cards (2026-09-18, 2nd) — see
 *   `PercentBChart.kt::CurrencyPercentBCard`'s own doc comment.
 *
 * **2026-09-18 (2nd) restyle — Pieter's ask, "let them all look similar."** Every card shares one
 * visual template: a black plot area (`colors.ground`) with a grey header strip
 * (`colors.surfaceRaised` — the same "frame" grey the app's own card/grouping surfaces already
 * use elsewhere, e.g. Tradeable Now) reading `[name in white][period/reading, smaller and grey]`.
 * Every line across every chart is white now, except MACD's own signal line (still `watch`) — RSI
 * no longer tints bull/bear, %B's own SMA signal line is gone entirely (BandWidth's squeeze
 * dots/column keep their `watch` tint; they're an annotation on the line, not the line itself).
 * %B's threshold-line treatment (plain dashed 10/90 + solid 50, no shading) is now RSI's too — the
 * shaded 30/70 band it used to have is gone. This sheet also can no longer be swipe-dismissed
 * (`BottomSheetHost.kt`'s own `confirmValueChange`) — a tall scrollable panel made an accidental
 * swipe-to-scroll dismiss the whole sheet; a "Close" text (top right, next to the pair name) is
 * the explicit way out now, back-press/scrim-tap still work as before.
 *
 * **2026-09-18 (3rd) — Pieter's ask, "every chart should have some sort of info at the bottom, for
 * uniformity."** Every card now also has a matching grey footer strip below its plot area (the
 * shared `IndicatorCard`, `SheetComponents.kt`, gained a `footer` slot) — BandWidth's and MACD's
 * existing legends moved into it rather than floating unstyled below the chart; %B and RSI, which
 * had nothing there before, each gained one useful reading of their own (see each card's own doc
 * comment for why that specific one).
 *
 * **2026-09-18 (4th) — Pieter's ask, "what is the graph FOR?"** Talked through before building,
 * per card: %B and RSI both ask "is this stretched or normal right now" (position-in-band vs.
 * momentum-velocity); BandWidth asks "is the market quiet or normal" (volatility); MACD asks
 * "which way is momentum pointing, and is it building or fading" — a genuinely different,
 * directional question, not a stretched/normal one. Every footer now answers its own card's
 * question directly, in words, rather than showing a bare supporting number: %B's footer became
 * "Stretched high/low"/"Normal range" (was the removed signal line's own reading); BandWidth's
 * became "Quiet (squeeze)"/"Normal" (was conditional, only shown when squeezed); MACD's gained
 * "Bullish/Bearish, building/fading" alongside its existing legend; RSI's Overbought/Oversold/
 * Neutral was already the right answer, unchanged. See each card's own doc comment for the exact
 * thresholds — all reused from what the chart already draws, nothing new invented.
 *
 * Every chart reads its series straight from `signals.json` — no on-device indicator math
 * (Architecture §8.3). `pair` is always a plain 6-char code throughout this app.
 */
@Composable
fun ChartSheet(pair: String, signals: Signals, colors: AtomColors, onClose: () -> Unit) {
    // Hoisted above every card — one D1/H4/H1/M15 choice drives all four indicators together.
    var tf by remember { mutableIntStateOf(1) } // H4 default, matching the CSM strip's own default
    val tfKey = TF_KEYS[tf]
    val momentum = signals.pairs[pair]?.momentumSeries.orEmpty()[tfKey]
    val bollinger = signals.pairs[pair]?.bollingerSeries.orEmpty()[tfKey]

    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(
            pair,
            colors,
            trailingContent = {
                Text(
                    text = "Close",
                    style = AtomType.Body.copy(color = colors.textSecondary),
                    modifier = Modifier.pressWash {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClose()
                    }.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            },
        )

        // 2026-09-18 (Pieter's restyle ask) — a bare full-width row, equal thirds, no card
        // wrapper of its own; same look HOME's own D1/H4/H1 row has below the wheel.
        ControlButtonRow(
            labels = TF_LABELS,
            selected = tf,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            onSelect = { tf = it },
        )

        PercentBCard(bollinger, colors, modifier = Modifier.padding(top = 10.dp))
        BandWidthCard(bollinger, colors, modifier = Modifier.padding(top = 10.dp))
        RsiCard(momentum, colors, modifier = Modifier.padding(top = 10.dp))
        MacdCard(momentum, colors, modifier = Modifier.padding(top = 10.dp))

        Spacer(modifier = Modifier.height(EXTRA_RISE))
    }
}

// M15 added 2026-09-18 (Pieter's ask) — a genuinely separate, faster-cadence fetch
// (scanner/scan_m15.py, its own ~45-min cadence, independent of scan_h1.py's 2h one).
// Appended last, chronologically-coarse-to-fine, matching the wheel's own D1→H4→H1
// convention. The default index below (1 = H4) is unaffected by the append.
private val TF_LABELS = listOf("D1", "H4", "H1", "M15")
private val TF_KEYS = listOf("d1", "h4", "h1", "m15")

/**
 * %B — the stock-standard 20-period read (`scanner/extend/bollinger_series.py`), NOT the BB touch
 * alert's own 12-period bands; see this file's own doc comment for why the two coexist. Same
 * `PercentBOscillator` drawing every %B chart in the app shares.
 *
 * 2026-09-18 (2nd, Pieter's restyle ask) — the pair name is gone from this card's own header
 * (the sheet's own title already names the pair, right above); the signal (SMA) line is gone too
 * (an empty list is passed for it — see `PercentBOscillator`'s own doc comment) — %B is now a
 * single white line, endpoint glowing to match the other three charts.
 *
 * **2026-09-18 (4th) — the footer answers what %B is actually FOR** (Pieter's own framing,
 * talked through before building): not a supporting number, but whether price is currently
 * *stretched* (pinned near/past one of its own bands) or in its *normal* middle range. See
 * `percentBState`'s own doc comment (`SheetComponents.kt`) for the exact thresholds — the same
 * 10/90 dashed lines already drawn on this chart, nothing new invented.
 */
@Composable
private fun PercentBCard(series: BollingerSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    val reading = series?.pctb?.lastOrNull()?.let { "(20) ${it.toInt()}" } ?: "(20)"
    val state = percentBState(series?.pctb?.lastOrNull(), colors)
    IndicatorCard(
        name = "%B",
        reading = reading,
        colors = colors,
        modifier = modifier,
        footer = state?.let { (word, tint) -> { Text(text = word, style = AtomType.Caption.copy(color = tint)) } },
    ) {
        if (series == null || series.pctb.isEmpty()) {
            NotAvailableRow("Bollinger %B", colors)
        } else {
            PercentBOscillator(series.pctb, emptyList(), colors, dates = series.dates)
        }
    }
}

/**
 * BandWidth — the volatility view: how wide the same 20-period bands are, as a series rather than
 * `bb_d1`'s single `width_pct` number plus an expanding/converging word. Squeeze bars (lowest
 * BandWidth in 125 bars, Bollinger's own definition) are flagged by the backend and marked on the
 * chart.
 *
 * **2026-09-18 (4th) — the footer answers what BandWidth is actually FOR**: is the market
 * currently *quiet* (a squeeze — bands compressed, a move may be coiling) or in its *normal*
 * state. Always one or the other now (was conditional — "In squeeze" only appeared when true,
 * nothing otherwise), matching RSI's own "always says something" footer. Deliberately NOT a third
 * "Expanding" state — that would need a new width-trend threshold this project has no tuned value
 * for yet (the same caution `bb_touch.py`'s own `width_trend` already flags about itself); the
 * squeeze flag is backend-computed, not invented here, so it's the one state this footer can
 * claim honestly. The "Squeeze" legend (left) still explains the amber dots/column on the chart;
 * the state word (right) is the new purpose-answer.
 */
@Composable
private fun BandWidthCard(series: BollingerSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    val squeezedNow = series?.squeeze?.lastOrNull() == true
    val reading = series?.bandwidth?.lastOrNull()?.let { "%.2f%%".format(java.util.Locale.US, it) }
    val hasData = series != null && series.bandwidth.isNotEmpty()
    IndicatorCard(
        name = "BandWidth",
        reading = reading,
        colors = colors,
        modifier = modifier,
        footer = if (hasData) {
            {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LegendItem("Squeeze (125-bar low)", colors.watch, colors)
                    val (word, tint) = if (squeezedNow) "Quiet (squeeze)" to colors.watch else "Normal" to colors.textMuted
                    Text(text = word, style = AtomType.Caption.copy(color = tint))
                }
            }
        } else null,
    ) {
        if (series == null || series.bandwidth.isEmpty()) {
            NotAvailableRow("BandWidth", colors)
        } else {
            BandWidthChart(series.bandwidth, colors, squeeze = series.squeeze, dates = series.dates)
        }
    }
}

/**
 * RSI (14). 2026-09-17 (Pieter's ask); restyled 2026-09-18 into its own card, and again
 * 2026-09-18 (2nd) to match %B's own threshold-line/white-line template exactly. Driven by
 * [ChartSheet]'s own shared D1/H4/H1/M15 row, not a picker of its own.
 *
 * 2026-09-18 (3rd) — the footer spells out the same overbought/oversold/neutral read the 30/70
 * dashed lines already show visually, in words — same "state word" convention BandWidth's own
 * "In squeeze" and the deferred 12-period %B card's "Still touching upper/lower" already use, and
 * the same upper-is-bear/lower-is-bull tint convention those two already agree on.
 */
@Composable
private fun RsiCard(series: MomentumSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    val reading = series?.rsi?.lastOrNull()?.let { "(14) ${it.toInt()}" } ?: "(14)"
    val last = series?.rsi?.lastOrNull()
    val state: Pair<String, Color>? = when {
        last == null -> null
        last >= 70.0 -> "Overbought" to colors.bear
        last <= 30.0 -> "Oversold" to colors.bull
        else -> "Neutral" to colors.textMuted
    }
    IndicatorCard(
        name = "RSI",
        reading = reading,
        colors = colors,
        modifier = modifier,
        footer = state?.let { (word, tint) -> { Text(text = word, style = AtomType.Caption.copy(color = tint)) } },
    ) {
        if (series == null || series.rsi.isEmpty()) {
            NotAvailableRow("RSI", colors)
        } else {
            RsiOscillator(series.rsi, colors, dates = series.dates)
        }
    }
}

/**
 * MACD sibling to [RsiCard] — see that card's own doc comment. Its own signal line is the one
 * line in the whole glance panel that stays `watch`-tinted rather than going white (Pieter's
 * restyle ask) — MACD is the one chart with two overlaid lines that need telling apart. The
 * Signal/Histogram legend (2026-09-18, 3rd) lives in the footer rather than floating unstyled
 * below the chart, same as every other card's own footer.
 *
 * **2026-09-18 (4th) — the footer answers what MACD is actually FOR**, a genuinely different
 * question from the other three cards: not "is this stretched," but "which way is momentum
 * pointing, and is it building or fading" — see [macdState]'s own doc comment for the exact read.
 */
@Composable
private fun MacdCard(series: MomentumSeries?, colors: AtomColors, modifier: Modifier = Modifier) {
    val histogram = series?.macdHistogram
    val hasData = !histogram.isNullOrEmpty()
    val state = histogram?.let { macdState(it, colors) }
    IndicatorCard(
        name = "MACD",
        reading = "(12, 26, 9)",
        colors = colors,
        modifier = modifier,
        footer = if (hasData) {
            {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendItem("Signal", colors.watch, colors)
                        LegendItem("Histogram", colors.bull, colors)
                    }
                    state?.let { (word, tint) -> Text(text = word, style = AtomType.Caption.copy(color = tint)) }
                }
            }
        } else null,
    ) {
        if (series == null || series.macdHistogram.isEmpty()) {
            NotAvailableRow("MACD", colors)
        } else {
            MacdOscillator(series.macdLine, series.macdSignal, series.macdHistogram, colors, dates = series.dates)
        }
    }
}

/**
 * MACD's own "what is this graph for" read (2026-09-18, Pieter's own framing, talked through
 * before building): direction from the histogram's own sign (same bull/bear convention the bars
 * themselves are already tinted with — positive = MACD above signal = bullish momentum), plus
 * whether that momentum is *building* or *fading* — the absolute histogram value growing away
 * from zero vs. shrinking back toward it, bar over bar. Deliberately a plain sign comparison
 * (`abs(last) > abs(prev)`), not a magnitude threshold — no new tuned number invented, the same
 * caution `BandWidthCard`'s own doc comment gives for not inventing a width-trend threshold.
 * Needs at least 2 histogram points for the building/fading half; falls back to direction alone
 * when there's only 1.
 */
private fun macdState(histogram: List<Double>, colors: AtomColors): Pair<String, Color>? {
    val last = histogram.lastOrNull() ?: return null
    val direction = if (last >= 0.0) "Bullish" to colors.bull else "Bearish" to colors.bear
    val momentum = if (histogram.size >= 2) {
        if (abs(last) > abs(histogram[histogram.size - 2])) "building" else "fading"
    } else null
    val text = if (momentum != null) "${direction.first}, $momentum" else direction.first
    return text to direction.second
}
