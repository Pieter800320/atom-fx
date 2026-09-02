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
    val breadth: Map<String, Map<String, BreadthEntry>> = emptyMap(),
    val pairs: Map<String, PairBlock> = emptyMap(),
    val potential: Map<String, PotentialEntry> = emptyMap(),
    val calendar: CalendarBlock? = null,
    val recommendation: RecommendationBlock? = null,
    @SerialName("deep_analysis") val deepAnalysis: DeepAnalysisBlock? = null,
    val breaking: BreakingBlock? = null,
    val catalyst: CatalystBlock? = null,
    val correlations: CorrelationsBlock? = null,
    @SerialName("macro_assets") val macroAssets: Map<String, MacroAssetEntry> = emptyMap(),
    val macro: MacroSummary? = null,
    @SerialName("regime_w1") val regimeW1: RegimeBlock? = null,
    @SerialName("macro_regime") val macroRegime: MacroRegimeBlock? = null,
    val spark: Map<String, SparkEntry> = emptyMap(),
)

@Serializable
data class CalendarBlock(
    val events: List<CalendarEvent> = emptyList(),
)

@Serializable
data class CalendarEvent(
    val day: String? = null,
    val time: String? = null,
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

@Serializable
data class BreadthEntry(
    val support: Int? = null,
    val total: Int? = null,
    val pct: Double? = null,
    val band: String? = null,
)

@Serializable
data class PairBlock(
    val pills: Pills? = null,
    val mom: Momentum? = null,
    val adx: Double? = null,
    val cont: Int? = null,
    val structure: StructureBlock? = null,
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
    val updated: String? = null,
)

/** Functional Spec §7 — the adversarial "does any headline conflict with the top setups?" check. */
@Serializable
data class CatalystBlock(
    val text: String? = null,
    val updated: String? = null,
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
)

@Serializable
data class SparkEntry(
    val d1: List<Double> = emptyList(),
    val h4: List<Double> = emptyList(),
    val h1: List<Double> = emptyList(),
)
