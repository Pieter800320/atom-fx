package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.BbD1
import com.pieter.atomfx.ui.chart.PercentBOscillator
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * 2026-09-10 (Pieter's ask) — the per-pair Bollinger %B oscillator, `PairSheet.kt`'s Breakdown
 * tab, same narrowed-field convention as its ALIGNMENT/MOMENTUM/STRUCTURE siblings
 * (`TfAlignmentStrip`/`MomentumTabContent`/`StructureTabContent`). Pure display over
 * `bbD1.pctb`/`pctbSma`, which `bb_touch.py` now computes fresh every scan — no on-device math.
 */
@Composable
fun PercentBChart(bbD1: BbD1?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (bbD1 == null) {
            NotAvailableRow("Bollinger %B", colors)
            return@Column
        }
        PercentBOscillator(bbD1.pctb, bbD1.pctbSma, colors, dates = bbD1.pctbDates)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem("%B", colors.textSecondary, colors)
            LegendItem("Signal (SMA 12)", colors.watch, colors)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, colors: AtomColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).background(color, CircleShape))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
    }
}
