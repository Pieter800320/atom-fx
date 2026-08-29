package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.PairBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.PairNode
import com.pieter.atomfx.ui.wheel.PotentialState

private val TABS = listOf("Overview", "Momentum", "Structure", "Entry", "Macro", "Correlation")

/**
 * Design §14.7 — the most important surface. Overview (default, never hidden behind a tab)
 * is the six-factor WHY checklist; the blocked factor is the one visually distinct row.
 * Momentum/Structure/Entry are the same per-pair content Design §14.4-§14.6 describe — reused
 * here as tabs rather than duplicated as separate ring-tap sheets (Architecture never asks for
 * two surfaces to show the same numbers twice).
 */
@Composable
fun PairSheet(node: PairNode, allNodes: List<PairNode>, signals: Signals, colors: AtomColors, initialTab: Int = 0) {
    var selectedTab by remember(node.pair) { mutableIntStateOf(initialTab) }
    val pairBlock = signals.pairs[node.pair]

    Column(modifier = Modifier.fillMaxWidth()) {
        PairHeader(node, allNodes, colors)
        SheetTabs(TABS, selectedTab, colors) { selectedTab = it }
        when (selectedTab) {
            0 -> WhyChecklist(node, signals, pairBlock, colors)
            1 -> MomentumTabContent(pairBlock?.mom, colors)
            2 -> StructureTabContent(pairBlock?.structure, colors)
            3 -> EntryTabContent(signals.potential[node.pair]?.setupRank, pairBlock, colors)
            4 -> MacroTabContent(signals, colors)
            5 -> CorrelationTabContent(node.pair, signals, colors)
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

@Composable
private fun WhyChecklist(node: PairNode, signals: Signals, pairBlock: PairBlock?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "WHY?",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        whyRows(node, signals, pairBlock).forEach { row ->
            val isBlocker = node.blockedAt == row.factor
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    text = if (row.passed) "✓" else "✗",
                    style = AtomType.Body.copy(color = if (row.passed) colors.bull else colors.bear),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.factor.shortLabel.uppercase(),
                        style = AtomType.Caption.copy(
                            color = if (isBlocker) colors.bear else colors.textPrimary,
                        ),
                    )
                    Text(text = row.explanation, style = AtomType.Body.copy(color = colors.textSecondary))
                    Text(text = row.value, style = AtomType.Caption.copy(color = colors.textMuted))
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
                SheetRow(otherPair, "%+.2f".format(value), colors, correlationColor(value, colors))
            }
    }
}

private fun correlationColor(value: Double, colors: AtomColors) = when {
    kotlin.math.abs(value) >= 0.7 -> colors.textPrimary
    kotlin.math.abs(value) >= 0.4 -> colors.textSecondary
    else -> colors.textMuted
}
