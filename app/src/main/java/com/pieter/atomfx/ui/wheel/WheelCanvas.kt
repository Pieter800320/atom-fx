package com.pieter.atomfx.ui.wheel

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.Ramp
import com.pieter.atomfx.ui.theme.csColor
import com.pieter.atomfx.ui.theme.stepColor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/** What a tap on the dial resolved to. */
sealed interface WheelTapTarget {
    data object Nucleus : WheelTapTarget
    data class Node(val pair: String) : WheelTapTarget          // a pair wedge
    data class Currency(val code: String) : WheelTapTarget      // a currency wedge
    data class CrossAsset(val id: String) : WheelTapTarget      // an outer-ring wedge
    data class Ring(val factor: Factor) : WheelTapTarget         // emitted by the factor pills, not the dial
    data class ModeToggle(val mode: WheelMode) : WheelTapTarget  // bottom corners: Currencies/Pairs
    data class TimeframeToggle(val timeframe: Timeframe) : WheelTapTarget // top corners: D1/H4
}

internal fun tintColor(tint: Tint, colors: AtomColors): Color = when (tint) {
    Tint.BULL -> colors.bull
    Tint.BEAR -> colors.bear
    Tint.WATCH -> colors.watch
    Tint.NEUTRAL -> colors.neutral
}

private fun rampFor(direction: Direction): Ramp = when (direction) {
    Direction.BULL -> Ramp.BULL
    Direction.BEAR -> Ramp.BEAR
    Direction.NEUTRAL -> Ramp.NEUTRAL
}

private const val GAP_DEG = 1.0f          // gutter between wedges
private const val PLATE_FRAC = 0.26f      // label plate depth as a fraction of the ring span

/**
 * The Wheel v2 radial dial. Three zones: outer cross-asset ring, a middle ring that cross-fades
 * between currencies and pairs, and the regime hub. Angular identity is fixed (WheelGeometry);
 * only radial fill animates as data changes (Design §7). Pure consumer of [WheelUiState].
 */
@Composable
fun WheelCanvas(
    state: WheelUiState,
    colors: AtomColors,
    isDark: Boolean,
    mode: WheelMode,
    timeframe: Timeframe,
    modifier: Modifier = Modifier,
    onTap: (WheelTapTarget) -> Unit = {},
    onLongPress: (String) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val activeCurrencies = state.currenciesFor(timeframe)

    // Cross-fade the middle ring on mode change (1 = pairs, 0 = currencies).
    val modeProgress by animateFloatAsState(
        targetValue = if (mode == WheelMode.PAIRS) 1f else 0f,
        animationSpec = tween(450), label = "modeProgress",
    )

    // Pulsating regime dot.
    val dotAlpha by rememberInfiniteTransition(label = "hubDot").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha",
    )

    // "Living dial": animate each pair's level and each currency's strength toward new values.
    val levelAnims = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
    state.nodes.forEach { levelAnims.getOrPut(it.pair) { Animatable(it.level.toFloat()) } }
    val strengthAnims = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
    activeCurrencies.forEach { strengthAnims.getOrPut(it.code) { Animatable(it.strength.toFloat()) } }

    // Rim-glow "flash and settle": a brief overshoot the moment a pair *earns* its tradeable/A+
    // rim, easing back down to its steady glow — the wheel's biggest moment of drama gets to feel
    // earned, not just appear. Steady state is 1f (no extra flash); a fresh transition snaps up
    // and springs back down.
    val rimFlash = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val prevTradeable = remember { mutableMapOf<String, Boolean>() }
    state.nodes.forEach { rimFlash.getOrPut(it.pair) { Animatable(1f) } }

    LaunchedEffect(state.nodes) {
        state.nodes.forEach { n -> levelAnims[n.pair]?.animateTo(n.level.toFloat(), tween(500)) }
    }
    LaunchedEffect(activeCurrencies) {
        activeCurrencies.forEach { c -> strengthAnims[c.code]?.animateTo(c.strength.toFloat(), tween(500)) }
    }
    LaunchedEffect(state.nodes) {
        state.nodes.forEach { n ->
            val isTradeableNow = n.state == PotentialState.TRADEABLE || n.state == PotentialState.APLUS
            val wasTradeable = prevTradeable[n.pair]
            if (isTradeableNow && wasTradeable == false) {
                rimFlash[n.pair]?.let { anim ->
                    launch {
                        anim.snapTo(2.4f)
                        anim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    }
                }
            }
            prevTradeable[n.pair] = isTradeableNow
        }
    }

    Canvas(
        modifier = modifier.pointerInput(state, mode) {
            detectTapGestures(
                onTap = { offset ->
                    val target = hitTest(offset, size.width.toFloat(), size.height.toFloat(), mode)
                    if (target != null) {
                        selectedKey = keyOf(target)
                        onTap(target)
                    }
                },
                onLongPress = { offset ->
                    val target = hitTest(offset, size.width.toFloat(), size.height.toFloat(), mode)
                    if (target is WheelTapTarget.Node) onLongPress(target.pair)
                },
            )
        },
    ) {
        drawDial(
            state, colors, isDark, textMeasurer, mode, timeframe, activeCurrencies,
            modeProgress = modeProgress,
            dotAlpha = dotAlpha,
            levelAnims = levelAnims,
            strengthAnims = strengthAnims,
            rimFlash = rimFlash,
            selectedKey = selectedKey,
        )
    }
}

