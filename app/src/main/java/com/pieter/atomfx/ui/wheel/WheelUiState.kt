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

/** Which ring the middle band shows. */
enum class WheelMode { PAIRS, CURRENCIES }

/**
 * Currencies-mode timeframe (2026-09-02, Pieter's toggle). `csm`/`csm_delta` are genuinely
 * per-timeframe in `signals.json` (d1/h4/h1 all present) so this changes real data in Currencies
 * mode. Pair `potential` is *not* per-timeframe — it's already a single cross-timeframe verdict —
 * so this has no effect on Pairs mode; the toggle stays visible but inert there.
 */
enum class Timeframe { D1, H4 }

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
    val level: Int, // 0..6
    val state: PotentialState,
    val potential: Int, // 0..100
    val factorsPassed: Set<Factor>,
    val blockedAt: Factor?,
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
    val crossAssets: List<CrossAssetSeg> = emptyList(), // size 10 (outer ring)
)

/**
 * The pair a factor-pill tap defaults to for pair-shaped sheets (Momentum/Structure/Entry):
 * highest potential among tradeable-tier nodes, falling back to highest overall. Display choice.
 */
fun WheelUiState.topPair(): PairNode =
    nodes.filter { it.state == PotentialState.TRADEABLE || it.state == PotentialState.APLUS }
        .maxByOrNull { it.potential }
        ?: nodes.maxBy { it.potential }
