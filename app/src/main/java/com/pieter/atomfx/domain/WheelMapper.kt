package com.pieter.atomfx.domain

import com.pieter.atomfx.data.model.PotentialEntry
import com.pieter.atomfx.data.model.PotentialFactors
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.NucleusState
import com.pieter.atomfx.ui.wheel.PairNode
import com.pieter.atomfx.ui.wheel.PotentialState
import com.pieter.atomfx.ui.wheel.RingDescriptor
import com.pieter.atomfx.ui.wheel.Tint
import com.pieter.atomfx.ui.wheel.WheelGeometry
import com.pieter.atomfx.ui.wheel.WheelUiState

/**
 * Pure `signals.json` → `WheelUiState` mapping (Architecture §8.3): every field here is a
 * straight copy, string-to-enum translation, or a presentational label over a value the
 * frozen/extend backend already computed. Nothing here re-derives a trading number. A pair
 * missing from `potential` (normal — Architecture §4.2) maps to the level-0 "no thesis" node,
 * never a crash or an invented score.
 */
object WheelMapper {

    fun map(signals: Signals): WheelUiState {
        val nodes = WheelGeometry.PAIR_ORDER.mapIndexed { index, pair ->
            mapNode(pair, index, signals.potential[pair])
        }
        return WheelUiState(
            nucleus = mapNucleus(signals),
            nodes = nodes,
            rings = mapRings(nodes),
        )
    }

    private fun mapNode(pair: String, index: Int, entry: PotentialEntry?): PairNode {
        if (entry == null) {
            return PairNode(pair, index, Direction.NEUTRAL, 0, PotentialState.LOW, 0, emptySet(), null)
        }
        return PairNode(
            pair = pair,
            index = index,
            direction = mapDirection(entry.direction),
            level = entry.level ?: 0,
            state = mapState(entry.state),
            potential = entry.score ?: 0,
            factorsPassed = mapFactors(entry.factors),
            blockedAt = mapFactor(entry.blockedAt),
        )
    }

    private fun mapDirection(raw: String?): Direction = when (raw) {
        "bull" -> Direction.BULL
        "bear" -> Direction.BEAR
        else -> Direction.NEUTRAL
    }

    private fun mapState(raw: String?): PotentialState = when (raw) {
        "watch" -> PotentialState.WATCH
        "tradeable" -> PotentialState.TRADEABLE
        "aplus" -> PotentialState.APLUS
        else -> PotentialState.LOW
    }

    private fun mapFactor(raw: String?): Factor? = when (raw) {
        "regime" -> Factor.REGIME
        "flow" -> Factor.FLOW
        "breadth" -> Factor.BREADTH
        "momentum" -> Factor.MOMENTUM
        "structure" -> Factor.STRUCTURE
        "entry" -> Factor.ENTRY
        else -> null
    }

    private fun mapFactors(factors: PotentialFactors?): Set<Factor> {
        if (factors == null) return emptySet()
        return buildSet {
            if (factors.regime) add(Factor.REGIME)
            if (factors.flow) add(Factor.FLOW)
            if (factors.breadth) add(Factor.BREADTH)
            if (factors.momentum) add(Factor.MOMENTUM)
            if (factors.structure) add(Factor.STRUCTURE)
            if (factors.entry) add(Factor.ENTRY)
        }
    }

    private fun mapNucleus(signals: Signals): NucleusState {
        val regime = signals.regimeH4
        val regimeName = regime?.regime ?: "Unknown"
        val score = regime?.score ?: 0.0
        val flow = signals.currencyFlow
        val flowLine = if (flow?.leader != null && flow.laggard != null) {
            "${flow.leader} leading · ${flow.laggard} weakening"
        } else {
            "No flow data"
        }
        return NucleusState(
            regimeLabel = regimeName.replace("-", " ").uppercase(),
            strengthWord = strengthWordFor(score),
            score = score,
            confidence = regime?.confidence ?: "—",
            flowLine = flowLine,
            tint = tintFor(regimeName),
        )
    }

    /** A presentational bucket over the frozen regime score — the score itself is untouched. */
    private fun strengthWordFor(score: Double): String = when {
        score >= 7.0 -> "Strong"
        score >= 4.0 -> "Moderate"
        else -> "Weak"
    }

    private fun tintFor(regime: String): Tint = when (regime) {
        "Risk-On" -> Tint.BULL
        "Risk-Off" -> Tint.BEAR
        "Mixed" -> Tint.WATCH
        else -> Tint.NEUTRAL // Ranging, or unknown/absent
    }

    /** A ring's tint is the majority direction among nodes that passed it — display aggregation only, Architecture §8.3. */
    private fun mapRings(nodes: List<PairNode>): List<RingDescriptor> =
        Factor.entries.map { factor ->
            val passing = nodes.filter { factor in it.factorsPassed }.map { it.direction }
            RingDescriptor(factor, aggregateTint(passing))
        }

    private fun aggregateTint(directions: List<Direction>): Tint {
        if (directions.isEmpty()) return Tint.NEUTRAL
        val bulls = directions.count { it == Direction.BULL }
        val bears = directions.count { it == Direction.BEAR }
        return when {
            bulls > bears -> Tint.BULL
            bears > bulls -> Tint.BEAR
            else -> Tint.WATCH
        }
    }
}