private fun keyOf(t: WheelTapTarget): String = when (t) {
    is WheelTapTarget.Nucleus -> "hub"
    is WheelTapTarget.Node -> "pair:${t.pair}"
    is WheelTapTarget.Currency -> "ccy:${t.code}"
    is WheelTapTarget.CrossAsset -> "xa:${t.id}"
    is WheelTapTarget.Ring -> "ring"
    is WheelTapTarget.ModeToggle -> "toggle:${t.mode}"
    is WheelTapTarget.TimeframeToggle -> "tf:${t.timeframe}"
}

// ── Hit testing ──────────────────────────────────────────────────────────────────────────────
private fun hitTest(offset: Offset, w: Float, h: Float, mode: WheelMode): WheelTapTarget? {
    val cx = w / 2f
    val cy = h / 2f
    val half = min(w, h) / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    val g = WheelGeometry

    if (dist <= g.HUB_FRAC * half) return WheelTapTarget.Nucleus

    val deg = g.compassDeg(cx, cy, offset)
    if (dist in (g.RING_R0_FRAC * half)..(g.RING_R1_FRAC * half)) {
        return if (mode == WheelMode.PAIRS) {
            WheelTapTarget.Node(g.PAIR_ORDER[g.segIndexAt(g.PAIR_ORDER.size, deg)])
        } else {
            WheelTapTarget.Currency(g.CCY_ORDER[g.segIndexAt(g.CCY_ORDER.size, deg)])
        }
    }
    if (dist in (g.XA_R0_FRAC * half)..(g.XA_R1_FRAC * half)) {
        return WheelTapTarget.CrossAsset(g.XASSET_ORDER[g.segIndexAt(g.XASSET_ORDER.size, deg)].first)
    }
    if (dist in (g.TOGGLE_R0_FRAC * half)..(g.TOGGLE_R1_FRAC * half)) {
        val (mode0, mode1) = g.cornerHitRange(g.TOGGLE_MODE_CENTER_DEG)
        val (h40, h41) = g.cornerHitRange(g.TOGGLE_H4_CENTER_DEG)
        val (d10, d11) = g.cornerHitRange(g.TOGGLE_D1_CENTER_DEG)
        val (h10, h11) = g.cornerHitRange(g.TOGGLE_H1_CENTER_DEG)
        // Single Pairs/Currencies toggle (2026-09-03) — one trapezoid, tap flips to the other mode.
        if (deg in mode0..mode1) {
            return WheelTapTarget.ModeToggle(if (mode == WheelMode.PAIRS) WheelMode.CURRENCIES else WheelMode.PAIRS)
        }
        // The D1/H4/H1 buttons are truly inert in Pairs mode — pair `potential` has no timeframe
        // axis, so there's nothing for a tap to do there. Not just dimmed: the tap is dropped
        // entirely, same as tapping empty space.
        if (mode == WheelMode.CURRENCIES) {
            if (deg in h40..h41) return WheelTapTarget.TimeframeToggle(Timeframe.H4)
            if (deg in d10..d11) return WheelTapTarget.TimeframeToggle(Timeframe.D1)
            if (deg in h10..h11) return WheelTapTarget.TimeframeToggle(Timeframe.H1)
        }
    }
    return null
}

