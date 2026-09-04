package com.pieter.atomfx.ui.wheel

/**
 * The wheel's own trimmed UI shape (Architecture §8.3). `WheelMapper` produces this from
 * `potential`, `csm`, `csm_delta`, `breadth`, `currency_flow`, `macro_assets`, `macro_regime`
 * and `regime_h4` — nothing here is computed; every field is a straight copy/format of a value
 * the frozen/extend backend already produced (spec §42).
 *
 * Wheel v2 (radial dial): the middle ring toggles between PAIRS (12 wedges, radius = potential
 * step-bands) and CURRENCIES (8 wedges, radius = CSM strength). The outer ring is the 10
 * cross-assets. The hub is the regime nucleus. See docs/ATOM_FX_WHEEL_V2_SPEC.md.
 */

enum class Direction { BULL, BEAR, NEUTRAL }

enum class PotentialState { LOW, WATCH, TRADEABLE, APLUS }

/**
 * Which value the middle ring's 12 pair-wedges show (2026-09-04 — third and settled pass, per
 * Pieter's own real workflow: by the time he reaches the wheel, CSM + correlation have already
 * settled *direction* — what the wheel needs to answer is "of several directionally-agreed
 * candidates, which is the best entry right now," a triage question, not a second direction
 * check. Continuation and Conviction (both tried first) mostly re-confirmed direction/gate-passing
 * already visible via Potential or already checked via CSM upstream. ADX (trend strength) and
 * Reset Score (entry-timing/overextension) don't — neither has any other home on the wheel or in
 * the upstream CSM check, and both directly differentiate "which candidate is actually moving,
 * and is it too late to chase it." The ring is exclusively pair-shaped, always — CSM/currency
 * strength has its own permanent strip below (`CsmBarStrip` in `WheelScreen.kt`). Each mode reads
 * a different score already sitting on [PairNode] — see [PairNode.level]/[PairNode.momentum]/
 * [PairNode.adx]/[PairNode.resetScore].
 */
enum class WheelMode { POTENTIAL, MOMENTUM, ADX, RESET }

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
    val level: Int, // 0..6 — WheelMode.POTENTIAL's own value
    val state: PotentialState,
    val potential: Int, // 0..100
    val factorsPassed: Set<Factor>,
    val blockedAt: Factor?,
    // 2026-09-04 — the other 3 wheel-wing values, all already computed backend-side (Rule #1: the
    // app never re-derives these, only reads them), all direction-agnostic technical reads (none
    // is itself bull/bear-signed), so all three take their wedge colour from `direction`, same as
    // Potential does.
    // Unsigned 0..100, 50 = neutral — pairs[pair].mom.d1, the frozen D1-only momentum oscillator
    // (not the D1/H4/H1-blended CMP — Pieter's own pick: a fast-moving, single-timeframe read).
    val momentum: Int = 0,
    // 0..100ish (ADX is mathematically bounded 0-100 but rarely exceeds ~60-70 in practice) —
    // pairs[pair].adx, frozen trend-strength indicator. Answers "is this pair actually trending,
    // or just directionally biased and choppy" — a question neither Potential nor Momentum
    // answers (ADX only enters the frozen pipeline as a *gate* elsewhere, capping Continuation's
    // score below 45 when ADX < 20, never as a primary reading of its own until now).
    val adx: Int = 0,
    // 0..100 — pairs[pair].reset_score, frozen mean-reversion entry-quality oscillator. LOW is
    // GOOD here (price has reset toward equilibrium; high = overextended/chasing) — the wheel
    // inverts this for display (WheelCanvas.modeFillFrac) so a bigger wedge still reads as "better"
    // everywhere on the dial, not just here.
    val resetScore: Int = 0,
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

/** One cross-asset wedge in the outer ring. */
data class CrossAssetSeg(
    val id: String,          // macro_assets map key (e.g. "vix", "curve")
    val index: Int,          // position in WheelGeometry.XASSET_ORDER (angle = identity)
    val label: String,       // display label (e.g. "VIX", "10Y-3M")
    val up: Boolean,         // direction == "up"
    val flat: Boolean,       // direction == "flat" (neither confirm-green nor -red emphasis)
    val confirm: Boolean,    // supports the current regime (macro_regime.evidence)
    val valueText: String,
    val deltaText: String,
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
    val nucleus: NucleusState,
    val nodes: List<PairNode>,             // size 12, PAIR_ORDER order (PAIRS mode)
    val rings: List<RingDescriptor>,       // size 6, the six factor legend pills
    val currencies: List<CurrencySeg> = emptyList(),   // size 8, H4 (CURRENCIES mode default)
    val currenciesD1: List<CurrencySeg> = emptyList(), // size 8, D1 (CURRENCIES mode, D1 toggle)
    val currenciesH1: List<CurrencySeg> = emptyList(), // size 8, H1 (CURRENCIES mode, H1 toggle)
    val crossAssets: List<CrossAssetSeg> = emptyList(), // size 10 (outer ring)
)

/**
 * The pair a factor-pill tap defaults to for pair-shaped sheets (Momentum/Structure/Entry):
 * highest potential among tradeable-tier nodes, falling back to highest overall. Display choice.
 */
/** The currency list for whichever timeframe is currently toggled (Currencies mode). */
fun WheelUiState.currenciesFor(timeframe: Timeframe): List<CurrencySeg> = when (timeframe) {
    Timeframe.D1 -> currenciesD1
    Timeframe.H4 -> currencies
    Timeframe.H1 -> currenciesH1
}

fun WheelUiState.topPair(): PairNode =
    nodes.filter { it.state == PotentialState.TRADEABLE || it.state == PotentialState.APLUS }
        .maxByOrNull { it.potential }
        ?: nodes.maxBy { it.potential }
