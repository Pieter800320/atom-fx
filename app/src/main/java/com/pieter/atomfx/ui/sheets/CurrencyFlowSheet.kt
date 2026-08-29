package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/** Design §14.2 — strength from frozen `csm.h4`; Δ/arrows from `csm_delta.h4` when the backend has it. */
@Composable
fun CurrencyFlowSheet(signals: Signals, colors: AtomColors, onCurrencyClick: (String) -> Unit = {}) {
    val strengths = signals.csm["h4"].orEmpty()
    val deltas = signals.csmDelta["h4"]

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle("CURRENCY FLOW", colors)

        if (strengths.isEmpty()) {
            NotAvailableRow("Currency strength (H4)", colors)
        } else {
            strengths.entries.sortedByDescending { it.value }.forEach { (ccy, strength) ->
                val delta = deltas?.get(ccy)
                ClickableRow(ccy, "${strength.toInt()}   ${deltaText(delta)}", colors, deltaColor(delta, colors)) {
                    onCurrencyClick(ccy)
                }
            }
        }

        SheetDivider(colors)
        val flow = signals.currencyFlow
        if (flow?.leader != null && flow.laggard != null) {
            ClickableRow("Current leader", flow.leader, colors, colors.bull) { onCurrencyClick(flow.leader) }
            ClickableRow("Current laggard", flow.laggard, colors, colors.bear) { onCurrencyClick(flow.laggard) }
        } else {
            NotAvailableRow("Flow leader / laggard", colors)
        }
        if (flow?.absoluteLeader != null && flow.absoluteLaggard != null) {
            ClickableRow("Absolute leader", flow.absoluteLeader, colors, colors.bull) { onCurrencyClick(flow.absoluteLeader) }
            ClickableRow("Absolute laggard", flow.absoluteLaggard, colors, colors.bear) { onCurrencyClick(flow.absoluteLaggard) }
        }

        Text(
            text = "Leader ≠ absolute leader: the leader is getting stronger fastest; the absolute leader is strongest right now.",
            style = AtomType.Caption.copy(color = colors.textMuted),
        )
    }
}

/** A `SheetRow` that opens this currency's detail sheet — same shape, just tappable. */
@Composable
private fun ClickableRow(label: String, value: String, colors: AtomColors, valueColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = AtomType.Body.copy(color = colors.textSecondary))
        Text(text = value, style = AtomType.Body.copy(color = valueColor))
    }
}

private fun deltaText(delta: Double?): String {
    if (delta == null) return "—"
    val arrows = when {
        delta >= 8 -> "↑↑↑"
        delta >= 4 -> "↑↑"
        delta > 0 -> "↑"
        delta == 0.0 -> "→"
        delta > -4 -> "↓"
        delta > -8 -> "↓↓"
        else -> "↓↓↓"
    }
    val sign = if (delta > 0) "+" else ""
    return "$sign${delta.toInt()} $arrows"
}

private fun deltaColor(delta: Double?, colors: AtomColors) = when {
    delta == null -> colors.textMuted
    delta > 0 -> colors.bull
    delta < 0 -> colors.bear
    else -> colors.textSecondary
}
