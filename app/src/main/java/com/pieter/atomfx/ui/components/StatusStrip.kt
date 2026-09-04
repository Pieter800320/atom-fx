package com.pieter.atomfx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.WheelUiState
import com.pieter.atomfx.ui.wheel.tintColor
import com.pieter.atomfx.ui.wheel.topPair
import kotlinx.coroutines.delay

// Pieter, 2026-09-03 follow-up: the first pass (StiffnessLow) read as sluggish — snappier now
// (StiffnessMedium + a light settle, not the heavier bounce the wheel's own rimFlash uses).
private val CASCADE_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
private val CASCADE_SPRING_SIZE = spring<IntSize>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
private const val OPEN_STAGGER_MS = 22L
private const val CLOSE_STAGGER_MS = 14L
private val CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * Pieter, 2026-09-03 — "Summary": the strip is now a single button driving "the Cascade" (Pieter's
 * name, 2026-09-03 — reuse this term for the pattern anywhere else in the app it fits): Item
 * Library #03's grow-from-the-icon mechanic, ported to a vertical list — each card grows/collapses
 * in place rather than converging on the icon's exact position, since these already sit directly
 * beneath it in document flow. Collapsed, only the button itself shows; tapping it cascades all 9
 * fields down as individual grey-squircle cards, staggered — no scrim, no floating layer, same as
 * Item #3. Closes ONLY on a second tap of the button itself (Pieter's explicit call — no
 * tap-outside-closes guard here, unlike #3's `dispatchTouchEvent` equivalent). Each card stays
 * independently tappable to its own sheet, same targets the old always-on cells used; tapping a
 * card does NOT collapse the cascade (only the button does).
 *
 * Follow-up (2026-09-03, same session) — Pieter explicitly does not want the wheel to shrink to
 * make room for the cascade; the whole landing Column scrolls instead (WheelScreen.kt's
 * `verticalScroll`), wheel included. This is a deliberate, flagged supersession of Design §17
 * ("the landing screen never scrolls") for this one interaction — see the comment on
 * `WheelArea`'s own sizing in WheelScreen.kt for the mechanics and the same flag repeated there.
 *
 * Regime/leader/leading-currency/top-pair are also answered at rest elsewhere on the landing
 * view regardless of this button's state (the wheel hub's regime word+archetype line, the
 * always-on Currency Flow ticker, and the pair ring itself) — collapsing this strip by default
 * does not regress the §20 acceptance test, it removes a redundant shortcut to information the
 * wheel already surfaces at a glance.
 */
