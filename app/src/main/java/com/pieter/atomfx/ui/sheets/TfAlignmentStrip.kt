package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 * tabs into Breakdown's new ALIGNMENT section (see PairSheet.kt's own doc comment), and restyled
 * from MomBar's stacked "label above, tinted square below" card look to ScrollingPills' own
 * electric-pill treatment (same ELECTRIC_PILL_SHAPE/HEIGHT, wash fill, lightened text — Macro's
 * top-card pills, small local copy per this codebase's established house style rather than
 * exporting those private constants). Found live: at the old card size, sitting right under the
 * header, these read as tappable buttons (Pieter's own words) — indistinguishable from the
 * sheet's real controls. Deliberately not the `ScrollingPills` composable itself (built for a
 * scrollable, tappable list) — these three always stay visible and evenly weighted side by side
 * for direct comparison, and never respond to a tap.
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

// Exactly ScrollingPills' ELECTRIC_PILL_HEIGHT/SHAPE — see this file's own doc comment.
private val TF_PILL_HEIGHT = 26.dp
private val TF_PILL_SHAPE = RoundedCornerShape(11.dp)

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
    Box(
        modifier = modifier
            .height(TF_PILL_HEIGHT)
            .background(tint.copy(alpha = 0.18f), TF_PILL_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label · ${pillAbbrev(pill)}",
            style = AtomType.Caption.copy(color = textColor, fontWeight = FontWeight.Normal),
        )
    }
}
