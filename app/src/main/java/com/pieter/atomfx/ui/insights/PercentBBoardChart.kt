package com.pieter.atomfx.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.PercentBBoardBlock
import com.pieter.atomfx.ui.chart.PercentBOscillator
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * 2026-09-10 (Pieter's ask) — "Board %B": the market-wide average of every pair's own %B/signal
 * line (`bb_touch.py`'s `compute_board_percent_b`), a 4th tab in `MarketIndicatorsCard` alongside
 * Rotation/Pulse/Thrust. Same `PercentBOscillator` drawing the per-pair chart uses
 * (`ui/sheets/PercentBChart.kt`) — only the data source differs.
 */
@Composable
fun PercentBBoardChart(board: PercentBBoardBlock?, colors: AtomColors) {
    PercentBOscillator(board?.line.orEmpty(), board?.signal.orEmpty(), colors, dates = board?.dates.orEmpty())
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem("%B", colors.textSecondary, colors)
        LegendItem("Signal (SMA 12)", colors.watch, colors)
    }
    Text(
        text = "The whole board's average %B — how stretched are pairs against their own D1 Bollinger bands, on average?",
        style = AtomType.Caption.copy(color = colors.textMuted),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LegendItem(label: String, color: Color, colors: AtomColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).background(color, CircleShape))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
    }
}
