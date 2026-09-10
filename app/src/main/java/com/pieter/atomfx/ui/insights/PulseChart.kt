package com.pieter.atomfx.ui.insights

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
import com.pieter.atomfx.data.model.PulseBlock
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

// Mirrors scanner/extend/potential_config.py's PULSE_MODERATE/PULSE_HIGH (50/70) — those reuse
// BREADTH_STRONG/BREADTH_MODERATE's own split; kept as plain constants here since the app never
// recomputes a backend number, it only needs the same two threshold lines to draw.
private const val PULSE_MODERATE = 50.0
private const val PULSE_HIGH = 70.0

/**
 * Concept 02 (2026-09-10) — a bounded 0-100 oscillator, direct extension of `LineChart`'s Design
 * §19.1 idiom (1.5dp stroke, soft area fill, emphasised endpoint) with two added dashed band
 * lines at 50/70. Colour comes from `band`, not the series' own start/end like `LineChart` — a
 * "confirmed" reading should read bull-green even if the line happened to dip mid-window.
 */
@Composable
fun PulseChart(pulse: PulseBlock?, colors: AtomColors, modifier: Modifier = Modifier) {
    val history = pulse?.history.orEmpty()
    if (pulse?.score == null || history.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet — building history", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    val bandColor = when (pulse.band) {
        "confirmed" -> colors.bull
        "mixed" -> colors.watch
        else -> colors.bear
    }

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val padTop = 10f
        val padBottom = 10f
        val plotH = size.height - padTop - padBottom
        fun py(v: Double): Float = (padTop + (1.0 - v / 100.0) * plotH).toFloat()

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        listOf(PULSE_MODERATE, PULSE_HIGH).forEach { threshold ->
            drawLine(
                color = colors.hairlineStrong,
                start = Offset(0f, py(threshold)),
                end = Offset(size.width, py(threshold)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
        }

        val n = history.size
        val stepX = if (n > 1) size.width / (n - 1) else 0f
        fun px(i: Int): Float = i * stepX

        val linePath = Path().apply {
            moveTo(px(0), py(history[0]))
            for (i in 1 until n) lineTo(px(i), py(history[i]))
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(px(n - 1), padTop + plotH)
            lineTo(px(0), padTop + plotH)
            close()
        }

        drawPath(fillPath, color = bandColor.copy(alpha = 0.14f))
        drawPath(linePath, color = colors.textSecondary, style = Stroke(width = 1.5.dp.toPx()))

        val endpoint = Offset(px(n - 1), py(history[n - 1]))
        drawCircle(color = bandColor, radius = 5.dp.toPx(), center = endpoint)
        drawCircle(color = colors.surface, radius = 5.dp.toPx(), center = endpoint, style = Stroke(width = 2.dp.toPx()))
    }
}
