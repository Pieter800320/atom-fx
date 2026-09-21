package com.pieter.atomfx.ui.sheets

import com.pieter.atomfx.data.model.CrowdLatest
import com.pieter.atomfx.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrowdFooterTest {
    private val c = LightColors

    private fun latest(top: Double, bottom: Double, topOn: List<String> = emptyList(), bottomOn: List<String> = emptyList(), cotOk: Boolean = true) =
        CrowdLatest(top = top, bottom = bottom, topOn = topOn, bottomOn = bottomOn, cotOk = cotOk)

    @Test
    fun `both scores at zero keep the old state word`() {
        assertEquals("Not crowded" to c.neutral, crowdFooter(latest(0.0, 0.0), c))
        assertEquals("No COT" to c.textMuted, crowdFooter(latest(0.0, 0.0, cotOk = false), c))
        assertNull(crowdFooter(null, c))
    }

    @Test
    fun `a score below the flag line lists its conditions instead of Not crowded, tinted by side`() {
        assertEquals("Top: %B, RSI" to c.bear, crowdFooter(latest(20.0, 0.0, topOn = listOf("bb_pctb", "rsi")), c))
        assertEquals("Bottom: Z-score, ATR stretch" to c.bull, crowdFooter(latest(0.0, 15.0, bottomOn = listOf("zscore", "atr_stretch")), c))
        assertEquals("Top: RSI · Bottom: COT" to c.watch, crowdFooter(latest(12.0, 25.0, topOn = listOf("rsi"), bottomOn = listOf("cot")), c))
    }

    @Test
    fun `at or over the flag line the state word leads and the conditions follow`() {
        assertEquals("Crowded top · %B, Z-score, RSI, Divergence" to c.bear,
            crowdFooter(latest(65.0, 0.0, topOn = listOf("bb_pctb", "zscore", "rsi", "divergence")), c))
        assertEquals("Crowded bottom · Divergence, COT" to c.bull, crowdFooter(latest(0.0, 62.0, bottomOn = listOf("divergence", "cot")), c))
        assertEquals("Mixed · Top: RSI · Bottom: Vol. spike" to c.watch,
            crowdFooter(latest(60.0, 60.0, topOn = listOf("rsi"), bottomOn = listOf("climax")), c))
    }

    @Test
    fun `with no COT the state word still leads and the conditions follow`() {
        assertEquals("No COT · Top: RSI extreme" to c.textMuted, crowdFooter(latest(10.0, 0.0, topOn = listOf("rsi_extreme"), cotOk = false), c))
    }
}
