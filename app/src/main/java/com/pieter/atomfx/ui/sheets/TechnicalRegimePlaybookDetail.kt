package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.push.TechnicalRegimeEntry
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * The Technical Regime Playbook detail — part 2 of the "living handbook" pass (2026-09-04),
 * same layout discipline as MacroScreen's RegimePlaybookDetail: no live evidence to sort by
 * here (TechnicalRegimePlaybookContent's own doc comment explains why — `RegimeBlock` has no
 * evidence/conflicts array), so this lays out the entry's static content in reading order.
 *
 * Reused from RegimeSheet (the live H4 regime, always available) and a `regime_flip` card in
 * Notification History (via the persisted `regimeFlipTo`).
 */
@Composable
fun TechnicalRegimePlaybookDetail(entry: TechnicalRegimeEntry, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = entry.coreStory, style = AtomType.Body.copy(color = colors.textPrimary))

        TrPlaybookSection("THE THREE VOTES", colors) {
            Text(text = entry.votesExplanation, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        TrPlaybookSection("READING THE CONFIDENCE BADGE", colors) {
            Text(text = entry.confidenceNote, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        TrPlaybookSection("READING THE SCORE", colors) {
            Text(text = entry.scoreNote, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        TrPlaybookSection("HOW THIS RELATES TO OTHER SIGNALS", colors) {
            Text(text = entry.relationToOtherSystems, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        TrPlaybookSection("WHAT WOULD PROVE THIS WRONG", colors) {
            entry.falsificationChecklist.forEach { question ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(text = "· ", style = AtomType.Caption.copy(color = colors.textMuted))
                    Text(text = question, style = AtomType.Caption.copy(color = colors.textSecondary))
                }
            }
        }
    }
}

@Composable
private fun TrPlaybookSection(label: String, colors: AtomColors, content: @Composable () -> Unit) {
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
