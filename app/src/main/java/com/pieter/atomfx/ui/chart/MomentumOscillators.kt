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

private val CHART_HEIGHT = 120.dp

/**
 * RSI (Wilder, 14-period — `scanner/score.py::_rsi`, unedited). Classic 0-100 oscillator, 30/70
 * dashed reference lines (the industry-standard levels every platform shows, not this app's own
 * tuning), a centre line at 50. No signal line — plain RSI doesn't have one; adding a fabricated
 * one would make this look like a different indicator than the one traders already know.
 * 2026-09-17 (Pieter's ask, ChartSheet glance panel).
 */
@Composable
fun RsiOscillator(values: List<Double>, colors: AtomColors, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        val padTop = 8f
        val padBottom = 8f
        val plotH = size.height - padTop - padBottom
        fun py(v: Double): Float = (padTop + (1.0 - v.coerceIn(0.0, 100.0) / 100.0) * plotH).toFloat()

        val n = values.size
        val stepX = size.width / (n - 1)
        fun px(i: Int): Float = i * stepX

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        listOf(30.0, 70.0).forEach { threshold ->
            drawLine(colors.hairlineStrong, Offset(0f, py(threshold)), Offset(size.width, py(threshold)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(colors.hairlineStrong, Offset(0f, py(50.0)), Offset(size.width, py(50.0)), strokeWidth = 1.dp.toPx())

        val path = Path().apply {
            moveTo(px(0), py(values[0]))
            for (i in 1 until n) lineTo(px(i), py(values[i]))
        }
        // Overbought/oversold reads as a bear/bull tilt (>=70 stretched up, due to revert; <=30
        // stretched down) — same "upper touch = bear, lower touch = bull" convention ChartSheet's
        // own %B touch colouring already uses, so the two charts agree on what a stretched
        // reading means rather than inventing a second colour rule.
        val last = values.last()
        val lineColor = when {
            last >= 70.0 -> colors.bear
            last <= 30.0 -> colors.bull
            else -> colors.textSecondary
        }
        drawPath(path, color = lineColor, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(px(n - 1), py(last)))
    }
}

/**
 * MACD (12/26/9 EMA — `scanner/score.py::_macd`, unedited): the histogram (line minus signal) as
 * bars, tinted by sign, plus the MACD/signal lines themselves overlaid — the standard three-part
 * MACD read every platform shows, not a simplified subset. Unbound (unlike RSI/%B), so the Y-axis
 * auto-scales to whatever range this pair's own data actually spans, symmetric around zero so the
 * zero line — the only fixed reference MACD has — always sits at the vertical centre.
 * 2026-09-17 (Pieter's ask, ChartSheet glance panel).
 */
@Composable
fun MacdOscillator(
    macdLine: List<Double>,
    macdSignal: List<Double>,
    histogram: List<Double>,
    colors: AtomColors,
    modifier: Modifier = Modifier,
) {
    if (macdLine.size < 2 || histogram.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        val padTop = 8f
        val padBottom = 8f
        val plotH = size.height - padTop - padBottom

        // Symmetric range around zero, from the widest excursion across all three series, so a
        // bar or line never clips and zero always sits at the vertical centre.
        val maxAbs = (macdLine + macdSignal + histogram).maxOfOrNull { kotlin.math.abs(it) }
            ?.takeIf { it > 0.0 } ?: 1.0
        fun py(v: Double): Float = (padTop + (1.0 - (v / maxAbs + 1.0) / 2.0) * plotH).toFloat()
        val zeroY = py(0.0)

        val hN = histogram.size
        val hStepX = size.width / hN
        val barWidth = hStepX * 0.6f
        histogram.forEachIndexed { i, v ->
            val x = i * hStepX + (hStepX - barWidth) / 2f
            val y = py(v)
            val top = if (v >= 0) y else zeroY
            val bottom = if (v >= 0) zeroY else y
            drawRect(
                color = if (v >= 0) colors.bull else colors.bear,
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, (bottom - top).coerceAtLeast(1f)),
            )
        }

        drawLine(colors.hairlineStrong, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())

        fun linePath(series: List<Double>): Path {
            val n = series.size
            val stepX = size.width / (n - 1).coerceAtLeast(1)
            return Path().apply {
                moveTo(0f, py(series[0]))
                for (i in 1 until n) lineTo(i * stepX, py(series[i]))
            }
        }
        drawPath(linePath(macdLine), color = colors.textPrimary, style = Stroke(width = 1.5.dp.toPx()))
        if (macdSignal.size >= 2) {
            drawPath(linePath(macdSignal), color = colors.watch, style = Stroke(width = 1.dp.toPx()))
        }
    }
}
