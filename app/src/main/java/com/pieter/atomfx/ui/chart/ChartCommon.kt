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
import java.time.temporal.ChronoUnit
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

/** The most date/time labels under a chart (Pieter's asks, 2026-09-20: six, then "evenly spaced, 3 days apart ... the exact amount is not that important"). */
internal const val DATE_LABEL_MAX = 7

private val DAY_STEPS = listOf(1, 2, 3, 4, 5, 7, 10, 14, 21, 28, 42, 56, 84)
private val HOUR_STEPS = listOf(1, 2, 3, 4, 6, 12, 24)

/**
 * Evenly spaced date labels in CALENDAR time (2026-09-20, Pieter's ask), as (bar index, label text) pairs, oldest first.
 *
 * The step is the smallest of 1, 2, 3, 4, 5, 7, 10, 14, 21 ... days that keeps the label count at or under [maxLabels] AND leaves no two labels crowded — typically 3-4 days on a 4-hour chart of ~15 days, three weeks
 * on a daily chart of ~4 months, one day on an hourly one. Labels count BACK from the newest bar, so the newest date is always shown and every gap between labels is the same number of days.
 * A label sits at the first bar on or after its date; where its date falls on a weekend (no bars) that is the Monday open, so weekend gaps show as slightly narrower label spacing on screen while the
 * dates themselves stay exactly one step apart. Intraday (M15) datetimes use hour steps instead and show local clock times.
 */
internal fun dateTicks(dates: List<String>, maxLabels: Int = DATE_LABEL_MAX): List<Pair<Int, String>> {
    if (dates.isEmpty()) return emptyList()
    val days = dates.map { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (days.all { it != null }) return dayTicks(days.map { it!! }, maxLabels)
    val times = dates.map { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    if (times.all { it != null }) return hourTicks(times.map { it!! }, maxLabels)
    return emptyList()
}

/** True when no two labels sit closer than a label's width — [n] bars shared by at most [maxLabels] labels, with a little slack. */
private fun spacedEnough(ticks: List<Pair<Int, String>>, n: Int, maxLabels: Int): Boolean =
    ticks.zipWithNext().all { (a, b) -> b.first - a.first >= maxOf(1, n / (maxLabels + 1)) }

private fun dayTicks(d: List<LocalDate>, maxLabels: Int): List<Pair<Int, String>> {
    val n = d.size
    val last = d.last()
    val span = ChronoUnit.DAYS.between(d.first(), last).toInt()
    fun build(step: Int): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var k = 0
        var target = last
        while (!target.isBefore(d.first())) {
            out += (if (k == 0) n - 1 else d.indexOfFirst { !it.isBefore(target) }) to target.format(DATE_FORMAT)
            k++
            target = last.minusDays((k * step).toLong())
        }
        return out.reversed()
    }
    // The smallest step that keeps the count down AND does not crowd labels: a weekend date has no bars, so its label sits at the Monday open and can land right beside the next one.
    val candidates = DAY_STEPS.filter { span / it + 1 <= maxLabels }
    val step = candidates.firstOrNull { spacedEnough(build(it), n, maxLabels) } ?: candidates.lastOrNull() ?: DAY_STEPS.last()
    return build(step).distinctBy { it.first }
}

private fun hourTicks(t: List<LocalDateTime>, maxLabels: Int): List<Pair<Int, String>> {
    val n = t.size
    val last = t.last()
    val spanHours = ChronoUnit.HOURS.between(t.first(), last).toInt()
    fun build(step: Int): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var k = 0
        var target = last
        while (!target.isBefore(t.first())) {
            val text = target.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).format(TIME_FORMAT)
            out += (if (k == 0) n - 1 else t.indexOfFirst { !it.isBefore(target) }) to text
            k++
            target = last.minusHours((k * step).toLong())
        }
        return out.reversed()
    }
    val candidates = HOUR_STEPS.filter { spanHours / it + 1 <= maxLabels }
    val step = candidates.firstOrNull { spacedEnough(build(it), n, maxLabels) } ?: candidates.lastOrNull() ?: HOUR_STEPS.last()
    return build(step).distinctBy { it.first }
}

/** Evenly spaced date labels below the plot (see [dateTicks]). The first label is left-aligned and the last right-aligned when they sit at the plot's edges; the rest are centred. */
internal fun DrawScope.drawDateRow(dates: List<String>, px: (Int) -> Float, labelY: Float, colors: AtomColors) {
    val n = dates.size
    val labelPaint = Paint().apply {
        isAntiAlias = true
        textSize = 9.dp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = colors.textMuted.toArgb()
    }
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val ticks = dateTicks(dates)
    ticks.forEach { (i, text) ->
        labelPaint.textAlign = when {
            i <= 2 -> Paint.Align.LEFT
            i >= n - 3 -> Paint.Align.RIGHT
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
