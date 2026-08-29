package com.pieter.atomfx.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A tolerant, minimal mirror of `signals.json` (Architecture §4) — only the fields the wheel
 * actually draws are modeled; everything else in the document is decoded with
 * `ignoreUnknownKeys = true` and simply skipped (Architecture §8.3: the app never re-derives a
 * value, it only reads one that's already there). Every field is nullable/defaulted so an
 * absent EXTEND key (a normal, documented state — Architecture §4.2) never fails parsing.
 */
@Serializable
data class Signals(
    val updated: String? = null,
    @SerialName("regime_h4") val regimeH4: RegimeBlock? = null,
    @SerialName("currency_flow") val currencyFlow: CurrencyFlow? = null,
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
)

@Serializable
data class PotentialEntry(
    val direction: String? = null,
    val level: Int? = null,
    val state: String? = null,
    val score: Int? = null,
    val factors: PotentialFactors? = null,
    @SerialName("blocked_at") val blockedAt: String? = null,
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