// ── Geometry helpers (pixel space) ─────────────────────────────────────────────────────────────
private fun DrawScope.wedgePath(cx: Float, cy: Float, r0: Float, r1: Float, a0: Float, a1: Float): Path {
    val p = Path()
    val outer = Rect(cx - r1, cy - r1, cx + r1, cy + r1)
    val inner = Rect(cx - r0, cy - r0, cx + r0, cy + r0)
    val start = a0 - 90f      // compass → Android arc convention (0° at 3 o'clock)
    val sweep = a1 - a0
    p.arcTo(outer, start, sweep, forceMoveTo = true)
    p.arcTo(inner, start + sweep, -sweep, forceMoveTo = false)
    p.close()
    return p
}

/** A real blurred halo behind [path] (Design §2.4: "glow... blurred"), not just an opaque stroke. */
private fun DrawScope.glowStroke(path: Path, color: Color, widthPx: Float, blurPx: Float) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = widthPx
        isAntiAlias = true
        maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
}

/** A soft blurred disc — used for ambient "lifted off the surface" shadows. */
private fun DrawScope.glowFillCircle(center: Offset, radius: Float, color: Color, blurPx: Float) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        style = Paint.Style.FILL
        isAntiAlias = true
        maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)
}

/**
 * Draws [text] curved along the circle of radius [r], centred at compass-degree [midDeg] and
 * confined to the wedge's own angular span [a0]..[a1]. Placed manually, glyph by glyph, rather
 * than via Canvas.drawTextOnPath: each glyph's angle is derived from its own measured width, and
 * the whole label is shrunk to fit the wedge's actual arc length first — so a label can never
 * spill into a neighbouring wedge, however narrow the slice.
 */
private fun DrawScope.curvedLabel(cx: Float, cy: Float, r: Float, midDeg: Float, a0: Float, a1: Float, text: String, color: Color, sizePx: Float, bold: Boolean) {
    if (text.isEmpty() || r <= 0f) return
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    val availableDeg = (a1 - a0).coerceAtLeast(0.1f)
    val availableArc = (Math.PI * r * availableDeg / 180.0).toFloat() * 0.94f

    var textSize = sizePx
    paint.textSize = textSize
    var totalWidth = paint.measureText(text)
    if (availableArc > 0f && totalWidth > availableArc) {
        textSize = (textSize * (availableArc / totalWidth)).coerceAtLeast(sizePx * 0.5f)
        paint.textSize = textSize
        totalWidth = paint.measureText(text)
    }

    val widths = FloatArray(text.length)
    paint.getTextWidths(text, widths)
    val lower = midDeg in 90f..270f

    var cursor = -totalWidth / 2f
    val nativeCanvas = drawContext.canvas.nativeCanvas
    for (i in text.indices) {
        val centerOffset = cursor + widths[i] / 2f
        cursor += widths[i]
        val deltaDeg = Math.toDegrees((centerOffset / r).toDouble()).toFloat()
        val glyphDeg = if (lower) midDeg - deltaDeg else midDeg + deltaDeg
        val pos = WheelGeometry.polar(cx, cy, r, glyphDeg)
        val rotation = if (lower) glyphDeg + 180f else glyphDeg
        val save = nativeCanvas.save()
        nativeCanvas.rotate(rotation, pos.x, pos.y)
        nativeCanvas.drawText(text, i, i + 1, pos.x, pos.y + textSize / 3f, paint)
        nativeCanvas.restoreToCount(save)
    }
}

