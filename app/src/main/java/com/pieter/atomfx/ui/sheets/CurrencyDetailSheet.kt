package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.wheel.WheelGeometry

/**
 * Design's `CurrencyDetailSheet`: CSM 3-TF + breadth + drivers + expressing pairs. Reached by
 * tapping a specific currency (StatusStrip's Leader/Laggard values, `CurrencyFlowSheet`'s
 * leader/laggard rows) — distinct from the market-wide `CurrencyFlowSheet` (Flow ring tap).
 * "Drivers" reuses the same D1/H4/H1 `csm_delta` numbers `CurrencyFlowSheet` already shows for
 * the whole market, scoped to just this currency — no new metric invented.
 */
@Composable
fun CurrencyDetailSheet(currency: String, signals: Signals, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(currency, colors)

        SheetRow("D1 strength", csmValue(signals.csm["d1"]?.get(currency)), colors)
        SheetRow("H4 strength", csmValue(signals.csm["h4"]?.get(currency)), colors)
        SheetRow("H1 strength", csmValue(signals.csm["h1"]?.get(currency)), colors)

        SheetDivider(colors)
        SheetRow("D1 driver (Δ)", deltaText(signals.csmDelta["d1"]?.get(currency)), colors, deltaColor(signals.csmDelta["d1"]?.get(currency), colors))
        SheetRow("H4 driver (Δ)", deltaText(signals.csmDelta["h4"]?.get(currency)), colors, deltaColor(signals.csmDelta["h4"]?.get(currency), colors))
        SheetRow("H1 driver (Δ)", deltaText(signals.csmDelta["h1"]?.get(currency)), colors, deltaColor(signals.csmDelta["h1"]?.get(currency), colors))

        val breadth = signals.breadth["h4"]?.get(currency)
        SheetDivider(colors)
        if (breadth?.pct != null) {
            // Colour from the backend's own band string, not a recomputed pct threshold — same
            // reasoning as BreadthSheet.kt: breadth.py owns that classification.
            val bandColor = when (breadth.band?.lowercase()) {
                "strong" -> colors.bull
                "moderate" -> colors.watch
                "weak" -> colors.bear
                else -> colors.textMuted
            }
            SheetRow("Breadth (H4)", "${breadth.support}/${breadth.total} · ${breadth.band ?: "—"}", colors, bandColor)
            BarMeter(
                fraction = breadth.pct.toFloat(),
                color = bandColor,
                colors = colors,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        } else {
            NotAvailableRow("Breadth (H4)", colors)
        }

        SheetDivider(colors)
        val expressingPairs = WheelGeometry.PAIR_ORDER.filter { it.take(3) == currency || it.takeLast(3) == currency }
        expressingPairs.forEach { pair ->
            val bias = signals.pairs[pair]?.pills?.h4 ?: "—"
            SheetRow(pair, bias.replaceFirstChar { it.uppercase() }, colors)
        }
    }
}

private fun csmValue(value: Double?): String = value?.let { it.toInt().toString() } ?: "—"

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
