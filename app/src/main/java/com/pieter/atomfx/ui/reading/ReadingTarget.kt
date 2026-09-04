package com.pieter.atomfx.ui.reading

import com.pieter.atomfx.push.AlertPlaybookEntry
import com.pieter.atomfx.push.RegimeExplanation
import com.pieter.atomfx.push.TechnicalRegimeEntry

/**
 * What the Reading Window shows (Pieter, 2026-09-04): "if bottom sheets are used for inspecting
 * data, then a window is used for study and reading." Deliberately its own sealed type, not
 * another `SheetTarget` case — a sheet inspects a live thing, this reads the theory behind it,
 * and the two are meant to feel like different kinds of screens, not variations of one.
 *
 * Each entry point (RegimeSheet, MacroScreen's archetype banner, a Notification History card)
 * already has the explanation/entry object in hand at the point the reader taps the book icon —
 * carried directly here rather than re-derived from raw signals, so a history card's entry-level-
 * only explanation (no live evidence snapshot to assemble) works exactly as it already does.
 */
sealed interface ReadingTarget {
    data class TechnicalRegime(val entry: TechnicalRegimeEntry) : ReadingTarget
    data class MacroArchetype(val explanation: RegimeExplanation) : ReadingTarget
    // Alert Playbook pass, 2026-09-04 — one shared case for all five remaining alert types
    // (structure_event/volatility_spike/tf_alignment/conviction_extreme/gold_signal), matching
    // AlertPlaybookEntry's own "one shared shape" design (see AlertPlaybookContent.kt) — five
    // sealed subtypes here would just be five copies of the same single field. [eyebrow] is the
    // small label the Reading Window shows above the title (e.g. "STRUCTURE PLAYBOOK"), since
    // there's no single regime-style name to derive it from the way the other two cases can.
    data class Alert(val entry: AlertPlaybookEntry, val eyebrow: String) : ReadingTarget
}
