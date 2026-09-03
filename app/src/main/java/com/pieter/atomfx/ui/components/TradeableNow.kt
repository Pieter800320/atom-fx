package com.pieter.atomfx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * Design §11 — level-6 pairs ranked by `setup_rank` (cross-referenced from [Signals], not
 * stored on [PairNode] — same pattern the pair sheet already uses), "NO A+ SETUPS" + closest
 * pair when none qualify, and a secondary Watch row (levels 3-5) below it.
 *
 * Pieter, 2026-09-03 — sits on its own dark grey card(s) now (was bare on the ground colour),
 * per direct instruction. Pills use Item Library #07's Electric Treatment: the leader (rank-1
 * tradeable pair) gets the with-border variant, every other pill (remaining tradeable + all
 * watch) gets the without-border variant — see [ScrollingPills]/[Pill]'s `electric`/`withBorder`.
 *
 * Follow-up (same session) — split into two side-by-side cards (Tradeable Now / Watch) instead
 * of one stacked card: a single card was leaving a lot of dead horizontal space whenever there
 * were only one or two pills. Watch's own card (and the gap before it) only exists when there's
 * something to watch — Tradeable Now's card takes the full width alone otherwise, for free, just
 * by being the single `weight(1f)` child in the Row. Watch's heading is `textMuted` (grey) now,
 * not `colors.watch` (amber) — a deliberate de-emphasis of the whole section relative to
 * Tradeable Now, matching how "TRADEABLE NOW"/"NO A+ SETUPS" already use that same grey.
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

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                // Pieter, 2026-09-03 — darker than surfaceRaised (the Summary button's own
                // colour), so the two don't read as the same kind of control.
                .background(colors.surface, CARD_SHAPE)
                .padding(16.dp),
        ) {
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
                    )
                }
            } else {
                ScrollingPills(
                    pills = tradeable.mapIndexed { i, node ->
                        // The leader — rank-1 among tradeable pairs — is the one pill across
                        // both cards that gets the with-border Electric Treatment; everything
                        // else (the rest of tradeable, all of watch) is wash+text only.
                        node.toPill(colors, withBorder = i == 0) { onSelect(SheetTarget.Node(node.pair)) }
                    },
                    colors = colors,
                )
            }
        }

        if (watching.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surface, CARD_SHAPE)
                    .padding(16.dp),
            ) {
                Text(
                    text = "WATCH",
                    style = AtomType.Caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                ScrollingPills(
                    pills = watching.map { node ->
                        Pill(
                            text = "${node.pair} · ${node.level}/6",
                            tint = colors.watch,
                            electric = true,
                            onClick = { onSelect(SheetTarget.Node(node.pair)) },
                        )
                    },
                    colors = colors,
                )
            }
        }
    }
}

private fun PairNode.toPill(colors: AtomColors, withBorder: Boolean, onClick: () -> Unit): Pill {
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
    return Pill(text = "$pair $glyph$potential", tint = tint, electric = true, withBorder = withBorder, onClick = onClick)
}
