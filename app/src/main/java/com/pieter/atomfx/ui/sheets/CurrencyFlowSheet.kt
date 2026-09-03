package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash

/**
 * Design §14.2 — strength from frozen `csm.h4`; Δ/arrows from `csm_delta.h4` when the backend has it.
 *
 * 2026-09-03 — added the bar meter the mockup's `shFlow()` always had (`.meter` per row, width =
 * strength%, coloured by delta direction) but this sheet never did; matches `BreadthSheet.kt`'s
 * own `BarMeter` usage one row below in Design §14.3's factor-sheet order, and Pieter's stated
 * preference this session for an at-a-glance read over plain text where the mockup already shows
 * one. Delta arrows dropped — the bar's length now carries the magnitude the arrow count used to.
 */
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
                FlowRow(ccy, strength, deltas?.get(ccy), colors) { onCurrencyClick(ccy) }
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
    }
}

@Composable
private fun FlowRow(ccy: String, strength: Double, delta: Double?, colors: AtomColors, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val dColor = deltaColor(delta, colors)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressWash {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ccy,
            style = AtomType.Body.copy(color = colors.textPrimary),
            modifier = Modifier.width(36.dp),
        )
        BarMeter(
            fraction = (strength / 100.0).toFloat(),
            color = dColor,
            colors = colors,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            text = strength.toInt().toString(),
            style = AtomType.Body.copy(color = colors.textPrimary),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = deltaText(delta),
            style = AtomType.Caption.copy(color = dColor),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** A `SheetRow` that opens this currency's detail sheet — same shape, just tappable. */
@Composable
private fun ClickableRow(label: String, value: String, colors: AtomColors, valueColor: Color, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressWash {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = AtomType.Body.copy(color = colors.textSecondary))
        Text(text = value, style = AtomType.Body.copy(color = valueColor))
    }
}

private fun deltaText(delta: Double?): String {
    if (delta == null) return "—"
    val sign = if (delta > 0) "+" else ""
    return "$sign${delta.toInt()}"
}

private fun deltaColor(delta: Double?, colors: AtomColors) = when {
    delta == null -> colors.textMuted
    delta > 0 -> colors.bull
    delta < 0 -> colors.bear
    else -> colors.textSecondary
}
