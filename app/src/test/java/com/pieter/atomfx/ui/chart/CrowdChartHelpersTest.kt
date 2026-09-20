package com.pieter.atomfx.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class CrowdChartHelpersTest {

    @Test
    fun `three date labels sit at the first, middle and last bar`() {
        val dates = (1..90).map { java.time.LocalDate.parse("2026-05-18").plusDays(it.toLong()).toString() }
        assertEquals(listOf(0, 45, 89), dateTicks(dates).map { it.first })
        assertEquals(listOf(0), dateTicks(listOf("2026-09-18")).map { it.first })
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
