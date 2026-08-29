package com.pieter.atomfx.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors

/**
 * Design §19.1 — a restrained close-price line, no axes/grid/candles: a 1.5dp stroke, a soft
 * ~8%-alpha area fill beneath it, a faint baseline at the window's first value, and an
 * emphasised endpoint dot. Colour comes from the series itself (endpoint vs window-start), not
 * any pair-level direction field the caller might have — this is a plain data-viz read of the
 * exact numbers on screen.
 */
@Composable
fun LineChart(closes: List<Double>, colors: AtomColors, modifier: Modifier = Modifier) {
    if (closes.size < 2) {
        Canvas(modifier = modifier) { /* nothing to draw — caller shows NotAvailableRow instead */ }
        return
    }

    val lineColor = when {
        closes.last() > closes.first() -> colors.bull
        closes.last() < closes.first() -> colors.bear
        else -> colors.neutral
    }

    Canvas(modifier = modifier) {
        val min = closes.min()
        val max = closes.max()
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (closes.size - 1)

        fun pointAt(i: Int): Offset {
            val normalized = (closes[i] - min) / range
            val y = size.height - (normalized * size.height).toFloat()
            return Offset(i * stepX, y)
        }

        val baselineY = pointAt(0).y
        drawLine(
            color = colors.hairline,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx(),
        )

        val linePath = Path().apply {
            moveTo(pointAt(0).x, pointAt(0).y)
            for (i in 1 until closes.size) lineTo(pointAt(i).x, pointAt(i).y)
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(pointAt(closes.size - 1).x, size.height)
            lineTo(pointAt(0).x, size.height)
            close()
        }

        drawPath(fillPath, color = lineColor.copy(alpha = 0.08f))
        drawPath(linePath, color = lineColor, style = Stroke(width = 1.5.dp.toPx()))

        val endpoint = pointAt(closes.size - 1)
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = endpoint)
    }
}
