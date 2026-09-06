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
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
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
 * (`rank.py::rank_pairs`, hard-capped to 3 by `scan_news.py::call_ranked_analysis` — never all 12,
 * however many pairs actually clear its directional+continuation gate). The glyphs sit in a row
 * above the wheel, horizontally scrollable if more than fit (in practice at most 3, so this is a
 * safety net, not the common case).
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
                    label = "—",
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
)

@Composable
private fun RecoGlyphColumn(
    label: String,
    direction: String?,
    selected: Boolean,
    colors: AtomColors,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RecommendationGlyph(
            direction = direction,
            colors = colors,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        )
        Text(
            text = label,
            // 10sp — same currency/pair-code caption size CsmBarStrip's own labels use
            // (WheelScreen.kt).
            style = AtomType.Caption.copy(color = if (selected) colors.textPrimary else colors.textMuted, fontSize = 10.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
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
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ConsensusItem("REGIME", regimeDotColor(item.node, colors), colors)
            ConsensusItem("TREND", trendDotColor(item.node, colors), colors)
            ConsensusItem("MOM", momentumDotColor(item.node, colors), colors)
            ConsensusItem("VOL", volatilityDotColor(item.node, colors), colors)
            ConsensusItem("STRUCTURE", structureDotColor(item.structureEvent, colors), colors)
        }
    }
}

@Composable
private fun ConsensusItem(label: String, dotColor: Color, colors: AtomColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EvidenceDot(color = dotColor, modifier = Modifier.padding(end = 6.dp))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted), maxLines = 1)
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

// Same four reads the pair sheet's own Overview tab shows (PairSheet.kt's `overviewRows`) —
// duplicated here on purpose rather than shared, same house style as RegimeSheet's own tiny
// `regimeTint` copy: a few lines of pure logic, not worth a shared API for.
private fun regimeDotColor(node: PairNode, colors: AtomColors): Color =
    if (Factor.REGIME in node.factorsPassed) colors.bull else colors.textMuted

private fun trendDotColor(node: PairNode, colors: AtomColors): Color = when {
    node.adx >= 25 && node.trendDirection == Direction.NEUTRAL -> colors.watch
    node.trendDirection == Direction.BULL -> colors.bull
    node.trendDirection == Direction.BEAR -> colors.bear
    else -> colors.textMuted
}

private fun momentumDotColor(node: PairNode, colors: AtomColors): Color =
    if (node.momentum >= 50) colors.bull else colors.bear

private fun volatilityDotColor(node: PairNode, colors: AtomColors): Color =
    if (node.volatility in 20..70) colors.bull else colors.watch

// Same BOS/CHoCH convention as PairSheet.kt's own Overview `structureRow` — BOS confirms the
// existing trend (bull-tinted), CHoCH is a live reversal warning (bear-tinted), no recent event
// reads as neutral, same as every other dot here when there's nothing to report.
private fun structureDotColor(event: String?, colors: AtomColors): Color = when (event) {
    "BOS" -> colors.bull
    "CHoCH" -> colors.bear
    else -> colors.textMuted
}
