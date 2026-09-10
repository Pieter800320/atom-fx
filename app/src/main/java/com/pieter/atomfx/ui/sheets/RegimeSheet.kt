package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.push.TECHNICAL_REGIME_PLAYBOOK
import com.pieter.atomfx.ui.components.BookPlusGlyph
import com.pieter.atomfx.ui.reading.ReadingTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
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
fun RegimeSheet(signals: Signals, colors: AtomColors, onOpenReading: (ReadingTarget) -> Unit) {
    val haptics = LocalHapticFeedback.current
    // 2026-09-09 (Pieter's ask) — headline switched H4 -> D1, matching the wheel nucleus
    // (WheelMapper.mapNucleus) and the regime_flip push trigger (state_alerts.py): "the H4 Regime
    // changes too much." The three-square breakdown below still shows all of D1/H4/H1 for context.
    val headline = signals.regimeD1
    val playbookEntry = TECHNICAL_REGIME_PLAYBOOK[headline?.regime]

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(
            "MARKET REGIME",
            colors,
            trailingContent = if (playbookEntry == null) null else {
                {
                    BookPlusGlyph(
                        colors = colors,
                        modifier = Modifier.pressWash {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenReading(ReadingTarget.TechnicalRegime(playbookEntry))
                        },
                    )
                }
            },
        )

        val tint = tintColor(regimeTint(headline?.regime), colors)
        Text(
            text = regimeDisplayName(headline?.regime),
            style = AtomType.Display.copy(color = tint),
        )
        Text(
            text = "Confidence: ${(headline?.confidence ?: "—").uppercase()}",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SheetRow(
            label = "Score (D1)",
            value = headline?.score?.let { "${if (it >= 0) "+" else ""}%.1f".format(java.util.Locale.US, it) } ?: "—",
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
                text = "Timeframes disagree — treat the D1 read as the principal regime.",
                style = AtomType.Caption.copy(color = colors.watch),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        SheetDivider(colors)
        SheetRow(
            label = "Regime stability (D1)",
            value = when (headline?.stable) {
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

/**
 * 2026-09-03 (Pieter's direct ask) — "same pattern as Momentum": D1/H4/H1 label above a
 * regime-tinted square, the regime word centred inside. Replaces the plain combined text row.
 *
 * 2026-09-10 (Pieter's ask) — migrated onto the shared `SmallPillCell` (SheetComponents.kt)
 * instead of its own near-duplicate implementation (a taller opaque-lerp square that had never
 * actually been brought in line with Momentum's own later move to the alpha-wash squircle, despite
 * this composable's own doc comment claiming they already matched). Now genuinely one shape/
 * height/wash definition for every "non-scrolling pill" in the app, Regime included.
 */
@Composable
private fun RegimeTfSquare(label: String, regime: String?, colors: AtomColors, modifier: Modifier = Modifier) {
    val hue = tintColor(regimeTint(regime), colors)
    SmallPillCell(label, hue, colors, modifier) {
        Text(text = tfRegimeWord(regime), style = AtomType.Body.copy(color = hue))
    }
}

private fun tfRegimeWord(regime: String?): String = when (regime) {
    "Risk-On" -> "R-on"
    "Risk-Off" -> "R-off"
    "Mixed" -> "Mixed"
    "Ranging" -> "Ranging"
    else -> "—"
}