// ── The draw pass ────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawDial(
    state: WheelUiState,
    colors: AtomColors,
    isDark: Boolean,
    textMeasurer: TextMeasurer,
    mode: WheelMode,
    timeframe: Timeframe,
    activeCurrencies: List<CurrencySeg>,
    modeProgress: Float,
    dotAlpha: Float,
    levelAnims: Map<String, Animatable<Float, *>>,
    strengthAnims: Map<String, Animatable<Float, *>>,
    rimFlash: Map<String, Animatable<Float, *>>,
    selectedKey: String?,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val half = size.minDimension / 2f
    val g = WheelGeometry

    // Background field.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(colors.groundRadial, colors.ground),
            center = Offset(cx, cy), radius = half * 1.1f,
        ),
    )

    drawCrossAssetRing(state, colors, cx, cy, half, selectedKey)
    drawCornerButtons(mode, timeframe, colors, cx, cy, half)

    // Middle ring cross-fade: draw whichever side has any alpha.
    val ccyAlpha = 1f - modeProgress
    val pairAlpha = modeProgress
    if (ccyAlpha > 0.02f) {
        scale(lerp01(0.9f, 1f, ccyAlpha), pivot = Offset(cx, cy)) {
            drawCurrencyRing(activeCurrencies, colors, cx, cy, half, ccyAlpha, strengthAnims, selectedKey)
        }
    }
    if (pairAlpha > 0.02f) {
        scale(lerp01(0.9f, 1f, pairAlpha), pivot = Offset(cx, cy)) {
            drawPairRing(state, colors, cx, cy, half, pairAlpha, levelAnims, rimFlash, selectedKey)
        }
    }

    drawHub(state, colors, cx, cy, half, dotAlpha, textMeasurer, selectedKey)
}

private fun lerp01(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

private fun DrawScope.drawCrossAssetRing(state: WheelUiState, colors: AtomColors, cx: Float, cy: Float, half: Float, selectedKey: String?) {
    val g = WheelGeometry
    val r0 = g.XA_R0_FRAC * half
    val r1 = g.XA_R1_FRAC * half
    val labelR = (r0 + r1) / 2f
    val count = state.crossAssets.size.coerceAtLeast(1)
    state.crossAssets.forEach { xa ->
        val (a0, a1) = g.segAngles(count, xa.index, GAP_DEG * 0.6f)
        val mid = g.midDeg(count, xa.index)
        val dirColor = when {
            xa.flat -> colors.textSecondary
            xa.up -> colors.bull
            else -> colors.bear
        }
        val bg = if (xa.confirm) colors.surfaceRaised else colors.surface
        val stroke = if (xa.confirm) dirColor else colors.hairline
        val path = wedgePath(cx, cy, r0, r1, a0, a1)
        drawPath(path, color = bg)
        drawPath(path, color = stroke.copy(alpha = if (xa.confirm) 0.9f else 0.4f), style = Stroke(if (xa.confirm) px(1.6f) else px(0.9f)))
        val labelColor = if (xa.confirm) colors.textPrimary else colors.textMuted
        curvedLabel(cx, cy, labelR, mid, a0, a1, xa.label, labelColor, sp(11f), bold = true)
        if (selectedKey == "xa:${xa.id}") drawPath(path, color = colors.textPrimary.copy(alpha = 0.8f), style = Stroke(px(2f)))
    }
}

/**
 * A tapered wedge — curved top/bottom edges (following the dial's own r0/r1 radii, so it still
 * "curves around the shape of the wheel"), wide at the hub-facing edge and narrower at the tip,
 * with only the two outer (narrow-end) corners rounded. Deliberately different from [wedgePath]'s
 * constant-width curved cells, so a corner button reads as chrome, not another data cell.
 */
private fun DrawScope.taperedCornerPath(
    cx: Float, cy: Float, r0: Float, r1: Float,
    innerA0: Float, innerA1: Float, outerA0: Float, outerA1: Float,
    outerCornerRadiusPx: Float,
): Path {
    val g = WheelGeometry
    val pInner0 = g.polar(cx, cy, r0, innerA0)
    val pInner1 = g.polar(cx, cy, r0, innerA1)
    val pOuter0 = g.polar(cx, cy, r1, outerA0)
    val pOuter1 = g.polar(cx, cy, r1, outerA1)

    fun offsetToward(from: Offset, to: Offset, dist: Float): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        val t = (dist / len).coerceIn(0f, 0.45f)
        return Offset(from.x + dx * t, from.y + dy * t)
    }

    val cornerAngleDeg = Math.toDegrees((outerCornerRadiusPx / r1).toDouble()).toFloat()
        .coerceAtMost((outerA1 - outerA0) * 0.45f)
    val sideNear1 = offsetToward(pOuter1, pInner1, outerCornerRadiusPx)
    val arcNear1 = g.polar(cx, cy, r1, outerA1 - cornerAngleDeg)
    val arcNear0 = g.polar(cx, cy, r1, outerA0 + cornerAngleDeg)
    val sideNear0 = offsetToward(pOuter0, pInner0, outerCornerRadiusPx)

    val outerRect = Rect(cx - r1, cy - r1, cx + r1, cy + r1)
    val innerRect = Rect(cx - r0, cy - r0, cx + r0, cy + r0)

    return Path().apply {
        moveTo(pInner0.x, pInner0.y)
        arcTo(innerRect, innerA0 - 90f, innerA1 - innerA0, forceMoveTo = false) // wide curved base
        lineTo(sideNear1.x, sideNear1.y)                                        // taper up to the tip
        quadraticTo(pOuter1.x, pOuter1.y, arcNear1.x, arcNear1.y)               // round outer corner
        arcTo(outerRect, (outerA1 - cornerAngleDeg) - 90f, -(outerA1 - outerA0 - 2 * cornerAngleDeg), forceMoveTo = false) // narrow curved tip
        quadraticTo(pOuter0.x, pOuter0.y, sideNear0.x, sideNear0.y)             // round outer corner
        lineTo(pInner0.x, pInner0.y)                                            // taper back down
        close()
    }
}

