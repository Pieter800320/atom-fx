package com.pieter.atomfx.ui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.components.HeaderBar
import com.pieter.atomfx.ui.components.StatusStrip
import com.pieter.atomfx.ui.components.TradeableNow
import com.pieter.atomfx.ui.sheets.BottomSheetHost
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType

private val TICKER_HEIGHT = 30.dp
private val TICKER_SPACING = 8.dp

/**
 * Design §5's landing screen, Wheel v2: header (incl. freshness/stale, Design §8-9) → status
 * strip → the radial dial (with the Currencies/Pairs toggle living on the dial's own bottom-right
 * corner, and the Currency Flow ticker beneath it when in Currencies mode) → Tradeable Now.
 * §17: no scroll — the wheel is a centred square sized to whatever fits.
 *
 * The six-factor pill row is gone: Regime/Breadth duplicate the Status Strip's own tap targets
 * (StatusStrip.kt routes REGIME/BREADTH cells to the same sheets), and Momentum/Structure/Entry
 * only ever had meaning for one pair — they're reached via that pair's own sheet tabs, not a
 * wheel-level row that had to arbitrarily pick `topPair()`.
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
    var mode by remember { mutableStateOf(WheelMode.PAIRS) }
    var timeframe by remember { mutableStateOf(Timeframe.H4) }
    val haptics = LocalHapticFeedback.current

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
                        mode = mode,
                        timeframe = timeframe,
                        onModeChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            mode = it
                        },
                        onTimeframeChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            timeframe = it
                        },
                        onTap = { target ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeSheet = target.toSheetTarget()
                        },
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
    is WheelTapTarget.Currency -> SheetTarget.Currency(code)
    is WheelTapTarget.CrossAsset -> SheetTarget.CrossAsset(id)
    is WheelTapTarget.ModeToggle -> error("ModeToggle is handled by WheelArea before reaching toSheetTarget()")
    is WheelTapTarget.TimeframeToggle -> error("TimeframeToggle is handled by WheelArea before reaching toSheetTarget()")
}

@Composable
private fun WheelArea(
    loaded: WheelScreenState.Loaded,
    isDark: Boolean,
    colors: AtomColors,
    mode: WheelMode,
    timeframe: Timeframe,
    onModeChange: (WheelMode) -> Unit,
    onTimeframeChange: (Timeframe) -> Unit,
    onTap: (WheelTapTarget) -> Unit,
    onLongPress: (String) -> Unit,
    onRingClick: (Factor) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Design §17: the landing screen never scrolls — the wheel is a centred square sized to
        // whatever fits (min(width, height − chrome)); it shrinks to fit, the layout never does.
        // The Currency Flow ticker is always on (both modes) so this reservation is constant too.
        val wheelSide = minOf(maxWidth, maxHeight - TICKER_HEIGHT - TICKER_SPACING)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(wheelSide)) {
                WheelCanvas(
                    state = loaded.state,
                    colors = colors,
                    isDark = isDark,
                    mode = mode,
                    timeframe = timeframe,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { target ->
                        when (target) {
                            is WheelTapTarget.ModeToggle -> onModeChange(target.mode)
                            is WheelTapTarget.TimeframeToggle -> onTimeframeChange(target.timeframe)
                            else -> onTap(target)
                        }
                    },
                    onLongPress = onLongPress,
                )
            }
            Spacer(modifier = Modifier.height(TICKER_SPACING))
            CurrencyFlowTicker(
                currencies = if (timeframe == Timeframe.D1) loaded.state.currenciesD1 else loaded.state.currencies,
                colors = colors,
                onClick = { onRingClick(Factor.FLOW) },
                modifier = Modifier.height(TICKER_HEIGHT).fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * Currency Flow, live under the dial in *both* modes — "EUR 83 +22  •  JPY 59 +6  •  …", strongest
 * first, auto-scrolling. Flow is relevant context whether you're looking at currencies or pairs
 * (it's *why* pairs are moving), so the ticker no longer hides in Pairs mode — same info, same
 * spot, always on, exactly the "always visible" component Pieter asked for. Replaces both the old
 * on-wheel strength numbers and the six-factor Flow pill; tapping it opens the full Currency Flow
 * sheet. Settles in with a small spring bounce once on first load, not a flat fade.
 */
@Composable
private fun CurrencyFlowTicker(
    currencies: List<CurrencySeg>,
    colors: AtomColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(modifier = modifier) {
        val density = LocalDensity.current
        val text = remember(currencies, colors) {
            buildAnnotatedString {
                val sorted = currencies.sortedByDescending { it.strength }
                sorted.forEachIndexed { i, c ->
                    withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold)) { append(c.code) }
                    append(" ")
                    withStyle(SpanStyle(color = colors.textSecondary)) { append(c.strength.toString()) }
                    append("  ")
                    val sign = if (c.delta > 0) "+" else ""
                    val deltaColor = when {
                        c.delta > 0 -> colors.bull
                        c.delta < 0 -> colors.bear
                        else -> colors.textMuted
                    }
                    withStyle(SpanStyle(color = deltaColor)) { append("$sign${c.delta.toInt()}") }
                    if (i != sorted.lastIndex) append("      •      ")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * -12f * density.density
                    scaleX = 0.92f + 0.08f * entrance.value
                    scaleY = 0.92f + 0.08f * entrance.value
                }
                .clip(RoundedCornerShape(999.dp))
                .background(colors.surface)
                .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = AtomType.Caption,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 500),
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AtomColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = AtomType.Body.copy(color = colors.textSecondary))
    }
}
