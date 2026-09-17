package com.pieter.atomfx.ui.chart

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// 2026-09-18 (Pieter's restyle ask, "very flat... increase the vertical level") — up from a flat
// 120dp for both charts, same taller frame `PercentBOscillator` already earns with its own date
// row (160/178dp split).
private val BASE_CHART_HEIGHT = 168.dp
private val DATE_ROW_HEIGHT = 18.dp
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private fun chartHeight(hasDates: Boolean): Dp = if (hasDates) BASE_CHART_HEIGHT + DATE_ROW_HEIGHT else BASE_CHART_HEIGHT

/** A soft blurred disc behind a chart's current-value endpoint dot — same `BlurMaskFilter`
 * technique the wheel's own hub glow uses (`WheelCanvas.kt::glowFillCircle`), not a new visual
 * language, just this app's existing "depth via blur" treatment on a second surface. */
private fun DrawScope.glowDot(center: Offset, color: Color, radiusPx: Float) {
    val paint = Paint().apply {
        this.color = color.copy(alpha = 0.45f).toArgb()
        style = Paint.Style.FILL
        isAntiAlias = true
        maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawCircle(center.x, center.y, radiusPx, paint)
}

/** Three sparse date labels (oldest/middle/newest) below the plot — same convention and layout
 * `PercentBOscillator.kt` already uses; factored out here since RSI and MACD both need it. */
private fun DrawScope.drawDateRow(dates: List<String>, px: (Int) -> Float, labelY: Float, colors: AtomColors) {
    val n = dates.size
    val labelPaint = Paint().apply {
        isAntiAlias = true
        textSize = 9.dp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = colors.textMuted.toArgb()
    }
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val indices = listOf(0, n / 2, n - 1)
    indices.forEachIndexed { pos, i ->
        val text = runCatching { LocalDate.parse(dates[i]).format(DATE_FORMAT) }.getOrNull() ?: return@forEachIndexed
        labelPaint.textAlign = when (pos) {
            0 -> Paint.Align.LEFT
            indices.size - 1 -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
        nativeCanvas.drawText(text, px(i), labelY, labelPaint)
    }
}

/**
 * RSI (Wilder, 14-period — `scanner/score.py::_rsi`, unedited). Classic 0-100 oscillator, 30/70
 * dashed reference lines (the industry-standard levels every platform shows, not this app's own
 * tuning), a centre line at 50, the overbought/oversold band shaded so a stretched reading reads
 * at a glance (Pieter's restyle ask). No signal line — plain RSI doesn't have one; adding a
 * fabricated one would make this look like a different indicator than the one traders already
 * know. 2026-09-17 (Pieter's ask, ChartSheet glance panel); restyled 2026-09-18.
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

        // Pieter's restyle ask — "the area between the thresholds can be darker."
        drawRect(
            color = colors.scrim.copy(alpha = 0.35f),
            topLeft = Offset(0f, py(70.0)),
            size = Size(size.width, py(30.0) - py(70.0)),
        )

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
        // reading means.
        val last = values.last()
        val lineColor = when {
            last >= 70.0 -> colors.bear
            last <= 30.0 -> colors.bull
            else -> colors.textSecondary
        }
        drawPath(path, color = lineColor, style = Stroke(width = 1.2.dp.toPx()))
        val endpoint = Offset(px(n - 1), py(last))
        glowDot(endpoint, lineColor, 8.dp.toPx())
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = endpoint)

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