/** A single straight (not curved) line of text, rotated to the tangent at [midDeg] — the corner
 *  buttons' curved-but-tapered shape still gets a straight label, reinforcing that it's chrome. */
private fun DrawScope.straightLabel(cx: Float, cy: Float, r: Float, midDeg: Float, availableWidthPx: Float, text: String, color: Color, sizePx: Float, bold: Boolean) {
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    var textSize = sizePx
    paint.textSize = textSize
    val w = paint.measureText(text)
    if (w > availableWidthPx && availableWidthPx > 0f) {
        textSize = (textSize * (availableWidthPx / w)).coerceAtLeast(sizePx * 0.4f)
        paint.textSize = textSize
    }
    val pos = WheelGeometry.polar(cx, cy, r, midDeg)
    val lower = midDeg in 90f..270f
    val rotation = if (lower) midDeg + 180f else midDeg
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val save = nativeCanvas.save()
    nativeCanvas.rotate(rotation, pos.x, pos.y)
    nativeCanvas.drawText(text, pos.x, pos.y + textSize / 3f, paint)
    nativeCanvas.restoreToCount(save)
}

private data class CornerButtonSpec(val label: String, val centerDeg: Float, val selected: Boolean)

/**
 * The four corner buttons — a 4th ring shaped and placed to read as chrome rather than data:
 * tapered, curved-edge wedges (wide base, narrow rounded tip), thicker than any data ring.
 * Bottom-left is the single Pairs/Currencies mode toggle (thumb zone); top-left/top-right/
 * bottom-right are the Currencies-mode D1/H4/H1 timeframe toggle (Pieter, 2026-09-03 — H1 added,
 * taking the corner the old separate Currencies button used before it merged into one toggle).
 *
 * Exactly two visual states (Pieter's call, 2026-09-03 — was three: a raised fill + strong border
 * for selected, a plain fill + hairline border for unselected, and a further 45%-alpha wash for
 * inert on top of that): fill is always [colors.surface], and only the border switches — white
 * ([colors.textPrimary]) when selected, [colors.hairline] otherwise. An inert timeframe button
 * gets no separate treatment; it reads identically to any other unselected button, exactly like
 * its tap being dropped reads identically to tapping empty space.
 */
