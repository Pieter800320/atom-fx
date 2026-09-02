package com.pieter.atomfx.ui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // Top is owned by MainActivity's persistent gear bar (Functional Spec §3.1's
            // "gear icon on any tab") — consuming it here too would double the status-bar gap.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
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

private const val TICKER_VELOCITY_DP_PER_SEC = 42f // basicMarquee's own default is ~30dp/s

/**
 * Currency Flow, live under the dial in *both* modes — one squircle chip per currency ("AUD 70
 * +27"), auto-scrolling. Flow is relevant context whether you're looking at currencies or pairs
 * (it's *why* pairs are moving), so the ticker no longer hides in Pairs mode — same info, same
 * spot, always on. Replaces both the old on-wheel strength numbers and the six-factor Flow pill;
 * tapping it opens the full Currency Flow sheet. Settles in with a small spring bounce on load.
 *
 * A custom seamless loop, not `Modifier.basicMarquee()`: the sequence is always
 * [WheelGeometry.CCY_ORDER] (never re-sorted by strength) and every chip has a *fixed pixel
 * width* (numeric fields measured from worst-case digit metrics, never from any specific value),
 * so a D1/H4 toggle changes only the digits inside a chip — cycle width never changes, so the
 * continuously-running scroll animation is never retargeted or reset. Toggling timeframe cannot
 * make it jump (Pieter's requirement).
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

    val density = LocalDensity.current
    TickerMarquee(
        currencies = currencies,
        colors = colors,
        modifier = modifier
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * -12f * density.density
                scaleX = 0.92f + 0.08f * entrance.value
                scaleY = 0.92f + 0.08f * entrance.value
            }
            .clickable(onClick = onClick),
    )
}

private data class TickerRun(val text: String, val color: Color, val bold: Boolean)

// [actualContentWidthPx] is this instant's real content width (varies slightly with digit count).
// [chipWidthPx] is the *fixed*, worst-case width the chip's squircle background is drawn at —
// derived from this currency's own code width (constant, codes don't change with timeframe) plus
// fixed numeric slots (worst-case digit/sign metrics, never a specific value's measured width).
// Content is centred within that fixed width, so short/long numbers just sit centred — and the
// chip's width, and therefore every later chip's position, can never change between D1 and H4.
private data class TickerChip(val runs: List<TickerRun>, val actualContentWidthPx: Float, val chipWidthPx: Float)

private val TICKER_CHIP_PADDING_DP = 10.dp
private val TICKER_CHIP_GAP_DP = 8.dp

@Composable
private fun TickerMarquee(currencies: List<CurrencySeg>, colors: AtomColors, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val textSizePx = with(density) { 12.sp.toPx() }
    val chipPaddingPx = with(density) { TICKER_CHIP_PADDING_DP.toPx() }
    val chipGapPx = with(density) { TICKER_CHIP_GAP_DP.toPx() }
    val borderWidthPx = with(density) { 1.dp.toPx() }

    // Stable order (never re-sorted by strength) — a D1/H4 toggle changes each currency's own
    // numbers, never its position in the sequence.
    val byCode = remember(currencies) { currencies.associateBy { it.code } }
    val ordered = remember(byCode) { WheelGeometry.CCY_ORDER.mapNotNull { byCode[it] } }

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            fontFeatureSettings = "tnum" // tabular figures (Design §3) — digits 0-9 equal width
            textSize = textSizePx
        }
    }
    val regularTypeface = remember { android.graphics.Typeface.DEFAULT }
    val boldTypeface = remember { android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
    val spaceWidthPx = remember(textSizePx) {
        textPaint.typeface = regularTypeface
        textPaint.measureText(" ")
    }
    // Fixed slot widths, derived once from digit/sign metrics — never from any specific
    // currency's actual value, so they can't vary between D1 and H4.
    val valueSlotPx = remember(textSizePx) {
        textPaint.typeface = regularTypeface
        (0..9).maxOf { textPaint.measureText(it.toString()) } * 3 // strength is always 0..100
    }
    val deltaSlotPx = remember(textSizePx) {
        textPaint.typeface = regularTypeface
        val digitWidth = (0..9).maxOf { textPaint.measureText(it.toString()) }
        val signWidth = maxOf(textPaint.measureText("+"), textPaint.measureText("-"))
        signWidth + digitWidth * 3 // sign + up to 3 digits
    }

    val chips = remember(ordered, colors, valueSlotPx, deltaSlotPx, spaceWidthPx, chipPaddingPx) {
        ordered.map { c ->
            textPaint.typeface = boldTypeface
            val codeWidthPx = textPaint.measureText(c.code)
            textPaint.typeface = regularTypeface
            val valueText = c.strength.toString()
            val deltaColor = when {
                c.delta > 0 -> colors.bull
                c.delta < 0 -> colors.bear
                else -> colors.textMuted
            }
            val deltaText = "${if (c.delta >= 0) "+" else "-"}${kotlin.math.abs(c.delta.toInt())}"
            val actualContentWidthPx = codeWidthPx + spaceWidthPx + textPaint.measureText(valueText) + spaceWidthPx + textPaint.measureText(deltaText)
            val maxContentWidthPx = codeWidthPx + spaceWidthPx + valueSlotPx + spaceWidthPx + deltaSlotPx
            TickerChip(
                runs = listOf(
                    TickerRun(c.code, colors.textPrimary, true),
                    TickerRun(" ", colors.textPrimary, false),
                    TickerRun(valueText, colors.textSecondary, false),
                    TickerRun(" ", colors.textPrimary, false),
                    TickerRun(deltaText, deltaColor, false),
                ),
                actualContentWidthPx = actualContentWidthPx,
                chipWidthPx = maxContentWidthPx + chipPaddingPx * 2,
            )
        }
    }

    val cycleWidthPx = remember(chips, chipGapPx) { chips.sumOf { (it.chipWidthPx + chipGapPx).toDouble() }.toFloat() }

    // A raw, frame-driven accumulator — not `animateFloat`/`InfiniteTransition`, whose target
    // (derived from cycleWidthPx) gets re-evaluated, and can be re-diffed and retargeted, on every
    // recomposition the changing `currencies` param causes. This offset is anchored by
    // `LaunchedEffect(Unit)` alone: it starts once, is never re-keyed by data, and never resets —
    // toggling D1/H4 cannot touch it. The modulo wrap below reads whatever cycleWidthPx currently
    // is at draw time, but the underlying accumulation itself is fully decoupled from the data.
    var rawOffsetPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameNanos
            rawOffsetPx += TICKER_VELOCITY_DP_PER_SEC * density.density * deltaSeconds
        }
    }

    val chipBgPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.FILL } }
    val chipBorderPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.STROKE } }

    Canvas(modifier = modifier) {
        if (cycleWidthPx <= 0f || chips.isEmpty()) return@Canvas
        val chipHeightPx = size.height * 0.86f
        val chipTop = (size.height - chipHeightPx) / 2f
        val cornerRadiusPx = chipHeightPx * 0.42f // a soft squircle, not a full capsule
        val baselineY = size.height / 2f + textSizePx / 3f
        val nativeCanvas = drawContext.canvas.nativeCanvas
        chipBgPaint.color = colors.surfaceRaised.toArgb()
        chipBorderPaint.color = colors.hairline.toArgb()
        chipBorderPaint.strokeWidth = borderWidthPx

        var cycleStartX = -(rawOffsetPx % cycleWidthPx) - cycleWidthPx
        while (cycleStartX < size.width) {
            var x = cycleStartX
            chips.forEach { chip ->
                if (x + chip.chipWidthPx > 0f && x < size.width) {
                    val rect = android.graphics.RectF(x, chipTop, x + chip.chipWidthPx, chipTop + chipHeightPx)
                    nativeCanvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, chipBgPaint)
                    nativeCanvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, chipBorderPaint)

                    var tx = x + (chip.chipWidthPx - chip.actualContentWidthPx) / 2f // centred in the fixed chip
                    chip.runs.forEach { r ->
                        textPaint.color = r.color.toArgb()
                        textPaint.typeface = if (r.bold) boldTypeface else regularTypeface
                        nativeCanvas.drawText(r.text, tx, baselineY, textPaint)
                        tx += textPaint.measureText(r.text)
                    }
                }
                x += chip.chipWidthPx + chipGapPx
            }
            cycleStartX += cycleWidthPx
        }
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AtomColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = AtomType.Body.copy(color = colors.textSecondary))
    }
}
