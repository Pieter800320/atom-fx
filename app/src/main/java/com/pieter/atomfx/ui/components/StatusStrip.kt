package com.pieter.atomfx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import com.pieter.atomfx.ui.wheel.PairNode
import com.pieter.atomfx.ui.wheel.WheelUiState

private val CARD_SHAPE = RoundedCornerShape(14.dp)

// Item Library #03 ("Inline Fan-Out Capture") timing, ported verbatim — 260ms,
// cubic-bezier(.34,1.56,.64,1) (Compose's own equivalent of Android's OvershootInterpolator(1.1f)).
private const val PANEL_DURATION_MS = 260
private val PANEL_EASING = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private const val EMPTY_KEY = "__empty__"

/**
 * 2026-09-06 (Pieter's follow-up ask) — was one glyph for the single deterministic
 * `recommendation.primary_pair`; now one small glyph PER pair in `signals.ranked.top`
 * (`rank.py::rank_pairs`, filtered to a score floor rather than a fixed count — see
 * `RECOMMENDATION_MIN_SCORE` in `scan_h1.py`/`scan_news.py`, 2026-09-17 — never all 12, however
 * many pairs actually clear both `rank.py`'s own gate and that floor). The glyphs sit in a row
 * above the wheel, horizontally scrollable if more than fit — genuinely needed now, not just a
 * safety net: a correlated trending day can clear the floor on several pairs at once.
 *
 * Tapping a glyph opens a FULL-WIDTH panel below the row — Item Library #03's canonical §4
 * "reflow, not overlay" behaviour (superseding this same feature's own brief same-day detour into
 * a floating `Popup`, §9): it's an ordinary in-flow composable, so it pushes the wheel/CSM
 * strip/TF row down exactly like the old always-visible Summary card used to, no manual position
 * math needed. Tapping the same glyph again, or a different glyph, collapses the panel (a
 * different-glyph tap swaps the panel's content directly rather than closing-then-reopening — the
 * visible effect is the same "was showing X, now shows Y").
 *
 * Content is deliberately the TECHNICAL counterpart to Insights' AI-narrated recommendation: each
 * pair's own Regime/Trend/Momentum/Volatility/Structure consensus, Structure now included (Pieter's
 * ask — it was on the old Overview tab already, `PairSheet.kt`'s own `structureRow`, just never
 * surfaced here) plus its ranked setup score (`ranked.top[].score` — there's no per-pair confidence
 * word at this granularity, only the deterministic `recommendation` object has that, so the raw
 * score is shown instead of inventing a bucketed label). Tapping the panel opens that pair's own
 * sheet.
 */
@Composable
fun StatusStrip(
    state: WheelUiState,
    signals: Signals,
    colors: AtomColors,
    onCellClick: (SheetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(signals.ranked, state.nodes) {
        signals.ranked?.top.orEmpty().mapNotNull { r ->
            val pair = r.pair ?: return@mapNotNull null
            val node = state.nodes.firstOrNull { it.pair == pair } ?: return@mapNotNull null
            RecoItem(
                pair = pair,
                direction = r.direction,
                score = r.score,
                node = node,
                structureEvent = signals.pairs[pair]?.structure?.h4?.event,
                structureDirection = signals.pairs[pair]?.structure?.h4?.direction,
            )
        }
    }

    val haptics = LocalHapticFeedback.current
    var expandedKey by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (items.isEmpty()) {
                RecoGlyphColumn(
                    label = null,
                    direction = null,
                    selected = expandedKey == EMPTY_KEY,
                    colors = colors,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expandedKey = if (expandedKey == EMPTY_KEY) null else EMPTY_KEY
                    },
                )
            } else {
                items.forEach { item ->
                    RecoGlyphColumn(
                        label = item.pair,
                        direction = item.direction,
                        selected = expandedKey == item.pair,
                        colors = colors,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expandedKey = if (expandedKey == item.pair) null else item.pair
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expandedKey != null,
            enter = expandVertically(tween(PANEL_DURATION_MS, easing = PANEL_EASING)) +
                fadeIn(tween(PANEL_DURATION_MS, easing = PANEL_EASING)),
            exit = shrinkVertically(tween(PANEL_DURATION_MS, easing = PANEL_EASING)) +
                fadeOut(tween(PANEL_DURATION_MS, easing = PANEL_EASING)),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp).animateContentSize()) {
                val item = items.firstOrNull { it.pair == expandedKey }
                if (item != null) {
                    RecommendationPanel(item, colors) {
                        expandedKey = null
                        onCellClick(SheetTarget.Node(item.pair))
                    }
                } else {
                    EmptyRecommendationPanel(colors)
                }
            }
        }
    }
}

