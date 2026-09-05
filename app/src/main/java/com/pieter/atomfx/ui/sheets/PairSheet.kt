package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.PairBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.chart.LineChart
import com.pieter.atomfx.ui.components.EvidenceDot
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.PairNode

private val TABS = listOf("Overview", "Breakdown", "Correlation")

// Mirrors Home/Macro/Insights' CARD_SHAPE — the one standard card radius — but the fill is
// `surfaceRaised`, not `cardSurface`: a sheet's own background is already `surface`, and
// `cardSurface` (tuned to sit on the *screen* background, `ground`) is identical to `surface` in
// light theme, so it'd be invisible here the same way Macro's bias boxes were before that fix.
// `surfaceRaised` reliably differs from `surface` in both themes — see SheetTabs' own active-tab
// fill for the existing precedent.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * Design §14.7 — the most important surface. Overview (default, never hidden behind a tab) is
 * five informational reads — Regime / Trend / Momentum / Volatility / Structure — the same five
 * concepts the wheel itself now uses (Simplification Rework, 2026-09-05, Pieter's own call).
 * Momentum/Structure are the same per-pair content Design §14.4-§14.5 describe — reused here as
 * tabs rather than duplicated as separate ring-tap sheets (Architecture never asks for two
 * surfaces to show the same numbers twice).
 *
 * 2026-09-05 — Overview was previously a six-factor pass/fail WHY checklist (Regime/Flow/
 * Breadth/Momentum/Structure/Entry, one card per factor, a "blocked" factor in bear-tinted red):
 * that sequential gate is retired app-wide (Pieter's own call — "drop the gate, use a composite
 * score"; see `PairNode.cont`). Overview is now five plain reads, no pass/fail glyphs — colour
 * still carries real signal (e.g. Structure's CHoCH still reads bear-red, a live reversal
 * warning) but nothing is "blocking" anything any more. Flow and Breadth dropped out of this
 * checklist entirely — both remain fully intact as their own currency-level bottom sheets
 * (`CurrencyDetailSheet.kt`), just no longer framed as gate factors on a *pair's* sheet. Entry's
 * useful parts folded into Trend (ADX) and Volatility (ATR percentile); Reset Score and Setup
 * Rank dropped from the visible UI entirely (still computed quietly backend-side for
 * `conviction.py`'s extension input — nothing to change there).
 *
 * Follow-up, same day (Pieter's direct ask) — Momentum/Structure/Entry/Macro/Correlation
 * collapsed from five separate tabs into one "Breakdown" tab: each was 2-5 rows on its own (Entry
 * always has two permanently "Not available" rows — Reset score/ATR percentile aren't in the data
 * contract at all), too little content each to justify its own tab switch.
 *
 * Second follow-up, same day — Macro cut entirely: its five cross-asset lines never varied by
 * pair (no reference to the pair's own base/quote), and the same `macro_assets` data is already
 * one tap away twice over — the app-level Macro tab's full table, and any cross-asset wedge's
 * `CrossAssetSheet` (a richer 10-asset read with impact copy). Correlation promoted the other
 * way, out of Breakdown into its own tab: unlike Macro it *is* pair-relative (this pair's row of
 * the matrix), so it has nowhere else to live, and now renders as a lollipop chart of all 11
 * other pairs (not a `.take(5)` text list) — substantial enough on its own to earn the tab
 * Breakdown's other section (Momentum) still doesn't individually justify.
 * Three tabs now: "Overview" (the five reads), "Breakdown" (Momentum/Structure), "Correlation".
 */
@Composable
fun PairSheet(node: PairNode, allNodes: List<PairNode>, signals: Signals, colors: AtomColors, initialTab: Int = 0) {
    var selectedTab by remember(node.pair) { mutableIntStateOf(initialTab) }
    val pairBlock = signals.pairs[node.pair]

    Column(modifier = Modifier.fillMaxWidth()) {
        PairHeader(node, allNodes, colors)
        TfAlignmentStrip(pairBlock?.pills, colors)
        Spark3Row(node.pair, signals, colors)
        SheetTabs(TABS, selectedTab, colors) { selectedTab = it }
        when (selectedTab) {
            0 -> OverviewChecklist(node, signals, pairBlock, colors)
            1 -> BreakdownContent(pairBlock, colors)
            else -> CorrelationTabContent(node.pair, signals, colors)
        }
    }
}

@Composable
private fun BreakdownContent(pairBlock: PairBlock?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BreakdownSection("MOMENTUM", colors) { MomentumTabContent(pairBlock?.mom, colors) }
        SheetDivider(colors)
        BreakdownSection("STRUCTURE", colors) { StructureTabContent(pairBlock?.structure, colors) }
    }
}

@Composable
private fun BreakdownSection(label: String, colors: AtomColors, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textSecondary), modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** The mockup's `.spark3` — D1/H4/H1 close-price lines with their own window % change, each on a
 *  standard card. Absent entirely (not a "Not available" placeholder) when there's no spark data
 *  at all for this pair — same "don't invent a gap that isn't there" call Macro's bias baskets and
 *  evidence axes already make when their own source list is empty. */
@Composable
private fun Spark3Row(pair: String, signals: Signals, colors: AtomColors) {
    val spark = signals.spark[pair] ?: return
    // Pieter, 2026-09-03 — more room below the sparklines than a normal section gap: the header
    // (pair name/state/sparklines) should read as one block, tabs+content as "the rest".
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SparkCell("D1", spark.d1, colors, Modifier.weight(1f))
        SparkCell("H4", spark.h4, colors, Modifier.weight(1f))
        SparkCell("H1", spark.h1, colors, Modifier.weight(1f))
    }
}

@Composable
private fun SparkCell(label: String, closes: List<Double>, colors: AtomColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 10.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
            if (closes.size >= 2) {
                val pct = (closes.last() - closes.first()) / closes.first() * 100.0
                Text(
                    text = "%+.1f%%".format(java.util.Locale.US, pct),
                    style = AtomType.Caption.copy(color = if (pct >= 0) colors.bull else colors.bear),
                )
            }
        }
        if (closes.size >= 2) {
            LineChart(closes, colors, modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = 4.dp))
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = 4.dp))
        }
    }
}

