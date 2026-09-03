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
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.PairNode
import com.pieter.atomfx.ui.wheel.PotentialState

private val TABS = listOf("Overview", "Breakdown", "Correlation")

// Mirrors Home/Macro/Insights' CARD_SHAPE — the one standard card radius — but the fill is
// `surfaceRaised`, not `cardSurface`: a sheet's own background is already `surface`, and
// `cardSurface` (tuned to sit on the *screen* background, `ground`) is identical to `surface` in
// light theme, so it'd be invisible here the same way Macro's bias boxes were before that fix.
// `surfaceRaised` reliably differs from `surface` in both themes — see SheetTabs' own active-tab
// fill for the existing precedent.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * Design §14.7 — the most important surface. Overview (default, never hidden behind a tab)
 * is the six-factor WHY checklist; the blocked factor is the one visually distinct row.
 * Momentum/Structure/Entry are the same per-pair content Design §14.4-§14.6 describe — reused
 * here as tabs rather than duplicated as separate ring-tap sheets (Architecture never asks for
 * two surfaces to show the same numbers twice).
 *
 * Rebuilt 2026-09-03 against the mockup's `shPair()`: the D1/H4/H1 sparkline row (§19.1's
 * LineChart, already built for ChartSheet) now sits permanently under the header — pair-level
 * context that doesn't change with the tab — and the WHY checklist takes the same card/dot/wash
 * treatment as Macro's EVIDENCE axes, extended with a third state Macro doesn't need: the single
 * blocked factor (§25's "unmistakable" requirement) gets a bear-tinted card of its own, not just
 * red text, so it's not just "not green" but visibly *the* problem.
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
 * Breakdown's other two sections (Momentum/Structure/Entry) still don't individually justify.
 * Three tabs now: "Overview" (WHY verdict), "Breakdown" (Momentum/Structure/Entry), "Correlation".
 */
@Composable
fun PairSheet(node: PairNode, allNodes: List<PairNode>, signals: Signals, colors: AtomColors, initialTab: Int = 0) {
    var selectedTab by remember(node.pair) { mutableIntStateOf(initialTab) }
    val pairBlock = signals.pairs[node.pair]

    Column(modifier = Modifier.fillMaxWidth()) {
        PairHeader(node, allNodes, colors)
        Spark3Row(node.pair, signals, colors)
        SheetTabs(TABS, selectedTab, colors) { selectedTab = it }
        when (selectedTab) {
            0 -> WhyChecklist(node, signals, pairBlock, colors)
            1 -> BreakdownContent(node, signals, pairBlock, colors)
            else -> CorrelationTabContent(node.pair, signals, colors)
        }
    }
}

@Composable
private fun BreakdownContent(node: PairNode, signals: Signals, pairBlock: PairBlock?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BreakdownSection("MOMENTUM", colors) { MomentumTabContent(pairBlock?.mom, colors) }
        SheetDivider(colors)
        BreakdownSection("STRUCTURE", colors) { StructureTabContent(pairBlock?.structure, colors) }
        SheetDivider(colors)
        BreakdownSection("ENTRY", colors) { EntryTabContent(signals.potential[node.pair]?.setupRank, pairBlock, colors) }
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
    val rank = allNodes.sortedByDescending { it.potential }.indexOfFirst { it.pair == node.pair } + 1
    // Pieter, 2026-09-03 — dropped the small "EUR / JPY" line; the big pair-code heading already
    // says it, just without the slash.
    Text(
        text = node.pair,
        style = AtomType.Display.copy(color = colors.textPrimary),
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = "${stateWord(node.state)} · ${directionWord(node.direction)}",
        style = AtomType.Caption.copy(color = directionColorFor(node.direction, colors)),
    )
    Text(
        text = "Potential ${node.potential} · Rank #$rank / ${allNodes.size}",
        style = AtomType.Caption.copy(color = colors.textSecondary),
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

// Same restraint as Macro's EVIDENCE_LIT_AMOUNT (design doc §2.4/§7.4's own glow alphas).
private const val WHY_LIT_AMOUNT = 0.08f

@Composable
private fun WhyChecklist(node: PairNode, signals: Signals, pairBlock: PairBlock?, colors: AtomColors) {
    // Pieter, 2026-09-03 — dropped the "WHY?" caption above the checklist; the six cards read as
    // a checklist on their own, right under the Overview tab, without needing a label to say so.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        whyRows(node, signals, pairBlock).forEach { row ->
            val isBlocker = node.blockedAt == row.factor
            // Macro's EVIDENCE treatment (card + dot + subtle wash), extended with a third state
            // Macro's own axes never needed: the blocked factor gets its own bear-tinted card —
            // §25's "unmistakable," not just red text on an otherwise plain row.
            val fill = when {
                row.passed -> lerp(colors.surfaceRaised, colors.bull, WHY_LIT_AMOUNT)
                isBlocker -> lerp(colors.surfaceRaised, colors.bear, WHY_LIT_AMOUNT)
                else -> colors.surfaceRaised
            }
            val dotColor = when {
                row.passed -> colors.bull
                isBlocker -> colors.bear
                else -> colors.textMuted
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(fill, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(text = "●", style = AtomType.Body.copy(color = dotColor), modifier = Modifier.padding(end = 10.dp, top = 1.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.factor.shortLabel.uppercase(),
                        style = AtomType.Caption.copy(color = if (isBlocker) colors.bear else colors.textPrimary),
                    )
                    Text(
                        text = row.explanation,
                        style = AtomType.Body.copy(color = colors.textSecondary),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(
                        text = row.value,
                        style = AtomType.Caption.copy(color = colors.textMuted),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

private data class WhyRow(val factor: Factor, val passed: Boolean, val explanation: String, val value: String)

private fun whyRows(node: PairNode, signals: Signals, pairBlock: PairBlock?): List<WhyRow> {
    val regime = signals.regimeH4
    val flow = signals.currencyFlow
    val base = node.pair.take(3)
    val quote = node.pair.takeLast(3)
    val baseBreadth = signals.breadth["h4"]?.get(base)
    val quoteBreadth = signals.breadth["h4"]?.get(quote)
    val h4Structure = pairBlock?.structure?.h4

    return Factor.entries.map { factor ->
        val passed = factor in node.factorsPassed
        when (factor) {
            Factor.REGIME -> WhyRow(
                factor,
                passed,
                if (passed) "${regime?.regime ?: "The regime"} supports this direction." else "${regime?.regime ?: "The regime"} does not support this direction.",
                "${regime?.regime ?: "—"} · ${regime?.confidence ?: "—"}",
            )

            Factor.FLOW -> WhyRow(
                factor,
                passed,
                if (flow?.leader != null) "Flow leader ${flow.leader}, laggard ${flow.laggard}." else "Flow data not available yet.",
                if (flow?.leaderDelta != null) {
                    "${flow.leader} ${signedInt(flow.leaderDelta)} / ${flow.laggard} ${signedInt(flow.laggardDelta)}"
                } else {
                    "—"
                },
            )

            Factor.BREADTH -> WhyRow(
                factor,
                passed,
                if (baseBreadth != null) "Move is broadly supported, not just one pair." else "Breadth data not available yet.",
                if (baseBreadth != null && quoteBreadth != null) {
                    "$base ${baseBreadth.support}/${baseBreadth.total} · $quote ${quoteBreadth.support}/${quoteBreadth.total}"
                } else {
                    "—"
                },
            )

            Factor.MOMENTUM -> WhyRow(
                factor,
                passed,
                if (passed) "Composite momentum supports this direction." else "Composite momentum is against this direction, or neutral.",
                pairBlock?.mom?.cmp?.let { "CMP $it" } ?: "—",
            )

            Factor.STRUCTURE -> WhyRow(
                factor,
                passed,
                if (h4Structure != null) "H4 structure: ${h4Structure.event ?: "—"}." else "Structure data not available yet.",
                h4Structure?.event?.uppercase() ?: "—",
            )

            Factor.ENTRY -> WhyRow(
                factor,
                passed,
                if (passed) "Continuation and entry location both check out." else "Continuation isn't high enough yet, or entry is extended.",
                pairBlock?.cont?.let { "Continuation $it%" } ?: "—",
            )
        }
    }
}

private fun signedInt(value: Double?): String {
    if (value == null) return "—"
    val sign = if (value > 0) "+" else ""
    return "$sign${value.toInt()}"
}

private fun stateWord(state: PotentialState): String = when (state) {
    PotentialState.LOW -> "LOW POTENTIAL"
    PotentialState.WATCH -> "DEVELOPING"
    PotentialState.TRADEABLE -> "HIGH POTENTIAL"
    PotentialState.APLUS -> "A+ SETUP"
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
