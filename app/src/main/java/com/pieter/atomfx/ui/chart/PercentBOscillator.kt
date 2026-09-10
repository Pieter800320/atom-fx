package com.pieter.atomfx.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * 2026-09-10 (Pieter's ask) — a 12-period Bollinger %B oscillator: the raw %B line, its own
 * 12-period SMA as a signal line, a centre line at 50, and dashed threshold lines at 10/90
 * (tunable — Pieter is still watching real data to see which levels hold up, so these are display
 * reference lines only, not a backend gate). `line`/`signal` are oldest-first; `signal` is shorter
 * than `line` by its own smoothing window and right-aligns under `line`'s most recent points —
 * both come from the same underlying %B series, never independently indexed.
 *
 * Y-domain is clamped 0-100 for *drawing only* — raw %B legitimately pierces past 0 or 100 (a
 * real "walk along the band"); the backend keeps that true value, only this chart bounds it, per
 * Pieter's own "normalise to oscillate between 0 and 100" ask.
 *
 * Shared by the per-pair chart (`ui/sheets/PercentBChart.kt`) and the market-wide "Board %B"
 * chart (`ui/insights/PercentBBoardChart.kt`) — same drawing, different data source, so this is
 * the one place the visual actually lives (avoids two near-identical Canvas blocks drifting).
 */
@Composable
fun PercentBOscillator(
    line: List<Double>,
    signal: List<Double>,
    colors: AtomColors,
    modifier: Modifier = Modifier,
) {
    if (line.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val padTop = 10f
        val padBottom = 10f
        val plotH = size.height - padTop - padBottom
        fun py(v: Double): Float = (padTop + (1.0 - v.coerceIn(0.0, 100.0) / 100.0) * plotH).toFloat()

        val n = line.size
        val stepX = size.width / (n - 1)
        fun px(i: Int): Float = i * stepX

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        listOf(10.0, 90.0).forEach { threshold ->
            drawLine(colors.hairlineStrong, Offset(0f, py(threshold)), Offset(size.width, py(threshold)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(colors.hairlineStrong, Offset(0f, py(50.0)), Offset(size.width, py(50.0)), strokeWidth = 1.dp.toPx())

        val linePath = Path().apply {
            moveTo(px(0), py(line[0]))
            for (i in 1 until n) lineTo(px(i), py(line[i]))
        }
        drawPath(linePath, color = colors.textSecondary, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = colors.textSecondary, radius = 3.dp.toPx(), center = Offset(px(n - 1), py(line.last())))

        // signal is shorter than line by its own smoothing window — right-align under line's tail.
        if (signal.size >= 2) {
            val offset = n - signal.size
            val signalPath = Path().apply {
                moveTo(px(offset), py(signal[0]))
                for (i in 1 until signal.size) lineTo(px(offset + i), py(signal[i]))
            }
            drawPath(signalPath, color = colors.watch, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = colors.watch, radius = 3.5.dp.toPx(), center = Offset(px(n - 1), py(signal.last())))
        }
    }
}
