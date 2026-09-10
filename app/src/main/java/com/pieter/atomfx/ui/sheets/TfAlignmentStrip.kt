package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Pills
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

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
 *
 * Same day, follow-up (Pieter's ask) — the abbreviation text was tinted (lighten(tint, 0.45) in
 * dark theme), then plain `textPrimary`, matching MOM's own value-number colour at the time —
 * both superseded 2026-09-10, when the standard flipped the other way: every non-scrolling
 * pill's main text now adopts the wash's own colour directly (no lighten), started on
 * RegimeSheet's pills and rolled out everywhere, MOM's own value included. This pill matches
 * that exactly again, just under a different rule than before.
 */
@Composable
fun TfAlignmentStrip(pills: Pills?, colors: AtomColors) {
    if (pills == null) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TfPill("D1", pills.d1, colors, Modifier.weight(1f))
        TfPill("H4", pills.h4, colors, Modifier.weight(1f))
        TfPill("H1", pills.h1, colors, Modifier.weight(1f))
    }
}

private fun pillAbbrev(pill: String?): String = when (pill) {
    "bull_strong" -> "SB"
    "bull" -> "B"
    "bear" -> "S"
    "bear_strong" -> "SS"
    else -> "N"
}

// 2026-09-10 (Pieter's ask) — "bull" used to read fainter than "bull_strong" (bullSoft vs full
// bull, same for bear/bear_strong) — now both members of a direction share one full-strength
// colour; SB/B and S/SS are only distinguished by their own abbreviation text, not by a second,
// fainter colour. Neutral (grey) is unchanged.
private fun pillColor(pill: String?, colors: AtomColors): Color = when (pill) {
    "bull_strong", "bull" -> colors.bull
    "bear", "bear_strong" -> colors.bear
    else -> colors.neutral
}

@Composable
private fun TfPill(label: String, pill: String?, colors: AtomColors, modifier: Modifier = Modifier) {
    val tint = pillColor(pill, colors)
    // 2026-09-10 (Pieter's ask) — was plain textPrimary; now adopts the pill's own wash colour,
    // matching RegimeSheet's pills (the standard now for every non-scrolling pill).
    SmallPillCell(label, tint, colors, modifier) {
        Text(text = pillAbbrev(pill), style = AtomType.Body.copy(color = tint))
    }
}
