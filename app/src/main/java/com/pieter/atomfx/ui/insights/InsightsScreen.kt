package com.pieter.atomfx.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.NextCatalyst
import com.pieter.atomfx.data.model.RecommendationBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.components.Pill
import com.pieter.atomfx.ui.components.ScrollingPills
import com.pieter.atomfx.ui.macro.AXIS_LABELS
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.DarkColors
import com.pieter.atomfx.ui.theme.lighten
import com.pieter.atomfx.ui.wheel.WheelScreenState
import com.pieter.atomfx.ui.wheel.WheelViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Architecture §8.2 `ui/insights/InsightsScreen.kt` — Functional Spec §7 + quick-reference rows
 * 46–50: recommendation, breaking headlines, the adversarial catalyst check, the calendar, the
 * daily brief, and (when present) the week-ahead brief, aggregated onto one screen (Design §19.2's
 * sibling entry: "recommendation card + theme-tagged news + calendar + brief"). Pure consumer of
 * `signals.json` — every section reads a field that's already there; nothing here is computed.
 *
 * Rebuilt 2026-09-03 against `docs/mockups/atom-fx-screen-kit.html`'s `scrInsights()`, the same
 * pass MacroScreen got: the recommendation on a standard card, theme-tagged news/calendar rows as
 * a divided list (not cards — the mockup doesn't card them, only the recommendation), every tag
 * using the Electric Treatment colour formula. Deliberately NOT sharing composables with
 * `CalendarEventRow` in `ui/sheets` — this is a distinct, card-based presentation for this screen
 * only; the sheets keep their own plain-row look. (`RecommendationSheet`/`RecommendationContent`,
 * the other composable this used to reference, was retired 2026-09-06 — it duplicated exactly
 * this screen's own recommendation card field-for-field and lost its only entry point when the
 * old "Summary" cascade was replaced by `StatusStrip`'s new deterministic Recommendation card;
 * this screen's AI-narrated card remains the one place that content lives.)
 */
@Composable
fun InsightsScreen(viewModel: WheelViewModel, colors: AtomColors, modifier: Modifier = Modifier) {
    val screenState by viewModel.screenState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            // Top is owned by MainActivity's persistent gear bar — see WheelScreen.kt's same note.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        when (val state = screenState) {
            WheelScreenState.Loading -> CenteredMessage("LOADING…", colors)
            WheelScreenState.Unavailable -> CenteredMessage("DATA UNAVAILABLE", colors)
            is WheelScreenState.Loaded -> InsightsContent(state.signals, colors)
        }
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AtomColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = AtomType.Body.copy(color = colors.textSecondary))
    }
}

// Mirrors Home/Macro's CARD_SHAPE — the one standard card radius.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

@Composable
private fun InsightsContent(signals: Signals, colors: AtomColors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val rec = signals.recommendation
        val deepAnalysis = signals.deepAnalysis?.text
        when {
            rec != null -> RecommendationCard(rec, colors)
            !deepAnalysis.isNullOrBlank() -> Column(modifier = Modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
                Text(text = "MARKET BRIEF", style = AtomType.Caption.copy(color = colors.textMuted))
                Text(text = deepAnalysis, style = AtomType.Body.copy(color = colors.textPrimary), modifier = Modifier.padding(top = 6.dp))
            }
            else -> NotAvailableSection("RECOMMENDATION", "Recommendation not available yet", colors)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "BREAKING", style = AtomType.Caption.copy(color = colors.textSecondary))
            BreakingHeadlines(signals.breaking?.headlines.orEmpty(), signals.breaking?.themes.orEmpty(), colors)
        }

        val catalyst = signals.catalyst?.text
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "CATALYST CHECK", style = AtomType.Caption.copy(color = colors.textSecondary))
            if (!catalyst.isNullOrBlank()) {
                // Pieter, 2026-09-03 — the watch-tinted wash (Macro's EVIDENCE_LIT_AMOUNT trick)
                // didn't read well here; a standard card, with the watch signal carried by the
                // text colour instead (as it was before this got a card at all).
                Column(modifier = Modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
                    Text(text = catalyst, style = AtomType.Body.copy(color = colors.watch))
                }
            } else {
                Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
            }
        }

        // A CALENDAR section lived here until 2026-09-04 — dropped as pure duplication (Pieter's
        // call): the gear bar's own calendar glyph already opens CalendarSheet with the identical
        // event data, on every tab, one tap away. That sheet is now the dedicated calendar surface
        // (day-grouped, with a relative countdown) instead of two copies of the same flat list.
        val brief = signals.deepAnalysis?.text
        if (!brief.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "DAILY BRIEF", style = AtomType.Caption.copy(color = colors.textSecondary))
                Text(text = brief, style = AtomType.Body.copy(color = colors.textPrimary))
            }
        }

        // Functional Spec §7 row 50 — generated Sunday evenings only, persisted ~24h server-side.
        // Its absence the rest of the week is normal, so the section itself only appears when
        // there's something to show, rather than reading "Not available yet" as a standing gap.
        val weekAhead = signals.weekAhead?.text
        if (!weekAhead.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "WEEK AHEAD", style = AtomType.Caption.copy(color = colors.textSecondary))
                Text(text = weekAhead, style = AtomType.Body.copy(color = colors.textPrimary))
            }
        }
    }
}

@Composable
private fun NotAvailableSection(label: String, message: String, colors: AtomColors) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textSecondary))
        Text(text = message, style = AtomType.Body.copy(color = colors.textMuted))
    }
}

/** The mockup's `.rec-card` — eyebrow, headline, three status chips (action / pair+direction /
 *  confidence), then the rationale and "what flips it" blocks. Standard card, no border. */
