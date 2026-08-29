package com.pieter.atomfx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.PairNode
import com.pieter.atomfx.ui.wheel.PotentialState

/**
 * Design §11 — level-6 pairs ranked by `setup_rank` (cross-referenced from [Signals], not
 * stored on [PairNode] — same pattern the pair sheet already uses), "NO A+ SETUPS" + closest
 * pair when none qualify, and a secondary Watch row (levels 3-5) below it.
 */
@Composable
fun TradeableNow(
    nodes: List<PairNode>,
    signals: Signals,
    colors: AtomColors,
    onSelect: (SheetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tradeable = nodes
        .filter { it.state == PotentialState.TRADEABLE || it.state == PotentialState.APLUS }
        .sortedByDescending { signals.potential[it.pair]?.setupRank ?: -1.0 }
    val watching = nodes.filter { it.state == PotentialState.WATCH }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = if (tradeable.isEmpty()) "NO A+ SETUPS" else "TRADEABLE NOW",
            style = AtomType.Caption.copy(color = colors.textMuted),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (tradeable.isEmpty()) {
            val closest = nodes.maxByOrNull { it.level }
            if (closest != null) {
                Text(
                    text = "Closest: ${closest.pair} — Level ${closest.level}/6",
                    style = AtomType.Body.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        } else {
            ScrollingPills(
                pills = tradeable.map { node -> node.toPill(colors, emphasized = node.state == PotentialState.APLUS) { onSelect(SheetTarget.Node(node.pair)) } },
                colors = colors,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (watching.isNotEmpty()) {
            Text(
                text = "WATCH",
                style = AtomType.Caption.copy(color = colors.watch),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            ScrollingPills(
                pills = watching.map { node ->
                    Pill(
                        text = "${node.pair} · ${node.level}/6",
                        tint = colors.watch,
                        onClick = { onSelect(SheetTarget.Node(node.pair)) },
                    )
                },
                colors = colors,
            )
        }
    }
}

private fun PairNode.toPill(colors: AtomColors, emphasized: Boolean, onClick: () -> Unit): Pill {
    val tint = when (direction) {
        Direction.BULL -> colors.bull
        Direction.BEAR -> colors.bear
        Direction.NEUTRAL -> colors.neutral
    }
    val glyph = when (direction) {
        Direction.BULL -> "↑"
        Direction.BEAR -> "↓"
        Direction.NEUTRAL -> ""
    }
    return Pill(text = "$pair $glyph$potential", tint = tint, emphasized = emphasized, onClick = onClick)
}
