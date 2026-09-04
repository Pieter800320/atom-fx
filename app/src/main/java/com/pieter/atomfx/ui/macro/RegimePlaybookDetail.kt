package com.pieter.atomfx.ui.macro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.push.RegimeExplanation
import com.pieter.atomfx.ui.sheets.SheetDivider
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * The Regime Playbook detail — the "living handbook" proof-of-concept (Pieter, 2026-09-04):
 * the FX Macro Flow Handbook's own theory, assembled for the LIVE regime rather than read
 * linearly. [explanation] already carries only the axis/conflict fragments that actually
 * apply to this exact moment (`RegimePlaybookContent.buildRegimeExplanation`) — this
 * composable just lays them out, in the order a reader should encounter them: what this
 * regime is and why (core story), which of the live signals are actually confirming it,
 * any tension between them, what it favours and why, then a falsification checklist rather
 * than a flat "buy this" table — the handbook's own Ch.27 "don't treat correlations as
 * laws" discipline, applied to itself.
 *
 * Reused from two places: MacroScreen's archetype banner (always available — the live
 * regime, whether or not a notification just fired) and an `archetype_change` card in
 * Notification History (via the persisted `regimeCode`, axis/conflict fragments empty
 * since a past moment's evidence isn't snapshotted — core content still shows).
 */
@Composable
fun RegimePlaybookDetail(explanation: RegimeExplanation, colors: AtomColors) {
    val entry = explanation.entry
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = entry.coreStory, style = AtomType.Body.copy(color = colors.textPrimary))

        if (explanation.axisFragments.isNotEmpty()) {
            PlaybookSection("CONFIRMING NOW", colors) {
                explanation.axisFragments.forEach { fragment ->
                    Text(
                        text = fragment,
                        style = AtomType.Caption.copy(color = colors.textSecondary),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }

        if (explanation.conflictFragments.isNotEmpty()) {
            PlaybookSection("TENSION", colors) {
                explanation.conflictFragments.forEach { fragment ->
                    Text(
                        text = fragment,
                        style = AtomType.Caption.copy(color = colors.watch),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }

        PlaybookSection("WHAT IT FAVOURS, AND WHY", colors) {
            Text(text = entry.biasMechanism, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        PlaybookSection("READING THE CONFIDENCE BADGE", colors) {
            Text(text = entry.confidenceNote, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        PlaybookSection("WHAT WOULD PROVE THIS WRONG", colors) {
            entry.falsificationChecklist.forEach { question ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(text = "· ", style = AtomType.Caption.copy(color = colors.textMuted))
                    Text(text = question, style = AtomType.Caption.copy(color = colors.textSecondary))
                }
            }
        }

        if (entry.historicalNote != null) {
            PlaybookSection("PRECEDENT", colors) {
                Text(text = entry.historicalNote, style = AtomType.Caption.copy(color = colors.textMuted))
            }
        }
    }
}

@Composable
private fun PlaybookSection(label: String, colors: AtomColors, content: @Composable () -> Unit) {
    SheetDivider(colors, modifier = Modifier.padding(vertical = 10.dp))
    Column {
        Text(
            text = label,
            style = AtomType.Caption.copy(color = colors.textMuted),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}
