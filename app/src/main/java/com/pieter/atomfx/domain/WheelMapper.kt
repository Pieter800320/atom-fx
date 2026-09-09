package com.pieter.atomfx.domain

import com.pieter.atomfx.data.model.PotentialEntry
import com.pieter.atomfx.data.model.PotentialFactors
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.wheel.CurrencySeg
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.NucleusState
import com.pieter.atomfx.ui.wheel.PairNode
import com.pieter.atomfx.ui.wheel.PotentialState
import com.pieter.atomfx.ui.wheel.RingDescriptor
import com.pieter.atomfx.ui.wheel.Tint
import com.pieter.atomfx.ui.wheel.WheelGeometry
import com.pieter.atomfx.ui.wheel.WheelUiState
import kotlin.math.roundToInt

/**
 * Pure `signals.json` → `WheelUiState` mapping (Architecture §8.3): every field is a straight
 * copy, string→enum translation, or a presentational label over a value the frozen/extend
 * backend already computed. Nothing here re-derives a trading number. Absent EXTEND keys
 * (normal — Architecture §4.2) map to safe empty/low states, never a crash.
 */
object WheelMapper {

    fun map(signals: Signals): WheelUiState {
        val nodes = WheelGeometry.PAIR_ORDER.mapIndexed { index, pair ->
            mapNode(pair, index, signals)
        }
        return WheelUiState(
            nucleus = mapNucleus(signals),
            nodes = nodes,
            rings = mapRings(nodes),
            currencies = mapCurrencies(signals, "h4"),
            currenciesD1 = mapCurrencies(signals, "d1"),
            currenciesH1 = mapCurrencies(signals, "h1"),
        )
    }

    // ── Pairs (the wheel's 4 modes: Overall/Trend(ADX)/Momentum(H4)/Volatility) ───────────
    private fun mapNode(pair: String, index: Int, signals: Signals): PairNode {
        // All already computed backend-side (Rule #1, never re-derived here), all straight off
        // the pair's own `pairs` block entry (mom.h4 the frozen H4-only momentum oscillator, adx
        // the frozen trend-strength read, atr_pct the frozen ATR percentile, cont the frozen
        // Continuation Score — see PairNode's own doc comments for what each feeds).
        val pairBlock = signals.pairs[pair]
        // 2026-09-09 (Pieter's ask) — switched D1 -> H4. D1 Momentum re-read roughly the same
        // candles D1 Regime already votes on (same timeframe, two lenses), which isn't real
        // multi-timeframe confluence — it's asking the same data twice. H4 Momentum genuinely
        // checks whether a faster timeframe still supports the D1 Regime bias, closer to a real
        // top-down "does this hold up when I look closer" read (Elder's triple-screen framing).
        // mom.h4 is already a normal, fully-populated field (not a fallback), so this is a
        // straight field swap, no backend change. Volatility stays D1 for now — its own H4
        // reading is only a rare data-availability fallback, not a real parallel series; Pieter's
        // reviewing charts to see whether it's worth the backend work to give it a real H4 output.
        val momentumScore = pairBlock?.mom?.h4 ?: 0
        val adxScore = pairBlock?.adx?.roundToInt() ?: 0
        val volatilityScore = pairBlock?.atrPct ?: 0
        val contScore = pairBlock?.cont ?: 0
        val trendDir = mapPillDirection(pairBlock?.pills?.h4)
        // 2026-09-09 bug fix — was entry.direction (signals.potential[pair], the older, demoted
        // six-factor gate), which the wheel's Overall wing used to TINT while filling from `cont`
        // (a different subsystem's own direction basis) — the two could disagree, or entry could
        // be entirely absent, forcing a neutral/grey tint regardless of cont's real direction.
        // pairBlock.direction is the SAME value compute_cont() itself used for THIS number.
        val overallDir = mapDirection(pairBlock?.direction)

        val entry = signals.potential[pair]
        if (entry == null) {
            return PairNode(
                pair, index, overallDir, 0, PotentialState.LOW, 0, emptySet(), null,
                momentum = momentumScore, adx = adxScore, volatility = volatilityScore, cont = contScore,
                trendDirection = trendDir,
            )
        }
        return PairNode(
            pair = pair,
            index = index,
            direction = overallDir,
            level = entry.level ?: 0,
            state = mapState(entry.state),
            potential = entry.score ?: 0,
            factorsPassed = mapFactors(entry.factors),
            blockedAt = mapFactor(entry.blockedAt),
            momentum = momentumScore,
            adx = adxScore,
            volatility = volatilityScore,
            cont = contScore,
            trendDirection = trendDir,
        )
    }

