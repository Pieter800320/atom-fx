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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import kotlin.math.abs

/**
 * RSI (Wilder, 14-period — `scanner/score.py::_rsi`, unedited). Classic 0-100 oscillator, a
 * centre line at 50, the overbought/oversold band shaded so a stretched reading reads at a
 * glance (Pieter's restyle ask). No signal line — plain RSI doesn't have one; adding a
 * fabricated one would make this look like a different indicator than the one traders already
 * know. 2026-09-17 (Pieter's ask, ChartSheet glance panel); restyled 2026-09-18.
 *
 * **Dashed reference lines draw at RSI's own real 30/70 (reverted 2026-09-18, 9th).** A same-day
 * earlier restyle briefly moved these to `THRESHOLD_LINE_LOW`/`_HIGH` (10/90, matching %B's own
 * dashed-line pixel height) for panel-wide visual consistency — but real RSI(14) rarely swings
 * below 10 or above 90 (it typically lives in 20-80), so on live data the line almost never
 * reached the widened lines, making the chart look like it was ignoring its own thresholds
 * (Pieter's catch, watching it live). Reverted: the lines are RSI's real 30/70 again, so the line
 * visibly crosses them at genuine overbought/oversold extremes, same as any standard RSI chart —
 * traded panel-wide symmetry with %B for the line actually meaning something against its own
 * lines. The Overbought/Oversold/Neutral footer state (`RsiCard`, `ChartSheet.kt`) was always
 * reading the real 30/70 regardless of where the lines were drawn; unaffected either way.
 */
