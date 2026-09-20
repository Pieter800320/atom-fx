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

/** The three dashed reference lines (Pieter's ask, 2026-09-20: "the 20, 40 AND 60 lines"). They are also the bands the Crowd score alert's minimum-level setting uses
 * (Any / 20 / 40 / 60), so the chart shows exactly the levels an alert can be set to. 60 stays the flag line and is drawn a step stronger. */
internal val CROWD_LEVEL_LINES = listOf(20.0, 40.0, 60.0)

/** Opacity of the strong-trend shading — about TradingView's own 92%-transparent background. */
private const val REGIME_SHADE_ALPHA = 0.10f

/** Contiguous runs of a non-zero regime as (firstIndex, lastIndex, sign) — what the chart shades. Pure, so it is unit-tested (`CrowdChartHelpersTest`). */
internal fun regimeRuns(regime: List<Int>): List<Triple<Int, Int, Int>> {
    val runs = mutableListOf<Triple<Int, Int, Int>>()
    var i = 0
    while (i < regime.size) {
        val sign = regime[i]
        if (sign == 0) { i++; continue }
        var j = i
        while (j + 1 < regime.size && regime[j + 1] == sign) j++
        runs += Triple(i, j, sign)
        i = j + 1
    }
    return runs
}

/**
 * The Crowd score chart (2026-09-19, Pieter's ask: "a graph that looks exactly like the indicator on
 * TradingView, under the long-press graphs") — the Crowded Market indicator's two score lines, 0-100:
 * **top** in `bear` (red — price stretched up and crowded long) and **bottom** in `bull` (green — the
 * mirror), a dashed reference at 60, and a small dot on each bar where a side first reaches 60 (the
 * indicator's own ▼ TOP / ▲ BOTTOM flag; TradingView's text labels are too wide for this many bars — the
 * value is in the card header instead).
 *
 * **Reference lines (2026-09-20):** dashed at 20, 40 and 60, each with a tiny value label. **Background shading (2026-09-20, Pieter's ask):** the indicator's strong-trend shading, with the colours
 * REVERSED on purpose: a strong uptrend (TradingView shades it green) is drawn in `bear` (red) and a strong downtrend in `bull` (green) — the colour of the score that is watching for a
 * reversal in that trend. The series carries the per-bar state (`crowd_series.<tf>.regime`, +1 / 0 / -1, computed by the backend); the chart only draws it, and draws nothing when it is absent.
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
    regime: List<Int> = emptyList(),
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

        // strong-trend shading first, so every line and label sits on top of it; colours reversed on purpose (see the doc comment)
        if (regime.size == n) {
            for ((first, last, sign) in regimeRuns(regime)) {
                val left = if (first == 0) 0f else px(first) - stepX / 2f
                val right = if (last == n - 1) size.width else px(last) + stepX / 2f
                drawRect(
                    color = (if (sign > 0) colors.bear else colors.bull).copy(alpha = REGIME_SHADE_ALPHA),
                    topLeft = Offset(left, padTop), size = Size(right - left, plotH),
                )
            }
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        for (level in CROWD_LEVEL_LINES) {
            drawLine(
                if (level == CROWD_FLAG_LINE) colors.hairlineStrong else colors.hairline,
                Offset(0f, py(level)), Offset(size.width, py(level)),
                strokeWidth = 1.dp.toPx(), pathEffect = dash,
            )
            drawLevelLabel(level.toInt().toString(), py(level), colors)
        }

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