    /** ADX (Trend) is computed on H4, so its own direction read must come from the H4 pill, not
     *  the pair's overall D1-derived `direction` — see PairNode.trendDirection's own doc comment. */
    private fun mapPillDirection(pill: String?): Direction = when (pill) {
        "bull", "bull_strong" -> Direction.BULL
        "bear", "bear_strong" -> Direction.BEAR
        else -> Direction.NEUTRAL
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

    // ── Currencies (CURRENCIES mode) ──────────────────────────────────────────────────────
    // [timeframe] selects csm/csm_delta (both genuinely per-TF in signals.json). breadth is only
    // ever published at h4 — there's no per-TF breadth in the data contract — so it always reads
    // h4 regardless of [timeframe]; that's a real gap in the source data, not a mapper bug.
    private fun mapCurrencies(signals: Signals, timeframe: String): List<CurrencySeg> {
        val csmTf = signals.csm[timeframe] ?: emptyMap()
        val deltaTf = signals.csmDelta[timeframe] ?: emptyMap()
        val breadthH4 = signals.breadth.h4
        return WheelGeometry.CCY_ORDER.mapIndexed { index, code ->
            val strength = (csmTf[code] ?: 0.0).toInt()
            CurrencySeg(
                code = code,
                index = index,
                strength = strength,
                delta = deltaTf[code] ?: 0.0,
                breadthBand = breadthH4[code]?.band ?: "weak",
                tint = if (strength >= 50) Tint.BULL else Tint.BEAR,
            )
        }
    }

    // ── Nucleus / hub ────────────────────────────────────────────────────────────────────────
    // Switched H4 -> D1 (2026-09-09, Pieter's ask): "the H4 Regime changes too much" — supersedes
    // the 2026-09-06 settled call below H4 was fixed on (a D1/H4/H1 toggle was tried and reverted
    // that session; this isn't that toggle coming back, it's the fixed choice itself moving to
    // the slower timeframe). The wheel's consensus set is now D1 Regime, H4 Trend, H4 Momentum
    // (also switched off D1, same day — see mapNode's own comment), D1 Volatility.
    // `_regime_flip_alert` (state_alerts.py) made the same H4->D1 switch alongside
    // this, so the regime shown at the hub and the one that pages Pieter stay the same timeframe.
    private fun mapNucleus(signals: Signals): NucleusState {
        val regime = signals.regimeD1
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
            archetypeLine = archetypeLine(signals),
        )
    }

    private fun archetypeLine(signals: Signals): String {
        val p = signals.macroRegime?.primary ?: return ""
        val code = p.code ?: return ""
        val name = (p.name ?: "").uppercase()
        val conf = (p.confidence ?: "").uppercase()
        return listOf(code, name, conf).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun strengthWordFor(score: Double): String = when {
        score >= 7.0 -> "Strong"
        score >= 4.0 -> "Moderate"
        else -> "Weak"
    }

    private fun tintFor(regime: String): Tint = when (regime) {
        "Risk-On" -> Tint.BULL
        "Risk-Off" -> Tint.BEAR
        "Mixed" -> Tint.WATCH
        else -> Tint.NEUTRAL
    }

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
