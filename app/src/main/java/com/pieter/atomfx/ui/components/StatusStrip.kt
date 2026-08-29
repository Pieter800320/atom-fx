package com.pieter.atomfx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.WheelUiState
import com.pieter.atomfx.ui.wheel.topPair

/** Design §10 — 5 micro-cells, no scrolling, tapping one opens the matching sheet (§14). */
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

    Row(modifier = modifier.fillMaxWidth()) {
        Cell("REGIME", state.nucleus.regimeLabel, colors, Modifier.weight(1f)) {
            onCellClick(SheetTarget.Ring(Factor.REGIME))
        }
        Cell("LEADER", flow?.leader?.let { "$it ${signedInt(flow.leaderDelta)}" } ?: "—", colors, Modifier.weight(1f)) {
            onCellClick(flow?.leader?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW))
        }
        Cell("LAGGARD", flow?.laggard?.let { "$it ${signedInt(flow.laggardDelta)}" } ?: "—", colors, Modifier.weight(1f)) {
            onCellClick(flow?.laggard?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW))
        }
        Cell("BREADTH", leaderBreadth?.band ?: "—", colors, Modifier.weight(1f)) {
            onCellClick(SheetTarget.Ring(Factor.BREADTH))
        }
        val topPair = state.topPair()
        Cell("TOP PAIR", topPair.pair, colors, Modifier.weight(1f)) {
            onCellClick(SheetTarget.Node(topPair.pair))
        }
    }
}

@Composable
private fun Cell(label: String, value: String, colors: AtomColors, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        Text(
            text = value,
            style = AtomType.Body.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun signedInt(value: Double?): String {
    if (value == null) return ""
    val sign = if (value > 0) "+" else ""
    return "$sign${value.toInt()}"
}
