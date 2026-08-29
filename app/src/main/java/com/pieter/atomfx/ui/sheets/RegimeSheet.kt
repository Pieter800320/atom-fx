package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.RegimeBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * Design §14.1. The vote-breakdown numbers in the mockup (Safe-Haven vs Risk-Basket score,
 * USD-proxy split, pair-balance counts) aren't part of the documented `signals.json` contract
 * (Architecture §4.1 only lists `{regime, confidence, score, stable}` per timeframe) — so
 * rather than invent them, this shows what's real: the three timeframes side by side, a
 * divergence note when they disagree, and the frozen stability flag.
 */
@Composable
fun RegimeSheet(signals: Signals, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle("MARKET REGIME", colors)

        val h4 = signals.regimeH4
        Text(
            text = regimeDisplayName(h4?.regime),
            style = AtomType.Display.copy(color = colors.textPrimary),
        )
        Text(
            text = "Confidence: ${(h4?.confidence ?: "—").uppercase()}",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        SheetRow("D1", regimeLine(signals.regimeD1), colors)
        SheetRow("H4", regimeLine(signals.regimeH4), colors)
        SheetRow("H1", regimeLine(signals.regimeH1), colors)

        val regimes = listOfNotNull(signals.regimeD1?.regime, signals.regimeH4?.regime, signals.regimeH1?.regime)
        if (regimes.toSet().size > 1) {
            Text(
                text = "Timeframes disagree — treat the H4 read as the principal regime.",
                style = AtomType.Caption.copy(color = colors.watch),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SheetDivider(colors)
        SheetRow(
            label = "Regime stability (H4)",
            value = when (h4?.stable) {
                true -> "Stable"
                false -> "Not stable"
                null -> "—"
            },
            colors = colors,
        )
    }
}

/** Matches the wheel nucleus's own convention (WheelMapper) — "Risk-On" reads as "RISK ON", not "RISK-ON". */
private fun regimeDisplayName(raw: String?): String = (raw ?: "Unknown").replace("-", " ").uppercase()

private fun regimeLine(block: RegimeBlock?): String {
    if (block == null) return "—"
    val regime = block.regime ?: "—"
    val confidence = block.confidence ?: "—"
    return "$regime · $confidence"
}
