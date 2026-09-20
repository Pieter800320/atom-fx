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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
// M15's own dates (scan_m15.py's _m15_dates) are full ISO datetimes, not plain dates — a
// separate clock-time format for that case, below.
internal val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

internal fun chartHeight(hasDates: Boolean): Dp =
    if (hasDates) BASE_CHART_HEIGHT + DATE_ROW_HEIGHT else BASE_CHART_HEIGHT

/**
 * %B's own real dashed-threshold band (10/90) — extracted here so the value has one source of
 * truth instead of a local literal in `PercentBOscillator.kt`.
 *
 * **2026-09-18 (later restyle, reverted 9th same day)** — briefly reused by RSI too, so both
 * charts' dashed lines matched pixel-for-pixel regardless of each indicator's own real threshold
 * (Pieter's ask, panel-wide visual consistency). Reverted the same day: real RSI(14) rarely
 * swings below 10 or above 90, so on live data its line almost never reached the widened lines —
 * looked like the line was ignoring its own thresholds. RSI now draws its own real 30/70 again
 * (`MomentumOscillators.kt`); this constant describes only %B's own real band once more.
 */
internal const val THRESHOLD_LINE_LOW = 10.0
internal const val THRESHOLD_LINE_HIGH = 90.0

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

/**
 * One bar's own date label — D1/H4/H1's dates are plain calendar dates ("2026-09-18"), M15's are
 * full ISO datetimes ("2026-09-18T14:30:00", scan_m15.py's own `_m15_dates()`). Tried as a plain
 * date first (the common case); a datetime falls through to a clock-time label instead, converted
 * UTC -> the device's own timezone — the same `atZone(UTC).withZoneSameInstant(systemDefault())`
 * conversion every other UTC timestamp in this app already goes through (MainActivity's header
 * clock, InsightsScreen, SessionsSheet) — not a new convention.
 *
 * **2026-09-18 fix (Pieter's catch, "would M15 require TIME?") — yes.** `LocalDate.parse` on an
 * M15 datetime string throws (no bare-date format matches a string with a time component); the
 * old code caught that, returned null, and silently skipped the label — every M15 chart was
 * drawing its date row with nothing in it, not a crash, just quietly empty.
 */
private fun formatDateLabel(raw: String): String? =
    runCatching { LocalDate.parse(raw).format(DATE_FORMAT) }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(raw).atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .format(TIME_FORMAT)
        }.getOrNull()

/** How many date/time labels sit under every long-press chart (Pieter's ask, 2026-09-20: six, up from three). */
internal const val DATE_LABEL_COUNT = 6

/** Evenly spaced bar indices for the date row: the first and last bar always included, at most [count] labels, never more than there are bars. */
internal fun dateLabelIndices(n: Int, count: Int = DATE_LABEL_COUNT): List<Int> {
    if (n <= 1) return listOf(0)
    val k = minOf(count, n)
    return (0 until k).map { (it * (n - 1).toDouble() / (k - 1)).roundToInt() }.distinct()
}

/** Six sparse date labels (oldest ... newest) below the plot — the same convention every chart shares, rather than one label per bar.
 * A run of bars on the same calendar day (an H1 chart spans only about four days) would repeat a label, so only the first label of such a run is drawn. */
internal fun DrawScope.drawDateRow(dates: List<String>, px: (Int) -> Float, labelY: Float, colors: AtomColors) {
    val n = dates.size
    val labelPaint = Paint().apply {
        isAntiAlias = true
        textSize = 9.dp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = colors.textMuted.toArgb()
    }
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val labels = mutableListOf<Pair<Int, String>>()
    for (i in dateLabelIndices(n)) {
        val text = formatDateLabel(dates[i]) ?: continue
        if (labels.lastOrNull()?.second != text) labels += i to text
    }
    labels.forEachIndexed { pos, (i, text) ->
        labelPaint.textAlign = when (pos) {
            0 -> Paint.Align.LEFT
            labels.lastIndex -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
        nativeCanvas.drawText(text, px(i), labelY, labelPaint)
    }
}

/** A tiny muted value label (e.g. "40") for a horizontal reference line, drawn just above the line at the plot's left edge. */
internal fun DrawScope.drawLevelLabel(text: String, y: Float, colors: AtomColors) {
    val paint = Paint().apply {
        isAntiAlias = true
        textSize = 8.dp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = colors.textMuted.toArgb()
        textAlign = Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(text, 2.dp.toPx(), y - 2.dp.toPx(), paint)
}