private fun DrawScope.drawCornerButtons(mode: WheelMode, timeframe: Timeframe, colors: AtomColors, cx: Float, cy: Float, half: Float) {
    val g = WheelGeometry
    val r0 = g.TOGGLE_R0_FRAC * half
    val r1 = g.TOGGLE_R1_FRAC * half
    val labelR = r0 + (r1 - r0) * 0.4f
    val cornerRadiusPx = px(7f)
    // The mode toggle is always "on" — the wheel is always in one of its two modes, never
    // neither — so it always carries the selected/white-border look; only its label swaps.
    val modeLabel = if (mode == WheelMode.PAIRS) "PAIRS" else "CURRENCIES"
    // Pieter, 2026-09-03: the selection border itself disappears in Pairs mode, not just the tap
    // response — the TF buttons must read as fully inert (no white anywhere among them) rather
    // than "H4 is still nominally chosen, just unresponsive." It reappears on the currently-active
    // TF the instant Currencies is toggled back on.
    val inCurrencies = mode == WheelMode.CURRENCIES

    listOf(
        CornerButtonSpec(modeLabel, g.TOGGLE_MODE_CENTER_DEG, true),
        CornerButtonSpec("D1", g.TOGGLE_D1_CENTER_DEG, inCurrencies && timeframe == Timeframe.D1),
        CornerButtonSpec("H4", g.TOGGLE_H4_CENTER_DEG, inCurrencies && timeframe == Timeframe.H4),
        CornerButtonSpec("H1", g.TOGGLE_H1_CENTER_DEG, inCurrencies && timeframe == Timeframe.H1),
    ).forEach { spec ->
        val angles = g.cornerButtonAngles(spec.centerDeg)
        val path = taperedCornerPath(cx, cy, r0, r1, angles.innerA0, angles.innerA1, angles.outerA0, angles.outerA1, cornerRadiusPx)
        drawPath(path, color = colors.surface)
        drawPath(
            path,
            color = if (spec.selected) colors.textPrimary else colors.hairline,
            style = Stroke(if (spec.selected) px(2f) else px(1f)),
        )
        val outerB = g.polar(cx, cy, r1, angles.outerA0)
        val outerC = g.polar(cx, cy, r1, angles.outerA1)
        val chordWidth = kotlin.math.sqrt((outerC.x - outerB.x).let { it * it } + (outerC.y - outerB.y).let { it * it }) * 0.9f
        val labelColor = if (spec.selected) colors.textPrimary else colors.textMuted
        straightLabel(cx, cy, labelR, spec.centerDeg, chordWidth, spec.label, labelColor, sp(11f), bold = true)
    }
}

private fun DrawScope.drawCurrencyRing(
    currencies: List<CurrencySeg>, colors: AtomColors, cx: Float, cy: Float, half: Float,
    alpha: Float, strengthAnims: Map<String, Animatable<Float, *>>, selectedKey: String?,
) {
    val g = WheelGeometry
    val r0 = g.RING_R0_FRAC * half
    val r1 = g.RING_R1_FRAC * half
    val span = r1 - r0
    val plateR0 = r1 - span * PLATE_FRAC
    val plateR1 = r1 - span * 0.02f
    val labelR = (plateR0 + plateR1) / 2f
    val graphMax = plateR0 - span * 0.03f
    val count = currencies.size.coerceAtLeast(1)

    currencies.forEach { c ->
        val (a0, a1) = g.segAngles(count, c.index, GAP_DEG)
        val mid = g.midDeg(count, c.index)
        val bgPath = wedgePath(cx, cy, r0, r1, a0, a1)
        drawPath(bgPath, color = colors.surfaceRaised.copy(alpha = alpha))
        drawPath(bgPath, color = colors.hairline.copy(alpha = 0.5f * alpha), style = Stroke(px(0.9f)))

        val v = (strengthAnims[c.code]?.value ?: c.strength.toFloat())
        val fillEnd = r0 + (graphMax - r0) * (v / 100f)
        val fillPath = wedgePath(cx, cy, r0 + 2f, max(r0 + 3f, fillEnd), a0 + 1.2f, a1 - 1.2f)
        // Brighter the further from the hub — radius already carries the meaning, so the
        // fill reinforces it: a muted blend near the hub ramping to the full accent at the tip.
        val full = csColor(c.strength, colors)
        val dim = lerp(colors.surfaceRaised, full, 0.35f)
        val gradRadius = max(fillEnd, r0 + 4f)
        val innerStop = (r0 / gradRadius).coerceIn(0f, 0.95f)
        drawPath(
            fillPath,
            brush = Brush.radialGradient(
                colorStops = arrayOf(innerStop to dim, 1f to full),
                center = Offset(cx, cy),
                radius = gradRadius,
            ),
            alpha = 0.85f * alpha,
        )

        val platePath = wedgePath(cx, cy, plateR0, plateR1, a0 + 0.8f, a1 - 0.8f)
        drawPath(platePath, color = colors.surface.copy(alpha = alpha))

        curvedLabel(cx, cy, labelR, mid, a0, a1, c.code, colors.textPrimary.copy(alpha = alpha), sp(15f), bold = true)
        if (selectedKey == "ccy:${c.code}") drawPath(bgPath, color = colors.textPrimary.copy(alpha = 0.8f * alpha), style = Stroke(px(2f)))
    }
}

