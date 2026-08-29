package com.pieter.atomfx.domain

import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.PotentialState
import com.pieter.atomfx.ui.wheel.Tint
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

/** Real-shaped verification: the doc's own canonical Risk-On fixture (Design §2.6), decoded as-is. */
private fun loadRiskOnFixture(): Signals {
    val stream = requireNotNull(WheelMapperTest::class.java.getResourceAsStream("/fixtures/state_risk_on.json")) {
        "fixtures/state_risk_on.json not found on the test classpath"
    }
    return json.decodeFromString(Signals.serializer(), stream.bufferedReader().readText())
}

class WheelMapperTest {

    @Test
    fun `EURUSD maps to a full-pass tradeable node`() {
        val state = WheelMapper.map(loadRiskOnFixture())
        val eurusd = state.nodes.first { it.pair == "EURUSD" }

        assertEquals(6, eurusd.level)
        assertEquals(PotentialState.TRADEABLE, eurusd.state)
        assertEquals(Direction.BULL, eurusd.direction)
        assertEquals(86, eurusd.potential)
        assertEquals(Factor.entries.toSet(), eurusd.factorsPassed)
        assertNull(eurusd.blockedAt)
    }

    @Test
    fun `GBPUSD maps to a watch node blocked at structure`() {
        val state = WheelMapper.map(loadRiskOnFixture())
        val gbpusd = state.nodes.first { it.pair == "GBPUSD" }

        assertEquals(4, gbpusd.level)
        assertEquals(PotentialState.WATCH, gbpusd.state)
        assertEquals(Factor.STRUCTURE, gbpusd.blockedAt)
        assertTrue(Factor.STRUCTURE !in gbpusd.factorsPassed)
        assertTrue(Factor.MOMENTUM in gbpusd.factorsPassed)
    }

    @Test
    fun `nucleus reflects the real regime block`() {
        val state = WheelMapper.map(loadRiskOnFixture())

        assertEquals("RISK ON", state.nucleus.regimeLabel)
        assertEquals(Tint.BULL, state.nucleus.tint)
        assertEquals("High", state.nucleus.confidence)
    }

    @Test
    fun `a pair missing from potential maps to the level-0 no-thesis node, not a crash`() {
        val signals = json.decodeFromString(
            Signals.serializer(),
            """{"updated":"2026-01-01T00:00:00+00:00","regime_h4":{"regime":"Mixed","confidence":"Low","score":2.0}}""",
        )

        val state = WheelMapper.map(signals)

        assertTrue(state.nodes.isNotEmpty())
        state.nodes.forEach { node ->
            assertEquals(0, node.level)
            assertEquals(PotentialState.LOW, node.state)
            assertEquals(Direction.NEUTRAL, node.direction)
            assertTrue(node.factorsPassed.isEmpty())
            assertNull(node.blockedAt)
        }
        assertEquals(Tint.WATCH, state.nucleus.tint) // Mixed
    }
}
