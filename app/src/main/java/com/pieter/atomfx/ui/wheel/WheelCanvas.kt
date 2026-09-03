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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.lighten
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


private const val GAP_DEG = 1.0f          // gutter between wedges
private const val PLATE_FRAC = 0.26f      // label plate depth as a fraction of the ring span

// Pieter, 2026-09-03 — tap-selection no longer draws a border anywhere on the wheel, corner
// buttons included (2026-09-03 follow-up dropped their own selected-state white border too — see
// drawCornerButtons): a translucent wash instead, same "electric" technique as the cross-asset
// moving cells. Uses colors.textPrimary, not a literal Color.White — the exact bug already found
// and fixed in pressWash (a hardcoded white wash is invisible in light mode; textPrimary is
// near-white in dark theme and near-black in light theme, so it reads in both).
private const val TAP_WASH_ALPHA = 0.16f

// "Soft corners everywhere else in the app, why not the wheel" experiment, 2026-09-03 — every
// wash cell (cross-asset cells, the green/red graph fill on the Potential/Strength rings) now
// uses the exact same colour formula as an Electric Treatment pill (ScrollingPills.kt's own
// wash = tint@18%, see the Macro "Confidence" pill), no border, corners rounded via
// [roundedWedgePath]. Previously 0.4f with a coloured border, added when the un-plated wash read
// as nearly invisible (~1.2:1) — that fix was to the wrong thing; the pill's own 18% reads fine
// once composited onto the surfaceRaised plate every wash cell already sits on.
private const val XA_WASH_ALPHA = 0.18f

// Matches the corner buttons' own outer-corner rounding (drawCornerButtons' cornerRadiusPx).
private const val WHEEL_CELL_CORNER_RADIUS_DP = 7f

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

/**
 * Same shape as [wedgePath], all four corners rounded by [cornerRadiusPx]. Pieter, 2026-09-03
 * follow-up — the first cut used Skia's `CornerPathEffect` on the plain [wedgePath]; on-device
 * that read as a straight chamfer cut into each corner, not a curve — `CornerPathEffect` doesn't
 * reliably round joins on a path built from `arcTo` segments the way it does on plain polylines.
 * This hand-rounds each corner instead: trim a small amount off both adjoining edges (an angular
 * trim on each arc, a radial trim on each straight side) and connect the trimmed ends with a
 * `quadraticTo` through the original sharp corner — the exact technique `taperedCornerPath`
 * already uses for the corner buttons' own two rounded corners, just applied to all four here.
 */
