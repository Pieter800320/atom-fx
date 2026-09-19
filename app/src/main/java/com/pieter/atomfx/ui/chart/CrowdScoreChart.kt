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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * The indicator's own flag line (Signals Roadmap §5c) — NOT a tuned threshold. It is the line the
 * TradingView indicator draws dotted at 60, the line a flag is defined against, and the only line
 * this chart draws besides the two scores; the footer's state word (`crowdState`, `SheetComponents.kt`)
 * reads off this same number, so the chart and its footer can never disagree.
 */
internal const val CROWD_FLAG_LINE = 60.0

/**
 * The Crowd score chart (2026-09-19, Pieter's ask: "a graph that looks exactly like the indicator on
 * TradingView, under the long-press graphs") — the Crowded Market indicator's two score lines, 0-100:
 * **top** in `bear` (red — price stretched up and crowded long) and **bottom** in `bull` (green — the
 * mirror), a dashed reference at 60, and a small dot on each bar where a side first reaches 60 (the
 * indicator's own ▼ TOP / ▲ BOTTOM flag; TradingView's text labels are too wide for this many bars — the
 * value is in the card header instead).
 *
 * Two coloured lines are a deliberate, agreed exception to the panel's "every line is white" rule
 * (Design §19.4b, 3rd restyle) — the same reason MACD's signal line is coloured: two overlaid lines
 * must be tellable apart. Top is drawn first and bottom second, so when both sit at 0 (most bars) the
 * baseline reads green, exactly as it does on TradingView.
 *
 * Pure consumer: draws the series `signals.json` carries and computes nothing (Architecture §8.3).
 * Context, not a signal — see `CrowdScoreCard` and the Library entry.
 */
@Composable
fun CrowdScoreChart(
    top: List<Double>,
    bottom: List<Double>,
    colors: AtomColors,
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val n = minOf(top.size, bottom.size)
    if (n < 2) {
        Box(modifier = modifier.fillMaxWidth().height(BASE_CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

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
        drawLine(
            colors.hairlineStrong, Offset(0f, py(CROWD_FLAG_LINE)), Offset(size.width, py(CROWD_FLAG_LINE)),
            strokeWidth = 1.dp.toPx(), pathEffect = dash,
        )

        // top first, bottom second — see the doc comment
        drawScoreLine(top, n, colors.bear, ::px, ::py)
        drawScoreLine(bottom, n, colors.bull, ::px, ::py)

        if (hasDates) drawDateRow(dates, ::px, padTop + plotH + dateRowHeight - 4.dp.toPx(), colors)
    }
}

/** One score line, its flag dots (a side's first bar at/over the flag line — never the very first bar, which
 * has no previous bar to have risen from) and its glowing endpoint. */
private fun DrawScope.drawScoreLine(
    values: List<Double>,
    n: Int,
    color: Color,
    px: (Int) -> Float,
    py: (Double) -> Float,
) {
    val path = Path().apply {
        moveTo(px(0), py(values[0]))
        for (i in 1 until n) lineTo(px(i), py(values[i]))
    }
    drawPath(path, color = color, style = Stroke(width = 1.5.dp.toPx()))
    for (i in 1 until n) {
        if (values[i] >= CROWD_FLAG_LINE && values[i - 1] < CROWD_FLAG_LINE) {
            drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(px(i), py(values[i])))
        }
    }
    val endpoint = Offset(px(n - 1), py(values[n - 1]))
    glowDot(endpoint, color, 8.dp.toPx())
    drawCircle(color = color, radius = 3.dp.toPx(), center = endpoint)
}
