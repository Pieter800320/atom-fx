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
 * 2026-09-10 (Pieter's ask) — a Bollinger %B oscillator: the raw %B line, its own SMA as a
 * signal line, a centre line at 50, and dashed threshold lines at 10/90
 * (tunable — Pieter is still watching real data to see which levels hold up, so these are display
 * reference lines only, not a backend gate). `line`/`signal` are oldest-first; `signal` is shorter
 * than `line` by its own smoothing window and right-aligns under `line`'s most recent points —
 * both come from the same underlying %B series, never independently indexed.
 *
 * Y-domain is clamped 0-100 for *drawing only* — raw %B legitimately pierces past 0 or 100 (a
 * real "walk along the band"); the backend keeps that true value, only this chart bounds it, per
 * Pieter's own "normalise to oscillate between 0 and 100" ask.
 *
 * [dates] (2026-09-10, 2nd) — one ISO date per [line] point, same order/length; three sparse
 * labels (oldest/middle/newest) print below the plot, matching the app's restrained "no axis
 * clutter" convention (RotationChart's own single "CSM →" label) rather than one per bar. Empty
 * or length-mismatched silently draws no date row at all — never a misaligned guess.
 *
 * Deliberately period-agnostic: it draws whatever line/signal pair it is handed. Two different
 * band periods feed it today — ChartSheet's glance-panel %B (standard 20-period,
 * `bollinger_series.py`) and Currency %B (12-period, `bb_touch.py`) — so each CALLER names its own
 * period in its legend rather than this primitive assuming one. One place the visual lives, which
 * is what stops near-identical Canvas blocks drifting apart.
 *
 * **Corrected 2026-09-18:** this comment used to cite `ui/insights/PercentBBoardChart.kt` as the
 * second caller. That file was deleted outright with the Insights %B picker (Design §19.4) and
 * does not exist anywhere in the repo — checked, not assumed.
 */
@Composable
fun PercentBOscillator(
    line: List<Double>,
    signal: List<Double>,
    colors: AtomColors,
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (line.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(BASE_CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    val n = line.size
    val hasDates = dates.size == n

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight(hasDates))) {
        val padTop = 10f
        val padBottom = 10f
        val dateRowHeight = if (hasDates) DATE_ROW_HEIGHT.toPx() else 0f
        val plotH = size.height - padTop - padBottom - dateRowHeight
        fun py(v: Double): Float = (padTop + (1.0 - v.coerceIn(0.0, 100.0) / 100.0) * plotH).toFloat()

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
        // 2026-09-18 (Pieter's restyle ask) — white, matching every other glance-panel line;
        // was textSecondary (a grey).
        drawPath(linePath, color = colors.textPrimary, style = Stroke(width = 1.5.dp.toPx()))
        val endpoint = Offset(px(n - 1), py(line.last()))
        // 2026-09-18 — glow to match RSI/MACD/BandWidth's own endpoint treatment; %B was the one
        // chart still missing it.
        glowDot(endpoint, colors.textPrimary, 8.dp.toPx())
        drawCircle(color = colors.textPrimary, radius = 3.dp.toPx(), center = endpoint)

        // signal is shorter than line by its own smoothing window — right-align under line's tail.
        // 2026-09-18 (Pieter's restyle ask) — ChartSheet's own %B(20) card no longer passes a
        // signal series at all (empty list), so this branch only still fires for Currency %B
        // (CurrencyDetailSheet), which keeps its own signal line untouched.
        if (signal.size >= 2) {
            val offset = n - signal.size
            val signalPath = Path().apply {
                moveTo(px(offset), py(signal[0]))
                for (i in 1 until signal.size) lineTo(px(offset + i), py(signal[i]))
            }
            // 2026-09-10 (Pieter's ask) — thin, not bolder than the raw %B line (was 2dp).
            drawPath(signalPath, color = colors.watch, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = colors.watch, radius = 3.dp.toPx(), center = Offset(px(n - 1), py(signal.last())))
        }

        // 2026-09-18 — was an inline copy of this exact loop; now the one shared helper in
        // `ChartCommon.kt` that every glance-panel chart draws its date row with.
        if (hasDates) drawDateRow(dates, ::px, padTop + plotH + dateRowHeight - 4.dp.toPx(), colors)
    }
}
