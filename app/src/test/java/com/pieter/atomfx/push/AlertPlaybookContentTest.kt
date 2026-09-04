package com.pieter.atomfx.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one thing that can silently break without ever showing up as a crash or a failed
 * build: a playbook map keyed by a string that no longer matches what the backend can actually
 * produce. `NotificationHistoryScreen`'s `readingTarget` resolution (`STRUCTURE_EVENT_PLAYBOOK[
 * record.structureKind]`, etc.) fails silently on a miss — `readingTarget` just resolves null,
 * the book icon simply doesn't appear, no error anywhere. The original Regime/Technical Regime
 * Playbook work (2026-09-04) shipped with no test coverage at all for this same risk; this file
 * exists so the Alert Playbook pass (same day) doesn't repeat that gap.
 *
 * Keys asserted here are the literal values `scanner/extend/state_alerts.py` and
 * `scanner/scan_h1.py`'s gold-signal block can produce — not re-derived, just pinned.
 */
class AlertPlaybookContentTest {

    @Test
    fun `structure event playbook covers exactly BOS and CHoCH`() {
        assertEquals(setOf("BOS", "CHoCH"), STRUCTURE_EVENT_PLAYBOOK.keys)
    }

    @Test
    fun `gold signal playbook covers exactly bull and bear`() {
        assertEquals(setOf("bull", "bear"), GOLD_SIGNAL_PLAYBOOK.keys)
    }

    @Test
    fun `conviction extreme playbook covers exactly bull and bear`() {
        assertEquals(setOf("bull", "bear"), CONVICTION_EXTREME_PLAYBOOK.keys)
    }

    @Test
    fun `every alert playbook entry has non-blank prose in every field`() {
        val allEntries = STRUCTURE_EVENT_PLAYBOOK.values + GOLD_SIGNAL_PLAYBOOK.values +
            CONVICTION_EXTREME_PLAYBOOK.values + listOf(VOLATILITY_SPIKE_PLAYBOOK, TF_ALIGNMENT_PLAYBOOK)
        allEntries.forEach { entry ->
            assertTrue("${entry.key}: title blank", entry.title.isNotBlank())
            assertTrue("${entry.key}: coreStory blank", entry.coreStory.isNotBlank())
            assertTrue("${entry.key}: mechanism blank", entry.mechanism.isNotBlank())
            assertTrue("${entry.key}: relationToOtherSystems blank", entry.relationToOtherSystems.isNotBlank())
            assertTrue("${entry.key}: falsificationChecklist empty", entry.falsificationChecklist.isNotEmpty())
            entry.falsificationChecklist.forEach {
                assertTrue("${entry.key}: a falsification question is blank", it.isNotBlank())
            }
        }
    }

    @Test
    fun `every alert type with a playbook still has an ALERT_GUIDANCE one-liner`() {
        // The book icon is an addition to the existing one-liner, never a replacement for it —
        // this pins that every alert type this pass touches still resolves guidance text too.
        listOf("structure_event", "volatility_spike", "tf_alignment", "conviction_extreme", "gold_signal")
            .forEach { type -> assertTrue("$type missing from ALERT_GUIDANCE", ALERT_GUIDANCE.containsKey(type)) }
    }
}
