package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Tint
import com.pieter.atomfx.ui.wheel.tintColor

/**
 * Design §14.1. The vote-breakdown numbers in the mockup (Safe-Haven vs Risk-Basket score,
 * USD-proxy split, pair-balance counts) aren't part of the documented `signals.json` contract
 * (Architecture §4.1 only lists `{regime, confidence, score, stable}` per timeframe) — so
 * rather than invent them, this shows what's real: the three timeframes side by side, a
 * divergence note when they disagree, and the frozen stability flag.
 *
 * 2026-09-03 — the regime name was always plain `textPrimary`, when the mockup's own CSS colours
 * it (`color:var(--a-bull)` for RISK ON) and the wheel's hub already does the same
 * (`WheelMapper`'s `tintFor`) — reused here via `Tint`/`tintColor` rather than a second mapping.
 * D1/H4/H1 also collapsed from three separate rows into the mockup's single combined row.
 */
@Composable
fun RegimeSheet(signals: Signals, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle("MARKET REGIME", colors)

        val h4 = signals.regimeH4
        val tint = tintColor(regimeTint(h4?.regime), colors)
        Text(
            text = regimeDisplayName(h4?.regime),
            style = AtomType.Display.copy(color = tint),
        )
        Text(
            text = "Confidence: ${(h4?.confidence ?: "—").uppercase()}",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SheetRow(
            label = "Score (H4)",
            value = h4?.score?.let { "${if (it >= 0) "+" else ""}%.1f".format(java.util.Locale.US, it) } ?: "—",
            colors = colors,
        )
        Spacer(modifier = Modifier.height(10.dp))

        val d1r = signals.regimeD1?.regime
        val h4r = signals.regimeH4?.regime
        val h1r = signals.regimeH1?.regime
        val regimes = listOfNotNull(d1r, h4r, h1r)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RegimeTfSquare("D1", d1r, colors, Modifier.weight(1f))
            RegimeTfSquare("H4", h4r, colors, Modifier.weight(1f))
            RegimeTfSquare("H1", h1r, colors, Modifier.weight(1f))
        }

        if (regimes.toSet().size > 1) {
            Text(
                text = "Timeframes disagree — treat the H4 read as the principal regime.",
                style = AtomType.Caption.copy(color = colors.watch),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
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

/** Same mapping as `WheelMapper`'s private `tintFor` — kept as its own tiny copy rather than
 *  exporting that one, since it's four lines and belongs to the hub's own mapper, not shared API. */
private fun regimeTint(regime: String?): Tint = when (regime) {
    "Risk-On" -> Tint.BULL
    "Risk-Off" -> Tint.BEAR
    "Mixed" -> Tint.WATCH
    else -> Tint.NEUTRAL
}

// Matches ScrollingPills' ELECTRIC_PILL_SHAPE — same squircle MomentumSheet's MOM_BAR_SHAPE uses.
private val REGIME_TF_SHAPE = RoundedCornerShape(11.dp)
private val REGIME_TF_HEIGHT = 52.dp
// Same evidence-style lerp as MomentumSheet's MOM_LIT_AMOUNT — one wash formula, reused.
private const val REGIME_TF_LIT_AMOUNT = 0.08f

/**
 * 2026-09-03 (Pieter's direct ask) — "same pattern as Momentum": D1/H4/H1 label above a
 * regime-tinted square, the regime word centred inside. Replaces the plain combined text row.
 */
@Composable
private fun RegimeTfSquare(label: String, regime: String?, colors: AtomColors, modifier: Modifier = Modifier) {
    val hue = tintColor(regimeTint(regime), colors)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(REGIME_TF_HEIGHT)
                .background(lerp(colors.surfaceRaised, hue, REGIME_TF_LIT_AMOUNT), REGIME_TF_SHAPE),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = tfRegimeWord(regime), style = AtomType.Body.copy(color = hue))
        }
    }
}

private fun tfRegimeWord(regime: String?): String = when (regime) {
    "Risk-On" -> "R-on"
    "Risk-Off" -> "R-off"
    "Mixed" -> "Mixed"
    "Ranging" -> "Ranging"
    else -> "—"
}