@Composable
fun StatusStrip(
    state: WheelUiState,
    signals: Signals,
    colors: AtomColors,
    onCellClick: (SheetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val flow = signals.currencyFlow
    val leaderBreadth = flow?.leader?.let { signals.breadth.h4[it] }
    val topPair = state.topPair()

    val items = listOf(
        StripItem("REGIME", state.nucleus.regimeLabel, tintColor(state.nucleus.tint, colors)) {
            onCellClick(SheetTarget.Ring(Factor.REGIME))
        },
        // Item 2, per Pieter (2026-09-03) — moved here from HeaderBar.kt's own preview line
        // rather than dropped: AI-generated (recommendation.py, Sonnet), so unlike Regime/Leader/
        // Laggard it isn't redundant with anything else on the landing view. Same tap target
        // (SheetTarget.Recommendation) HeaderBar used to route straight to.
        StripItem("RECOMMENDATION", signals.recommendation?.headline ?: "—", colors.textPrimary) {
            onCellClick(SheetTarget.Recommendation)
        },
        StripItem("LEADER", flow?.leader?.let { "$it ${signedInt(flow.leaderDelta)}" } ?: "—", deltaColor(flow?.leaderDelta, colors)) {
            onCellClick(flow?.leader?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW))
        },
        // Item Library #04's restore, per Pieter (2026-09-03) — Glossary "Absolute leader/
        // laggard": highest/lowest absolute CSM right now, distinct from flow leader/laggard
        // (fastest-moving). Dropped from the UI 2026-09-02; back now, inside the cascade only.
        StripItem("ABSOLUTE LEADER", flow?.absoluteLeader ?: "—", colors.textPrimary) {
            onCellClick(flow?.absoluteLeader?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW))
        },
        StripItem("LAGGARD", flow?.laggard?.let { "$it ${signedInt(flow.laggardDelta)}" } ?: "—", deltaColor(flow?.laggardDelta, colors)) {
            onCellClick(flow?.laggard?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW))
        },
        StripItem("ABSOLUTE LAGGARD", flow?.absoluteLaggard ?: "—", colors.textPrimary) {
            onCellClick(flow?.absoluteLaggard?.let { SheetTarget.Currency(it) } ?: SheetTarget.Ring(Factor.FLOW))
        },
        // Architecture §5.1: driver_spread = leader_delta − laggard_delta — how sharply
        // currencies are diverging right now, not a CSM level. Never shown in the UI before.
        StripItem("DRIVER SPREAD", flow?.driverSpread?.let { signedInt(it) } ?: "—", colors.textPrimary) {
            onCellClick(SheetTarget.Ring(Factor.FLOW))
        },
        StripItem("BREADTH", leaderBreadth?.band ?: "—", bandColor(leaderBreadth?.band, colors)) {
            onCellClick(SheetTarget.Ring(Factor.BREADTH))
        },
        StripItem("TOP PAIR", topPair.pair, colors.textPrimary) {
            onCellClick(SheetTarget.Node(topPair.pair))
        },
    )

    // One visibility flag per card, flipped in a staggered wave rather than all at once —
    // AnimatedVisibility's own expandVertically is what reflows WheelArea's weight(1f) box
    // beneath this, card by card, exactly as each one settles.
    val cardVisible = remember { mutableStateListOf(*Array(items.size) { false }) }
    LaunchedEffect(expanded) {
        if (expanded) {
            cardVisible.indices.forEach { i -> delay(OPEN_STAGGER_MS); cardVisible[i] = true }
        } else {
            cardVisible.indices.reversed().forEach { i -> delay(CLOSE_STAGGER_MS); cardVisible[i] = false }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SummaryButton(expanded = expanded, colors = colors) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            expanded = !expanded
        }
        items.forEachIndexed { i, item ->
            AnimatedVisibility(
                visible = cardVisible.getOrElse(i) { false },
                enter = fadeIn(CASCADE_SPRING) + expandVertically(CASCADE_SPRING_SIZE, expandFrom = Alignment.Top) + scaleIn(CASCADE_SPRING, initialScale = 0.85f),
                exit = fadeOut(CASCADE_SPRING) + shrinkVertically(CASCADE_SPRING_SIZE, shrinkTowards = Alignment.Top) + scaleOut(CASCADE_SPRING, targetScale = 0.85f),
            ) {
                StripCard(item, colors, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private data class StripItem(val label: String, val value: String, val valueColor: Color, val onClick: () -> Unit)

/** The "Summary" button. Was a miniature of the wheel itself (hub + ring + 4 trapezoid wings) as
 *  the glyph (Pieter, 2026-09-03) — dropped 2026-09-04 (Pieter's ask), text + chevron only now. */
@Composable
private fun SummaryButton(expanded: Boolean, colors: AtomColors, onClick: () -> Unit) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "summaryChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Experiment, 2026-09-03 — control treatment (Color.kt): brighter fill in dark theme,
            // darker in light, always bordered since this is pressable.
            .background(colors.controlSurface, CARD_SHAPE)
            .border(1.dp, colors.controlBorder, CARD_SHAPE)
            .pressWash(CARD_SHAPE, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SUMMARY",
            // Pieter, 2026-09-03 — Caption's default weight is SemiBold, same tier Display/Title
            // (the app's actual heading styles) use, so it read as a heading despite being the
            // smallest size. Normal here, same move ScrollingPills' electric pills already made
            // for the same reason.
            style = AtomType.Caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Normal),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "▾",
            style = AtomType.Caption.copy(color = colors.textMuted),
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}

/** One cascaded line — "info presented inline on the card": label and value share one row on
 *  their own grey squircle (Pieter's words), not stacked like the old micro-cells. */
@Composable
private fun StripCard(item: StripItem, colors: AtomColors, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Same control treatment as the Summary button — a Cascade row is just as pressable.
            .background(colors.controlSurface, CARD_SHAPE)
            .border(1.dp, colors.controlBorder, CARD_SHAPE)
            .pressWash(CARD_SHAPE) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                item.onClick()
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = item.label, style = AtomType.Caption.copy(color = colors.textMuted), maxLines = 1)
        // RECOMMENDATION's value is an AI headline sentence, not a short code/number like every
        // other card — weight(1f) + end-align + ellipsis keeps it on one line without pushing the
        // label, while every short value just right-aligns in the leftover space as before.
        Text(
            text = item.value,
            style = AtomType.Body.copy(color = item.valueColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun signedInt(value: Double?): String {
    if (value == null) return ""
    val sign = if (value > 0) "+" else ""
    return "$sign${value.toInt()}"
}

private fun deltaColor(delta: Double?, colors: AtomColors): Color = when {
    delta == null -> colors.textPrimary
    delta > 0 -> colors.bull
    delta < 0 -> colors.bear
    else -> colors.textSecondary
}

// Same convention as BreadthSheet.kt/CurrencyDetailSheet.kt — colour from the backend's own
// band string, not a recomputed threshold (breadth.py owns that classification).
private fun bandColor(band: String?, colors: AtomColors): Color = when (band?.lowercase()) {
    "strong" -> colors.bull
    "moderate" -> colors.watch
    "weak" -> colors.bear
    else -> colors.textPrimary
}
