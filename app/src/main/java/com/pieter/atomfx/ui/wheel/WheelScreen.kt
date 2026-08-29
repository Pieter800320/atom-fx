package com.pieter.atomfx.ui.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.components.HeaderBar
import com.pieter.atomfx.ui.components.Pill
import com.pieter.atomfx.ui.components.ScrollingPills
import com.pieter.atomfx.ui.components.StatusStrip
import com.pieter.atomfx.ui.components.TradeableNow
import com.pieter.atomfx.ui.sheets.BottomSheetHost
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType

private val RING_KEY_ROW_HEIGHT = 48.dp
private val RING_KEY_ROW_SPACING = 8.dp

/**
 * Design §5's landing screen: header (§9) → status strip (§10) → the wheel, sized to whatever's
 * left → Tradeable Now/Watch (§11). A tap on a ring/node/nucleus, a status-strip cell, or a pill
 * all open the same `BottomSheetHost` (Design §13.1, §14); the header's "events"/"brief" chips
 * open the Calendar/Recommendation panels the same way. `viewModel` is owned by `AtomFxApp`
 * (Phase 9) and shared with `MacroScreen` — one fetch, one `Signals` for both screens.
 */
@Composable
fun WheelScreen(
    viewModel: WheelViewModel,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    initialDeepLink: SheetTarget? = null,
) {
    val screenState by viewModel.screenState.collectAsState()
    val colors = AtomTheme.colors
    var activeSheet by remember { mutableStateOf(initialDeepLink) }

    LaunchedEffect(initialDeepLink) {
        if (initialDeepLink != null) activeSheet = initialDeepLink
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val loaded = screenState as? WheelScreenState.Loaded
        Column(modifier = Modifier.fillMaxSize()) {
            if (loaded != null) {
                HeaderBar(
                    state = loaded.state,
                    updated = loaded.signals.updated,
                    isFresh = loaded.freshness == Freshness.FRESH,
                    colors = colors,
                    onCalendarClick = { activeSheet = SheetTarget.Calendar },
                    onRecommendationClick = { activeSheet = SheetTarget.Recommendation },
                    recommendationHeadline = loaded.signals.recommendation?.headline,
                )
                StatusStrip(
                    state = loaded.state,
                    signals = loaded.signals,
                    colors = colors,
                    onCellClick = { target -> activeSheet = target },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val current = screenState) {
                    WheelScreenState.Loading -> CenteredMessage("LOADING…", colors)
                    WheelScreenState.Unavailable -> CenteredMessage("DATA UNAVAILABLE", colors)
                    is WheelScreenState.Loaded -> WheelArea(
                        loaded = current,
                        isDark = isDark,
                        colors = colors,
                        onTap = { target -> activeSheet = target.toSheetTarget() },
                        onLongPress = { pair -> activeSheet = SheetTarget.Chart(pair) },
                        onRingClick = { factor -> activeSheet = SheetTarget.Ring(factor) },
                    )
                }
            }

            if (loaded != null) {
                TradeableNow(
                    nodes = loaded.state.nodes,
                    signals = loaded.signals,
                    colors = colors,
                    onSelect = { target -> activeSheet = target },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        val sheet = activeSheet
        if (sheet != null && loaded != null) {
            BottomSheetHost(
                target = sheet,
                wheelState = loaded.state,
                signals = loaded.signals,
                colors = colors,
                onDismiss = { activeSheet = null },
                onNavigate = { activeSheet = it },
            )
        }
    }
}

private fun WheelTapTarget.toSheetTarget(): SheetTarget = when (this) {
    is WheelTapTarget.Nucleus -> SheetTarget.Nucleus
    is WheelTapTarget.Ring -> SheetTarget.Ring(factor)
    is WheelTapTarget.Node -> SheetTarget.Node(pair)
}

@Composable
private fun WheelArea(
    loaded: WheelScreenState.Loaded,
    isDark: Boolean,
    colors: AtomColors,
    onTap: (WheelTapTarget) -> Unit,
    onLongPress: (String) -> Unit,
    onRingClick: (Factor) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wheelSide = minOf(maxWidth, maxHeight - RING_KEY_ROW_HEIGHT - RING_KEY_ROW_SPACING)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (loaded.freshness == Freshness.STALE) {
                Text(
                    text = "DATA STALE",
                    style = AtomType.Caption.copy(color = colors.bear),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Box(modifier = Modifier.size(wheelSide)) {
                WheelCanvas(
                    state = loaded.state,
                    colors = colors,
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize(),
                    onTap = onTap,
                    onLongPress = onLongPress,
                )
            }
            Spacer(modifier = Modifier.height(RING_KEY_ROW_SPACING))
            RingKeyRow(rings = loaded.state.rings, colors = colors, onRingClick = onRingClick, modifier = Modifier.width(wheelSide))
        }
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AtomColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = AtomType.Body.copy(color = colors.textSecondary))
    }
}

/**
 * Design review: was a static legend row; the ring bands themselves are too thin a tap target
 * to rely on, so this doubles as six scrollable, tappable pills opening the same factor sheet
 * a ring tap would (ring tap still works too — this is the reliable route, not a replacement).
 */
@Composable
private fun RingKeyRow(rings: List<RingDescriptor>, colors: AtomColors, onRingClick: (Factor) -> Unit, modifier: Modifier = Modifier) {
    ScrollingPills(
        pills = rings.mapIndexed { i, ring ->
            Pill(
                text = "${i + 1} ${ring.factor.shortLabel}",
                tint = tintColor(ring.tint, colors),
                onClick = { onRingClick(ring.factor) },
            )
        },
        colors = colors,
        modifier = modifier.height(RING_KEY_ROW_HEIGHT),
    )
}
