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

    @Test
    fun `a 4-hour chart of about 15 days gets evenly spaced labels that never crowd each other, newest date included`() {
        val dates = weekdays("2026-08-31", 90, perDay = 6)               // six H4 bars a weekday; the newest date is a Friday
        val ticks = dateTicks(dates)
        val last = java.time.LocalDate.parse(dates.last())
        assertEquals(dates.size - 1, ticks.last().first)
        assertTrue(ticks.size in 4..7)
        assertEquals(1, gapsInDays(ticks, dates, last).toSet().size)                         // one constant gap in days
        assertTrue(ticks.zipWithNext().all { (a, b) -> b.first - a.first >= dates.size / (DATE_LABEL_MAX + 1) })   // no overlap: a Saturday label at the Monday open must not sit beside Tuesday's
    }

    @Test
    fun `a daily chart of about four months gets evenly spaced, coarser labels`() {
        val dates = weekdays("2026-05-18", 90)
        val ticks = dateTicks(dates)
        val last = java.time.LocalDate.parse(dates.last())
        assertTrue(ticks.size in 4..7)
        assertEquals(1, gapsInDays(ticks, dates, last).toSet().size)     // one constant gap
        assertTrue(gapsInDays(ticks, dates, last).first() >= 7)          // weekly or coarser: 3-day labels would not fit
    }

    @Test
    fun `an hourly chart of about four days gets one label a day, and duplicates never appear`() {
        val dates = weekdays("2026-09-15", 90, perDay = 24)
        val ticks = dateTicks(dates)
        assertEquals(ticks.map { it.first }.distinct(), ticks.map { it.first })
        assertTrue(ticks.size in 3..5)
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
