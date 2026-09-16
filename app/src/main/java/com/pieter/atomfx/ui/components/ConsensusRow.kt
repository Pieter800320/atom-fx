package com.pieter.atomfx.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.PairNode

/**
 * REGIME · TREND · MOM · VOL · STRUCTURE — the five-factor consensus dot row. One shared
 * implementation for every call site (StatusStrip's RecommendationPanel and the Watchlist card,
 * 2026-09-16) rather than this codebase's usual "small local copy" house style: a second hand
 * copy of these five colour functions is exactly how the VOL dot briefly shipped bull-green
 * instead of Volatility's own blue/amber (see [volatilityDotColor]) — one source prevents that
 * bug coming back silently at whichever call site didn't get the fix.
 */
@Composable
fun ConsensusDotRow(
    node: PairNode,
    structureEvent: String?,
    structureDirection: String?,
    colors: AtomColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ConsensusItem("REGIME", regimeDotColor(node, colors), colors)
        ConsensusItem("TREND", trendDotColor(node, colors), colors)
        ConsensusItem("MOM", momentumDotColor(node, colors), colors)
        ConsensusItem("VOL", volatilityDotColor(node, colors), colors)
        ConsensusItem("STRUCTURE", structureDotColor(structureEvent, structureDirection, colors), colors)
    }
}

@Composable
fun ConsensusItem(label: String, dotColor: Color, colors: AtomColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EvidenceDot(color = dotColor, modifier = Modifier.padding(end = 6.dp))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted), maxLines = 1)
    }
}

// Same four reads the pair sheet's own Overview tab shows (PairSheet.kt's `overviewRows`).
fun regimeDotColor(node: PairNode, colors: AtomColors): Color =
    if (Factor.REGIME in node.factorsPassed) colors.bull else colors.textMuted

fun trendDotColor(node: PairNode, colors: AtomColors): Color = when {
    node.adx >= 25 && node.trendDirection == Direction.NEUTRAL -> colors.watch
    node.trendDirection == Direction.BULL -> colors.bull
    node.trendDirection == Direction.BEAR -> colors.bear
    else -> colors.textMuted
}

fun momentumDotColor(node: PairNode, colors: AtomColors): Color =
    if (node.momentum >= 50) colors.bull else colors.bear

// Volatility is its own blue/amber system, never the directional traffic light — see
// WheelCanvas.kt's WheelMode.VOLATILITY case and Color.kt's wheelSane doc comment.
fun volatilityDotColor(node: PairNode, colors: AtomColors): Color =
    if (node.volatility in 20..70) colors.wheelSane else colors.watch

// Same convention as PairSheet.kt's own Overview `structureRow` — coloured by the event's own
// `direction` (real price direction), not by event type. No recent event reads as neutral, same
// as every other dot here when there's nothing to report — the backend sends event as the
// literal string "none" (not JSON null) in that case, so this matches on "BOS"/"CHoCH" first
// rather than checking for null, same as PairSheet's own row does.
fun structureDotColor(event: String?, direction: String?, colors: AtomColors): Color = when (event) {
    "BOS", "CHoCH" -> when (direction) {
        "bull" -> colors.bull
        "bear" -> colors.bear
        else -> colors.textMuted
    }
    else -> colors.textMuted
}
