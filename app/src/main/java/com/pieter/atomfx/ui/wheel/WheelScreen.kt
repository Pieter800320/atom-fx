package com.pieter.atomfx.ui.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pieter.atomfx.ui.components.StatusStrip
import com.pieter.atomfx.ui.reading.ReadingTarget
import com.pieter.atomfx.ui.sheets.BottomSheetHost
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CARD_TOP_SPACING = 16.dp

/**
 * Design §5's landing screen, Wheel v2: header (incl. freshness/stale, Design §8-9) → status
 * strip → the radial dial → the Currency Strength Meter (always visible, both wheel modes) →
 * the D1/H4/H1 timeframe buttons. §17: no scroll — the wheel is a centred square sized to
 * whatever fits.
 *
 * 2026-09-04, "get rid of the ticker entirely" — the Strength/Potential ticker (and, before it,
 * the separate Tradeable Now/Watch card) is gone for good. Currency strength now lives in its
 * own permanent [CsmBarStrip] below the wheel, visible at all times regardless of wheel mode —
 * this is a strictly stronger reading of the §20 acceptance test ("...with no sheet open") than
 * the old toggle ever gave: strength and whatever the wheel itself is showing are both on screen
 * simultaneously, nothing to switch between first. D1/H4/H1 moved off the wheel's own top
 * corners into [TimeframeButtons], its own row below the CSM strip, styled exactly like
 * StatusStrip's Summary button (same `CARD_SHAPE`, same padding recipe, so "same height" falls
 * out of reusing the recipe rather than a copied dp constant).
 *
 * The wheel's 4 corner wings are direct mode-selectors — Potential/Momentum(D1)/ADX/Reset Score,
 * all reading the same 12-pair ring (`WheelCanvas.kt`/`WheelGeometry.kt`) — CSM/
 * currency strength has no wheel mode of its own any more, only the always-visible strip below.
 * One thing still deliberately unfinished: the CSM bar strip is plain bars, not yet the
 * "stunning, echoes the wheel" visual pass — the layout/space-budget step (§17) is done and
 * verified on-device; only the visual polish pass on that one component remains.
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
    onOpenReading: (ReadingTarget) -> Unit = {},
) {
    val screenState by viewModel.screenState.collectAsState()
    val colors = AtomTheme.colors
    var activeSheet by remember { mutableStateOf(initialDeepLink) }
    // 2026-09-04 — POTENTIAL by default, the wheel's flagship mode (matches the thumb-zone
    // bottom-left wing). The other 3 modes (Momentum/ADX/Reset) are a tap away.
    var mode by remember { mutableStateOf(WheelMode.POTENTIAL) }
    var timeframe by remember { mutableStateOf(Timeframe.H4) }
    val haptics = LocalHapticFeedback.current
    val hapticScope = rememberCoroutineScope()

    LaunchedEffect(initialDeepLink) {
        if (initialDeepLink != null) activeSheet = initialDeepLink
    }

    // Pieter, 2026-09-03 follow-up — BoxWithConstraints (not a plain Box) so the true viewport
    // height (maxHeight, below) is captured OUTSIDE the verticalScroll boundary further down —
    // inside a scrollable Column, incoming height constraints are unbounded (Constraints.Infinity),
    // same reason the wheel's own aspectRatio sizing had to be resolved at this level too.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            // Top is owned by MainActivity's persistent gear bar (Functional Spec §3.1's
            // "gear icon on any tab") — consuming it here too would double the status-bar gap.
            // Pieter, 2026-09-03 — Bottom dropped too: it was reserving safe-drawing bottom inset
            // a SECOND time, above AtomBottomNav's own Material3 NavigationBar, which already
            // applies its own bottom system-bar padding. That double reservation was exactly the
            // "dead area right above the bottom nav" — the scrollable content now runs flush to
            // where AtomBottomNav (correctly inset on its own) begins.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val loaded = screenState as? WheelScreenState.Loaded
        val viewportHeight = maxHeight
        // Pieter, 2026-09-03 — deliberate, flagged supersession of Design §17 ("the landing
        // screen never scrolls") for the Summary cascade specifically: he doesn't want the wheel
        // shrinking to make room for it, so the whole Column scrolls instead, wheel included. The
        // wheel's own square (below) is sized purely from width via aspectRatio(1f) — not
        // min(width, height-chrome) any more — so it stays the same size whether the cascade is
        // open or not; §17's original "wheel shrinks to fit" behavior is gone by construction,
        // not just unused. Same fallback now covers the CSM strip/TF row skeleton if it ever
        // doesn't fit — the goal is for that never to trigger in normal use, verified on-device.
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Pieter, 2026-09-03 follow-up — "distribute the leftover space evenly": every direct
            // child here gets one shared, evenly-sized gap between it and the next, computed from
            // real leftover space (EvenlySpacedColumn below) rather than fixed padding values.
            // The header (AtomGearBar) stays a fixed, small gap — it's a sibling of the pager in
            // MainActivity, shared across all three tabs, not something this screen should
            // stretch differently from Macro/Insights.
            EvenlySpacedColumn(
                modifier = Modifier.fillMaxWidth(),
                targetHeight = viewportHeight,
                minGap = CARD_TOP_SPACING,
            ) {
                if (loaded != null) {
                    StatusStrip(
                        state = loaded.state,
                        signals = loaded.signals,
                        colors = colors,
                        onCellClick = { target -> activeSheet = target },
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    when (val current = screenState) {
                        WheelScreenState.Loading -> CenteredMessage("LOADING…", colors)
                        WheelScreenState.Unavailable -> CenteredMessage("DATA UNAVAILABLE", colors)
                        is WheelScreenState.Loaded -> WheelArea(
                            loaded = current,
                            isDark = isDark,
                            colors = colors,
                            mode = mode,
                            onModeChange = {
                                // Aesthetics pass, 2026-09-03 — Pieter: turn the trapezoid presses'
                                // haptics way up. LongPress (not TextHandleMove, used for every
                                // other plain-selection tap on the dial) is the strongest standard
                                // feedback Compose exposes — these corner buttons drive a real
                                // mode switch, not just a selection. Guarded like the bottom
                                // TimeframeButtons/CsmBarStrip: re-tapping the already-active wing
                                // is a no-op, no haptic, no re-trigger of the fill animation.
                                if (it != mode) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    mode = it
                                }
                            },
                            onTap = { target ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeSheet = target.toSheetTarget()
                            },
                            onLongPress = { pair -> activeSheet = SheetTarget.Chart(pair) },
                        )
                    }
                }

                // 2026-09-04 — the CSM strip replaces the old ticker slot: always-on currency
                // strength, independent of whichever mode the wheel itself is in. Tapping a bar
                // opens that currency's own sheet directly (no whole-strip fallback target — the
                // bars fill essentially the whole row width, unlike the old ticker's chip gaps).
                if (loaded != null) {
                    CsmBarStrip(
                        currencies = loaded.state.currenciesFor(timeframe),
                        colors = colors,
                        onCurrencyClick = { code ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeSheet = SheetTarget.Currency(code)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }

                // D1/H4/H1, relocated off the wheel (Pieter, 2026-09-04: "the 3 TF buttons make
                // sense somewhere else") — drives the SAME `timeframe` state the wheel's own
                // (still-present, for now) corner toggle does, exactly how the old bottom
                // Strength/Potential buttons duplicated the wheel's old single mode-toggle corner
                // button rather than requiring its removal first.
                if (loaded != null) {
                    TimeframeButtons(
                        timeframe = timeframe,
                        onChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            timeframe = it
                            hapticScope.launch {
                                delay(150)
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }

                // Zero-height phantom child — gives EvenlySpacedColumn a trailing gap slot (TF
                // buttons → the bottom of the scrollable area, i.e. where AtomBottomNav begins).
                // Nothing is drawn; only the GAP before it (sized the same as the others) matters.
                Spacer(modifier = Modifier.fillMaxWidth().height(0.dp))
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
                onOpenReading = onOpenReading,
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
}

/**
 * 2026-09-04 — now a thin pass-through to [WheelCanvas]: the ticker (and the reserved-space math
 * it needed below the wheel) is gone, so there's nothing left for this composable to lay out
 * beyond the dial itself. Kept as its own function rather than inlined at the call site, to keep
 * the `Loading`/`Unavailable`/`Loaded` `when` block above unchanged.
 */
