package com.pieter.atomfx.ui.chart

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared drawing primitives for the ChartSheet glance panel's four oscillators.
 *
 * 2026-09-18 — extracted from `MomentumOscillators.kt`, where these started life as private
 * helpers for RSI and MACD alone. Adding %B-20 and BandWidth (`BandWidthChart.kt`) made them
 * three- and four-way shared, and the alternative was a second copy of the same glow/date-row
 * code drifting out of step with the first. Nothing about the drawing changed in the move.
 */

// 2026-09-18 (Pieter's restyle ask, "very flat... increase the vertical level") — up from a flat
// 120dp, same taller frame `PercentBOscillator` already earns with its own date row.
internal val BASE_CHART_HEIGHT = 168.dp
internal val DATE_ROW_HEIGHT = 18.dp
internal val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

internal fun chartHeight(hasDates: Boolean): Dp =
    if (hasDates) BASE_CHART_HEIGHT + DATE_ROW_HEIGHT else BASE_CHART_HEIGHT

/** A soft blurred disc behind a chart's current-value endpoint dot — same `BlurMaskFilter`
 * technique the wheel's own hub glow uses (`WheelCanvas.kt::glowFillCircle`), not a new visual
 * language, just this app's existing "depth via blur" treatment on a second surface. */
internal fun DrawScope.glowDot(center: Offset, color: Color, radiusPx: Float) {
    val paint = Paint().apply {
        this.color = color.copy(alpha = 0.45f).toArgb()
        style = Paint.Style.FILL
        isAntiAlias = true
        maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawCircle(center.x, center.y, radiusPx, paint)
}

/** Three sparse date labels (oldest/middle/newest) below the plot — same convention and layout
 * `PercentBOscillator.kt` already uses, rather than one label per bar. */
internal fun DrawScope.drawDateRow(dates: List<String>, px: (Int) -> Float, labelY: Float, colors: AtomColors) {
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
