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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.ThrustBlock
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

private const val THRUST_MAX_ABS = 8f // 8 currencies — see breadth.py's compute_thrust

/**
 * Concept 03 (2026-09-10) — the FX advance/decline line as a centered-zero histogram: one bar per
 * recent scan, `strong`-minus-`weak` currency count either side of a zero baseline. Today's bar
 * (the last one) is outlined to stand out from the illustrative-feeling run of history beside it.
 */
@Composable
fun ThrustChart(thrust: ThrustBlock?, colors: AtomColors, modifier: Modifier = Modifier) {
    val history = thrust?.history.orEmpty()
    if (history.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        }
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val padTop = 8f
        val padBottom = 8f
        val plotH = size.height - padTop - padBottom
        val midY = padTop + plotH / 2f

        drawLine(colors.hairlineStrong, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())

        val n = history.size
        val slot = size.width / n
        val barW = slot * 0.6f

        history.forEachIndexed { i, value ->
            val isToday = i == n - 1
            val barH = (kotlin.math.abs(value) / THRUST_MAX_ABS) * (plotH / 2f - 4f)
            val h = barH.coerceAtLeast(2.5f)
            val top = if (value >= 0) midY - h else midY
            val left = slot * i + (slot - barW) / 2f
            val color = when {
                value > 0 -> colors.bull
                value < 0 -> colors.bear
                else -> colors.neutral
            }
            drawRect(
                color = color.copy(alpha = if (isToday) 1f else 0.5f),
                topLeft = Offset(left, top),
                size = Size(barW, h),
            )
            if (isToday) {
                drawRect(
                    color = colors.textPrimary,
                    topLeft = Offset(left, top),
                    size = Size(barW, h),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}