private fun DrawScope.drawPairRing(
    state: WheelUiState, colors: AtomColors, cx: Float, cy: Float, half: Float,
    alpha: Float, levelAnims: Map<String, Animatable<Float, *>>, rimFlash: Map<String, Animatable<Float, *>>, selectedKey: String?,
) {
    val g = WheelGeometry
    val r0 = g.RING_R0_FRAC * half
    val r1 = g.RING_R1_FRAC * half
    val span = r1 - r0
    val plateR0 = r1 - span * PLATE_FRAC
    val plateR1 = r1 - span * 0.02f
    val labelR = (plateR0 + plateR1) / 2f
    val graphMax = plateR0 - span * 0.03f
    val count = state.nodes.size.coerceAtLeast(1)

    state.nodes.forEach { node ->
        val (a0, a1) = g.segAngles(count, node.index, GAP_DEG)
        val mid = g.midDeg(count, node.index)
        val ramp = rampFor(node.direction)

        val bgPath = wedgePath(cx, cy, r0, r1, a0, a1)
        drawPath(bgPath, color = colors.surfaceRaised.copy(alpha = alpha))
        drawPath(bgPath, color = colors.hairline.copy(alpha = 0.5f * alpha), style = Stroke(px(0.9f)))

        // Step bands, growing to the animated level. Each band is itself a radial gradient —
        // muted at its own inner edge, full stepColor at its outer edge — same "brighter further
        // from the hub" language as the currency ring, while keeping the 6 levels visibly
        // distinct (this is discrete data — a count of factors passed — unlike currency strength).
        val levelValue = (levelAnims[node.pair]?.value ?: node.level.toFloat()).coerceIn(0f, 6f)
        val fillFrac = levelValue / 6f
        for (k in 1..6) {
            val innerFrac = (k - 1) / 6f
            val outerFrac = k / 6f
            if (fillFrac <= innerFrac) break
            val topFrac = min(outerFrac, fillFrac)
            val rIn = r0 + (graphMax - r0) * innerFrac
            val rOut = r0 + (graphMax - r0) * topFrac
            val bandPath = wedgePath(cx, cy, rIn + 1f, rOut, a0 + 2f, a1 - 2f)
            val full = stepColor(k, ramp, colors)
            val dim = lerp(colors.surfaceRaised, full, 0.45f)
            val gradRadius = max(rOut, rIn + 2f)
            val innerStop = (rIn / gradRadius).coerceIn(0f, 0.95f)
            drawPath(
                bandPath,
                brush = Brush.radialGradient(
                    colorStops = arrayOf(innerStop to dim, 1f to full),
                    center = Offset(cx, cy),
                    radius = gradRadius,
                ),
                alpha = 0.9f * alpha,
            )
        }

        // Blocking-factor marker: a bright hairline at the top of the filled stack.
        if (node.blockedAt != null && node.level in 1..5) {
            val rMark = r0 + (graphMax - r0) * (node.level / 6f)
            val markPath = wedgePath(cx, cy, rMark, rMark + px(2f), a0 + 2f, a1 - 2f)
            drawPath(markPath, color = tintForDir(node.direction, colors).copy(alpha = alpha))
        }

        val platePath = wedgePath(cx, cy, plateR0, plateR1, a0 + 0.6f, a1 - 0.6f)
        drawPath(platePath, color = colors.surface.copy(alpha = alpha))
        curvedLabel(cx, cy, labelR, mid, a0, a1, node.pair, colors.textPrimary.copy(alpha = alpha), sp(12f), bold = true)

        // A+/tradeable rim glow — a real blurred halo (Design §2.4) behind the crisp rim stroke.
        // The moment a pair earns this rim, [rimFlash] briefly overshoots (bigger, brighter halo)
        // and eases back to its steady glow — the wheel's biggest moment gets to feel earned.
        if (node.state == PotentialState.TRADEABLE || node.state == PotentialState.APLUS) {
            val isAplus = node.state == PotentialState.APLUS
            val rimColor = tintForDir(node.direction, colors)
            val flash = (rimFlash[node.pair]?.value ?: 1f).coerceAtLeast(1f)
            val glowAlpha = ((if (isAplus) 0.5f else 0.28f) * alpha * flash).coerceAtMost(1f)
            val glowSize = (if (isAplus) 6f else 4f) * flash.coerceAtMost(1.7f)
            val glowBlur = (if (isAplus) 9f else 6f) * flash.coerceAtMost(1.9f)
            glowStroke(bgPath, rimColor.copy(alpha = glowAlpha), px(glowSize), px(glowBlur))
            drawPath(bgPath, color = rimColor.copy(alpha = (if (isAplus) 0.9f else 0.5f) * alpha), style = Stroke(px(2f)))
        }
        if (selectedKey == "pair:${node.pair}") drawPath(bgPath, color = colors.textPrimary.copy(alpha = 0.8f * alpha), style = Stroke(px(2f)))
    }
}

