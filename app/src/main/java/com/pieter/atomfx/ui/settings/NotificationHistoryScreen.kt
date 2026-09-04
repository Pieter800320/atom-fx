package com.pieter.atomfx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.NotificationRecord
import com.pieter.atomfx.push.ALERT_GUIDANCE
import com.pieter.atomfx.push.CONVICTION_EXTREME_PLAYBOOK
import com.pieter.atomfx.push.GOLD_SIGNAL_PLAYBOOK
import com.pieter.atomfx.push.STRUCTURE_EVENT_PLAYBOOK
import com.pieter.atomfx.push.TECHNICAL_REGIME_PLAYBOOK
import com.pieter.atomfx.push.TF_ALIGNMENT_PLAYBOOK
import com.pieter.atomfx.push.VOLATILITY_SPIKE_PLAYBOOK
import com.pieter.atomfx.push.buildRegimeExplanation
import com.pieter.atomfx.push.parseDeepLink
import com.pieter.atomfx.ui.components.BookPlusGlyph
import com.pieter.atomfx.ui.reading.ReadingTarget
import com.pieter.atomfx.ui.sheets.SheetDivider
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash

private val NH_CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * Notification History (2026-09-04, Pieter's ask) — a durable, on-device record of every push
 * that's fired, since a data-only FCM message (see `AtomFxMessagingService`) means the app now
 * builds and records every one, not just the ones caught while foregrounded. Same "gear → panel"
 * swap-in pattern `LibraryScreen` already established, and the same guidance content that lives
 * in `ALERT_GUIDANCE` is what a live-tapped notification's "what to consider" line is built
 * from too — one source, not duplicated between a live push and its history entry.
 *
 * Tapping a card navigates via its stored deeplink — identical behaviour to tapping the original
 * system notification, whether that happens the instant it fires or days later from here.
 * "Learn more" opens the Library pre-scrolled to the matching mechanism writeup (`LibraryScreen`'s
 * `initialExpandedId`) rather than duplicating that prose here.
 */
@Composable
fun NotificationHistoryScreen(
    records: List<NotificationRecord>,
    colors: AtomColors,
    onNavigate: (SheetTarget) -> Unit,
    onOpenLibraryEntry: (String) -> Unit,
    onOpenReading: (ReadingTarget) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "NOTIFICATION HISTORY", style = AtomType.Title.copy(color = colors.textPrimary))
            Text(
                text = "Back",
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.pressWash {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                },
            )
        }

        if (records.isEmpty()) {
            Text(
                text = "No notifications yet",
                style = AtomType.Body.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 8.dp),
            )
            return
        }

        records.forEach { record ->
            NotificationCard(record, colors, onNavigate, onOpenLibraryEntry, onOpenReading)
        }
    }
}

@Composable
private fun NotificationCard(
    record: NotificationRecord,
    colors: AtomColors,
    onNavigate: (SheetTarget) -> Unit,
    onOpenLibraryEntry: (String) -> Unit,
    onOpenReading: (ReadingTarget) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val guidance = ALERT_GUIDANCE[record.type]
    val dirColor = when (record.direction) {
        "bull" -> colors.bull
        "bear" -> colors.bear
        else -> colors.textSecondary
    }
    // Resolved once, up here, so both the book+ icon (this row) and the tap target below use the
    // exact same object — one canonical Reading Window per card, no separate inline copy.
    val readingTarget: ReadingTarget? = when {
        record.type == "archetype_change" && record.regimeCode != null ->
            buildRegimeExplanation(record.regimeCode, emptyList(), emptyList())?.let { ReadingTarget.MacroArchetype(it) }
        record.type == "regime_flip" && record.regimeFlipTo != null ->
            TECHNICAL_REGIME_PLAYBOOK[record.regimeFlipTo]?.let { ReadingTarget.TechnicalRegime(it) }
        // Alert Playbook pass, 2026-09-04 — same "resolve once, up here" shape as the two cases
        // above, now covering the five remaining alert types (level_alert and potential_state
        // deliberately excluded, Pieter's own call — see AlertPlaybookContent.kt's own doc
        // comment for why).
        record.type == "structure_event" && record.structureKind != null ->
            STRUCTURE_EVENT_PLAYBOOK[record.structureKind]?.let { ReadingTarget.Alert(it, "STRUCTURE PLAYBOOK") }
        record.type == "gold_signal" && record.direction != null ->
            GOLD_SIGNAL_PLAYBOOK[record.direction]?.let { ReadingTarget.Alert(it, "GOLD SIGNAL PLAYBOOK") }
        record.type == "conviction_extreme" && record.direction != null ->
            CONVICTION_EXTREME_PLAYBOOK[record.direction]?.let { ReadingTarget.Alert(it, "POSITIONING PLAYBOOK") }
        record.type == "volatility_spike" -> ReadingTarget.Alert(VOLATILITY_SPIKE_PLAYBOOK, "VOLATILITY PLAYBOOK")
        record.type == "tf_alignment" -> ReadingTarget.Alert(TF_ALIGNMENT_PLAYBOOK, "ALIGNMENT PLAYBOOK")
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(colors.surfaceRaised, NH_CARD_SHAPE)
            .pressWash(NH_CARD_SHAPE) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                record.deeplink?.let { parseDeepLink(android.net.Uri.parse(it)) }?.let(onNavigate)
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = alertTypeLabel(record.type),
                style = AtomType.Caption.copy(color = dirColor),
                modifier = Modifier.weight(1f),
            )
            if (readingTarget != null) {
                BookPlusGlyph(
                    colors = colors,
                    modifier = Modifier.padding(end = 10.dp).pressWash {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenReading(readingTarget)
                    },
                )
            }
            if (!record.read) {
                Text(text = "●", style = AtomType.Caption.copy(color = colors.bull))
            }
            Text(
                text = relativeTime(record.timestamp),
                style = AtomType.Caption.copy(color = colors.textMuted),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = record.title,
            style = AtomType.Body.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = record.body,
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(top = 2.dp),
        )
        if (guidance != null) {
            SheetDivider(colors, modifier = Modifier.padding(vertical = 10.dp))
            Text(text = guidance.text, style = AtomType.Body.copy(color = colors.textSecondary))
            Text(
                text = "Learn more →",
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 6.dp).pressWash {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenLibraryEntry(guidance.libraryEntryId)
                },
            )
        }
    }
}

private fun alertTypeLabel(type: String): String = when (type) {
    "gold_signal" -> "GOLD SIGNAL"
    "level_alert" -> "LEVEL ALERT"
    "potential_state" -> "SETUP"
    "structure_event" -> "STRUCTURE"
    "regime_flip" -> "REGIME"
    "archetype_change" -> "MACRO ARCHETYPE"
    "volatility_spike" -> "VOLATILITY"
    "tf_alignment" -> "ALIGNMENT"
    "conviction_extreme" -> "POSITIONING"
    else -> type.uppercase()
}

private fun relativeTime(timestamp: Long): String {
    val diffMinutes = (System.currentTimeMillis() - timestamp) / 60_000
    return when {
        diffMinutes < 1 -> "just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffMinutes < 60 * 24 -> "${diffMinutes / 60}h ago"
        else -> "${diffMinutes / (60 * 24)}d ago"
    }
}
