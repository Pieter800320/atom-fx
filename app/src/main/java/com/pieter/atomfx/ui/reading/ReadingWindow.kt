package com.pieter.atomfx.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.macro.RegimePlaybookDetail
import com.pieter.atomfx.ui.sheets.AlertPlaybookDetail
import com.pieter.atomfx.ui.sheets.SheetDivider
import com.pieter.atomfx.ui.sheets.TechnicalRegimePlaybookDetail
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash

/**
 * The Reading Window (Pieter, 2026-09-04) — a full-screen destination for the handbook content,
 * deliberately NOT a bottom sheet: no 80%-height cap, no scrim showing the screen behind it, no
 * shared chrome with Pair/Currency/Regime's own "inspect this live thing" sheets. Opening it
 * should read as leaving the data surface and stepping into a page, closer to how Settings/
 * Notification History/Library already read as their own screens rather than sheets.
 *
 * Hosted at the top level (MainActivity), above whatever's open underneath — a wheel sheet, a tab,
 * or the Settings panel — so all three of this session's entry points (RegimeSheet, MacroScreen's
 * archetype banner, a Notification History card) can open the same window and return to exactly
 * where they were. Dismissed via the "Close" text control, the same convention Settings/
 * Notification History already use, plus the system back button (wired in MainActivity).
 */
@Composable
fun ReadingWindow(target: ReadingTarget, colors: AtomColors, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(text = readingEyebrow(target), style = AtomType.Caption.copy(color = colors.textSecondary))
                    Text(
                        text = readingTitle(target),
                        style = AtomType.Display.copy(color = colors.textPrimary),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val subtitle = readingSubtitle(target)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = AtomType.Caption.copy(color = colors.textMuted),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Text(
                    text = "Close",
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                    modifier = Modifier.pressWash {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClose()
                    },
                )
            }

            SheetDivider(colors, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

            Column(modifier = Modifier.padding(top = 6.dp)) {
                when (target) {
                    is ReadingTarget.TechnicalRegime -> TechnicalRegimePlaybookDetail(target.entry, colors)
                    is ReadingTarget.MacroArchetype -> RegimePlaybookDetail(target.explanation, colors)
                    is ReadingTarget.Alert -> AlertPlaybookDetail(target.entry, colors)
                }
            }
        }
    }
}

private fun readingEyebrow(target: ReadingTarget): String = when (target) {
    is ReadingTarget.TechnicalRegime -> "REGIME PLAYBOOK"
    is ReadingTarget.MacroArchetype -> "REGIME PLAYBOOK"
    is ReadingTarget.Alert -> target.eyebrow
}

private fun readingTitle(target: ReadingTarget): String = when (target) {
    is ReadingTarget.TechnicalRegime -> target.entry.regime
    is ReadingTarget.MacroArchetype -> target.explanation.entry.name
    is ReadingTarget.Alert -> target.entry.title
}

private fun readingSubtitle(target: ReadingTarget): String = when (target) {
    is ReadingTarget.TechnicalRegime -> "H4 Structural Regime"
    is ReadingTarget.MacroArchetype -> "Macro Archetype ${target.explanation.entry.code}"
    is ReadingTarget.Alert -> ""
}