@Composable
fun RsiOscillator(values: List<Double>, colors: AtomColors, dates: List<String> = emptyList(), modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(BASE_CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    val n = values.size
    val hasDates = dates.size == n

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight(hasDates))) {
        val padTop = 10f
        val padBottom = 10f
        val dateRowHeight = if (hasDates) DATE_ROW_HEIGHT.toPx() else 0f
        val plotH = size.height - padTop - padBottom - dateRowHeight
        fun py(v: Double): Float = (padTop + (1.0 - v.coerceIn(0.0, 100.0) / 100.0) * plotH).toFloat()

        val stepX = size.width / (n - 1)
        fun px(i: Int): Float = i * stepX

        // 2026-09-18 (Pieter's restyle ask, 2nd — "%B is the template") — the shaded band
        // between 30/70 is gone; dashed-line-only now matches %B's own threshold treatment
        // exactly, so every glance-panel chart's reference lines look the same.
        //
        // 2026-09-18 (9th, reverted) — back to RSI's own real 30/70 (briefly 10/90 earlier the
        // same day) — see this file's own doc comment on RsiOscillator for why.
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        listOf(30.0, 70.0).forEach { threshold ->
            drawLine(colors.hairlineStrong, Offset(0f, py(threshold)), Offset(size.width, py(threshold)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(colors.hairlineStrong, Offset(0f, py(50.0)), Offset(size.width, py(50.0)), strokeWidth = 1.dp.toPx())

        val path = Path().apply {
            moveTo(px(0), py(values[0]))
            for (i in 1 until n) lineTo(px(i), py(values[i]))
        }
        // 2026-09-18 (Pieter's restyle ask, 2nd) — always white, matching every other
        // glance-panel line. Was bear/bull-tinted at >=70/<=30; the 30/70 dashed lines
        // already carry that read, tinting the line itself on top was one signal too many.
        drawPath(path, color = colors.textPrimary, style = Stroke(width = 1.2.dp.toPx()))
        val endpoint = Offset(px(n - 1), py(values.last()))
        glowDot(endpoint, colors.textPrimary, 8.dp.toPx())
        drawCircle(color = colors.textPrimary, radius = 3.dp.toPx(), center = endpoint)

        if (hasDates) drawDateRow(dates, ::px, padTop + plotH + dateRowHeight - 4.dp.toPx(), colors)
    }
}

/**
 * MACD (12/26/9 EMA — `scanner/score.py::_macd`, unedited): the histogram (line minus signal) as
 * bars, tinted by sign, plus the MACD/signal lines themselves overlaid — the standard three-part
 * MACD read every platform shows, not a simplified subset.
 *
 * The histogram and the two lines get INDEPENDENT vertical scales, both symmetric around the same
 * shared zero line — the histogram is a difference of two similarly-sized series, so it is
 * mathematically much smaller than either line on its own; one shared scale flattens it to a
 * sliver. Two scales keep the zero line meaningful for both while letting each read at full
 * height (Pieter's restyle ask, "the MACD histogram appear a little higher").
 * 2026-09-17 (Pieter's ask, ChartSheet glance panel); restyled 2026-09-18.
 */
@Composable
fun MacdOscillator(
    macdLine: List<Double>,
    macdSignal: List<Double>,
    histogram: List<Double>,
    colors: AtomColors,
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (macdLine.size < 2 || histogram.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(BASE_CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    val n = histogram.size
    val hasDates = dates.size == n

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight(hasDates))) {
        val padTop = 10f
        val padBottom = 10f
        val dateRowHeight = if (hasDates) DATE_ROW_HEIGHT.toPx() else 0f
        val plotH = size.height - padTop - padBottom - dateRowHeight

        val lineMaxAbs = (macdLine + macdSignal).maxOfOrNull { abs(it) }?.takeIf { it > 0.0 } ?: 1.0
        val histMaxAbs = histogram.maxOfOrNull { abs(it) }?.takeIf { it > 0.0 } ?: 1.0
        fun pyLine(v: Double): Float = (padTop + (1.0 - (v / lineMaxAbs + 1.0) / 2.0) * plotH).toFloat()
        fun pyHist(v: Double): Float = (padTop + (1.0 - (v / histMaxAbs + 1.0) / 2.0) * plotH).toFloat()
        val zeroY = pyLine(0.0)

        // One shared slot grid for bars AND line points, so a line point sits directly above/
        // below the histogram bar for the same bar — the two series were drifting apart visually
        // before (bars keyed off their own count, lines off theirs).
        val stepX = size.width / n
        val barWidth = stepX * 0.6f
        fun barX(i: Int): Float = i * stepX + (stepX - barWidth) / 2f
        fun px(i: Int): Float = i * stepX + stepX / 2f

        histogram.forEachIndexed { i, v ->
            val x = barX(i)
            val y = pyHist(v)
            val top = if (v >= 0) y else zeroY
            val bottom = if (v >= 0) zeroY else y
            drawRect(
                color = if (v >= 0) colors.bull else colors.bear,
                topLeft = Offset(x, top),
                size = Size(barWidth, (bottom - top).coerceAtLeast(1f)),
            )
        }

        drawLine(colors.hairlineStrong, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())

        fun linePath(series: List<Double>): Path {
            val m = series.size
            val offset = n - m // right-align if ever shorter than the histogram
            return Path().apply {
                moveTo(px(offset), pyLine(series[0]))
                for (i in 1 until m) lineTo(px(offset + i), pyLine(series[i]))
            }
        }
        drawPath(linePath(macdLine), color = colors.textPrimary, style = Stroke(width = 1.2.dp.toPx()))
        if (macdSignal.size >= 2) {
            drawPath(linePath(macdSignal), color = colors.watch, style = Stroke(width = 1.dp.toPx()))
        }
        val lastLine = macdLine.last()
        val endpoint = Offset(px(n - 1), pyLine(lastLine))
        glowDot(endpoint, colors.textPrimary, 8.dp.toPx())
        drawCircle(color = colors.textPrimary, radius = 3.dp.toPx(), center = endpoint)

        if (hasDates) drawDateRow(dates, ::px, padTop + plotH + dateRowHeight - 4.dp.toPx(), colors)
    }
}
