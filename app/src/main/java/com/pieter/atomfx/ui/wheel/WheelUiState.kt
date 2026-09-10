package com.pieter.atomfx.ui.wheel

/**
 * The wheel's own trimmed UI shape (Architecture §8.3). `WheelMapper` produces this from
 * `potential`, `csm`, `csm_delta`, `breadth`, `currency_flow`, `macro_assets`, `macro_regime`
 * and `regime_d1` (hub source since 2026-09-09, was `regime_h4`) — nothing here is computed;
 * every field is a straight copy/format of a value the frozen/extend backend already produced
 * (spec §42).
 *
 * Wheel v2 (radial dial): the middle ring toggles between PAIRS (12 wedges, radius = potential
 * step-bands) and CURRENCIES (8 wedges, radius = CSM strength). The outer ring is the 10
 * cross-assets. The hub is the regime nucleus. See docs/ATOM_FX_WHEEL_V2_SPEC.md.
 */

enum class Direction { BULL, BEAR, NEUTRAL }

enum class PotentialState { LOW, WATCH, TRADEABLE, APLUS }

/**
 * Which value the middle ring's 12 pair-wedges show (2026-09-05 simplification pass — Pieter's
 * own call: the wheel had grown into a 4-mode toggle whose modes [Potential/Momentum/ADX/Reset]
 * didn't map to a mental model he could hold onto. Replaced with the same 4 physical corner
 * buttons reading four legible concepts instead — Overall / Trend / Momentum / Volatility — with
 * Structure deliberately left off the wheel (Pieter's own call: it's event-based, not a magnitude,
 * and belongs to its own notification + the pair sheet's Structure tab, not a wedge fill).
 * Every value here is still a frozen/already-computed field, never re-derived — see
 * [PairNode.cont] (Overall), [PairNode.adx] (Trend), [PairNode.momentum] (Momentum),
 * [PairNode.volatility] (Volatility). The ring is exclusively pair-shaped, always — CSM/currency
 * strength has its own permanent strip below (`CsmBarStrip` in `WheelScreen.kt`).
 */
enum class WheelMode { OVERALL, TREND, MOMENTUM, VOLATILITY }

/**
 * Currencies-mode timeframe (2026-09-02, Pieter's toggle; H1 added 2026-09-03). `csm`/`csm_delta`
 * are genuinely per-timeframe in `signals.json` (d1/h4/h1 all present) so this changes real data
 * in Currencies mode. Pair `potential` is *not* per-timeframe — it's already a single
 * cross-timeframe verdict — so this has no effect on Pairs mode; the toggle stays visible but
 * inert there.
 */
enum class Timeframe { D1, H4, H1 }

/** The six confluence factors, in the fixed order R·F·B·M·S·E (Design §6.7, Glossary). */
enum class Factor(val glyph: String, val ringLabel: String, val shortLabel: String) {
    REGIME("R", "1 · REGIME", "Regime"),
    FLOW("F", "2 · CURRENCY FLOW", "Flow"),
    BREADTH("B", "3 · BREADTH", "Breadth"),
    MOMENTUM("M", "4 · MOMENTUM", "Momentum"),
    STRUCTURE("S", "5 · STRUCTURE", "Structure"),
    ENTRY("E", "6 · ENTRY SETUP", "Entry"),
}

/** Semantic tint a ring or the nucleus can carry; resolved to an [AtomColors] value at draw time. */
enum class Tint { BULL, BEAR, WATCH, NEUTRAL }

