package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/** Design §14.2 — strength from frozen `csm.h4`; Δ/arrows from `csm_delta.h4` when the backend has it. */
@Composable
fun CurrencyFlowSheet(signals: Signals, colors: AtomColors) {
    val strengths = signals.csm["h4"].orEmpty()
    val deltas = signals.csmDelta["h4"]

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle("CURRENCY FLOW", colors)

        if (strengths.isEmpty()) {
            NotAvailableRow("Currency strength (H4)", colors)
        } else {
            strengths.entries.sortedByDescending { it.value }.forEach { (ccy, strength) ->
                val delta = deltas?.get(ccy)
                SheetRow(
                    label = ccy,
                    value = "${strength.toInt()}   ${deltaText(delta)}",
                    colors = colors,
                    valueColor = deltaColor(delta, colors),
                )
            }
        }

        SheetDivider(colors)
        val flow = signals.currencyFlow
        if (flow?.leader != null && flow.laggard != null) {
            SheetRow("Current leader", flow.leader, colors, colors.bull)
            SheetRow("Current laggard", flow.laggard, colors, colors.bear)
        } else {
            NotAvailableRow("Flow leader / laggard", colors)
        }
        if (flow?.absoluteLeader != null && flow.absoluteLaggard != null) {
            SheetRow("Absolute leader", flow.absoluteLeader, colors, colors.bull)
            SheetRow("Absolute laggard", flow.absoluteLaggard, colors, colors.bear)
        }

        Text(
            text = "Leader ≠ absolute leader: the leader is getting stronger fastest; the absolute leader is strongest right now.",
            style = AtomType.Caption.copy(color = colors.textMuted),
        )
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