@Composable
private fun RecommendationCard(rec: RecommendationBlock, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
        Text(
            text = "RECOMMENDATION" + (formatGeneratedAt(rec.generatedAt)?.let { " · $it" } ?: ""),
            style = AtomType.Caption.copy(color = colors.textMuted),
        )
        Text(
            text = rec.headline ?: "—",
            style = AtomType.Title.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        ScrollingPills(
            pills = listOfNotNull(
                Pill(actionLabel(rec.action), actionTint(rec.action, colors), electric = true),
                rec.primaryPair?.let { pair ->
                    val label = rec.direction?.let { "$pair · ${it.replaceFirstChar(Char::uppercase)}" } ?: pair
                    Pill(label, directionTint(rec.direction, colors), electric = true)
                },
                rec.confidence?.let { Pill("Confidence: $it", colors.neutral, electric = true) },
            ),
            colors = colors,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (!rec.rationale.isNullOrBlank()) {
            RecBlock("RATIONALE", rec.rationale, colors.textPrimary, colors)
        }
        if (!rec.invalidation.isNullOrBlank()) {
            RecBlock("WHAT FLIPS IT", rec.invalidation, colors.textSecondary, colors, cutout = true, modifier = Modifier.padding(top = 10.dp))
        }
        if (rec.nextCatalyst != null) {
            RecBlock("NEXT CATALYST", catalystLine(rec.nextCatalyst), colors.textSecondary, colors, cutout = true, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** [cutout] — same "hole punched through the card" treatment as Macro's STRONG/WEAK bias boxes:
 *  `ground`, not a raised nested surface (which would repeat the same bug — in dark theme,
 *  `surfaceRaised` is identical to this card's own `cardSurface`, so it'd be invisible). Used for
 *  "What flips it" / "Next catalyst" — the two asides, not the primary Rationale text. */
@Composable
private fun RecBlock(label: String, text: String, textColor: Color, colors: AtomColors, cutout: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .let { if (cutout) it.fillMaxWidth().background(colors.ground, RoundedCornerShape(10.dp)).padding(9.dp) else it },
    ) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted), modifier = Modifier.padding(bottom = 4.dp))
        Text(text = text, style = AtomType.Body.copy(color = textColor))
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
    else -> colors.neutral
}

private fun directionTint(direction: String?, colors: AtomColors) = when (direction?.lowercase()) {
    "long", "up", "bull" -> colors.bull
    "short", "down", "bear" -> colors.bear
    else -> colors.neutral
}

private fun catalystLine(catalyst: NextCatalyst): String {
    val event = catalyst.event ?: return "—"
    val time = catalyst.iso?.let {
        runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) }.getOrNull()
    }
    return if (time != null) "$event ($time)" else event
}

private fun formatGeneratedAt(generatedAt: String?): String? {
    val timestamp = generatedAt ?: return null
    return runCatching { OffsetDateTime.parse(timestamp).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
}

/** Breaking headlines, theme-tagged — the mockup's `.news-row`: a divided list (dividers between
 *  items, not cards), each with a small coloured topic tag. The tag colour is illustrative topic
 *  categorisation (matching the mockup's risk=bull/rates=watch/usd=bear), not a live directional
 *  read — a headline tagged RISK isn't necessarily bullish, it's just about that axis. */
@Composable
private fun BreakingHeadlines(headlines: List<String>, themes: List<String?>, colors: AtomColors) {
    if (headlines.isEmpty()) {
        Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        headlines.forEachIndexed { index, headline ->
            if (index > 0) RowDivider(colors)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val axis = themes.getOrNull(index)
                // Pieter, 2026-09-03 follow-up — the squircle itself should hug its word (not
                // stretch to a fixed width), but the headline text still needs to start at the
                // same x on every row regardless of "RISK" vs "RATES". Fixed-width outer Box,
                // left-aligned tag inside it: the reserved space is invisible, the badge isn't.
                Box(modifier = Modifier.width(BREAKING_TAG_WIDTH), contentAlignment = Alignment.CenterStart) {
                    if (axis != null) {
                        InlineTag((AXIS_LABELS[axis] ?: axis).uppercase(), axisTint(axis, colors), colors)
                    }
                }
                Text(text = headline, style = AtomType.Body.copy(color = colors.textSecondary), modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun axisTint(axis: String?, colors: AtomColors) = when (axis) {
    "risk" -> colors.bull
    "rates" -> colors.watch
    "usd" -> colors.bear
    "commodity" -> colors.bull
    "safe_haven" -> colors.neutral
    else -> colors.neutral
}

/** A quiet hairline between list items — Macro's `Divider`, same shape. */
@Composable
private fun RowDivider(colors: AtomColors) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
}

// Item Library #07's Electric Treatment colour formula (ScrollingPills.kt), at a smaller scale for
// an inline badge (a topic/currency tag sitting mid-sentence in a row) rather than a full pill.
private val INLINE_TAG_SHAPE = RoundedCornerShape(6.dp)

// Wide enough for the longest axis label ("SAFE-HAVEN") at Caption size + its own padding.
private val BREAKING_TAG_WIDTH = 84.dp

@Composable
private fun InlineTag(text: String, tint: Color, colors: AtomColors, modifier: Modifier = Modifier) {
    val isDark = colors == DarkColors
    Text(
        text = text,
        style = AtomType.Caption.copy(color = if (isDark) lighten(tint, 0.45f) else tint),
        modifier = modifier
            .background(tint.copy(alpha = 0.18f), INLINE_TAG_SHAPE)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
