package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.push.AlertPlaybookEntry
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * The Alert Playbook detail — part 3 of the "living handbook" pass (2026-09-04), same layout
 * discipline as TechnicalRegimePlaybookDetail: static entry content in reading order, no live
 * evidence to sort by (these five alert types have no evidence/conflicts array the way the Macro
 * Archetype does). One shape covers all five (`AlertPlaybookEntry` — see AlertPlaybookContent.kt
 * for why), so one detail composable covers all five too.
 */
@Composable
fun AlertPlaybookDetail(entry: AlertPlaybookEntry, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = entry.coreStory, style = AtomType.Body.copy(color = colors.textPrimary))

        AlertPlaybookSection("HOW IT'S TRIGGERED", colors) {
            Text(text = entry.mechanism, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        AlertPlaybookSection("HOW THIS RELATES TO OTHER SIGNALS", colors) {
            Text(text = entry.relationToOtherSystems, style = AtomType.Caption.copy(color = colors.textSecondary))
        }

        AlertPlaybookSection("WHAT WOULD PROVE THIS WRONG", colors) {
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
private fun AlertPlaybookSection(label: String, colors: AtomColors, content: @Composable () -> Unit) {
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
