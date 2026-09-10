package com.pieter.atomfx.ui.watchlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.WatchlistItem
import com.pieter.atomfx.data.WatchlistStore
import com.pieter.atomfx.data.model.PairBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.settings.BB_REVERSAL_HOW_TO_READ
import com.pieter.atomfx.ui.settings.BB_REVERSAL_WHY_IT_MATTERS
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash

private val WL_CARD_SHAPE = RoundedCornerShape(14.dp)
private val WL_BUTTON_SHAPE = RoundedCornerShape(10.dp)

// Item Library #05 — same slide-in side panel recipe SettingsScreen.kt's own
// PANEL_WIDTH_FRACTION/entrance Animatable use, duplicated rather than shared (this codebase's
// established house style, e.g. StatusStrip.kt's directionColor doc comment) so Watchlist reads
// as a sibling panel to Settings, not nested inside it, matching Pieter's own nav placement call.
private const val PANEL_WIDTH_FRACTION = 0.82f

/**
 * Signals Roadmap §5 (2026-09-09, Pieter's own design) — a place to track a pair after a BB
 * touch, since whether a reversal is actually developing usually takes a few D1 sessions to
 * show, not one scan. Reached from a new header icon (bookmark, next to calendar/gear) —
 * Pieter's own call, a sibling side panel to Settings, not nested inside it and not a bottom
 * sheet.
 *
 * Deliberately no confirmation scoring of its own — each card shows the same live data the
 * pair sheet does (ADX, pill alignment, Reset Score, band-width trend) plus the same reversal
 * checklist Settings' Library entry carries, collapsed behind its own tap-to-reveal so the
 * card stays scannable. Judging whether a touch is turning into a real reversal stays Pieter's
 * own call — this screen's job is just to keep the relevant data in one place while he watches.
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
                .background(Color.Black.copy(alpha = 0.6f * entrance.value))
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
            WatchlistCard(
                item = item,
                block = signals.pairs[item.pair],
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
    colors: AtomColors,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(colors.surfaceRaised, WL_CARD_SHAPE)
            .pressWash(WL_CARD_SHAPE) { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                // 2026-09-10 (Pieter's ask) — pair name + "Added…" inline on one line, freeing
                // the touching badge to move onto its own line below instead of fighting Remove
                // for horizontal room in the same row (see this file's earlier fix for what that
                // squeeze did).
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = item.pair, style = AtomType.Body.copy(color = colors.textPrimary))
                    Text(text = timeAgo(item.addedAt), style = AtomType.Caption.copy(color = colors.textMuted))
                }
                val touching = block?.bbD1?.touching
                if (touching == "upper" || touching == "lower") {
                    val dir = if (touching == "upper") "bear" else "bull"
                    Text(
                        text = if (touching == "upper") "STILL TOUCHING UPPER" else "STILL TOUCHING LOWER",
                        style = AtomType.Caption.copy(color = directionColor(dir, colors)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
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

        // 2026-09-10 (Pieter's ask) — each cell now takes an equal Modifier.weight(1f) share of
        // the row instead of packing left with a fixed gap, so the metrics spread across the
        // card's full width (there was dead space on the right at the old fixed-spacing size).
        // Values read at AtomType.Caption — the same size as their own label above them, per
        // Pieter's ask — not AtomType.Body; still colour-distinguished (textPrimary vs textMuted).
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            MetricCell("ADX", block?.adx?.let { "%.1f".format(java.util.Locale.US, it) } ?: "—", colors, Modifier.weight(1f))
            MetricCell("RESET", block?.resetScore?.toString() ?: "—", colors, Modifier.weight(1f))
            MetricCell("BANDS", block?.bbD1?.widthTrend?.replaceFirstChar { it.uppercase() } ?: "—", colors, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            MetricCell("D1", pillWord(block?.pills?.d1), colors, Modifier.weight(1f))
            MetricCell("H4", pillWord(block?.pills?.h4), colors, Modifier.weight(1f))
            MetricCell("H1", pillWord(block?.pills?.h1), colors, Modifier.weight(1f))
        }

        // 2026-09-10 (Pieter's ask) — a real button (ControlButtonRow's own controlSurface/
        // controlBorder/AtomType.Button recipe), not a plain label+"+"/"−" row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(colors.controlSurface, WL_BUTTON_SHAPE)
                .border(1.dp, colors.controlBorder, WL_BUTTON_SHAPE)
                .pressWash(WL_BUTTON_SHAPE) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = !expanded
                }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = "WHAT TO LOOK FOR", style = AtomType.Button.copy(color = colors.textPrimary))
        }
        if (expanded) {
            Text(
                text = BB_REVERSAL_HOW_TO_READ,
                style = AtomType.Caption.copy(color = colors.textPrimary),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = BB_REVERSAL_WHY_IT_MATTERS,
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, colors: AtomColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        // 2026-09-10 (Pieter's ask) — same size as the label above it (AtomType.Caption, not
        // Body); still reads as "the value" via colour (textPrimary vs the label's textMuted).
        Text(text = value, style = AtomType.Caption.copy(color = colors.textPrimary))
    }
}

private fun pillWord(pill: String?): String = when (pill) {
    "bull_strong" -> "Strong+"
    "bull" -> "Bull"
    "bear_strong" -> "Strong−"
    "bear" -> "Bear"
    "neutral" -> "Neutral"
    else -> "—"
}

// Same "small local copy, not shared" house style StatusStrip.kt's own directionColor uses.
private fun directionColor(direction: String?, colors: AtomColors): Color = when (direction) {
    "bull" -> colors.bull
    "bear" -> colors.bear
    else -> colors.textSecondary
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
