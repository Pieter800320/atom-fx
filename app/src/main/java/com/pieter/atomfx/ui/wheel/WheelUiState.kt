package com.pieter.atomfx.ui.wheel

/**
 * Mock-only shape for Phase 2 (Architecture §8.3, trimmed to what the wheel Canvas draws).
 * Once the app reads live `signals.json` (Phase 3), a real `WheelMapper` will produce this
 * same shape from `potential`, `currency_flow`, and `regime_h4` — nothing here is computed
 * from anything; it is a straight copy/format of backend values (spec §42).
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
    val isTopPair: Boolean = false,
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
