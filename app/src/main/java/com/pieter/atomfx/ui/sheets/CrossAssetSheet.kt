package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.MacroAssetEntry
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.WheelGeometry

/**
 * Wheel v2: tapping an outer cross-asset wedge opens the full 10-asset list with the tapped
 * asset pinned to the top (Functional Spec §6.6). Pure consumer of `macro_assets`; no trading
 * number is computed here.
 *
 * 2026-09-03 — simplified after two rounds of feedback: the wash now tells one thing, the asset's
 * own direction (bull wash on an up move, bear on a down move, plain otherwise) — no dot, no
 * arrow glyph, no separate "confirms the regime" signal layered in (that was a second, different
 * question — "is this axis supporting the regime" — competing with direction in the same colour
 * and reading as muddled). Value/delta are vertically centred against the *whole* card (title +
 * impact line), not just the title line, via one `Alignment.CenterVertically` Row.
 */
@Composable
fun CrossAssetSheet(selectedId: String, signals: Signals, colors: AtomColors) {
    // Canonical order, then float the tapped asset to the top.
    val ordered = WheelGeometry.XASSET_ORDER
        .sortedByDescending { it.first == selectedId }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetTitle("Cross-assets", colors)
        ordered.forEach { (key, fallbackLabel) ->
            CrossAssetRow(
                key = key,
                fallbackLabel = fallbackLabel,
                entry = signals.macroAssets[key],
                pinned = key == selectedId,
                colors = colors,
            )
        }
    }
}

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

private val XA_CARD_SHAPE = RoundedCornerShape(14.dp)
// Same evidence formula as Macro's EvidenceAxes / PairSheet's WhyChecklist, now keyed to the
// asset's own direction instead of regime-support.
private const val XA_LIT_AMOUNT = 0.08f

@Composable
private fun CrossAssetRow(
    key: String,
    fallbackLabel: String,
    entry: MacroAssetEntry?,
    pinned: Boolean,
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
    // Locale.US explicitly — the default locale's decimal separator (e.g. a comma) isn't what a
    // trading number should ever render with, regardless of device region.
    val value = entry?.value?.let {
        if (kotlin.math.abs(it) >= 100) "%.0f".format(java.util.Locale.US, it) else "%.2f".format(java.util.Locale.US, it)
    } ?: "—"
    val delta = entry?.deltaPct?.let { "%+.1f%%".format(java.util.Locale.US, it) }
        ?: entry?.deltaBp?.let { "%+.1fbp".format(java.util.Locale.US, it) } ?: "—"
    val impact = IMPACT[key to up] ?: ""

    val fill = when {
        up -> lerp(colors.surfaceRaised, colors.bull, XA_LIT_AMOUNT)
        down -> lerp(colors.surfaceRaised, colors.bear, XA_LIT_AMOUNT)
        else -> colors.surfaceRaised
    }

    // Left side (title + impact) stacks top-down inside its own Column; the value/delta on the
    // right are direct children of this outer Row, so CenterVertically centres them against the
    // Column's full height instead of pinning them to the title line.
    Row(
        modifier = Modifier.fillMaxWidth().background(fill, XA_CARD_SHAPE).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry?.label ?: fallbackLabel,
                style = AtomType.Body.copy(
                    color = colors.textPrimary,
                    fontWeight = if (pinned) FontWeight.SemiBold else AtomType.Body.fontWeight,
                ),
            )
            if (impact.isNotBlank()) {
                Text(
                    text = impact,
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Text(text = value, style = AtomType.Body.copy(color = colors.textPrimary))
        Text(text = "  $delta", style = AtomType.Body.copy(color = dirColor))
    }
}
