package com.pieter.atomfx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.WheelUiState
import com.pieter.atomfx.ui.wheel.tintColor
import com.pieter.atomfx.ui.wheel.topPair

/**
 * Design §10 / mockup `strip()` (`atom-fx-screen-kit.html`) — 5 micro-cells, hairline-separated
 * (not cards), no scrolling. Regime is tinted per its status colour (mockup `.cell .v` inline
 * style); leader/laggard carry their signed delta in bull/bear; breadth's band word is coloured
 * by band, same convention as `BreadthSheet`/`CurrencyDetailSheet`. Tapping a cell opens the
 * matching sheet (§14).
 */
@Composable
fun StatusStrip(
    state: WheelUiState,
    signals: Signals,
    colors: AtomColors,
    onCellClick: (SheetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = signals.currencyFlow
    val leaderBreadth = flow?.leader?.let { signals.breadth["h4"]?.get(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Cell(
                "REGIME", state.nucleus.regimeLabel,
                tintColor(state.nucleus.tint, colors), colors, Modifier.weight(1f),
            ) { onCellClick(SheetTarget.Ring(Factor.REGIME)) }
            VDivider(colors)
            Cell(
                "LEADER", flow?.leader?.let { "$it ${signedInt(flow.leaderDelta)}" } ?: "—",
                deltaColor(flow?.leaderDelta, colors), colors, Modifier.weight(1f),
            ) { onCellClick(flow?.leader?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW)) }
            VDivider(colors)
            Cell(
                "LAGGARD", flow?.laggard?.let { "$it ${signedInt(flow.laggardDelta)}" } ?: "—",
                deltaColor(flow?.laggardDelta, colors), colors, Modifier.weight(1f),
            ) { onCellClick(flow?.laggard?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW)) }
            VDivider(colors)
            Cell(
                "BREADTH", leaderBreadth?.band ?: "—",
                bandColor(leaderBreadth?.band, colors), colors, Modifier.weight(1f),
            ) { onCellClick(SheetTarget.Ring(Factor.BREADTH)) }
            VDivider(colors)
            val topPair = state.topPair()
            Cell(
                "TOP PAIR", topPair.pair,
                colors.textPrimary, colors, Modifier.weight(1f),
            ) { onCellClick(SheetTarget.Node(topPair.pair)) }
        }
        // strip border-bottom (mockup .strip).
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}

@Composable
private fun VDivider(colors: AtomColors) {
    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(colors.hairline))
}

@Composable
private fun Cell(label: String, value: String, valueColor: Color, colors: AtomColors, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        Text(
            text = value,
            style = AtomType.Body.copy(color = valueColor),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun signedInt(value: Double?): String {
    if (value == null) return ""
    val sign = if (value > 0) "+" else ""
    return "$sign${value.toInt()}"
}

private fun deltaColor(delta: Double?, colors: AtomColors): Color = when {
    delta == null -> colors.textPrimary
    delta > 0 -> colors.bull
    delta < 0 -> colors.bear
    else -> colors.textSecondary
}

// Same convention as BreadthSheet.kt/CurrencyDetailSheet.kt — colour from the backend's own
// band string, not a recomputed threshold (breadth.py owns that classification).
private fun bandColor(band: String?, colors: AtomColors): Color = when (band?.lowercase()) {
    "strong" -> colors.bull
    "moderate" -> colors.watch
    "weak" -> colors.bear
    else -> colors.textPrimary
}
