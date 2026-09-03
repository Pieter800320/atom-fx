package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
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

private val TABS = listOf("Overview", "Breakdown")

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
 * contract at all), too little content each to justify its own tab switch. Two tabs now:
 * "Overview" (the WHY verdict) and "Breakdown" (everything else, one scroll).
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
            else -> BreakdownContent(node, signals, pairBlock, colors)
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
        SheetDivider(colors)
        BreakdownSection("MACRO", colors) { MacroTabContent(signals, colors) }
        SheetDivider(colors)
        BreakdownSection("CORRELATION", colors) { CorrelationTabContent(node.pair, signals, colors) }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SparkCell("D1", spark.d1, colors, Modifier.weight(1f))
        SparkCell("H4", spark.h4, colors, Modifier.weight(1f))
        SparkCell("H1", spark.h1, colors, Modifier.weight(1f))
    }
}

@Composable
private fun SparkCell(label: String, closes: List<Double>, colors: AtomColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 10.dp, vertical = 8.dp)) {
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
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(text = node.pair, style = AtomType.Display.copy(color = colors.textPrimary))
        Text(
            text = "  ${node.pair.take(3)} / ${node.pair.takeLast(3)}",
            style = AtomType.Body.copy(color = colors.textSecondary),
        )
    }
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

// Macro/Correlation tabs live here rather than their own MomentumSheet.kt-style file to avoid
// a naming clash with the unrelated Macro *screen* (Phase 9's MacroScreen.kt).

/** Design §14.7/§26 Macro tab: cross-asset support lines, spec §36's exact phrasing style, from real `macro_assets` directions. */
@Composable
private fun MacroTabContent(signals: Signals, colors: AtomColors) {
    val ma = signals.macroAssets
    val lines = listOfNotNull(
        ma["dxy"]?.direction?.let { supportLine("DXY", it, "USD bid", "USD offered") },
        ma["spx"]?.direction?.let { supportLine("SPX", it, "risk appetite", "risk aversion") },
        ma["vix"]?.direction?.let { supportLine("VIX", it, "risk aversion", "risk appetite") },
        ma["gold"]?.direction?.let { supportLine("Gold", it, "safe-haven bid", "safe-haven offered") },
        ma["copper"]?.direction?.let { supportLine("Copper", it, "growth demand", "growth concern") },
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        if (lines.isEmpty()) {
            NotAvailableRow("Cross-asset context", colors)
        } else {
            lines.forEach { line ->
                Text(text = line, style = AtomType.Body.copy(color = colors.textSecondary), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private fun supportLine(label: String, direction: String, upPhrase: String, downPhrase: String): String = when (direction) {
    "up" -> "$label↑ → $upPhrase"
    "down" -> "$label↓ → $downPhrase"
    else -> "$label flat"
}

/** Design §14.7/§26/§37 Correlation tab: the frozen `correlations` matrix, sorted by |correlation|. */
@Composable
private fun CorrelationTabContent(pair: String, signals: Signals, colors: AtomColors) {
    val corr = signals.correlations
    val rowIndex = corr?.pairs?.indexOf(pair) ?: -1
    Column(modifier = Modifier.fillMaxWidth()) {
        if (corr == null || rowIndex < 0) {
            NotAvailableRow("Correlation", colors)
            return@Column
        }
        val row = corr.matrix.getOrNull(rowIndex).orEmpty()
        corr.pairs.indices
            .filter { it != rowIndex }
            .mapNotNull { i -> corr.pairs.getOrNull(i)?.let { p -> row.getOrNull(i)?.let { v -> p to v } } }
            .sortedByDescending { kotlin.math.abs(it.second) }
            .take(5)
            .forEach { (otherPair, value) ->
                SheetRow(otherPair, "%+.2f".format(java.util.Locale.US, value), colors, correlationColor(value, colors))
            }
    }
}

private fun correlationColor(value: Double, colors: AtomColors) = when {
    kotlin.math.abs(value) >= 0.7 -> colors.textPrimary
    kotlin.math.abs(value) >= 0.4 -> colors.textSecondary
    else -> colors.textMuted
}
