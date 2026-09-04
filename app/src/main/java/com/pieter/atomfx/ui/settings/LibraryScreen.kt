package com.pieter.atomfx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash

private val LIB_CARD_SHAPE = RoundedCornerShape(14.dp)
private val LIB_SEARCH_SHAPE = RoundedCornerShape(10.dp)

/**
 * The Library (2026-09-03, Pieter's direct ask) — replaces the old six-line ABOUT section. Every
 * distinct calculation and element in the app, in one searchable, groupable reference: content
 * lives in [LIBRARY_ENTRIES] ([LibraryContent.kt]), each entry grounded in the actual frozen
 * source, not paraphrased from a doc.
 *
 * Same slide-in panel [SettingsScreen] already uses — this composable is swapped in for the
 * settings groups rather than opening a new surface, so there's one consistent "gear → panel"
 * mental model instead of a second navigation pattern for one sub-feature.
 *
 * [initialExpandedId] (added 2026-09-04 for Notification History's "Learn more" links) opens
 * straight to one entry instead of the unfiltered list — mirrors `PairSheet`'s existing
 * `initialTab` precedent for "jump straight to the relevant part of an otherwise general sheet."
 */
@Composable
fun LibraryScreen(colors: AtomColors, initialExpandedId: String? = null, onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf(initialExpandedId) }

    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            LIBRARY_ENTRIES
        } else {
            LIBRARY_ENTRIES.filter { e ->
                e.term.lowercase().contains(q) ||
                    e.summary.lowercase().contains(q) ||
                    e.category.lowercase().contains(q) ||
                    e.howItWorks.lowercase().contains(q) ||
                    e.whyItMatters.lowercase().contains(q)
            }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.category } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "LIBRARY", style = AtomType.Title.copy(color = colors.textPrimary))
            Text(
                text = "Back",
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.pressWash {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                },
            )
        }

        LibrarySearchField(query, colors) { query = it }

        if (filtered.isEmpty()) {
            Text(
                text = "No matches for “$query”",
                style = AtomType.Body.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        LIBRARY_CATEGORIES.forEach { category ->
            val entries = grouped[category].orEmpty()
            if (entries.isEmpty()) return@forEach
            Text(
                text = category.uppercase(),
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            entries.forEach { entry ->
                LibraryCard(
                    entry,
                    expanded = expandedId == entry.id,
                    scrollToOnEnter = entry.id == initialExpandedId,
                    colors = colors,
                ) {
                    expandedId = if (expandedId == entry.id) null else entry.id
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchField(value: String, colors: AtomColors, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.surfaceRaised, LIB_SEARCH_SHAPE)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(text = "Search the library…", style = AtomType.Body.copy(color = colors.textMuted))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AtomType.Body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.textPrimary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LibraryCard(
    entry: LibraryEntry,
    expanded: Boolean,
    scrollToOnEnter: Boolean,
    colors: AtomColors,
    onToggle: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    if (scrollToOnEnter) {
        LaunchedEffect(entry.id) { bringIntoViewRequester.bringIntoView() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .background(colors.surfaceRaised, LIB_CARD_SHAPE)
            .pressWash(LIB_CARD_SHAPE) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = entry.term,
                style = AtomType.Body.copy(color = colors.textPrimary),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "−" else "+",
                style = AtomType.Body.copy(color = colors.textMuted),
            )
        }
        Text(
            text = entry.summary,
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(top = 3.dp),
        )
        if (expanded) {
            Text(
                text = "HOW IT WORKS",
                style = AtomType.Caption.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 12.dp, bottom = 3.dp),
            )
            Text(text = entry.howItWorks, style = AtomType.Body.copy(color = colors.textPrimary))
            Text(
                text = "WHY IT MATTERS",
                style = AtomType.Caption.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 10.dp, bottom = 3.dp),
            )
            Text(text = entry.whyItMatters, style = AtomType.Body.copy(color = colors.textSecondary))
        }
    }
}