@Composable
private fun PairHeader(node: PairNode, allNodes: List<PairNode>, colors: AtomColors) {
    // 2026-09-05 — ranked and worded off `cont` (the frozen Continuation Score), not the retired
    // Level/Potential gate — see the file's own top-of-file doc comment.
    val rank = allNodes.sortedByDescending { it.cont }.indexOfFirst { it.pair == node.pair } + 1
    // Pieter, 2026-09-03 — dropped the small "EUR / JPY" line; the big pair-code heading already
    // says it, just without the slash.
    Text(
        text = node.pair,
        style = AtomType.Display.copy(color = colors.textPrimary),
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = "${setupWord(node.cont)} · ${directionWord(node.direction)}",
        style = AtomType.Caption.copy(color = directionColorFor(node.direction, colors)),
    )
    Text(
        text = "Setup ${node.cont} · Rank #$rank / ${allNodes.size}",
        style = AtomType.Caption.copy(color = colors.textSecondary),
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/** Continuation-Score bands, replacing the old Level/Potential state words (2026-09-05). Bands
 *  match `rank.py`'s own `cont >= 45` qualifying threshold, not an arbitrary new split. */
private fun setupWord(cont: Int): String = when {
    cont >= 85 -> "A+ SETUP"
    cont >= 65 -> "STRONG SETUP"
    cont >= 45 -> "DEVELOPING"
    else -> "LOW SETUP"
}

// Same restraint as Macro's EVIDENCE_LIT_AMOUNT (design doc §2.4/§7.4's own glow alphas).
private const val OVERVIEW_LIT_AMOUNT = 0.08f
// EvidenceDot's own width (7dp) + the row's 10dp spacing after it — see CrossAssetSheet's
// matching XA_DOT_INDENT.
private val OVERVIEW_DOT_INDENT = 17.dp

/**
 * The five headline reads, 2026-09-05 (see this file's top-of-file doc comment for why the old
 * six-factor gate is gone). Each row still colours by real signal — just never as "blocking"
 * anything: [OverviewTint.BULL]/[BEAR] are genuine reads (Structure's CHoCH still reads bear-red,
 * a live warning), [WATCH] flags "outside the sane band" (Volatility only), [NEUTRAL] is a plain
 * informational read with nothing notable either way.
 */
private enum class OverviewTint { BULL, BEAR, WATCH, NEUTRAL }

@Composable
private fun OverviewChecklist(node: PairNode, signals: Signals, pairBlock: PairBlock?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        overviewRows(node, signals, pairBlock).forEach { row ->
            val hue = when (row.tint) {
                OverviewTint.BULL -> colors.bull
                OverviewTint.BEAR -> colors.bear
                OverviewTint.WATCH -> colors.watch
                OverviewTint.NEUTRAL -> null
            }
            val fill = hue?.let { lerp(colors.surfaceRaised, it, OVERVIEW_LIT_AMOUNT) } ?: colors.surfaceRaised
            val dotColor = hue ?: colors.textMuted
            Column(
                modifier = Modifier.fillMaxWidth().background(fill, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // Dot centred against just the title line — see EvidenceDot's own note on why a
                // dedicated CenterVertically row beats Alignment.Top + a text glyph.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EvidenceDot(color = dotColor, modifier = Modifier.padding(end = 10.dp))
                    Text(
                        text = row.label,
                        style = AtomType.Caption.copy(color = colors.textPrimary),
                    )
                }
                Text(
                    text = row.explanation,
                    style = AtomType.Body.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(start = OVERVIEW_DOT_INDENT, top = 3.dp),
                )
                Text(
                    text = row.value,
                    style = AtomType.Caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(start = OVERVIEW_DOT_INDENT, top = 3.dp),
                )
            }
        }
    }
}

private data class OverviewRow(val label: String, val tint: OverviewTint, val explanation: String, val value: String)

// 2026-09-06 — each row's timeframe is now named in its own label (H4/D1), not left implicit,
// and each is FIXED — Pieter's own settled call after weighing a togglable wheel and rejecting
// it: Regime/Trend/Momentum/Volatility form a deliberate consensus set (H4 Regime, H4 Trend, D1
// Momentum, D1 Volatility), not four independent dials. See WheelCanvas.modeFillFrac's own doc
// comment for the full mathematical reasoning (Trend has no D1/H1 variant at all; Volatility is
// D1 by construction, the same candles Momentum reads).
private fun overviewRows(node: PairNode, signals: Signals, pairBlock: PairBlock?): List<OverviewRow> {
    val regime = signals.regimeH4
    val h4Structure = pairBlock?.structure?.h4
    val dd1 = pairBlock?.mom?.dd1
    val dir = node.direction

    val regimeAligned = Factor.REGIME in node.factorsPassed
    val regimeRow = OverviewRow(
        label = "REGIME (H4)",
        tint = if (regimeAligned) OverviewTint.BULL else OverviewTint.NEUTRAL,
        explanation = if (regimeAligned) {
            "H4 regime supports this pair's ${directionWord(dir).lowercase()} bias."
        } else {
            "H4 regime is neutral or against this pair's ${directionWord(dir).lowercase()} bias."
        },
        value = "${regime?.regime ?: "—"} · ${regime?.confidence ?: "—"}",
    )

    // 2026-09-06 (Pieter's own catch) — was tinted off `node.direction`, the pair's overall
    // D1-derived bias, while the text described H4's own ADX: a pair can show real, persistent
    // H4 directional movement (ADX 25+) while the D1 composite nets neutral, and that read as a
    // grey wedge next to text saying "strong trend" — an internal contradiction, not a subtle
    // one. Tint and text now both come off `node.trendDirection` (the H4 pill), the same field
    // the wheel's own Trend wing uses (WheelCanvas.modeHue) — one source, not two.
    val h4Direction = node.trendDirection
    val trendUnconfirmed = node.adx >= 25 && h4Direction == Direction.NEUTRAL
    // Succinct on purpose (Pieter's ask) — "X is happening, good/bad/meh," one short line, not a
    // paragraph. ADX 60+ still gets its own "risky" word rather than just "strong," since a
    // reading that rare (see the Library's ADX entry) is genuinely a different situation from a
    // garden-variety 25-40, not more of the same.
    val trendRow = OverviewRow(
        label = "TREND (H4)",
        tint = when {
            trendUnconfirmed -> OverviewTint.WATCH
            h4Direction == Direction.BULL -> OverviewTint.BULL
            h4Direction == Direction.BEAR -> OverviewTint.BEAR
            else -> OverviewTint.NEUTRAL
        },
        explanation = when {
            trendUnconfirmed -> "Trending on H4, but direction unconfirmed."
            node.adx >= 60 -> "Extreme trend on H4 — exhaustion risk."
            node.adx >= 40 -> "Very strong trend on H4."
            node.adx >= 25 -> "Trending on H4."
            node.adx >= 15 -> "Trend building on H4."
            else -> "No trend on H4 — choppy."
        },
        value = "ADX ${node.adx} · ${pillAbbrevForTrend(pairBlock?.pills?.h4)}",
    )

    val momentumRow = OverviewRow(
        label = "MOMENTUM (D1)",
        tint = if (node.momentum >= 50) OverviewTint.BULL else OverviewTint.BEAR,
        explanation = when {
            node.momentum >= 50 && (dd1 ?: 0) > 0 -> "Bullish momentum, strengthening."
            node.momentum >= 50 -> "Bullish momentum."
            (dd1 ?: 0) < 0 -> "Bearish momentum, strengthening."
            else -> "Bearish momentum."
        },
        value = "MOM ${node.momentum}",
    )

    val volatilityRow = OverviewRow(
        label = "VOLATILITY (D1)",
        tint = if (node.volatility in 20..70) OverviewTint.BULL else OverviewTint.WATCH,
        explanation = when {
            node.volatility < 20 -> "Compressed — too quiet to trust a breakout yet."
            node.volatility > 70 -> "Expanding fast — widen stops, or wait for it to settle."
            else -> "Normal range — sane conditions to consider an entry."
        },
        value = "ATR percentile ${node.volatility}",
    )

    val structureRow = OverviewRow(
        label = "STRUCTURE (H4)",
        tint = when (h4Structure?.event) {
            "BOS" -> OverviewTint.BULL
            "CHoCH" -> OverviewTint.BEAR
            else -> OverviewTint.NEUTRAL
        },
        explanation = when (h4Structure?.event) {
            "BOS" -> "Fresh break of structure on H4 — confirms the existing trend."
            "CHoCH" -> "Fresh change of character on H4 — a live reversal warning."
            else -> "No recent structural event on H4."
        },
        value = h4Structure?.event?.uppercase() ?: "—",
    )

    return listOf(regimeRow, trendRow, momentumRow, volatilityRow, structureRow)
}

// Same SB/B/N/S/SS vocabulary TfAlignmentStrip's own pillAbbrev uses (that file's copy is
// private to it, not shared) — kept identical so the same pill never reads two different ways
// on the same sheet.
private fun pillAbbrevForTrend(pill: String?): String = when (pill) {
    "bull_strong" -> "SB"
    "bull" -> "B"
    "bear" -> "S"
    "bear_strong" -> "SS"
    else -> "N"
}

private fun directionWord(direction: Direction): String = when (direction) {
    Direction.BULL -> "LONG"
    Direction.BEAR -> "SHORT"
    Direction.NEUTRAL -> "NO BIAS"
}

private fun directionColorFor(direction: Direction, colors: AtomColors) = when (direction) {
    Direction.BULL -> colors.bull
    Direction.BEAR -> colors.bear
    Direction.NEUTRAL -> colors.neutral
}

// Correlation tab lives here rather than its own MomentumSheet.kt-style file to avoid a naming
// clash with the unrelated Correlation *matrix* model (data/model/Signals.kt).

private const val CORR_HIGHLIGHT = 0.75

/** Design §14.7/§26/§37 Correlation tab: the frozen `correlations` matrix, sorted by |correlation|.
 *
 *  Redesigned 2026-09-03 (Pieter's ask, "make it visual... dedicated tab") — promoted out of
 *  Breakdown into its own tab, all 11 other pairs shown (not `.take(5)`) as a lollipop chart: a
 *  -1..+1 axis per row, a stem from the zero-line to the pair's correlation, a dot at the tip.
 *  Same Canvas convention `LineChart.kt` already uses (no library, native DrawScope). Any pair at
 *  or above ±[CORR_HIGHLIGHT] gets the bull/bear-tinted dot + bold label; everything else stays a
 *  quiet grey read — this is the "duplicate exposure" signal spec §37 asks for, now a shape to
 *  scan instead of five numbers to read one at a time.
 */
@Composable
private fun CorrelationTabContent(pair: String, signals: Signals, colors: AtomColors) {
    val corr = signals.correlations
    val rowIndex = corr?.pairs?.indexOf(pair) ?: -1
    if (corr == null || rowIndex < 0) {
        NotAvailableRow("Correlation", colors)
        return
    }
    val row = corr.matrix.getOrNull(rowIndex).orEmpty()
    val rows = corr.pairs.indices
        .filter { it != rowIndex }
        .mapNotNull { i -> corr.pairs.getOrNull(i)?.let { p -> row.getOrNull(i)?.let { v -> p to v } } }
        .sortedByDescending { kotlin.math.abs(it.second) }

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { (otherPair, value) -> CorrelationLollipopRow(otherPair, value, colors) }
    }
}

@Composable
private fun CorrelationLollipopRow(otherPair: String, value: Double, colors: AtomColors) {
    val highlighted = kotlin.math.abs(value) >= CORR_HIGHLIGHT
    val hue = if (value >= 0) colors.bull else colors.bear
    val dotColor = if (highlighted) hue else colors.textMuted
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = otherPair,
            style = AtomType.Caption.copy(
                color = if (highlighted) colors.textPrimary else colors.textSecondary,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier.width(64.dp),
        )
        Canvas(modifier = Modifier.weight(1f).height(20.dp)) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val valueX = ((value + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) * size.width
            drawLine(
                color = colors.hairline,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = colors.hairline,
                start = Offset(centerX, centerY - 4.dp.toPx()),
                end = Offset(centerX, centerY + 4.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = dotColor,
                start = Offset(centerX, centerY),
                end = Offset(valueX, centerY),
                strokeWidth = 2.dp.toPx(),
            )
            drawCircle(color = dotColor, radius = (if (highlighted) 5f else 3.5f).dp.toPx(), center = Offset(valueX, centerY))
        }
        Text(
            text = "%+.2f".format(java.util.Locale.US, value),
            style = AtomType.Caption.copy(
                color = if (highlighted) hue else colors.textMuted,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            ),
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}