data class PairNode(
    val pair: String,
    val index: Int, // 0..11, fixed position in WheelGeometry.PAIR_ORDER (angle = identity)
    val direction: Direction,
    // 2026-09-05 — level/state/potential/factorsPassed/blockedAt are the old six-factor gate's
    // output (`potential.py`, still frozen-adjacent EXTEND, not yet retired backend-side). No
    // longer read by the wheel itself (see WheelMode below). `factorsPassed` (Factor.REGIME only)
    // is still read by StatusStrip's own recommendation-glyph dots (`regimeDotColor`) — 2026-09-10,
    // checked directly: PairSheet's own Overview Regime row does NOT read this any more (it moved
    // to `signals.regimeD1` directly on 2026-09-09), despite that function's own comment still
    // claiming the two match. Worth a decision later, not fixed here. The rest of this group
    // (level/state/potential/blockedAt) has no UI reader left — the Summary cascade's `topPair()`,
    // its last one, was retired 2026-09-06 and the function itself removed 2026-09-10 (dead code,
    // its one remaining caller was itself unreachable). Don't wire new behaviour to any of these;
    // use `cont` instead.
    val level: Int, // 0..6
    val state: PotentialState,
    val potential: Int, // 0..100
    val factorsPassed: Set<Factor>,
    val blockedAt: Factor?,
    // 2026-09-05 — the wheel's 4 modes, each a straight read of an already-frozen-computed value
    // (Rule #1: never re-derived here). Momentum/Trend(ADX)/Volatility are direction-agnostic
    // technical reads (none is itself bull/bear-signed) so they take their wedge colour from
    // `direction` (Trend) or their own value (Momentum), except Volatility, which isn't
    // inherently bullish or bearish at all — see WheelCanvas.modeHue.
    // Unsigned 0..100, 50 = neutral — pairs[pair].mom.h4, the frozen H4-only momentum oscillator
    // (not the D1/H4/H1-blended CMP — Pieter's own pick: a fast-moving, single-timeframe read).
    // Fixed at H4 everywhere it's used (the wheel's Momentum wing and the pair sheet's Overview
    // row alike) — switched from D1 to H4, 2026-09-09 (Pieter's ask): D1 Momentum re-read roughly
    // the same candles D1 Regime already votes on (same timeframe, two lenses), not real
    // multi-timeframe confluence; H4 genuinely checks whether a faster timeframe still supports
    // the D1 Regime bias — the wheel is a deliberate, fixed-TF consensus (D1 Regime, H4 Trend, H4
    // Momentum, D1 Volatility), not a togglable one. A D1/H4/H1 toggle was tried and reverted a
    // separate session — see WheelScreen's TimeframeButtons doc comment for why it stays scoped to
    // the CSM strip only.
    val momentum: Int = 0,
    // 0..100ish (ADX is mathematically bounded 0-100 but rarely exceeds ~60-70 in practice) —
    // pairs[pair].adx, frozen trend-strength indicator. This *is* Trend — ADX literally measures
    // how strongly a pair is trending, it just needed relabelling, not recomputing.
    val adx: Int = 0,
    // 0..100 — pairs[pair].atr_pct, frozen ATR percentile (the same field volatility_spike
    // alerts already fire from). Not direction-signed; see WheelCanvas.modeHue for its own
    // calm/sane/hot colour ramp instead of a bull/bear one.
    val volatility: Int = 0,
    // 0..100 — pairs[pair].cont, the frozen Continuation Score (`scanner/cont_score.py`, a
    // verbatim port of Forex1212's QAI). A continuous, ungated "how good is this setup right now"
    // read across TF alignment/entry position/CSM divergence/regime fit/session fit — this is the
    // wheel's new Overall/flagship mode, replacing the old six-factor Level/Potential score.
    val cont: Int = 0,
    // 2026-09-06 (Pieter's own catch) — pills[pair].h4's own direction, deliberately separate
    // from `direction` above (which is the pair's overall D1-derived bias). `adx` is computed on
    // H4, so the Trend wing/row needs H4's own directional read, not D1's — a pair can show real
    // H4 trend strength (high ADX) while its D1 composite nets neutral; tinting Trend off
    // `direction` in that case drew a grey wedge next to a "strong trend" reading, an internal
    // contradiction. See WheelCanvas.modeHue's TREND case and PairSheet's trendRow.
    val trendDirection: Direction = Direction.NEUTRAL,
)

/** One currency wedge in CURRENCIES mode. */
data class CurrencySeg(
    val code: String,
    val index: Int,         // position in WheelGeometry.CCY_ORDER (angle = identity)
    val strength: Int,      // csm["h4"][code], 0..100 -> radius fill
    val delta: Double,      // csm_delta["h4"][code]
    val breadthBand: String, // breadth["h4"][code].band
    val tint: Tint,         // BULL if strength>=50 else BEAR
)

data class NucleusState(
    val regimeLabel: String,
    val strengthWord: String,
    val score: Double,
    val confidence: String,
    val flowLine: String,
    val tint: Tint,
    val archetypeLine: String = "", // macro_regime.primary -> "A · GROWTH RISK-ON · HIGH"
)

data class RingDescriptor(
    val factor: Factor,
    val tint: Tint,
)

data class WheelUiState(
    val nucleus: NucleusState,   // fixed D1 — regime_d1, part of the wheel's own consensus set
    val nodes: List<PairNode>,             // size 12, PAIR_ORDER order (PAIRS mode)
    val rings: List<RingDescriptor>,       // size 6, the six factor legend pills
    val currencies: List<CurrencySeg> = emptyList(),   // size 8, H4 (CURRENCIES mode default)
    val currenciesD1: List<CurrencySeg> = emptyList(), // size 8, D1 (CURRENCIES mode, D1 toggle)
    val currenciesH1: List<CurrencySeg> = emptyList(), // size 8, H1 (CURRENCIES mode, H1 toggle)
)

/** The currency list for whichever timeframe is currently toggled (Currencies mode). */
fun WheelUiState.currenciesFor(timeframe: Timeframe): List<CurrencySeg> = when (timeframe) {
    Timeframe.D1 -> currenciesD1
    Timeframe.H4 -> currencies
    Timeframe.H1 -> currenciesH1
}
