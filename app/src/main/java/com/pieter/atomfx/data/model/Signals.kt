package com.pieter.atomfx.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A tolerant, minimal mirror of `signals.json` (Architecture §4) — only the fields the wheel
 * and its sheets actually show are modeled; everything else in the document is decoded with
 * `ignoreUnknownKeys = true` and simply skipped (Architecture §8.3: the app never re-derives a
 * value, it only reads one that's already there). Every field is nullable/defaulted so an
 * absent EXTEND key (a normal, documented state — Architecture §4.2) never fails parsing.
 */
@Serializable
data class Signals(
    val updated: String? = null,
    @SerialName("regime_d1") val regimeD1: RegimeBlock? = null,
    @SerialName("regime_h4") val regimeH4: RegimeBlock? = null,
    @SerialName("regime_h1") val regimeH1: RegimeBlock? = null,
    val csm: Map<String, Map<String, Double>> = emptyMap(),
    @SerialName("csm_delta") val csmDelta: Map<String, Map<String, Double>> = emptyMap(),
    @SerialName("currency_flow") val currencyFlow: CurrencyFlow? = null,
    val breadth: BreadthBlock = BreadthBlock(),
    val pairs: Map<String, PairBlock> = emptyMap(),
    val potential: Map<String, PotentialEntry> = emptyMap(),
    val calendar: CalendarBlock? = null,
    val recommendation: RecommendationBlock? = null,
    val ranked: RankedBlock? = null,
    @SerialName("deep_analysis") val deepAnalysis: DeepAnalysisBlock? = null,
    val breaking: BreakingBlock? = null,
    val catalyst: CatalystBlock? = null,
    @SerialName("week_ahead") val weekAhead: WeekAheadBlock? = null,
    val correlations: CorrelationsBlock? = null,
    @SerialName("macro_assets") val macroAssets: Map<String, MacroAssetEntry> = emptyMap(),
    val macro: MacroSummary? = null,
    @SerialName("regime_w1") val regimeW1: RegimeBlock? = null,
    @SerialName("macro_regime") val macroRegime: MacroRegimeBlock? = null,
    val spark: Map<String, SparkEntry> = emptyMap(),
    val conviction: ConvictionBlock? = null,
    // 2026-09-10 — Rotation/Pulse/Thrust, three new market-state indicators (Insights tab). All
    // three are pure aggregation of values already in this same document (csm/csm_delta/breadth/
    // macro_regime/pairs.bb_d1) — see each block's own doc comment.
    val rotation: RotationBlock? = null,
    val pulse: PulseBlock? = null,
    @SerialName("breadth_thrust") val breadthThrust: ThrustBlock? = null,
    @SerialName("schema_version") val schemaVersion: Int? = null,
)

@Serializable
data class CalendarBlock(
    val events: List<CalendarEvent> = emptyList(),
)

@Serializable
data class CalendarEvent(
    val day: String? = null,
    val time: String? = null,
    val iso: String? = null,
    val currency: String? = null,
    val name: String? = null,
    val forecast: String? = null,
    val previous: String? = null,
    val note: String? = null,
)

@Serializable
data class RegimeBlock(
    val regime: String? = null,
    val confidence: String? = null,
    val score: Double? = null,
    val stable: Boolean? = null,
    // Only present on regime_w1 — harmless no-ops for regime_d1/h4/h1, which reuse this block.
    val signals: Int? = null,
    val total: Int? = null,
)

@Serializable
data class CurrencyFlow(
    val leader: String? = null,
    @SerialName("leader_delta") val leaderDelta: Double? = null,
    val laggard: String? = null,
    @SerialName("laggard_delta") val laggardDelta: Double? = null,
    @SerialName("absolute_leader") val absoluteLeader: String? = null,
    @SerialName("absolute_laggard") val absoluteLaggard: String? = null,
    @SerialName("driver_spread") val driverSpread: Double? = null,
)

// 2026-09-10 — `dir`/`net` were already computed by `breadth.py` (`compute_breadth`) but never
// declared here, so kotlinx.serialization silently dropped them on every parse. `band` and `dir`
// are two different axes that happen to share confusingly overlapping vocabulary ("strong"/"weak"
// on both) — `band` is how UNANIMOUS the currency's pairs agree (support/total, direction-blind);
// `dir` is the actual direction of that agreed-on net move (`strong`=net positive, `weak`=net
// negative, `flat`=net zero). A currency can be `band: strong` (near-unanimous) while `dir: weak`
// (that unanimous move is a weakening one) — CurrencyDetailSheet.kt's own colour bug was
// conflating the two. Never read `band` as a stand-in for direction; use `dir`.
@Serializable
data class BreadthEntry(
    val support: Int? = null,
    val total: Int? = null,
    val pct: Double? = null,
    val band: String? = null,
    val net: Double? = null,
    val dir: String? = null,
)

/**
 * 2026-09-04 — was a raw `Map<String, Map<String, BreadthEntry>>` (dynamic "h4"/"d1" keys); now a
 * real shape so the new `pairs` sub-block (added to `breadth.py` this session — same base-minus-
 * quote differential technique `conviction.py`'s own `pairs` block already uses) can sit alongside
 * the per-currency `h4`/`d1` maps without forcing every leaf in the JSON object to share one type.
 * Every current call site only ever read the `h4` key (breadth has no real per-timeframe UI use
 * yet), so `signals.breadth["h4"]` becomes `signals.breadth.h4` — a mechanical rename, not a
 * behavior change.
 */
@Serializable
data class BreadthBlock(
    val h4: Map<String, BreadthEntry> = emptyMap(),
    val d1: Map<String, BreadthEntry> = emptyMap(),
    val pairs: Map<String, Map<String, Int>> = emptyMap(), // tf ("h4"/"d1") -> pair -> signed score
)

@Serializable
data class PairBlock(
    val pills: Pills? = null,
    val mom: Momentum? = null,
    val adx: Double? = null,
    val cont: Int? = null,
    val structure: StructureBlock? = null,
    // Added 2026-09-03 — scan_h1.py already computed these to feed cont/the WHY-checklist ENTRY
    // gate; now also written to signals.json so EntrySheet can show real numbers.
    @SerialName("reset_score") val resetScore: Int? = null,
    @SerialName("atr_pct") val atrPct: Int? = null,
    // 2026-09-09 — the SAME direction (D1-else-H4 pill fallback, cont_score.pill_direction)
    // compute_cont() itself used to decide whether/how to score this pair, exposed so the app
    // can tint the wheel's Overall wing off the basis its own displayed number actually used
    // (was reading signals.potential[pair].direction, an unrelated, demoted subsystem — see
    // WheelMapper.kt's own doc comment for the bug this replaces). "bull" | "bear" | null.
    val direction: String? = null,
    // Signals Roadmap §5 (2026-09-09) — D1 12-period/2-sigma Bollinger touch state. Null
    // (whole object, matching bb_touch.py's own "not enough history yet" convention) rather
    // than a null-fielded object, so ignoreUnknownKeys/defaults never need to fight a partial
    // shape — same lesson as csm_dispersion_history's own on-device parse crash this session:
    // match the backend's actual shape exactly, including its null cases.
    @SerialName("bb_d1") val bbD1: BbD1? = null,
)

@Serializable
data class BbD1(
    val touching: String? = null,
    val sma: Double? = null,
    val upper: Double? = null,
    val lower: Double? = null,
    @SerialName("width_pct") val widthPct: Double? = null,
    @SerialName("width_trend") val widthTrend: String? = null,
)

@Serializable
data class Pills(
    val d1: String? = null,
    val h4: String? = null,
    val h1: String? = null,
)

@Serializable
data class Momentum(
    val d1: Int? = null,
    val dd1: Int? = null,
    val h4: Int? = null,
    val dh4: Int? = null,
    val h1: Int? = null,
    val dh1: Int? = null,
    val cmp: Int? = null,
    val dcmp4: Int? = null,
    val dcmp8: Int? = null,
    val dcmp12: Int? = null,
)

@Serializable
data class StructureBlock(
    val h4: StructureEntry? = null,
    val d1: StructureEntry? = null,
)

@Serializable
data class StructureEntry(
    val direction: String? = null,
    val event: String? = null,
    val strength: Double? = null,
)

@Serializable
data class PotentialEntry(
    val direction: String? = null,
    val level: Int? = null,
    val state: String? = null,
    val score: Int? = null,
    val factors: PotentialFactors? = null,
    @SerialName("blocked_at") val blockedAt: String? = null,
    @SerialName("setup_rank") val setupRank: Double? = null,
    val quality: Int? = null,
)

@Serializable
data class RecommendationBlock(
    val headline: String? = null,
    val bias: String? = null,
    val action: String? = null,
    @SerialName("primary_pair") val primaryPair: String? = null,
    val direction: String? = null,
    val confidence: String? = null,
    val rationale: String? = null,
    val invalidation: String? = null,
    @SerialName("next_catalyst") val nextCatalyst: NextCatalyst? = null,
    val headlines: List<String> = emptyList(),
    @SerialName("generated_at") val generatedAt: String? = null,
)

/** `rank.py::rank_pairs` scored+sorted (never re-derived here), capped to the top 3 by
 *  `scan_news.py::call_ranked_analysis` — never all 12, whatever the number of pairs that pass
 *  its hard gate (directional D1 pill + cont >= 45). Home's per-pair Recommendation glyphs
 *  (`StatusStrip.kt`) read this list directly, one glyph per entry. */
@Serializable
data class RankedBlock(
    val text: String? = null,
    val top: List<RankedEntry> = emptyList(),
)

@Serializable
data class RankedEntry(
    val pair: String? = null,
    val direction: String? = null,
    val score: Double? = null,
)

@Serializable
data class NextCatalyst(
    val event: String? = null,
    val iso: String? = null,
)

@Serializable
data class DeepAnalysisBlock(
    val text: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
)

/** Functional Spec §7 — top-3 Haiku-curated breaking headlines. */
@Serializable
data class BreakingBlock(
    val headlines: List<String> = emptyList(),
    // Functional Spec §7 theme tagging — same length/order as [headlines], one of
    // "risk"/"rates"/"usd"/"commodity"/"safe_haven" per item, or null if untagged.
    val themes: List<String?> = emptyList(),
    val updated: String? = null,
)

/** Functional Spec §7 — the adversarial "does any headline conflict with the top setups?" check. */
@Serializable
data class CatalystBlock(
    val text: String? = null,
    val updated: String? = null,
)

/** Functional Spec §7 row 50 — generated Sunday evenings, persisted ~24h server-side; a normal,
 *  documented absence the rest of the week (Architecture §4.2), not a bug. */
@Serializable
data class WeekAheadBlock(
    val text: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
)

@Serializable
data class PotentialFactors(
    val regime: Boolean = false,
    val flow: Boolean = false,
    val breadth: Boolean = false,
    val momentum: Boolean = false,
    val structure: Boolean = false,
    val entry: Boolean = false,
)

@Serializable
data class CorrelationsBlock(
    val pairs: List<String> = emptyList(),
    val matrix: List<List<Double>> = emptyList(),
)

@Serializable
data class MacroAssetEntry(
    val value: Double? = null,
    @SerialName("delta_pct") val deltaPct: Double? = null,
    @SerialName("delta_bp") val deltaBp: Double? = null,
    val direction: String? = null,
    val label: String? = null,
)

@Serializable
data class MacroSummary(
    val label: String? = null,
    val signals: Int? = null,
    val total: Int? = null,
    val confidence: String? = null,
    val stable: Boolean? = null,
)

@Serializable
data class MacroRegimeBlock(
    val primary: MacroArchetype? = null,
    val secondary: MacroArchetype? = null,
    @SerialName("gold_overlay") val goldOverlay: String? = null,
    @SerialName("usd_regime") val usdRegime: String? = null,
    @SerialName("currency_bias") val currencyBias: CurrencyBias? = null,
    val evidence: List<MacroEvidence> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val narrative: String? = null,
    val updated: String? = null,
)

@Serializable
data class MacroArchetype(
    val code: String? = null,
    val name: String? = null,
    val confidence: String? = null,
    @SerialName("distinct_axes") val distinctAxes: Int? = null,
)

@Serializable
data class CurrencyBias(
    val strong: List<String> = emptyList(),
    val weak: List<String> = emptyList(),
)

@Serializable
data class MacroEvidence(
    val axis: String? = null,
    val read: String? = null,
    val supports: Boolean = false,
    // "confirming" | "diverging" | "quiet" — 2026-09-06: is TODAY's daily move backing this
    // axis's own W1 trend, fighting it, or silent (see macro_regime.py's own doc comment).
    // A property of the axis itself, not of whichever regime `supports` happens to be about.
    @SerialName("confirms_today") val confirmsToday: String? = null,
)

/** Signals Roadmap §4 — the COT-based Conviction/crowding overlay. Weekly cadence (its own
 *  `scan_cot.py` job, not the hourly scan), so `updated`/`cotDate` can lag `signals.updated`
 *  by up to several days — presented as a positioning overlay, not a live signal. */
@Serializable
data class ConvictionBlock(
    val currencies: Map<String, ConvictionEntry> = emptyMap(),
    val pairs: Map<String, Int> = emptyMap(),
    @SerialName("cot_date") val cotDate: String? = null,
    @SerialName("cot_stale") val cotStale: Boolean? = null,
    val updated: String? = null,
)

@Serializable
data class ConvictionEntry(
    val conviction: Int? = null,
    val direction: Int? = null,
    @SerialName("cot_available") val cotAvailable: Boolean? = null,
)

@Serializable
data class SparkEntry(
    val d1: List<Double> = emptyList(),
    val h4: List<Double> = emptyList(),
    val h1: List<Double> = emptyList(),
)

/** 2026-09-10 — currency strength (x, CSM h4) plotted against momentum-of-strength (y, CSM Delta
 *  h4), quadrant-classified. `history` is a short oldest-first comet trail per currency, each
 *  entry `[x, y]`. See `rotation.py`'s own doc comment for the quadrant convention. */
@Serializable
data class RotationBlock(
    val tf: String? = null,
    val points: Map<String, RotationPoint> = emptyMap(),
    val history: Map<String, List<List<Double>>> = emptyMap(),
)

@Serializable
data class RotationPoint(
    val x: Double? = null,
    val y: Double? = null,
    val quadrant: String? = null,
)

/** 2026-09-10 — "is this a market where today's signals can be trusted, or is it noise?" A 0-100
 *  composite of unanimity/regime-clarity/separation/volatility-phase; `score`/`band` are null
 *  when fewer than 2 of the 4 axes are available (see `market_pulse.py`'s own doc comment).
 *  `history` is oldest-first, one score per scan it was computable. */
@Serializable
data class PulseBlock(
    val score: Double? = null,
    val band: String? = null,
    val axes: PulseAxes = PulseAxes(),
    val history: List<Double> = emptyList(),
)

@Serializable
data class PulseAxes(
    val unanimity: Double? = null,
    @SerialName("regime_clarity") val regimeClarity: Double? = null,
    val separation: Double? = null,
    @SerialName("volatility_phase") val volatilityPhase: Double? = null,
)

/** 2026-09-10 — the FX advance/decline line: count of currencies with breadth `dir == "strong"`
 *  minus `dir == "weak"`, range -8..+8. `history` is oldest-first. See `breadth.py`'s
 *  `compute_thrust` for the exact aggregation. */
@Serializable
data class ThrustBlock(
    val h4: Int? = null,
    val history: List<Int> = emptyList(),
)
