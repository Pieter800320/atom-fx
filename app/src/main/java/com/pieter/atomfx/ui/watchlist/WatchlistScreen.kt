package com.pieter.atomfx.ui.watchlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.WatchlistItem
import com.pieter.atomfx.data.WatchlistStore
import com.pieter.atomfx.data.model.PairBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.domain.WheelMapper
import com.pieter.atomfx.ui.components.ConsensusDotRow
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import com.pieter.atomfx.ui.wheel.Direction
import com.pieter.atomfx.ui.wheel.PairNode

private val WL_CARD_SHAPE = RoundedCornerShape(14.dp)
private val WL_PILL_SHAPE = RoundedCornerShape(8.dp)

// Item Library #05 — same slide-in side panel recipe SettingsScreen.kt's own
// PANEL_WIDTH_FRACTION/entrance Animatable use, duplicated rather than shared (this codebase's
// established house style, e.g. StatusStrip.kt's directionColor doc comment) so Watchlist reads
// as a sibling panel to Settings, not nested inside it, matching Pieter's own nav placement call.
private const val PANEL_WIDTH_FRACTION = 0.82f

/**
 * Signals Roadmap §5 (2026-09-09, Pieter's own design) — a place to track a pair over time.
 * Reached from a new header icon (bookmark, next to calendar/gear) — Pieter's own call, a
 * sibling side panel to Settings, not nested inside it and not a bottom sheet.
 *
 * 2026-09-16 (Pieter's ask) — retired the original BB-touch-specific card (touching badge,
 * ADX/Reset/Bands metrics, the "what to look for" reversal checklist) in favour of a general
 * pair-state card: each card now shows the same Regime/Trend/Momentum/Volatility/Structure
 * consensus and Continuation Score the wheel and pair sheet already read, not just BB-reversal
 * bookkeeping — a watched pair is no longer assumed to be mid-reversal-watch. Judging whether a
 * setup is actually developing stays Pieter's own call — this screen's job is just to keep the
 * relevant data in one place while he watches.
 */
@Composable
fun WatchlistScreen(signals: Signals, colors: AtomColors, onPairClick: (String) -> Unit, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220)) }
    val dismiss: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClose()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val panelWidthPx = with(density) { (maxWidth * PANEL_WIDTH_FRACTION).toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = 0.6f * entrance.value))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = dismiss,
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(PANEL_WIDTH_FRACTION)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .graphicsLayer { translationX = (1f - entrance.value) * panelWidthPx }
                .background(colors.ground)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                WatchlistContent(signals, colors, onPairClick, onClose)
            }
        }
    }
}

@Composable
private fun WatchlistContent(
    signals: Signals,
    colors: AtomColors,
    onPairClick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { WatchlistStore(context.applicationContext) }
    val items by store.state.collectAsState()
    val haptics = LocalHapticFeedback.current

    // Same PairNode/consensus reads the wheel and StatusStrip use (Architecture §8.3: never
    // re-derive a trading number here) — built from signals rather than threaded in from
    // MainActivity, since this screen only ever needs it for whichever pairs are watched.
    val wheelState = remember(signals) { WheelMapper.map(signals) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "WATCHLIST", style = AtomType.Title.copy(color = colors.textPrimary))
            Text(
                text = "Close",
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.pressWash {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClose()
                },
            )
        }

        if (items.isEmpty()) {
            Text(
                text = "Watchlist empty",
                style = AtomType.Body.copy(color = colors.textMuted),
            )
            return@Column
        }

        items.sortedByDescending { it.addedAt }.forEach { item ->
            val node = wheelState.nodes.firstOrNull { it.pair == item.pair }
            val rankedDirection = signals.ranked?.top.orEmpty().firstOrNull { it.pair == item.pair }?.direction
            val isRecommended = signals.ranked?.top.orEmpty().any { it.pair == item.pair }
            WatchlistCard(
                item = item,
                block = signals.pairs[item.pair],
                node = node,
                isRecommended = isRecommended,
                rankedDirection = rankedDirection,
                potentialState = signals.potential[item.pair]?.state,
                structureEvent = signals.pairs[item.pair]?.structure?.h4?.event,
                structureDirection = signals.pairs[item.pair]?.structure?.h4?.direction,
                colors = colors,
                onOpen = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPairClick(item.pair)
                },
                onRemove = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    store.remove(item.pair)
                },
            )
        }
    }
}

