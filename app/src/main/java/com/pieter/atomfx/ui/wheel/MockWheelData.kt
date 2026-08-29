package com.pieter.atomfx.ui.wheel

import com.pieter.atomfx.ui.wheel.Factor.BREADTH
import com.pieter.atomfx.ui.wheel.Factor.ENTRY
import com.pieter.atomfx.ui.wheel.Factor.FLOW
import com.pieter.atomfx.ui.wheel.Factor.MOMENTUM
import com.pieter.atomfx.ui.wheel.Factor.REGIME
import com.pieter.atomfx.ui.wheel.Factor.STRUCTURE

/**
 * Phase 2 has no networking yet, so this stands in for a real `WheelMapper` output. The
 * numbers are not invented — they are copied verbatim from `fixtures/state_risk_on.json`
 * (the doc's own canonical Risk-On example, Design §2.6), so the mock exercises every real
 * potential state and every real `blocked_at` value the backend actually produces.
 */
object MockWheelData {

    private val ALL_SIX = setOf(REGIME, FLOW, BREADTH, MOMENTUM, STRUCTURE, ENTRY)

    val state = WheelUiState(
        nucleus = NucleusState(
            regimeLabel = "RISK ON",
            strengthWord = "Strong",
            score = 9.2,
            confidence = "High",
            flowLine = "EUR leading · USD weakening",
            tint = Tint.BULL,
        ),
        // Pieter's wheel order (not the frozen-engine pair order, Architecture §2.1): each
        // pair keeps its own data below, only the position/index around the wheel changes.
        nodes = listOf(
            PairNode("EURUSD", 0, Direction.BULL, 6, PotentialState.TRADEABLE, 86, ALL_SIX, null, isTopPair = true),
            PairNode("GBPUSD", 1, Direction.BULL, 4, PotentialState.WATCH, 68, setOf(REGIME, FLOW, BREADTH, MOMENTUM), STRUCTURE),
            PairNode("AUDUSD", 2, Direction.BULL, 6, PotentialState.TRADEABLE, 74, ALL_SIX, null),
            PairNode("NZDUSD", 3, Direction.BULL, 5, PotentialState.WATCH, 66, setOf(REGIME, FLOW, BREADTH, MOMENTUM, STRUCTURE), ENTRY),
            PairNode("USDCAD", 4, Direction.NEUTRAL, 1, PotentialState.LOW, 20, setOf(REGIME), FLOW),
            PairNode("USDCHF", 5, Direction.NEUTRAL, 1, PotentialState.LOW, 18, setOf(REGIME), FLOW),
            PairNode("EURJPY", 6, Direction.BULL, 6, PotentialState.TRADEABLE, 79, ALL_SIX, null),
            PairNode("GBPJPY", 7, Direction.BULL, 4, PotentialState.WATCH, 62, setOf(REGIME, FLOW, BREADTH, MOMENTUM), STRUCTURE),
            PairNode("AUDJPY", 8, Direction.BULL, 4, PotentialState.WATCH, 70, setOf(REGIME, FLOW, BREADTH, MOMENTUM), STRUCTURE),
            PairNode("NZDJPY", 9, Direction.BULL, 4, PotentialState.WATCH, 61, setOf(REGIME, FLOW, BREADTH, MOMENTUM), STRUCTURE),
            PairNode("CADJPY", 10, Direction.NEUTRAL, 2, PotentialState.LOW, 40, setOf(REGIME, FLOW), BREADTH),
            PairNode("USDJPY", 11, Direction.NEUTRAL, 1, PotentialState.LOW, 24, setOf(REGIME), FLOW),
        ),
        rings = listOf(
            RingDescriptor(REGIME, Tint.BULL),
            RingDescriptor(FLOW, Tint.BULL),
            RingDescriptor(BREADTH, Tint.BULL),
            RingDescriptor(MOMENTUM, Tint.BULL),
            RingDescriptor(STRUCTURE, Tint.BULL),
            RingDescriptor(ENTRY, Tint.BULL),
        ),
    )
}
