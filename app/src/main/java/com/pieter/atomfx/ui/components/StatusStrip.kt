package com.pieter.atomfx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.PairNode
import com.pieter.atomfx.ui.wheel.WheelUiState
import kotlinx.coroutines.launch

private val CARD_SHAPE = RoundedCornerShape(14.dp)
// 272dp, not the first-guess 240dp — device-checked twice: the 4-item consensus row (REGIME/
// TREND/MOM/VOL, each dot+label) needs more room than a quick guess gave it. 240dp wrapped VOL
// letter-by-letter; 250dp+10dp gaps still wrapped it "VO/L". Paired with the tighter 8dp gap below.
private val POPUP_WIDTH = 272.dp
private val POPUP_GAP = 8.dp

// Item Library #03 ("Inline Fan-Out Capture") timing, ported from its Views/CSS source verbatim —
// 260ms, cubic-bezier(.34,1.56,.64,1) (Compose's own equivalent of Android's
// OvershootInterpolator(1.1f)). This app's own variant floats over the page rather than reflowing
// content below it (Item #03's own canonical behaviour) — Pieter's explicit call, "pop out over
// the page to the right" — so it's a `Popup`, not an in-place reveal; the growth mechanic itself
// (small+transparent at the trigger's own corner, animating to full size/opacity) is unchanged.
private const val FAN_OUT_DURATION_MS = 260
private val FAN_OUT_EASING = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private const val FAN_OUT_MIN_SCALE = 0.15f

/**
 * 2026-09-06 (Pieter's ask) — replaces the old always-visible "Summary"/"Recommendation" card
 * (itself a replacement for the original 9-button cascade, same day) with a small glyph
 * (`RecommendationGlyph`) that fans the same content out from its own corner, Item Library #03's
 * "doors grow from the +" mechanic ported to Compose. Collapsed, this is the entire footprint —
 * `EvenlySpacedColumn` (`WheelScreen.kt`) redistributes the vertical space the old permanent card
 * used to occupy as larger gaps between Home's other elements automatically, no separate spacing
 * change needed.
 *
 * Content itself is unchanged from the previous pass: deliberately the TECHNICAL counterpart to
 * Insights' AI-narrated recommendation — `signals.recommendation`'s deterministic seed
 * (`primary_pair`/`direction`/`confidence`, refreshed every hourly scan, no model call) plus that
 * pair's own Regime/Trend/Momentum/Volatility consensus (the same four the pair sheet's Overview
 * tab shows). Tapping the card opens that pair's own sheet and collapses the popup.
 */
@Composable
fun StatusStrip(
    state: WheelUiState,
    signals: Signals,
    colors: AtomColors,
    onCellClick: (SheetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rec = signals.recommendation
    val primaryPair = rec?.primaryPair
    val node = primaryPair?.let { pair -> state.nodes.firstOrNull { it.pair == pair } }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // `expanded` is the target state (drives the needle-adjacent tap toggle); `showPopup` is
    // actual presence — stays true through the close animation so it can play out, only unmounts
    // once `progress` has actually reached 0 (mirrors the old cascade's own
    // "close, then hide" sequencing, now for one Popup instead of nine staggered cards).
    var expanded by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    fun open() {
        expanded = true
        showPopup = true
        scope.launch { progress.animateTo(1f, tween(FAN_OUT_DURATION_MS, easing = FAN_OUT_EASING)) }
    }
    fun close() {
        expanded = false
        scope.launch {
            progress.animateTo(0f, tween(FAN_OUT_DURATION_MS, easing = FAN_OUT_EASING))
            showPopup = false
        }
    }

    Box(modifier = modifier) {
        RecommendationGlyph(
            direction = rec?.direction,
            colors = colors,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (expanded) close() else open()
            },
        )

        if (showPopup) {
            val offsetPx = with(density) { (RECOMMENDATION_GLYPH_SIZE + POPUP_GAP).roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(offsetPx, 0),
                onDismissRequest = { close() },
                properties = PopupProperties(focusable = false),
            ) {
                val p = progress.value
                val scale = FAN_OUT_MIN_SCALE + (1f - FAN_OUT_MIN_SCALE) * p
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = p
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                ) {
                    if (rec == null || primaryPair == null || node == null) {
                        EmptyRecommendationCard(colors)
                    } else {
                        RecommendationCard(
                            pair = primaryPair,
                            direction = rec.direction,
                            confidence = rec.confidence,
                            node = node,
                            colors = colors,
                            // A second, faintly staggered fade remapped off the same shared
                            // `progress` (Item #03's own per-item stagger, cheapened to one
                            // Animatable instead of N) — the consensus row settles a beat after
                            // the header rather than everything landing in lockstep.
                            consensusAlpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f),
                        ) {
                            close()
                            onCellClick(SheetTarget.Node(primaryPair))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecommendationCard(colors: AtomColors) {
    Box(
        modifier = Modifier
            .width(POPUP_WIDTH)
            .background(colors.controlSurface, CARD_SHAPE)
            .border(1.dp, colors.controlBorder, CARD_SHAPE)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            Text(text = "RECOMMENDATION", style = AtomType.Caption.copy(color = colors.textMuted, fontWeight = FontWeight.Normal))
            Text(
                text = "No qualifying setup this scan.",
                style = AtomType.Body.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    pair: String,
    direction: String?,
    confidence: String?,
    node: PairNode,
    colors: AtomColors,
    consensusAlpha: Float,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .width(POPUP_WIDTH)
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
        Row(modifier = Modifier, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "RECOMMENDATION", style = AtomType.Caption.copy(color = colors.textMuted, fontWeight = FontWeight.Normal))
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = pair, style = AtomType.Title.copy(color = colors.textPrimary))
            Text(text = directionWord(direction), style = AtomType.Caption.copy(color = directionColor(direction, colors)))
        }
        if (confidence != null) {
            Text(
                text = "$confidence confidence".uppercase(),
                style = AtomType.Caption.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .graphicsLayer { alpha = consensusAlpha },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConsensusItem("REGIME", regimeDotColor(node, colors), colors)
            ConsensusItem("TREND", trendDotColor(node, colors), colors)
            ConsensusItem("MOM", momentumDotColor(node, colors), colors)
            ConsensusItem("VOL", volatilityDotColor(node, colors), colors)
        }
    }
}

@Composable
private fun ConsensusItem(label: String, dotColor: Color, colors: AtomColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EvidenceDot(color = dotColor, modifier = Modifier.padding(end = 6.dp))
        // maxLines = 1 as a safety net, not the real fix — the real fix is POPUP_WIDTH actually
        // fitting the row (see its own comment); this just guarantees a future width regression
        // clips instead of wrapping "VOL" into "VO"/"L" the way two earlier width guesses did.
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
