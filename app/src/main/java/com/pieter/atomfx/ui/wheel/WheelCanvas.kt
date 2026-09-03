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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
import com.pieter.atomfx.ui.theme.darken
import com.pieter.atomfx.ui.theme.lighten
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

// Pieter, 2026-09-03 — every highlighted/emphasis border on the wheel (selection outlines, the
// cross-asset "moving" rim, the corner buttons' selected border), thinned from a flat 2px.
private const val HIGHLIGHT_STROKE_PX = 1.2f
// The cross-asset ring's own "moving" stroke was 1.6px (not 2px like the rest) — thinned
// proportionally to the same degree as HIGHLIGHT_STROKE_PX's cut from 2px.
private const val XA_MOVING_STROKE_PX = 1.0f

// Pieter, 2026-09-03 — tap-selection no longer draws a border anywhere on the wheel (the wings/
// corner buttons are the one deliberate exception — untouched, see drawCornerButtons): a
// translucent wash instead, same "electric" technique as the cross-asset moving cells. Uses
// colors.textPrimary, not a literal Color.White — the exact bug already found and fixed in
// pressWash (a hardcoded white wash is invisible in light mode; textPrimary is near-white in
// dark theme and near-black in light theme, so it reads in both).
private const val TAP_WASH_ALPHA = 0.16f

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
    // Pieter, 2026-09-03 — tap feedback is a brief flash, not a persistent "selected" state:
    // snaps to full wash on tap, animates back down to 0. Keyed per-element so multiple wedges/
    // wings can each hold their own independent fade. Applies to every tappable thing on the
    // dial, wings included.
    val tapFlash = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val tapScope = rememberCoroutineScope()
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

    LaunchedEffect(state.nodes) {
        state.nodes.forEach { n -> levelAnims[n.pair]?.animateTo(n.level.toFloat(), tween(125)) }
    }
    LaunchedEffect(activeCurrencies) {
        activeCurrencies.forEach { c -> strengthAnims[c.code]?.animateTo(c.strength.toFloat(), tween(125)) }
    }

    Canvas(
        modifier = modifier.pointerInput(state, mode) {
            detectTapGestures(
                onTap = { offset ->
                    val target = hitTest(offset, size.width.toFloat(), size.height.toFloat(), mode)
                    if (target != null) {
                        val key = keyOf(target)
                        // Launched on a scope outside detectTapGestures' own coroutine, so the
                        // ~350ms fade never blocks the next tap from being detected.
                        tapScope.launch {
                            val anim = tapFlash.getOrPut(key) { Animatable(0f) }
                            anim.snapTo(1f)
                            anim.animateTo(0f, tween(350))
                        }
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
            tapFlash = tapFlash,
        )
    }
}

// Fixed, mode/timeframe-VALUE-independent keys for the two wing kinds — a ModeToggle target
// carries the mode it would switch TO (not which physical button was pressed, and there's only
// one mode button anyway), so a fixed key sidesteps that entirely; a TimeframeToggle target's
// own timeframe already unambiguously identifies which of the three buttons was tapped.
private fun keyOf(t: WheelTapTarget): String = when (t) {
    is WheelTapTarget.Nucleus -> "hub"
    is WheelTapTarget.Node -> "pair:${t.pair}"
    is WheelTapTarget.Currency -> "ccy:${t.code}"
    is WheelTapTarget.CrossAsset -> "xa:${t.id}"
    is WheelTapTarget.Ring -> "ring"
    is WheelTapTarget.ModeToggle -> "wing:mode"
    is WheelTapTarget.TimeframeToggle -> "wing:${t.timeframe.name.lowercase()}"
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
    tapFlash: Map<String, Animatable<Float, *>>,
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

    drawCrossAssetRing(state, colors, cx, cy, half, tapFlash)
    drawCornerButtons(mode, timeframe, colors, cx, cy, half, tapFlash)

    // Middle ring cross-fade: draw whichever side has any alpha.
    val ccyAlpha = 1f - modeProgress
    val pairAlpha = modeProgress
    if (ccyAlpha > 0.02f) {
        scale(lerp01(0.9f, 1f, ccyAlpha), pivot = Offset(cx, cy)) {
            drawCurrencyRing(activeCurrencies, colors, cx, cy, half, ccyAlpha, strengthAnims, tapFlash)
        }
    }
    if (pairAlpha > 0.02f) {
        scale(lerp01(0.9f, 1f, pairAlpha), pivot = Offset(cx, cy)) {
            drawPairRing(state, colors, cx, cy, half, pairAlpha, levelAnims, tapFlash)
        }
    }

    drawHub(state, colors, cx, cy, half, dotAlpha, textMeasurer, tapFlash)
}

/** Reads the current tap-flash value (0f if never/no-longer flashing) for [key]. */
private fun flashOf(tapFlash: Map<String, Animatable<Float, *>>, key: String): Float =
    tapFlash[key]?.value ?: 0f

private fun lerp01(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

/**
 * Pieter, 2026-09-03 — simplified to a two-state indicator: moving (any non-flat direction, up or
 * down alike) is green, inert (flat) is dim. Was previously three dimensions at once (up/down/
 * flat direction × regime-confirm × label emphasis) — deliberately dropped the direction (green
 * vs. red) and regime-confirm distinctions from the ring itself; both are still one tap away in
 * the Cross Asset sheet (Functional Spec §6.6), which keeps its full up/down arrows and
 * confirm/dim badges unchanged. The ring's own job now is exactly what Pieter asked for: "which
 * cross assets are moving, and which aren't" — nothing more.
 */
private fun DrawScope.drawCrossAssetRing(state: WheelUiState, colors: AtomColors, cx: Float, cy: Float, half: Float, tapFlash: Map<String, Animatable<Float, *>>) {
    val g = WheelGeometry
    val r0 = g.XA_R0_FRAC * half
    val r1 = g.XA_R1_FRAC * half
    val labelR = (r0 + r1) / 2f
    val count = state.crossAssets.size.coerceAtLeast(1)
    state.crossAssets.forEach { xa ->
        val (a0, a1) = g.segAngles(count, xa.index, GAP_DEG * 0.6f)
        val mid = g.midDeg(count, xa.index)
        // Pieter, 2026-09-03 follow-up — back to three states (up/down/flat), not two: the
        // two-state "just moving vs. inert" simplification read as a real bug in practice — a
        // falling DXY still showed green, since green meant "moving," not "up." Direction is
        // real information, not redundant with anything else at a glance (only the Cross Asset
        // sheet, one tap away, had it). Electric Treatment (wash + bright rim + brighter-still
        // text, all one hue) now keyed on direction: bull green when up, bear red when down,
        // dim/hairline grey when flat — the mechanics are unchanged from the two-state version,
        // only which hue (or none) drives them.
        val hue = if (xa.flat) null else if (xa.up) colors.bull else colors.bear
        val bg = hue?.copy(alpha = 0.18f) ?: colors.surface
        val stroke = hue ?: colors.hairline
        val path = wedgePath(cx, cy, r0, r1, a0, a1)
        drawPath(path, color = bg)
        drawPath(path, color = stroke.copy(alpha = if (hue != null) 0.9f else 0.4f), style = Stroke(if (hue != null) px(XA_MOVING_STROKE_PX) else px(0.9f)))
        val labelColor = hue?.let { lighten(it, 0.45f) } ?: colors.textMuted
        curvedLabel(cx, cy, labelR, mid, a0, a1, xa.label, labelColor, sp(11f), bold = false)
        val flash = flashOf(tapFlash, "xa:${xa.id}")
        if (flash > 0f) drawPath(path, color = colors.textPrimary.copy(alpha = TAP_WASH_ALPHA * flash))
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

private data class CornerButtonSpec(val label: String, val centerDeg: Float, val selected: Boolean, val flashKey: String)

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
private fun DrawScope.drawCornerButtons(
    mode: WheelMode, timeframe: Timeframe, colors: AtomColors, cx: Float, cy: Float, half: Float,
    tapFlash: Map<String, Animatable<Float, *>>,
) {
    val g = WheelGeometry
    val r0 = g.TOGGLE_R0_FRAC * half
    val r1 = g.TOGGLE_R1_FRAC * half
    val labelR = r0 + (r1 - r0) * 0.4f
    val cornerRadiusPx = px(7f)
    // The mode toggle is always "on" — the wheel is always in one of its two modes, never
    // neither — so it always carries the selected/white-border look; only its label swaps.
    // Pieter, 2026-09-03 — labelled by the METRIC (what the radius/fill means), not the entity
    // type (which set of wedges you're looking at) — the ring itself already makes the entity
    // type obvious (currency codes vs. pair codes), but not what the fill actually represents.
    // Still never "Strength" for pairs (Glossary/WHEEL_V2_SPEC §0's rule): each mode keeps its
    // own correct metric name, this just changes which of the two ideas the label leads with.
    val modeLabel = if (mode == WheelMode.PAIRS) "POTENTIAL" else "STRENGTH"
    // Pieter, 2026-09-03: the selection border itself disappears in Pairs mode, not just the tap
    // response — the TF buttons must read as fully inert (no white anywhere among them) rather
    // than "H4 is still nominally chosen, just unresponsive." It reappears on the currently-active
    // TF the instant Currencies is toggled back on.
    val inCurrencies = mode == WheelMode.CURRENCIES

    listOf(
        CornerButtonSpec(modeLabel, g.TOGGLE_MODE_CENTER_DEG, true, "wing:mode"),
        CornerButtonSpec("D1", g.TOGGLE_D1_CENTER_DEG, inCurrencies && timeframe == Timeframe.D1, "wing:d1"),
        CornerButtonSpec("H4", g.TOGGLE_H4_CENTER_DEG, inCurrencies && timeframe == Timeframe.H4, "wing:h4"),
        CornerButtonSpec("H1", g.TOGGLE_H1_CENTER_DEG, inCurrencies && timeframe == Timeframe.H1, "wing:h1"),
    ).forEach { spec ->
        val angles = g.cornerButtonAngles(spec.centerDeg)
        val path = taperedCornerPath(cx, cy, r0, r1, angles.innerA0, angles.innerA1, angles.outerA0, angles.outerA1, cornerRadiusPx)
        drawPath(path, color = colors.surface)
        drawPath(
            path,
            color = if (spec.selected) colors.textPrimary else colors.hairline,
            style = Stroke(if (spec.selected) px(HIGHLIGHT_STROKE_PX) else px(1f)),
        )
        // Pieter, 2026-09-03 — wings get the same brief tap wash as every other cell (the one
        // deliberate exception is that they KEEP their own white border on top of it, unlike
        // every other ring, whose persistent selection border was replaced by a wash entirely).
        val flash = flashOf(tapFlash, spec.flashKey)
        if (flash > 0f) drawPath(path, color = colors.textPrimary.copy(alpha = TAP_WASH_ALPHA * flash))
        // Pieter, 2026-09-03 follow-up — curved now, not straight: every other label on the
        // dial already curves to match the wheel's own curvature, and having the wings be the
        // one straight exception (originally deliberate — WHEEL_V2_SPEC.md §11: "reinforcing
        // that it's chrome") read as inconsistent once seen next to everything else. Supersedes
        // that addendum note. curvedLabel needs an angular span at the LABEL's own radius, not
        // the wedge's wide base or narrow tip — a wing tapers between them, so interpolate the
        // actual boundary angles at the same 0.4 fraction labelR itself was placed at (below),
        // rather than reusing either end's span verbatim.
        val labelT = 0.4f
        val la0 = angles.innerA0 + (angles.outerA0 - angles.innerA0) * labelT
        val la1 = angles.innerA1 + (angles.outerA1 - angles.innerA1) * labelT
        // Pieter, 2026-09-03 — inert (Pairs mode) D1/H4/H1 labels now match their own border
        // colour (hairline) instead of textMuted, so an inert wing reads as one dim unit exactly
        // like the cross-asset ring's inert cells do. Currencies-mode behaviour is unchanged.
        val labelColor = if (spec.selected) colors.textPrimary else if (inCurrencies) colors.textMuted else colors.hairline
        curvedLabel(cx, cy, labelR, spec.centerDeg, la0, la1, spec.label, labelColor, sp(11f), bold = false)
    }
}

private fun DrawScope.drawCurrencyRing(
    currencies: List<CurrencySeg>, colors: AtomColors, cx: Float, cy: Float, half: Float,
    alpha: Float, strengthAnims: Map<String, Animatable<Float, *>>, tapFlash: Map<String, Animatable<Float, *>>,
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
        // Depth via shade, not a blend toward a neutral surface token (Pieter, 2026-09-03 — the
        // old version read as dirty/washed out, not shadowed) — a true shadow of the same hue
        // near the hub, growing in gradations out to a highlight catching the light at the tip.
        // Continuous, unlike the pair ring's flat per-band steps: CSM strength is a continuous
        // 0-100 value, not a discrete factor count, so its depth cue should read as one smooth
        // gradient across the whole fill, not a stepped one.
        // 2026-09-03 follow-up — inner shade brought down from 0.3f (read as too dark at the
        // hub); outer lift raised to keep the "gradually brighter toward the rim" shape strong.
        val full = csColor(c.strength, colors)
        val inner = darken(full, 0.15f)
        val outer = lighten(full, 0.2f)
        val gradRadius = max(fillEnd, r0 + 4f)
        val innerStop = (r0 / gradRadius).coerceIn(0f, 0.95f)
        drawPath(
            fillPath,
            brush = Brush.radialGradient(
                colorStops = arrayOf(innerStop to inner, 1f to outer),
                center = Offset(cx, cy),
                radius = gradRadius,
            ),
            alpha = 0.85f * alpha,
        )

        val platePath = wedgePath(cx, cy, plateR0, plateR1, a0 + 0.8f, a1 - 0.8f)
        drawPath(platePath, color = colors.surface.copy(alpha = alpha))

        curvedLabel(cx, cy, labelR, mid, a0, a1, c.code, colors.textPrimary.copy(alpha = alpha), sp(15f), bold = false)
        val flash = flashOf(tapFlash, "ccy:${c.code}")
        if (flash > 0f) drawPath(bgPath, color = colors.textPrimary.copy(alpha = TAP_WASH_ALPHA * flash * alpha))
    }
}

private fun DrawScope.drawPairRing(
    state: WheelUiState, colors: AtomColors, cx: Float, cy: Float, half: Float,
    alpha: Float, levelAnims: Map<String, Animatable<Float, *>>, tapFlash: Map<String, Animatable<Float, *>>,
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

        // Step bands, growing to the animated level. Pieter, 2026-09-03: each band is now a
        // FLAT fill (stepColor(k, ...) is already a genuine shade of the ramp colour, darker at
        // low k, full accent at k=6 — see Color.kt) rather than its own internal radial gradient
        // — the depth cue here is the discrete jump between adjacent bands' shades, not a smooth
        // blend within one, since this is discrete data (a count of factors passed), unlike the
        // currency ring's continuous strength value.
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
            drawPath(bandPath, color = stepColor(k, ramp, colors), alpha = 0.9f * alpha)
        }

        // Blocking-factor marker: a bright hairline at the top of the filled stack.
        if (node.blockedAt != null && node.level in 1..5) {
            val rMark = r0 + (graphMax - r0) * (node.level / 6f)
            val markPath = wedgePath(cx, cy, rMark, rMark + px(2f), a0 + 2f, a1 - 2f)
            drawPath(markPath, color = tintForDir(node.direction, colors).copy(alpha = alpha))
        }

        val platePath = wedgePath(cx, cy, plateR0, plateR1, a0 + 0.6f, a1 - 0.6f)
        drawPath(platePath, color = colors.surface.copy(alpha = alpha))
        curvedLabel(cx, cy, labelR, mid, a0, a1, node.pair, colors.textPrimary.copy(alpha = alpha), sp(12f), bold = false)

        // Pieter, 2026-09-03 — the A+/tradeable rim glow is gone entirely (was a blurred halo,
        // before that also a crisp stroke). A level-6 pair is now indicated by its fill alone
        // (stepColor's own full accent colour at step 6) — no separate rim treatment at all.
        val flash = flashOf(tapFlash, "pair:${node.pair}")
        if (flash > 0f) drawPath(bgPath, color = colors.textPrimary.copy(alpha = TAP_WASH_ALPHA * flash * alpha))
    }
}

private fun tintForDir(direction: Direction, colors: AtomColors): Color = when (direction) {
    Direction.BULL -> colors.bull
    Direction.BEAR -> colors.bear
    Direction.NEUTRAL -> colors.neutral
}

private fun DrawScope.drawHub(
    state: WheelUiState, colors: AtomColors, cx: Float, cy: Float, half: Float,
    dotAlpha: Float, textMeasurer: TextMeasurer, tapFlash: Map<String, Animatable<Float, *>>,
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
    val hubFlash = flashOf(tapFlash, "hub")
    if (hubFlash > 0f) drawCircle(color = colors.textPrimary.copy(alpha = TAP_WASH_ALPHA * hubFlash), radius = r, center = center)

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
