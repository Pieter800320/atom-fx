package com.pieter.atomfx.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class CrowdChartHelpersTest {

    @Test
    fun `six evenly spaced date labels include the first and last bar`() {
        assertEquals(listOf(0, 18, 36, 53, 71, 89), dateLabelIndices(90))
        assertEquals(DATE_LABEL_COUNT, dateLabelIndices(90).size)
    }

    @Test
    fun `fewer bars than labels never repeat an index`() {
        assertEquals(listOf(0, 1, 2), dateLabelIndices(3))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), dateLabelIndices(6))
        assertEquals(listOf(0), dateLabelIndices(1))
        assertEquals(listOf(0), dateLabelIndices(0))
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
