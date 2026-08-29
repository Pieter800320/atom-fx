package com.pieter.atomfx.ui.wheel

/**
 * The wheel's own trimmed UI shape (Architecture §8.3). `WheelMapper` produces this from
 * `potential`, `currency_flow`, and `regime_h4` — nothing here is computed from anything; it
 * is a straight copy/format of backend values (spec §42).
 */

enum class Direction { BULL, BEAR, NEUTRAL }

enum class PotentialState { LOW, WATCH, TRADEABLE, APLUS }

/** The six confluence factors, in the fixed order R·F·B·M·S·E (Design §6.7, Glossary). */
enum class Factor(val glyph: String, val ringLabel: String, val shortLabel: String) {
    REGIME("R", "1 · REGIME", "Regime"),
    FLOW("F", "2 · CURRENCY FLOW", "Flow"),
    BREADTH("B", "3 · BREADTH", "Breadth"),
    MOMENTUM("M", "4 · MOMENTUM", "Mom"),
    STRUCTURE("S", "5 · STRUCTURE", "Struct"),
    ENTRY("E", "6 · ENTRY SETUP", "Entry"),
}

/** Semantic tint a ring or the nucleus can carry; resolved to an [AtomColors] value at draw time. */
enum class Tint { BULL, BEAR, WATCH, NEUTRAL }

data class PairNode(
    val pair: String,
    val index: Int, // 0..11, fixed position in the architecture §2.1 pair order
    val direction: Direction,
    val level: Int, // 0..6
    val state: PotentialState,
    val potential: Int, // 0..100
    val factorsPassed: Set<Factor>,
    val blockedAt: Factor?,
)

data class NucleusState(
    val regimeLabel: String,
    val strengthWord: String,
    val score: Double,
    val confidence: String,
    val flowLine: String,
    val tint: Tint,
)

data class RingDescriptor(
    val factor: Factor,
    val tint: Tint,
)

data class WheelUiState(
    val nucleus: NucleusState,
    val nodes: List<PairNode>, // size 12, PAIR_ORDER order
    val rings: List<RingDescriptor>, // size 6, ring 1..6 order
)

/**
 * The pair a ring tap defaults to for rings whose sheet content is inherently pair-shaped
 * (Momentum/Structure/Entry — Design §14.4-§14.6 read as a single pair's numbers, unlike
 * Regime/Flow/Breadth which are market-wide): highest potential among tradeable-tier nodes,
 * falling back to the highest potential overall. A display choice, not a new ranking number.
 */
fun WheelUiState.topPair(): PairNode =
    nodes.filter { it.state == PotentialState.TRADEABLE || it.state == PotentialState.APLUS }
        .maxByOrNull { it.potential }
        ?: nodes.maxBy { it.potential }
