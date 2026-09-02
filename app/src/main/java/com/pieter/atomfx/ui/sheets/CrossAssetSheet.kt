package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.MacroAssetEntry
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.WheelGeometry

/**
 * Wheel v2: tapping an outer cross-asset wedge opens the full 10-asset list with the tapped
 * asset pinned to the top and highlighted (Functional Spec §6.6). Pure consumer of `macro_assets`
 * + `macro_regime.evidence` (the confirm badge); no trading number is computed here.
 */
@Composable
fun CrossAssetSheet(selectedId: String, signals: Signals, colors: AtomColors) {
    val supportingAxes = signals.macroRegime?.evidence
        ?.filter { it.supports }?.mapNotNull { it.axis }?.toSet() ?: emptySet()

    // Canonical order, then float the tapped asset to the top.
    val ordered = WheelGeometry.XASSET_ORDER
        .sortedByDescending { it.first == selectedId }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle("Cross-assets", colors)
        ordered.forEach { (key, fallbackLabel) ->
            CrossAssetRow(
                key = key,
                fallbackLabel = fallbackLabel,
                entry = signals.macroAssets[key],
                pinned = key == selectedId,
                confirm = (ASSET_AXES[key] ?: emptyList()).any { it in supportingAxes },
                colors = colors,
            )
        }
    }
}

private val ASSET_AXES: Map<String, List<String>> = mapOf(
    "vix" to listOf("risk"), "spx" to listOf("risk"), "btc" to listOf("risk"),
    "us10y" to listOf("rates"), "us3m" to listOf("rates"), "curve" to listOf("rates"),
    "dxy" to listOf("usd"), "wti" to listOf("commodity"),
    "copper" to listOf("risk", "commodity"), "gold" to listOf("commodity", "safe_haven"),
)

/** Short, presentational read of what the move implies — copy only, not a computed signal. */
private val IMPACT: Map<Pair<String, Boolean>, String> = mapOf(
    ("vix" to true) to "risk-off — JPY/CHF bid", ("vix" to false) to "risk-on — AUD/NZD bid",
    ("spx" to true) to "risk-on", ("spx" to false) to "risk-off",
    ("us10y" to true) to "USD bid vs JPY", ("us10y" to false) to "JPY/CHF relief",
    ("us3m" to true) to "front-end firm — USD", ("us3m" to false) to "easing bets",
    ("curve" to true) to "steepening", ("curve" to false) to "flattening",
    ("dxy" to true) to "broad USD strength", ("dxy" to false) to "broad USD weakness",
    ("wti" to true) to "CAD bid", ("wti" to false) to "CAD offered",
    ("copper" to true) to "AUD/NZD bid", ("copper" to false) to "AUD/NZD offered",
    ("gold" to true) to "defensive/inflation bid", ("gold" to false) to "USD firm",
    ("btc" to true) to "risk-on", ("btc" to false) to "risk-off",
)

@Composable
private fun CrossAssetRow(
    key: String,
    fallbackLabel: String,
    entry: MacroAssetEntry?,
    pinned: Boolean,
    confirm: Boolean,
    colors: AtomColors,
) {
    val dir = entry?.direction
    val up = dir == "up"
    val down = dir == "down"
    val dirColor = when {
        up -> colors.bull
        down -> colors.bear
        else -> colors.textSecondary
    }
    val value = entry?.value?.let { if (kotlin.math.abs(it) >= 100) "%.0f".format(it) else "%.2f".format(it) } ?: "—"
    val delta = entry?.deltaPct?.let { "%+.1f%%".format(it) }
        ?: entry?.deltaBp?.let { "%+.1fbp".format(it) } ?: "—"
    val arrow = if (up) "▲" else if (down) "▼" else "—"
    val impact = IMPACT[key to up] ?: ""

    val rowModifier = if (pinned) {
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(10.dp))
            .border(1.dp, dirColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
    }

    Column(modifier = rowModifier.padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = entry?.label ?: fallbackLabel,
                style = AtomType.Body.copy(
                    color = if (pinned) colors.textPrimary else colors.textPrimary,
                    fontWeight = if (pinned) FontWeight.Bold else FontWeight.Medium,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(text = value, style = AtomType.Body.copy(color = colors.textPrimary))
            Text(
                text = "  $arrow $delta",
                style = AtomType.Body.copy(color = dirColor),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                text = if (impact.isNotBlank()) impact else " ",
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.weight(1f),
            )
            ConfirmBadge(confirm, colors)
        }
    }
}

@Composable
private fun ConfirmBadge(confirm: Boolean, colors: AtomColors) {
    val (label, color: Color) = if (confirm) "CONFIRMS" to colors.bull else "—" to colors.textMuted
    Text(text = label, style = AtomType.Caption.copy(color = color))
}
