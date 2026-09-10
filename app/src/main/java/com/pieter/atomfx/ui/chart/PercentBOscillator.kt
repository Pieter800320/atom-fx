package com.pieter.atomfx.ui.chart

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

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
 * [dates] (2026-09-10, 2nd) — one ISO date per [line] point, same order/length; three sparse
 * labels (oldest/middle/newest) print below the plot, matching the app's restrained "no axis
 * clutter" convention (RotationChart's own single "CSM →" label) rather than one per bar. Empty
 * or length-mismatched silently draws no date row at all — never a misaligned guess.
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
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (line.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    val n = line.size
    val hasDates = dates.size == n
    val chartHeight = if (hasDates) 178.dp else 160.dp

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight)) {
        val padTop = 10f
        val padBottom = 10f
        val dateRowHeight = if (hasDates) 18.dp.toPx() else 0f
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
        drawPath(linePath, color = colors.textSecondary, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = colors.textSecondary, radius = 3.dp.toPx(), center = Offset(px(n - 1), py(line.last())))

        // signal is shorter than line by its own smoothing window — right-align under line's tail.
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

        if (hasDates) {
            val labelPaint = Paint().apply {
                isAntiAlias = true
                textSize = 9.dp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = colors.textMuted.toArgb()
            }
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val labelY = padTop + plotH + dateRowHeight - 4.dp.toPx()
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
    }
}
