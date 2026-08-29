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
)

@Serializable
data class RegimeBlock(
    val regime: String? = null,
    val confidence: String? = null,
    val score: Double? = null,
    val stable: Boolean? = null,
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
data class PotentialFactors(
    val regime: Boolean = false,
    val flow: Boolean = false,
    val breadth: Boolean = false,
    val momentum: Boolean = false,
    val structure: Boolean = false,
    val entry: Boolean = false,
)
