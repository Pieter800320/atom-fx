package com.pieter.atomfx.domain

import com.pieter.atomfx.data.model.PotentialEntry
import com.pieter.atomfx.data.model.PotentialFactors
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.wheel.CrossAssetSeg
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
import kotlin.math.abs
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
            crossAssets = mapCrossAssets(signals),
        )
    }

    // ── Pairs (the wheel's 4 modes: Overall/Trend(ADX)/Momentum(D1)/Volatility) ───────────
    private fun mapNode(pair: String, index: Int, signals: Signals): PairNode {
        // All already computed backend-side (Rule #1, never re-derived here), all straight off
        // the pair's own `pairs` block entry (mom.d1 the frozen D1-only momentum oscillator, adx
        // the frozen trend-strength read, atr_pct the frozen ATR percentile, cont the frozen
        // Continuation Score — see PairNode's own doc comments for what each feeds).
        val pairBlock = signals.pairs[pair]
        val momentumScore = pairBlock?.mom?.d1 ?: 0
        val adxScore = pairBlock?.adx?.roundToInt() ?: 0
        val volatilityScore = pairBlock?.atrPct ?: 0
        val contScore = pairBlock?.cont ?: 0
        val trendDir = mapPillDirection(pairBlock?.pills?.h4)

        val entry = signals.potential[pair]
        if (entry == null) {
            return PairNode(
                pair, index, Direction.NEUTRAL, 0, PotentialState.LOW, 0, emptySet(), null,
                momentum = momentumScore, adx = adxScore, volatility = volatilityScore, cont = contScore,
                trendDirection = trendDir,
            )
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

    // ── Cross-assets (outer ring) ───────────────────────────────────────────────────────────
    /** Each asset → the macro axis it belongs to (an asset may touch two). */
    private val ASSET_AXES: Map<String, List<String>> = mapOf(
        "vix" to listOf("risk"), "spx" to listOf("risk"), "btc" to listOf("risk"),
        "us10y" to listOf("rates"), "us3m" to listOf("rates"), "curve" to listOf("rates"),
        "dxy" to listOf("usd"),
        "wti" to listOf("commodity"),
        "copper" to listOf("risk", "commodity"),
        "gold" to listOf("commodity", "safe_haven"),
    )

    private fun mapCrossAssets(signals: Signals): List<CrossAssetSeg> {
        val supportingAxes: Set<String> = signals.macroRegime?.evidence
            ?.filter { it.supports }
            ?.mapNotNull { it.axis }
            ?.toSet()
            ?: emptySet()

        return WheelGeometry.XASSET_ORDER.mapIndexed { index, (key, fallbackLabel) ->
            val entry = signals.macroAssets[key]
            val dir = entry?.direction
            val axes = ASSET_AXES[key] ?: emptyList()
            CrossAssetSeg(
                id = key,
                index = index,
                label = entry?.label ?: fallbackLabel,
                up = dir == "up",
                flat = dir == null || dir == "flat",
                confirm = axes.any { it in supportingAxes },
                valueText = formatValue(entry?.value),
                deltaText = formatDelta(entry?.deltaPct, entry?.deltaBp),
            )
        }
    }

    private fun formatValue(v: Double?): String = when {
        v == null -> "—"
        abs(v) >= 100 -> "%.0f".format(v)
        else -> "%.2f".format(v)
    }

    private fun formatDelta(pct: Double?, bp: Double?): String = when {
        pct != null -> "%+.1f%%".format(pct)
        bp != null -> "%+.1fbp".format(bp)
        else -> "—"
    }

    // ── Nucleus / hub ────────────────────────────────────────────────────────────────────────
    // Fixed at H4, deliberately (2026-09-06, Pieter's own settled call) — part of the wheel's own
    // consensus set (H4 Regime, H4 Trend, D1 Momentum, D1 Volatility), not a togglable read. A
    // D1/H4/H1 toggle here was tried and reverted the same session.
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
