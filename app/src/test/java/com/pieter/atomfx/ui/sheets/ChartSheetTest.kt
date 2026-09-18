package com.pieter.atomfx.ui.sheets

import com.pieter.atomfx.ui.theme.DarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the 2026-09-18 audit's MACD sign-flip bug: `macdState` used to compare
 * `abs(last) > abs(prev)` unconditionally, so a fresh bullish/bearish cross (histogram flipping
 * sign) could read "fading" purely because the new bar happened to be smaller in magnitude than
 * the old one on the *other* side of zero — two unrelated numbers being compared across a
 * zero-crossing. The fix checks for a sign flip first and always reports "building" on one.
 */
class ChartSheetTest {

    @Test
    fun `a fresh bearish cross always reads building, even when the new bar is smaller`() {
        // prev = +0.5 (bullish), last = -0.1 (bearish) — a real cross, and |−0.1| < |0.5|, exactly
        // the shape that used to be misread as "fading" before the fix.
        val (text, color) = requireNotNull(macdState(listOf(0.5, -0.1), DarkColors))
        assertEquals("Bearish, building", text)
        assertEquals(DarkColors.bear, color)
    }

    @Test
    fun `a fresh bullish cross always reads building, even when the new bar is smaller`() {
        val (text, color) = requireNotNull(macdState(listOf(-0.5, 0.1), DarkColors))
        assertEquals("Bullish, building", text)
        assertEquals(DarkColors.bull, color)
    }

    @Test
    fun `growing on the same side of zero reads building`() {
        val (text, _) = requireNotNull(macdState(listOf(0.2, 0.5), DarkColors))
        assertEquals("Bullish, building", text)
    }

    @Test
    fun `shrinking on the same side of zero reads fading`() {
        val (text, _) = requireNotNull(macdState(listOf(0.5, 0.2), DarkColors))
        assertEquals("Bullish, fading", text)
    }

    @Test
    fun `a single reading has no momentum word yet`() {
        val (text, _) = requireNotNull(macdState(listOf(0.3), DarkColors))
        assertEquals("Bullish", text)
    }

    @Test
    fun `an empty histogram has no state at all`() {
        assertNull(macdState(emptyList(), DarkColors))
    }
}