private fun tintForDir(direction: Direction, colors: AtomColors): Color = when (direction) {
    Direction.BULL -> colors.bull
    Direction.BEAR -> colors.bear
    Direction.NEUTRAL -> colors.neutral
}

private fun DrawScope.drawHub(
    state: WheelUiState, colors: AtomColors, cx: Float, cy: Float, half: Float,
    dotAlpha: Float, textMeasurer: TextMeasurer, selectedKey: String?,
) {
    val r = WheelGeometry.HUB_FRAC * half
    val center = Offset(cx, cy)
    val tint = tintColor(state.nucleus.tint, colors)

    // Soft ambient shadow — lifts the hub off the dial, a touch of 3-D depth (Design §1's
    // "depth comes from light and blur" — dark shadow works in both themes, unlike a status glow).
    glowFillCircle(center + Offset(0f, px(3f)), r * 1.03f, colors.ground.copy(alpha = 0.4f), px(9f))
    drawCircle(brush = Brush.radialGradient(listOf(colors.surfaceRaised, colors.surface), center, r), radius = r, center = center)
    drawCircle(color = colors.hairlineStrong, radius = r, center = center, style = Stroke(px(1.5f)))
    drawCircle(color = tint.copy(alpha = 0.5f), radius = r, center = center, style = Stroke(px(1f)))
    if (selectedKey == "hub") drawCircle(color = colors.textPrimary.copy(alpha = 0.8f), radius = r, center = center, style = Stroke(px(2f)))

    // Pulsating regime dot near the top of the hub.
    drawCircle(color = tint.copy(alpha = dotAlpha), radius = px(3f), center = Offset(cx, cy - r * 0.62f))

    // Centred text stack: REGIME · big regime word.
    val lines = buildList {
        add("REGIME" to TextStyle(color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
        add(state.nucleus.regimeLabel to TextStyle(color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
    val laid = lines.map { (t, s) -> textMeasurer.measure(t, s) }
    val totalH = laid.sumOf { it.size.height }
    var y = cy - totalH / 2f
    laid.forEach { l ->
        val topLeft = Offset((cx - l.size.width / 2f).coerceAtLeast(0f), y.coerceAtLeast(0f))
        drawText(textLayoutResult = l, topLeft = topLeft)
        y += l.size.height
    }
}

// dp / sp → px helpers (DrawScope is a Density).
private fun DrawScope.px(dp: Float): Float = dp * density
private fun DrawScope.sp(sp: Float): Float = sp * density * fontScale