@Composable
private fun WheelArea(
    loaded: WheelScreenState.Loaded,
    isDark: Boolean,
    colors: AtomColors,
    mode: WheelMode,
    onModeChange: (WheelMode) -> Unit,
    onTap: (WheelTapTarget) -> Unit,
    onLongPress: (String) -> Unit,
) {
    WheelCanvas(
        state = loaded.state,
        colors = colors,
        isDark = isDark,
        mode = mode,
        modifier = Modifier.fillMaxSize(),
        onTap = { target ->
            when (target) {
                is WheelTapTarget.ModeToggle -> onModeChange(target.mode)
                else -> onTap(target)
            }
        },
        onLongPress = onLongPress,
    )
}

private val CSM_BAR_AREA_HEIGHT = 52.dp
private val CSM_LABEL_SPACING = 4.dp
private val CSM_BAR_GAP = 4.dp
private val CSM_BAR_CORNER = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)

/**
 * The Currency Strength Meter, always visible below the wheel (2026-09-04 — see the doc comment
 * on [WheelScreen]). Plain bars for now — deliberately unstyled beyond basic legibility, since
 * this step is only proving the vertical space budget; the "stunning, echoes the wheel" visual
 * pass is the next, separate step once this fits on-device.
 */
@Composable
private fun CsmBarStrip(
    currencies: List<CurrencySeg>,
    colors: AtomColors,
    onCurrencyClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // Stable order (never re-sorted by strength) — same WheelGeometry.CCY_ORDER the wheel's own
    // currency ring and the old ticker both used.
    val ordered = remember(currencies) {
        val byCode = currencies.associateBy { it.code }
        WheelGeometry.CCY_ORDER.mapNotNull { byCode[it] }
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(CSM_BAR_AREA_HEIGHT),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            ordered.forEach { c ->
                val barColor = if (c.tint == Tint.BULL) colors.bull else colors.bear
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = CSM_BAR_GAP)
                        // Pieter, 2026-09-04 — a real track/plate now (colors.surfaceRaised), same
                        // structure the wheel's own wedges and the cross-asset cells already use
                        // (plate first, then a colour wash on top) — a flat colour wash needs
                        // something to composite against, same reason those two never sat
                        // directly on the page background either. Whole track is now the tap
                        // target too (was just the filled portion), matching how the visible slot
                        // reads once it has a background of its own.
                        .background(colors.surfaceRaised, CSM_BAR_CORNER)
                        .pressWash(CSM_BAR_CORNER) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCurrencyClick(c.code)
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = (c.strength / 100f).coerceIn(0.05f, 1f))
                            .background(barColor.copy(alpha = PAIR_WASH_ALPHA), CSM_BAR_CORNER),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(CSM_LABEL_SPACING))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ordered.forEach { c ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = c.code, style = AtomType.Caption.copy(color = colors.textMuted, fontSize = 10.sp))
                }
            }
        }
    }
}

