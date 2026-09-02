package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.NextCatalyst
import com.pieter.atomfx.data.model.RecommendationBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.components.Pill
import com.pieter.atomfx.ui.components.ScrollingPills
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Design §12's left edge panel. Sourced from `recommendation` (Architecture §6); falls back to
 * `deep_analysis`'s free text when the deterministic seed had no primary pair to talk about
 * (Architecture §4.2 — a normal, not-a-bug state, same as the current live feed).
 */
@Composable
fun RecommendationSheet(signals: Signals, colors: AtomColors) {
    val rec = signals.recommendation
    val deepAnalysis = signals.deepAnalysis?.text

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            rec != null -> RecommendationContent(rec, colors)
            !deepAnalysis.isNullOrBlank() -> {
                SheetTitle("MARKET BRIEF", colors)
                Text(text = deepAnalysis, style = AtomType.Body.copy(color = colors.textPrimary))
            }
            else -> {
                SheetTitle("RECOMMENDATION", colors)
                NotAvailableRow("Recommendation", colors)
            }
        }
    }
}

/** Non-private: also reused by `InsightsScreen` (Architecture §8.2) so the recommendation card
 *  renders identically wherever it appears. */
@Composable
internal fun RecommendationContent(rec: RecommendationBlock, colors: AtomColors) {
    SheetTitle(rec.headline ?: "RECOMMENDATION", colors)

    ScrollingPills(
        pills = listOf(Pill(text = actionLabel(rec.action), tint = actionTint(rec.action, colors))),
        colors = colors,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    SheetRow("Primary pair", rec.primaryPair ?: "—", colors)
    SheetRow("Direction", rec.direction?.replaceFirstChar { it.uppercase() } ?: "—", colors)
    SheetRow("Confidence", rec.confidence ?: "—", colors)

    if (!rec.rationale.isNullOrBlank()) {
        SheetDivider(colors)
        Text(text = rec.rationale, style = AtomType.Body.copy(color = colors.textPrimary))
    }

    if (!rec.invalidation.isNullOrBlank()) {
        Text(
            text = "Invalidation: ${rec.invalidation}",
            style = AtomType.Caption.copy(color = colors.watch),
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (rec.nextCatalyst != null) {
        SheetDivider(colors)
        SheetRow("Next catalyst", catalystLine(rec.nextCatalyst), colors)
    }
}

private fun actionLabel(action: String?): String = when (action) {
    "trade" -> "TRADE"
    "watch" -> "WATCH"
    "stand_aside" -> "STAND ASIDE"
    else -> "—"
}

private fun actionTint(action: String?, colors: AtomColors) = when (action) {
    "trade" -> colors.bull
    "watch" -> colors.watch
    else -> colors.textMuted
}

private fun catalystLine(catalyst: NextCatalyst): String {
    val event = catalyst.event ?: return "—"
    val time = catalyst.iso?.let {
        runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) }.getOrNull()
    }
    return if (time != null) "$event ($time)" else event
}
