package com.pieter.atomfx.ui.sheets

import com.pieter.atomfx.data.model.PairBlock
import com.pieter.atomfx.ui.chart.CROWD_FLAG_LINE
import com.pieter.atomfx.ui.theme.DarkColors
import com.pieter.atomfx.ui.theme.LightColors
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

/**
 * Crowd score (Signals Roadmap §5c, 2026-09-19): the footer's state word and the model's tolerance of the backend's
 * real JSON shapes. The footer reads off the ONE line the chart draws (`CROWD_FLAG_LINE` = 60), so these tests are the
 * guarantee the word and the chart cannot disagree, and that "No COT" outranks a state word (a capped score silently
 * reads as "less crowded").
 */
class CrowdScoreTest {

    private val c = DarkColors

    private fun state(top: Double?, bottom: Double?, cotOk: Boolean = true) = crowdState(top, bottom, cotOk, c)

    @Test
    fun `the flag line the footer reads is the indicator's own 60`() {
        assertEquals(60.0, CROWD_FLAG_LINE, 0.0)
    }

    @Test
    fun `top at the flag line reads Crowded top in bear`() {
        assertEquals("Crowded top" to c.bear, state(60.0, 0.0))
        assertEquals("Crowded top" to c.bear, state(75.0, 20.0))
    }

    @Test
    fun `bottom at the flag line reads Crowded bottom in bull`() {
        assertEquals("Crowded bottom" to c.bull, state(0.0, 60.0))
    }

    @Test
    fun `just under the line is not crowded`() {
        assertEquals("Not crowded" to c.neutral, state(59.9, 59.9))
        assertEquals("Not crowded" to c.neutral, state(0.0, 0.0))
    }

    @Test
    fun `both sides at the line read Mixed in watch`() {
        assertEquals("Mixed" to c.watch, state(65.0, 62.0))
    }

    @Test
    fun `no COT outranks every state word`() {
        assertEquals("No COT" to c.textMuted, state(70.0, 0.0, cotOk = false))
        assertEquals("No COT" to c.textMuted, state(0.0, 0.0, cotOk = false))
        assertEquals("No COT" to c.textMuted, state(65.0, 65.0, cotOk = false))
    }

    @Test
    fun `a missing reading has no state at all`() {
        assertNull(state(null, 10.0))
        assertNull(state(10.0, null))
    }

    @Test
    fun `the state words use theme tokens in both themes`() {
        assertEquals(LightColors.bear, crowdState(80.0, 0.0, true, LightColors)!!.second)
        assertEquals(LightColors.bull, crowdState(0.0, 80.0, true, LightColors)!!.second)
    }

    // ── model ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the backend's real crowd_series shape parses`() {
        val raw = """
            {"crowd_series": {
              "d1": {"dates": ["2026-09-17", "2026-09-18"], "top": [0.0, 25.0], "bottom": [10.0, 0.0],
                     "latest": {"bar_date": "2026-09-18", "top": 25.0, "bottom": 0.0,
                                "top_on": ["cot"], "bottom_on": [], "cot_pctl": 91.2, "cot_ok": true, "cot_asof": "2026-09-08"}},
              "h4": {"dates": [], "top": [], "bottom": [], "latest": null}}}
        """.trimIndent()
        val pair = json.decodeFromString<PairBlock>(raw)
        val d1 = pair.crowdSeries.getValue("d1")
        assertEquals(listOf(0.0, 25.0), d1.top)
        assertEquals(listOf("cot"), d1.latest!!.topOn)
        assertEquals(91.2, d1.latest!!.cotPctl!!, 0.0)
        assertTrue(d1.latest!!.cotOk)
        // the backend's empty block (too little history) parses to an empty series with no latest -> card hidden
        val h4 = pair.crowdSeries.getValue("h4")
        assertTrue(h4.top.isEmpty())
        assertNull(h4.latest)
    }

    @Test
    fun `an older signals file with no crowd_series key parses to an empty map`() {
        val pair = json.decodeFromString<PairBlock>("""{"adx": 25.0}""")
        assertTrue(pair.crowdSeries.isEmpty())
    }

    @Test
    fun `a pair whose COT is unresolved parses with cot_ok false and null percentile`() {
        val raw = """{"crowd_series": {"h4": {"dates": ["2026-09-18"], "top": [15.0], "bottom": [0.0],
            "latest": {"bar_date": "2026-09-18", "top": 15.0, "bottom": 0.0, "top_on": ["zscore"], "bottom_on": [],
                       "cot_pctl": null, "cot_ok": false, "cot_asof": null}}}}"""
        val latest = json.decodeFromString<PairBlock>(raw).crowdSeries.getValue("h4").latest
        assertNotNull(latest)
        assertFalse(latest!!.cotOk)
        assertNull(latest.cotPctl)
        assertNull(latest.cotAsof)
    }
}
