package com.pieter.atomfx.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdChartHelpersTest {

    private fun weekdays(start: String, count: Int, perDay: Int = 1): List<String> {
        val out = mutableListOf<String>()
        var d = java.time.LocalDate.parse(start)
        while (out.size < count) {
            if (d.dayOfWeek.value <= 5) repeat(perDay) { if (out.size < count) out += d.toString() }
            d = d.plusDays(1)
        }
        return out
    }

    private fun gapsInDays(ticks: List<Pair<Int, String>>, dates: List<String>, last: java.time.LocalDate): List<Long> {
        // labels are "MMM d"; recover each label's date relative to the newest date to check the spacing
        val f = java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy", java.util.Locale.US)
        val ds = ticks.map { java.time.LocalDate.parse(it.second + " " + last.year, f) }
        return ds.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b) }
    }

    private fun indexGaps(ticks: List<Pair<Int, String>>): List<Int> = ticks.zipWithNext { a, b -> b.first - a.first }

    @Test
    fun `a 4-hour chart of five trading weeks gets weekly labels exactly 30 bars and 7 days apart`() {
        val dates = weekdays("2026-08-17", 150, perDay = 6)              // 25 weekdays x 6 blocks = 5 weeks, newest date a Friday
        val ticks = dateTicks(dates)
        val last = java.time.LocalDate.parse(dates.last())
        assertEquals(5, ticks.size)
        assertEquals(setOf(30), indexGaps(ticks).toSet())                                   // even on screen ...
        assertEquals(setOf(7L), gapsInDays(ticks, dates, last).toSet())                    // ... and in calendar days
        assertEquals(dates.indexOfFirst { it == dates.last() }, ticks.last().first)         // the newest label sits under the first bar of the newest date
    }

    @Test
    fun `the same holds when the newest bar is mid-week`() {
        val dates = weekdays("2026-08-19", 150, perDay = 6)              // starts Wednesday: newest date is a Tuesday
        val ticks = dateTicks(dates)
        assertEquals(setOf(30), indexGaps(ticks).toSet())
    }

    @Test
    fun `a daily chart of 90 bars gets three-weekly labels, equal on screen and in days`() {
        val dates = weekdays("2026-05-18", 90)
        val ticks = dateTicks(dates)
        val last = java.time.LocalDate.parse(dates.last())
        assertTrue(ticks.size in 5..7)
        assertEquals(setOf(15), indexGaps(ticks).toSet())                                   // 3 weeks = 15 trading days
        assertEquals(setOf(21L), gapsInDays(ticks, dates, last).toSet())
    }

    @Test
    fun `an hourly chart labels each trading day once, equally spaced, skipping the weekend`() {
        val dates = weekdays("2026-09-10", 90, perDay = 24)              // Thu, Fri, Mon, then part of Tue
        val ticks = dateTicks(dates)
        assertEquals(4, ticks.size)
        assertEquals(setOf(24), indexGaps(ticks).toSet())
        assertEquals(ticks.map { it.first }.distinct(), ticks.map { it.first })
    }

    @Test
    fun `intraday datetimes get evenly spaced hour labels and empty input gets none`() {
        val start = java.time.LocalDateTime.parse("2026-09-18T11:15:00")
        val dates = (0 until 90).map { start.plusMinutes(15L * it).toString() }
        val ticks = dateTicks(dates)
        assertTrue(ticks.size in 4..7)
        assertEquals(89, ticks.last().first)
        assertEquals(emptyList<Pair<Int, String>>(), dateTicks(emptyList()))
    }

    @Test
    fun `regime runs group contiguous same-sign bars and skip zeros`() {
        assertEquals(
            listOf(Triple(1, 2, 1), Triple(4, 4, -1), Triple(5, 6, 1)),
            regimeRuns(listOf(0, 1, 1, 0, -1, 1, 1, 0)),
        )
        assertEquals(emptyList<Triple<Int, Int, Int>>(), regimeRuns(listOf(0, 0, 0)))
        assertEquals(listOf(Triple(0, 3, -1)), regimeRuns(listOf(-1, -1, -1, -1)))
    }

    @Test
    fun `the three crowd reference lines are the alert levels and 60 is still the flag line`() {
        assertEquals(listOf(20.0, 40.0, 60.0), CROWD_LEVEL_LINES)
        assertEquals(CROWD_FLAG_LINE, CROWD_LEVEL_LINES.last(), 0.0)
    }
}