private val TF_BUTTON_SHAPE = RoundedCornerShape(14.dp) // matches StatusStrip's own Summary-button CARD_SHAPE

/**
 * D1/H4/H1, relocated off the wheel's own top corners (2026-09-04 — see the doc comment on
 * [WheelScreen]). Same control recipe as StatusStrip's Summary button — `TF_BUTTON_SHAPE`,
 * `controlSurface`/`controlBorder`, 14/12dp padding around a plain-weight Caption label — reused
 * verbatim rather than a copied height constant, so "same height" is exact by construction.
 */
@Composable
private fun TimeframeButtons(
    timeframe: Timeframe,
    onChange: (Timeframe) -> Unit,
    colors: AtomColors,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Timeframe.D1 to "D1", Timeframe.H4 to "H4", Timeframe.H1 to "H1").forEach { (tf, label) ->
            val active = tf == timeframe
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(colors.controlSurface, TF_BUTTON_SHAPE)
                    .border(1.dp, colors.controlBorder, TF_BUTTON_SHAPE)
                    .pressWash(TF_BUTTON_SHAPE) {
                        if (!active) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onChange(tf)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = AtomType.Caption.copy(
                        color = if (active) colors.textPrimary else colors.textMuted,
                        fontWeight = FontWeight.Normal,
                    ),
                )
            }
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
 * Pieter, 2026-09-03 — "distribute the leftover space evenly" between every direct child here.
 * Places [content]'s direct children top-to-bottom with ONE shared gap size between every
 * consecutive pair: leftover space (`targetHeight` minus the children's own natural heights)
 * split evenly across the gaps, floored at [minGap]. A single measure-and-place pass, not a
 * measure-then-react-to-a-remembered-height loop — the gap's own size would otherwise feed back
 * into "how much height is already used," which doesn't converge (each frame's correction
 * overshoots the other way rather than settling).
 *
 * When content is already taller than [targetHeight] (the Summary cascade open, or — until the
 * CSM strip's visual pass lands — a genuinely cramped device), gaps collapse to [minGap] and this
 * composable simply reports its own larger height — the ancestor's `verticalScroll` takes over
 * from there, same as it already did before this existed.
 */
@Composable
private fun EvenlySpacedColumn(
    modifier: Modifier = Modifier,
    targetHeight: Dp,
    minGap: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        // Only height is relaxed — width stays exactly as given (already bounded, even inside a
        // scrollable ancestor, since verticalScroll only makes height unbounded), so a child like
        // the wheel's own Modifier.aspectRatio(1f) still has the one bounded dimension it needs.
        val looseConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(looseConstraints) }
        val contentHeightPx = placeables.sumOf { it.height }
        val gapCount = (placeables.size - 1).coerceAtLeast(0)
        val targetHeightPx = targetHeight.roundToPx()
        val minGapPx = minGap.roundToPx()
        val leftoverPx = (targetHeightPx - contentHeightPx).coerceAtLeast(0)
        val gapPx = if (gapCount > 0) maxOf(leftoverPx / gapCount, minGapPx) else 0
        val totalHeightPx = maxOf(targetHeightPx, contentHeightPx + gapPx * gapCount)
        layout(constraints.maxWidth, totalHeightPx) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height + gapPx
            }
        }
    }
}
