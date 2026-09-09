package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Pills
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.DarkColors
import com.pieter.atomfx.ui.theme.lighten

/**
 * Signals Roadmap §2.7 — the "3-TF alignment" strip. `pills.{d1,h4,h1}` is the same frozen
 * 5-state technical score (`score.py`) the wheel's pill colours already read from; this is a
 * compact readout of it, not a new calculation (Rule #1: NEW/pure UI, reads an existing field).
 *
 * 2026-09-09 (Pieter's ask) — Pair Sheet reorg: moved from a standalone strip sitting above the
 * tabs into Breakdown's new ALIGNMENT section (see PairSheet.kt's own doc comment). Restyled onto
 * the shared `SmallPillCell` (SheetComponents.kt) — label above, small wash-squircle below,
 * exactly matching MOMENTUM's own bars below it in the same tab for uniformity ("style the
 * Momentum pills like the technical pills... but keep the text like MOM's pills are now" —
 * dropped the earlier same-day "D1 · B" inline text for a plain label-above/value-inside layout
 * to match). Found live: at the old, taller card size sitting right under the header, these read
 * as tappable buttons (Pieter's own words) — indistinguishable from the sheet's real controls.
 */
@Composable
fun TfAlignmentStrip(pills: Pills?, colors: AtomColors) {
    if (pills == null) return
    val isDark = colors == DarkColors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TfPill("D1", pills.d1, colors, isDark, Modifier.weight(1f))
        TfPill("H4", pills.h4, colors, isDark, Modifier.weight(1f))
        TfPill("H1", pills.h1, colors, isDark, Modifier.weight(1f))
    }
}

private fun pillAbbrev(pill: String?): String = when (pill) {
    "bull_strong" -> "SB"
    "bull" -> "B"
    "bear" -> "S"
    "bear_strong" -> "SS"
    else -> "N"
}

private fun pillColor(pill: String?, colors: AtomColors): Color = when (pill) {
    "bull_strong" -> colors.bull
    "bull" -> colors.bullSoft
    "bear" -> colors.bearSoft
    "bear_strong" -> colors.bear
    else -> colors.neutral
}

@Composable
private fun TfPill(label: String, pill: String?, colors: AtomColors, isDark: Boolean, modifier: Modifier = Modifier) {
    val tint = pillColor(pill, colors)
    // Same electric-pill text-colour rule as ScrollingPills: lighten(tint, 0.45) only reads
    // correctly against a near-black wash — light theme's tint is already tuned to sit on white.
    val textColor = if (isDark) lighten(tint, 0.45f) else tint
    SmallPillCell(label, tint, colors, modifier) {
        Text(
            text = pillAbbrev(pill),
            style = AtomType.Caption.copy(color = textColor, fontWeight = FontWeight.Normal),
        )
    }
}