@Composable
private fun WatchlistCard(
    item: WatchlistItem,
    block: PairBlock?,
    node: PairNode?,
    isRecommended: Boolean,
    rankedDirection: String?,
    potentialState: String?,
    structureEvent: String?,
    structureDirection: String?,
    colors: AtomColors,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val headerDirection = when (node?.direction) {
        Direction.BULL -> "bull"
        Direction.BEAR -> "bear"
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(colors.surfaceRaised, WL_CARD_SHAPE)
            .pressWash(WL_CARD_SHAPE) { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 2026-09-17 (3rd, Pieter's ask) — "Added…" moved off the pair-name line onto its own
        // line beneath it; Remove stays the second child of this SpaceBetween row (Row defaults
        // to top-aligned children, so it lines up with the pair name, not the Added line below).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = item.pair, style = AtomType.Body.copy(color = colors.textPrimary))
                    Text(
                        text = directionWord(headerDirection),
                        style = AtomType.Caption.copy(color = directionColor(headerDirection, colors)),
                    )
                }
                Text(
                    text = timeAgo(item.addedAt),
                    style = AtomType.Caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (isRecommended) {
                    // Filled, tinted pill instead of a second LONG/SHORT word (2026-09-17, 3rd,
                    // Pieter's ask) — the header bias above already states direction in words;
                    // the pill's own tint carries it here, the star just flags which watched
                    // pair currently holds the live `signals.ranked` recommendation.
                    Row(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .background(directionSoft(rankedDirection, colors), WL_PILL_SHAPE)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "★ RECOMMENDED",
                            style = AtomType.Caption.copy(color = directionColor(rankedDirection, colors)),
                        )
                    }
                }
            }
            Text(
                text = "Remove",
                style = AtomType.Caption.copy(color = colors.textMuted),
                maxLines = 1,
                modifier = Modifier.pressWash {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRemove()
                },
            )
        }

        if (node != null) {
            val setupText = if (potentialState != null) {
                "Setup ${node.cont} · $potentialState"
            } else {
                "Setup ${node.cont}"
            }
            Text(
                text = setupText,
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 10.dp),
            )
            ConsensusDotRow(
                node = node,
                structureEvent = structureEvent,
                structureDirection = structureDirection,
                colors = colors,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            MetricCell("D1", pillAbbrev(block?.pills?.d1), colors, Modifier.weight(1f), pillColor(block?.pills?.d1, colors))
            MetricCell("H4", pillAbbrev(block?.pills?.h4), colors, Modifier.weight(1f), pillColor(block?.pills?.h4, colors))
            MetricCell("H1", pillAbbrev(block?.pills?.h1), colors, Modifier.weight(1f), pillColor(block?.pills?.h1, colors))
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, colors: AtomColors, modifier: Modifier = Modifier, valueColor: Color = colors.textPrimary) {
    Column(modifier = modifier) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        // 2026-09-10 (Pieter's ask) — same size as the label above it (AtomType.Caption, not
        // Body); still reads as "the value" via colour (textPrimary vs the label's textMuted).
        Text(text = value, style = AtomType.Caption.copy(color = valueColor))
    }
}

// Same SB/B/N/S/SS abbreviations + bull/bear/neutral colouring as TfAlignmentStrip.kt's own
// pillAbbrev/pillColor — small local copy, not shared, same house style as this file's own
// directionColor doc comment above.
private fun pillAbbrev(pill: String?): String = when (pill) {
    "bull_strong" -> "SB"
    "bull" -> "B"
    "neutral" -> "N"
    "bear" -> "S"
    "bear_strong" -> "SS"
    else -> "—"
}

private fun pillColor(pill: String?, colors: AtomColors): Color = when (pill) {
    "bull_strong", "bull" -> colors.bull
    "bear", "bear_strong" -> colors.bear
    else -> colors.neutral
}

// Same directionWord/directionColor convention as StatusStrip.kt's own copies — small local
// copy, not shared, same house style as StatusStrip.kt's own directionColor doc comment.
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

// The RECOMMENDED pill's own low-alpha fill — reuses the existing bullSoft/bearSoft tokens
// rather than a new alpha value. `else` (rankedDirection null) is effectively unreachable in
// practice: rank.py never puts a direction-less entry in `ranked.top`, so this only fires
// alongside `isRecommended == true`, where a real bull/bear direction is always present.
private fun directionSoft(direction: String?, colors: AtomColors): Color = when (direction) {
    "bear" -> colors.bearSoft
    else -> colors.bullSoft
}

private fun timeAgo(atMillis: Long): String {
    val diffMs = System.currentTimeMillis() - atMillis
    val hours = diffMs / (60 * 60 * 1000)
    return when {
        hours < 1 -> "Added just now"
        hours < 24 -> "Added ${hours}h ago"
        else -> "Added ${hours / 24}d ago"
    }
}