private data class RecoItem(
    val pair: String,
    val direction: String?,
    val score: Double?,
    val node: PairNode,
    val structureEvent: String?,
    val structureDirection: String?,
)

@Composable
private fun RecoGlyphColumn(
    // Null for the "no qualifying setups" placeholder — 2026-09-06 (Pieter's ask): a bare "—"
    // caption under the one glyph in that state read as a stray dash sitting under the glyph
    // for no reason, not a label. There's nothing to distinguish when there's only ever one
    // glyph in this state, so it goes back to having no caption at all, same as the single-
    // glyph design before per-pair labels existed.
    label: String?,
    direction: String?,
    selected: Boolean,
    colors: AtomColors,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RecommendationGlyph(
            direction = direction,
            colors = colors,
            // 2026-09-06 (Pieter's ask) — was a raw `clickable(indication = null)`, i.e. no press
            // feedback at all. `pressWash(CircleShape)` masks the ripple to the glyph's own circle
            // (Item Library #04 — the badge is round, its touch bounds aren't; an unmasked wash
            // would bleed into the corners the glyph itself never draws into).
            modifier = Modifier.pressWash(shape = CircleShape, onClick = onClick),
        )
        if (label != null) {
            Text(
                text = label,
                // 10sp — same currency/pair-code caption size CsmBarStrip's own labels use
                // (WheelScreen.kt).
                style = AtomType.Caption.copy(color = if (selected) colors.textPrimary else colors.textMuted, fontSize = 10.sp),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyRecommendationPanel(colors: AtomColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlSurface, CARD_SHAPE)
            .border(1.dp, colors.controlBorder, CARD_SHAPE)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = "RECOMMENDATION", style = AtomType.Caption.copy(color = colors.textMuted, fontWeight = FontWeight.Normal))
        Text(
            text = "No qualifying setups this scan.",
            style = AtomType.Body.copy(color = colors.textSecondary),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RecommendationPanel(item: RecoItem, colors: AtomColors, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlSurface, CARD_SHAPE)
            .border(1.dp, colors.controlBorder, CARD_SHAPE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = "RECOMMENDATION", style = AtomType.Caption.copy(color = colors.textMuted, fontWeight = FontWeight.Normal))
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = item.pair, style = AtomType.Title.copy(color = colors.textPrimary))
            Text(text = directionWord(item.direction), style = AtomType.Caption.copy(color = directionColor(item.direction, colors)))
        }
        if (item.score != null) {
            Text(
                text = "SETUP SCORE %.1f".format(java.util.Locale.US, item.score),
                style = AtomType.Caption.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ConsensusDotRow(
            node = item.node,
            structureEvent = item.structureEvent,
            structureDirection = item.structureDirection,
            colors = colors,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private fun directionWord(direction: String?): String = when (direction) {
    "bull" -> "LONG"
    "bear" -> "SHORT"
    else -> "—"
}

private fun directionColor(direction: String?, colors: AtomColors): Color = when (direction) {
    "bull" -> colors.bull
    "bear" -> colors.bear
    else -> colors.textSecondary
}
