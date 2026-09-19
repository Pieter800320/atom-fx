package com.pieter.atomfx.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdAlertFilterTest {

    @Test
    fun `a level at or above the timeframe minimum passes and one below it does not`() {
        assertTrue(crowdAlertPasses("d1", 40.0, minD1 = 40, minH4 = 60))
        assertTrue(crowdAlertPasses("d1", 72.5, minD1 = 40, minH4 = 60))
        assertFalse(crowdAlertPasses("d1", 39.9, minD1 = 40, minH4 = 60))
        assertFalse(crowdAlertPasses("h4", 45.0, minD1 = 40, minH4 = 60))
        assertTrue(crowdAlertPasses("h4", 60.0, minD1 = 40, minH4 = 60))
    }

    @Test
    fun `each timeframe uses only its own minimum`() {
        assertTrue(crowdAlertPasses("h4", 25.0, minD1 = 60, minH4 = 20))
        assertFalse(crowdAlertPasses("d1", 25.0, minD1 = 60, minH4 = 20))
    }

    @Test
    fun `any level (0) passes every positive score`() {
        assertTrue(crowdAlertPasses("d1", 0.5, minD1 = 0, minH4 = 0))
        assertTrue(crowdAlertPasses("h4", 12.0, minD1 = 0, minH4 = 0))
        assertFalse(crowdAlertPasses("h4", 0.0, minD1 = 0, minH4 = 0))
    }

    @Test
    fun `a push with no readable level or timeframe is shown, not dropped`() {
        assertTrue(crowdAlertPasses("d1", null, minD1 = 60, minH4 = 60))
        assertTrue(crowdAlertPasses(null, 10.0, minD1 = 60, minH4 = 60))
        assertTrue(crowdAlertPasses("h1", 10.0, minD1 = 60, minH4 = 60))
    }

    @Test
    fun `the choices and defaults line up`() {
        assertEquals(listOf(0, 20, 40, 60), CROWD_MIN_LEVEL_CHOICES)
        assertTrue(CROWD_MIN_LEVEL_D1_DEFAULT in CROWD_MIN_LEVEL_CHOICES)
        assertTrue(CROWD_MIN_LEVEL_H4_DEFAULT in CROWD_MIN_LEVEL_CHOICES)
        assertEquals("ANY", crowdMinLevelLabel(0))
        assertEquals("40", crowdMinLevelLabel(40))
    }
}
