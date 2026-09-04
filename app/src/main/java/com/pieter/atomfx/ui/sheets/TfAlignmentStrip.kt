package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Pills
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * Signals Roadmap §2.7 — the "3-TF alignment" strip. `pills.{d1,h4,h1}` is the same frozen
 * 5-state technical score (`score.py`) the wheel's pill colours already read from; this is a
 * compact readout of it, not a new calculation (Rule #1: NEW/pure UI, reads an existing field).
 *
 * Same "label above, tinted squircle below, centred value" recipe as MomentumSheet's `MomBar` —
 * reused verbatim rather than inventing a second visual language for the same three timeframes
 * already anchoring this row (Pieter, 2026-09-04: keep it a standalone strip, not folded into
 * the sparkline cards below).
 */
@Composable
fun TfAlignmentStrip(pills: Pills?, colors: AtomColors) {
    if (pills == null) return
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        TfCell("D1", pills.d1, colors, Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        TfCell("H4", pills.h4, colors, Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        TfCell("H1", pills.h1, colors, Modifier.weight(1f))
    }
}

private val TF_CELL_SHAPE = RoundedCornerShape(11.dp)
private val TF_CELL_HEIGHT = 36.dp
// Same subtle evidence-style lerp as MomBar/EvidenceAxes/WhyChecklist — an opaque wash, not an
// alpha composite.
private const val TF_LIT_AMOUNT = 0.08f

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
private fun TfCell(label: String, pill: String?, colors: AtomColors, modifier: Modifier = Modifier) {
    val hue = pillColor(pill, colors)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(TF_CELL_HEIGHT)
                .background(lerp(colors.surfaceRaised, hue, TF_LIT_AMOUNT), TF_CELL_SHAPE),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = pillAbbrev(pill), style = AtomType.Body.copy(color = hue))
        }
    }
}