private fun DrawScope.roundedWedgePath(cx: Float, cy: Float, r0: Float, r1: Float, a0: Float, a1: Float, cornerRadiusPx: Float): Path {
    val g = WheelGeometry
    val outerRect = Rect(cx - r1, cy - r1, cx + r1, cy + r1)
    val innerRect = Rect(cx - r0, cy - r0, cx + r0, cy + r0)
    val sweep = a1 - a0
    val maxTrimDeg = kotlin.math.abs(sweep) * 0.45f
    val outerTrimDeg = Math.toDegrees((cornerRadiusPx / r1).toDouble()).toFloat().coerceIn(0f, maxTrimDeg)
    val innerTrimDeg = if (r0 > 1f) Math.toDegrees((cornerRadiusPx / r0).toDouble()).toFloat().coerceIn(0f, maxTrimDeg) else 0f
    val sideLen = (r1 - r0).coerceAtLeast(0.01f)
    val radialTrim = cornerRadiusPx.coerceIn(0f, sideLen * 0.45f)

    val pOuterA0 = g.polar(cx, cy, r1, a0)
    val pOuterA1 = g.polar(cx, cy, r1, a1)
    val pInnerA0 = g.polar(cx, cy, r0, a0)
    val pInnerA1 = g.polar(cx, cy, r0, a1)
    val pSideA0Outer = g.polar(cx, cy, r1 - radialTrim, a0)
    val pSideA0Inner = g.polar(cx, cy, r0 + radialTrim, a0)
    val pSideA1Outer = g.polar(cx, cy, r1 - radialTrim, a1)
    val pSideA1Inner = g.polar(cx, cy, r0 + radialTrim, a1)
    val pOuterArcStart = g.polar(cx, cy, r1, a0 + outerTrimDeg)
    val pInnerArcNearA1 = g.polar(cx, cy, r0, a1 - innerTrimDeg)

    val startDeg = a0 - 90f // compass → Android arc convention (0° at 3 o'clock), as in wedgePath

    return Path().apply {
        moveTo(pSideA0Outer.x, pSideA0Outer.y)
        quadraticTo(pOuterA0.x, pOuterA0.y, pOuterArcStart.x, pOuterArcStart.y)                 // round outer-a0 corner
        arcTo(outerRect, startDeg + outerTrimDeg, sweep - 2 * outerTrimDeg, forceMoveTo = false) // outer arc
        quadraticTo(pOuterA1.x, pOuterA1.y, pSideA1Outer.x, pSideA1Outer.y)                      // round outer-a1 corner
        lineTo(pSideA1Inner.x, pSideA1Inner.y)                                                   // side at a1
        quadraticTo(pInnerA1.x, pInnerA1.y, pInnerArcNearA1.x, pInnerArcNearA1.y)                // round inner-a1 corner
        arcTo(innerRect, startDeg + sweep - innerTrimDeg, -(sweep - 2 * innerTrimDeg), forceMoveTo = false) // inner arc
        quadraticTo(pInnerA0.x, pInnerA0.y, pSideA0Inner.x, pSideA0Inner.y)                      // round inner-a0 corner
        lineTo(pSideA0Outer.x, pSideA0Outer.y)                                                   // side at a0, back to start
        close()
    }
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

    drawCrossAssetRing(state, colors, isDark, cx, cy, half, tapFlash)
    drawCornerButtons(mode, timeframe, colors, isDark, cx, cy, half, tapFlash)

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
private fun DrawScope.drawCrossAssetRing(state: WheelUiState, colors: AtomColors, isDark: Boolean, cx: Float, cy: Float, half: Float, tapFlash: Map<String, Animatable<Float, *>>) {
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
        val path = roundedWedgePath(cx, cy, r0, r1, a0, a1, px(WHEEL_CELL_CORNER_RADIUS_DP))
        // Plate first (matches drawPairRing/drawCurrencyRing's own base), then the hue wash on
        // top — see XA_WASH_ALPHA above for why. Borderless, corners rounded — the pill treatment,
        // not the old bordered/sharp-cornered cell.
        drawPath(path, color = colors.surfaceRaised)
        val bg = hue?.copy(alpha = XA_WASH_ALPHA) ?: colors.surface
        drawPath(path, color = bg)
        // Aesthetics pass, 2026-09-03 — lighten(hue, 0.45) only reads against a near-black wedge
        // fill (dark theme); on a light wedge it washed the label toward white on white, nearly
        // invisible. Same fix shape as ScrollingPills' pill text: raw hue in light theme, it's
        // already tuned to sit on a light fill.
        val labelColor = hue?.let { if (isDark) lighten(it, 0.45f) else it } ?: colors.textMuted
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
 * Fill is always [colors.controlSurface] and the border is always [colors.controlBorder] — no
 * separate selected-state border (Pieter, 2026-09-03 follow-up: the white border read as heavy;
 * "pressed and active" is carried by the label colour alone, [colors.textPrimary] when selected,
 * same signal the rest of the wheel already uses text/fill colour for rather than a border). An
 * inert timeframe button gets no separate treatment either; it reads identically to any other
 * unselected button, exactly like its tap being dropped reads identically to tapping empty space.
 */
private fun DrawScope.drawCornerButtons(
    mode: WheelMode, timeframe: Timeframe, colors: AtomColors, isDark: Boolean, cx: Float, cy: Float, half: Float,
    tapFlash: Map<String, Animatable<Float, *>>,
) {
    val g = WheelGeometry
    val r0 = g.TOGGLE_R0_FRAC * half
    val r1 = g.TOGGLE_R1_FRAC * half
    val labelR = r0 + (r1 - r0) * 0.4f
    val cornerRadiusPx = px(7f)
    // Pieter, 2026-09-03 follow-up — light theme only: controlBorder (shared with the Summary
    // button/Cascade rows/ticker chips) read too faint against the trapezoids' own controlSurface
    // fill specifically, so these get their own slightly-darker border rather than changing the
    // shared token everywhere else it's used. Dark theme is untouched.
    val borderColor = if (isDark) colors.controlBorder else lerp(colors.controlBorder, Color.Black, 0.15f)
    // The mode toggle is always "on" — the wheel is always in one of its two modes, never
    // neither — so it always carries the selected look; only its label swaps.
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
        drawPath(path, color = colors.controlSurface)
        drawPath(path, color = borderColor, style = Stroke(px(1f)))
        // Pieter, 2026-09-03 — wings get the same brief tap wash as every other cell.
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
        // colour (borderColor, was hairline before the control treatment) instead of textMuted,
        // so an inert wing reads as one dim unit exactly like the cross-asset ring's inert cells
        // do. Currencies-mode behaviour is unchanged.
        val labelColor = if (spec.selected) colors.textPrimary else if (inCurrencies) colors.textMuted else borderColor
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
        drawPath(bgPath, color = colors.hairline.copy(alpha = 0.4f * alpha), style = Stroke(px(0.9f)))

        val v = (strengthAnims[c.code]?.value ?: c.strength.toFloat())
        val fillEnd = r0 + (graphMax - r0) * (v / 100f)
        // "Soft corners" experiment, 2026-09-03 — borderless, rounded, the same pill-matching
        // wash as the cross-asset cells (XA_WASH_ALPHA). The cell itself (bgPath, above) is
        // untouched — sharp-cornered plate + quiet hairline, same as before; only the graph fill
        // gets the new treatment.
        val hue = if (v >= 50f) colors.bull else colors.bear
        if (fillEnd > r0 + 2f) {
            val fillPath = roundedWedgePath(cx, cy, r0 + 2f, fillEnd, a0 + 1.2f, a1 - 1.2f, px(WHEEL_CELL_CORNER_RADIUS_DP))
            drawPath(fillPath, color = hue.copy(alpha = XA_WASH_ALPHA), alpha = alpha)
        }

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
        val hue = tintForDir(node.direction, colors)

        val bgPath = wedgePath(cx, cy, r0, r1, a0, a1)
        drawPath(bgPath, color = colors.surfaceRaised.copy(alpha = alpha))
        drawPath(bgPath, color = colors.hairline.copy(alpha = 0.4f * alpha), style = Stroke(px(0.9f)))

        // "Soft corners" experiment, 2026-09-03 — borderless, rounded, the same pill-matching
        // wash as the cross-asset cells (XA_WASH_ALPHA). The cell itself (bgPath, above) is
        // untouched — sharp-cornered plate + quiet hairline, same as before; only the graph fill
        // gets the new treatment.
        val levelValue = (levelAnims[node.pair]?.value ?: node.level.toFloat()).coerceIn(0f, 6f)
        val fillFrac = levelValue / 6f
        if (fillFrac > 0f) {
            val rOut = r0 + (graphMax - r0) * fillFrac
            val fillPath = roundedWedgePath(cx, cy, r0 + 1f, rOut, a0 + 2f, a1 - 2f, px(WHEEL_CELL_CORNER_RADIUS_DP))
            drawPath(fillPath, color = hue.copy(alpha = XA_WASH_ALPHA), alpha = alpha)
        }

        val platePath = wedgePath(cx, cy, plateR0, plateR1, a0 + 0.6f, a1 - 0.6f)
        drawPath(platePath, color = colors.surface.copy(alpha = alpha))
        curvedLabel(cx, cy, labelR, mid, a0, a1, node.pair, colors.textPrimary.copy(alpha = alpha), sp(12f), bold = false)

        // A level-6 pair is indicated by its fill/border alone — no separate rim glow treatment.
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
        // Aesthetics pass, 2026-09-03 — was a one-off TextStyle (9.sp/Bold, off the 4-level
        // scale); now the actual Caption/Title tokens, which also gives the hub Inter once
        // AtomType picks it up, and drops a fifth ad-hoc font weight nothing else used.
        add("REGIME" to AtomType.Caption.copy(color = colors.textMuted))
        add(state.nucleus.regimeLabel to AtomType.Title.copy(color = tint))
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
