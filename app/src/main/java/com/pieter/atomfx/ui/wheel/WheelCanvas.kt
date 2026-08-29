package com.pieter.atomfx.ui.wheel

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pieter.atomfx.ui.theme.AtomColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a tap on the wheel resolved to (Design §13.1: ring → factor sheet, node → pair sheet, nucleus → regime sheet). */
sealed interface WheelTapTarget {
    data object Nucleus : WheelTapTarget
    data class Node(val pair: String) : WheelTapTarget
    data class Ring(val factor: Factor) : WheelTapTarget
}

/**
 * The wheel's geometry, resolved once per draw/hit-test pass in actual pixels rather than a
 * proportional 0..1000 space — the nucleus and the outer ring both need to fit *measured
 * text* exactly (Pieter: nucleus text must fit perfectly; rim labels must sit right at the
 * ring edge without any pair bleeding off the shortest screen dimension), and text metrics
 * don't scale with an arbitrary viewBox. [nucleusRadius]..[outerRadius] are spaced evenly in
 * six equal steps ([ringPitch] apart) exactly as requested.
 */
private data class WheelLayout(
    val center: Offset,
    val nucleusRadius: Float,
    val ringPitch: Float,
    val outerRadius: Float,
    val labelHalfExtent: Float,
    val labelGap: Float,
    val haloClearance: Float,
    val lowDotRadius: Float,
    val watchNodeRadius: Float,
    val solidNodeRadius: Float,
    val ringLegendRadius: Float,
)

private fun WheelLayout.radiusForLevel(level: Int): Float = nucleusRadius + level.coerceIn(0, 6) * ringPitch

/** Float overload for mid-animation positions (Phase 8) — layout/margin sizing stays on the Int version. */
private fun WheelLayout.radiusForLevel(level: Float): Float = nucleusRadius + level.coerceIn(0f, 6f) * ringPitch

/**
 * A node's actual *drawn* position — [radiusForLevel] floored to a minimum clearance from the
 * nucleus edge. Level 0 alone sits exactly on that edge by the raw formula, which is why it
 * used to render half-hidden under the nucleus fill; levels 1+ are already clear.
 *
 * Pieter's design review: an earlier version also *ceilinged* this, pulling a level-6 node in
 * so its halo wouldn't cross the outer ring — but "this node's centre sits exactly on its own
 * level's ring" is the wheel's whole navigational language (spec: reaching ring 4 means sitting
 * on ring 4), true for every other level, and level 6 is not a special case worth breaking that
 * for. A halo reaching slightly past the outer ring is a normal, minor visual and reads as "this
 * node is highlighted at the boundary," not as broken geometry — unlike a node that visibly
 * doesn't sit on its own ring, which does.
 */
private fun WheelLayout.renderRadiusForLevel(level: Float): Float =
    radiusForLevel(level).coerceAtLeast(nucleusRadius + ringPitch * 0.45f)

private fun WheelLayout.pointAt(index: Int, radius: Float): Offset {
    val rad = WheelGeometry.angleRad(index)
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

/** The ring-number legend's spoke — fixed at 12 o'clock (slot 0 of 13), never a pair. */
private fun WheelLayout.legendPointAt(radius: Float): Offset {
    val rad = WheelGeometry.legendAngleRad
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

private fun WheelLayout.nodeRadiusFor(state: PotentialState): Float = when (state) {
    PotentialState.LOW -> lowDotRadius
    PotentialState.WATCH -> watchNodeRadius
    PotentialState.TRADEABLE, PotentialState.APLUS -> solidNodeRadius
}

/**
 * A label only ever needs to clear *its own* node — the rim is one pair per angle — so a
 * small (low/watch tier) node lets its label sit closer to the ring than a big (solid tier)
 * one needs. Using one worst-case radius for every label (an earlier version) left an
 * oversized, uniform gap wherever the actual node was smaller than that worst case.
 *
 * Pieter's design review: a *per-halo* extra bump here (an even-earlier version) made haloed
 * labels sit visibly farther out than their neighbours — three distinct label distances (low,
 * watch/solid, haloed) reads as misaligned, not intentional. `labelHalfExtent`/`labelGap` alone
 * already clear the halo's own ~4dp reach beyond the disc, so no per-node halo add-on is
 * needed — the halo is still accounted for once, safely, in the *margin* (screen-fit), just not
 * as a per-label offset.
 */
private fun WheelLayout.labelRadiusFor(node: PairNode): Float =
    outerRadius + nodeRadiusFor(node.state) + labelHalfExtent + labelGap

/** Half the angular chord between two adjacent radials (13 equally-spaced slots, incl. the legend). */
private val HALF_STEP_SIN = sin(Math.toRadians(180.0 / WheelGeometry.SLOT_COUNT)).toFloat()

private val RimLabelStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 9.sp)

/** Pieter: the rim label is identity only (the pair code) — direction now shows inside the node. */
private fun rimLabelStyleFor(colors: AtomColors) = RimLabelStyle.copy(color = colors.textPrimary)

// Pieter: the nucleus needs its own, smaller type scale (not the shared AtomType tokens,
// which stay at the Design doc's sizes for use elsewhere) so both the circle and its text can
// shrink together rather than the text forcing a bigger circle. 10% smaller again on Pieter's
// last pass.
private val NucleusTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
private val NucleusBody = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)

/**
 * The nucleus is the wheel's focal point, so it says exactly one thing: strength + regime
 * ("Moderate" / "RANGING"). The regime score and confidence word are one tap away in the
 * Regime sheet — showing all four here (Pieter's design review) fought for attention at the
 * one place that most needs a single, instant read.
 */
private fun nucleusLines(
    nucleus: NucleusState,
    colors: AtomColors,
    tint: Color = tintColor(nucleus.tint, colors),
): List<Pair<String, TextStyle>> = listOf(
    nucleus.strengthWord to NucleusBody.copy(color = colors.textSecondary),
    nucleus.regimeLabel to NucleusTitle.copy(color = tint),
)

/**
 * The true minimum circle radius that contains a vertically-stacked, horizontally-centred
 * block of text lines, plus [padding]. A simple max(width)/2 vs. sum(height)/2 check (what
 * this replaced) treats the nucleus like a bounding *box* — but lines near the top/bottom of
 * the stack sit close to the circle's edge, where the chord is far narrower than the full
 * diameter, so they overflowed the circle even though they "fit" the box.
 */
private fun minCircleRadiusFor(layouts: List<TextLayoutResult>, padding: Float): Float {
    val totalHeight = layouts.sumOf { it.size.height }
    var cursorY = -totalHeight / 2f
    var needed = 0f
    for (l in layouts) {
        val farY = maxOf(abs(cursorY), abs(cursorY + l.size.height))
        val halfWidth = l.size.width / 2f
        needed = maxOf(needed, sqrt(halfWidth.pow(2) + farY.pow(2)))
        cursorY += l.size.height
    }
    return needed + padding
}

/**
 * The margin reserved for rim labels needs *some* solid-node size to clear, but that size is
 * itself only known after the ring geometry (which the margin determines) is resolved — so
 * this resolves in two passes: the first reserves margin for a modest placeholder just to get
 * real ring geometry, the second re-reserves margin for the *actual* resulting solid-node
 * size. Using the aspirational "desired" size for both, instead, was self-defeating: pushing
 * that target up only reserved more edge margin, which starved the ring pitch that caps the
 * real size right back down.
 */
private fun computeLayout(
    canvasSidePx: Float,
    textMeasurer: TextMeasurer,
    state: WheelUiState,
    colors: AtomColors,
    density: Density,
): WheelLayout {
    val placeholderRadius = with(density) { 12.dp.toPx() }
    val pass1 = computeLayoutPass(canvasSidePx, textMeasurer, state, colors, density, placeholderRadius)
    return computeLayoutPass(canvasSidePx, textMeasurer, state, colors, density, pass1.solidNodeRadius)
}

private fun computeLayoutPass(
    canvasSidePx: Float,
    textMeasurer: TextMeasurer,
    state: WheelUiState,
    colors: AtomColors,
    density: Density,
    marginNodeRadius: Float,
): WheelLayout {
    val center = Offset(canvasSidePx / 2f, canvasSidePx / 2f)

    // The nucleus's own (smaller) type scale sized to the true circle it sits in — see
    // minCircleRadiusFor's doc for why this isn't a simple width/height bounding-box check.
    // The floor below is deliberately tiny: the real, always-safe lower bound is the text fit.
    val nucleusLayouts = nucleusLines(state.nucleus, colors).map { (text, style) -> textMeasurer.measure(text, style) }
    val nucleusPadding = with(density) { 2.dp.toPx() }
    val minNucleusRadius = canvasSidePx * 0.0135f
    val nucleusRadius = maxOf(minCircleRadiusFor(nucleusLayouts, nucleusPadding), minNucleusRadius)

    // A label is centred on a point past its own node (see labelRadiusFor) — clearing that
    // node needs one half-extent of headroom on the *inward* side; staying on the canvas needs
    // a second half-extent on the *outward* side. Labels are now rotated tangent to the rim
    // (design review), so a label's *radial* footprint is just its own height — its width runs
    // along the tangent, not outward — regardless of where around the circle it sits; using the
    // full diagonal here (as the old horizontal labels needed) would waste most of that margin
    // now. Margin also has to clear the *halo*, not just the bare disc, on the biggest node.
    val gap = with(density) { 1.dp.toPx() }
    val safety = with(density) { 1.dp.toPx() }
    val haloClearance = with(density) { 5.dp.toPx() }
    val labelLayouts = state.nodes.map { textMeasurer.measure(it.pair, RimLabelStyle) }
    val labelHeight = labelLayouts.maxOf { it.size.height.toFloat() }
    val labelHalfExtent = labelHeight / 2f
    val margin = marginNodeRadius + haloClearance + labelHeight + gap + safety

    val outerRadius = (canvasSidePx / 2f - margin).coerceAtLeast(nucleusRadius + 10f)
    val ringPitch = (outerRadius - nucleusRadius) / 6f

    // Low-tier nodes (levels 0-2) are now small fixed dots, not part of the watch/solid size
    // chain at all (Pieter's design review) — so the chord constraint that used to protect the
    // *smallest* occupied radius from angular collision now anchors to the smallest radius a
    // watch-or-bigger node can actually occupy (level 3+), since that's the tier the chain
    // still sizes. The ring-pitch check still bounds the biggest (solid) tier against reaching
    // its neighbouring ring.
    val desiredWatchBase = with(density) { 46.dp.toPx() } // ~20% bigger than the old 38dp baseline
    val minWatchOrAboveLevel = state.nodes.filter { it.level >= 3 }.minOfOrNull { it.level } ?: 3
    val minOccupiedRadius = nucleusRadius + minWatchOrAboveLevel.coerceIn(0, 6) * ringPitch
    val chordCap = minOccupiedRadius * HALF_STEP_SIN * 0.96f
    val pitchCap = ringPitch * 0.495f
    val chordSafeWatch = minOf(desiredWatchBase, chordCap)
    val solidFromChord = chordSafeWatch * TIER_GROWTH
    val solidNodeRadius = minOf(solidFromChord, pitchCap)
    val shrink = if (solidFromChord > 0f) solidNodeRadius / solidFromChord else 1f
    val watchNodeRadius = chordSafeWatch * shrink
    val lowDotRadius = with(density) { 6.dp.toPx() }

    return WheelLayout(
        center = center,
        nucleusRadius = nucleusRadius,
        ringPitch = ringPitch,
        outerRadius = outerRadius,
        labelHalfExtent = labelHalfExtent,
        labelGap = gap,
        haloClearance = haloClearance,
        lowDotRadius = lowDotRadius,
        watchNodeRadius = watchNodeRadius,
        solidNodeRadius = solidNodeRadius,
        ringLegendRadius = with(density) { 8.dp.toPx() },
    )
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx.pow(2) + dy.pow(2))
}

internal fun tintColor(tint: Tint, colors: AtomColors): Color = when (tint) {
    Tint.BULL -> colors.bull
    Tint.BEAR -> colors.bear
    Tint.WATCH -> colors.watch
    Tint.NEUTRAL -> colors.neutral
}

private fun directionColor(direction: Direction, colors: AtomColors): Color = when (direction) {
    Direction.BULL -> colors.bull
    Direction.BEAR -> colors.bear
    Direction.NEUTRAL -> colors.neutral
}

// ── Phase 8: motion state (Design §7 / spec §28-29, §56) ───────────────────────────────────

/**
 * One pair's animated radial position: [level] is the value actually drawn (an `Animatable`
 * so a redraw is requested each frame it's in flight, per Compose's snapshot-aware Canvas).
 * [targetLevel]/[targetFactorsPassed] are the last values a transition was started *toward* —
 * plain vars, not state, since only [level]/[factorBlend] need to trigger redraws — used to
 * detect "did this pair actually change" on the next poll without re-deriving it from `level`
 * mid-flight. [factorBlend] crossfades marker colors between [previousFactorsPassed] and
 * whatever `PairNode.factorsPassed` currently is, over the same window as the radial move.
 */
private class NodeAnim(initialLevel: Int, initialFactors: Set<Factor>) {
    val level = Animatable(initialLevel.toFloat())
    var targetLevel = initialLevel
    var previousFactorsPassed = initialFactors
    var targetFactorsPassed = initialFactors
    val factorBlend = Animatable(1f)

    /** Design review: every solid-tier node gets a halo now, not just the single top pair. */
    val haloAlpha = Animatable(0f)
    var targetHaloQualifies = false
}

/** Duration scales gently with how far a node moved, capped to Design §7.1's 400-700ms window. */
private fun durationForLevelChange(from: Int, to: Int): Int =
    (400 + (abs(to - from).coerceAtLeast(1) - 1) * 50).coerceIn(400, 700)

/** §7.4: "respect reduce-motion... shorten transitions to a cross-fade" — one short duration for everything. */
private fun effectiveDuration(reduceMotion: Boolean, base: Int): Int = if (reduceMotion) 150 else base

private fun effectiveDelay(reduceMotion: Boolean, staggerIndex: Int): Long =
    if (reduceMotion) 0L else staggerIndex * 60L

/** A crossfading colour, used for the nucleus accent and each ring's tint (Design §7.3). */
private class ColorAnim(initial: Color) {
    var from = initial
    var to = initial
    val progress = Animatable(0f)
}

private fun ColorAnim.current(): Color = lerp(from, to, progress.value)

private suspend fun ColorAnim.transitionTo(target: Color, durationMs: Int) {
    if (target == to) return
    from = current()
    to = target
    progress.snapTo(0f)
    progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
}

/** Design review: every solid-tier (tradeable/A+) node gets a halo, fading in/out as it enters/leaves that tier. */
private fun haloTargetAlpha(isDark: Boolean): Float = if (isDark) 0.6f else 0.35f

private const val GLOW_ALPHA_DARK = 0.14f
private const val GLOW_ALPHA_LIGHT = 0.12f

/** §7.2: only the two high-potential tiers glow at all; everything else is "little/no glow, nearly static." */
private fun glowsAtRest(state: PotentialState): Boolean =
    state == PotentialState.TRADEABLE || state == PotentialState.APLUS

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The Energy Wheel (Design §6). Draws the fixed z-stack (§6.2): background field, rings,
 * radial paths, factor markers, nodes, top-pair halo, nucleus. Geometry itself never moves
 * (§6.9/§56) — only a node's radial position, its factor-marker colours, the nucleus/ring
 * tints, and glow/halo alphas animate, all driven by Design §7 (Phase 8).
 */
@Composable
fun WheelCanvas(
    state: WheelUiState,
    colors: AtomColors,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onTap: (WheelTapTarget) -> Unit = {},
    onLongPress: (String) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val reduceMotion = rememberReduceMotion()

    val nodeAnims = remember { mutableMapOf<String, NodeAnim>() }
    // Seeded synchronously (not in the LaunchedEffect below) so every pair has an entry before
    // the first draw — the effect only needs to run once composition has already guaranteed
    // presence, and can then focus purely on detecting changes and starting animations.
    state.nodes.forEach { node -> nodeAnims.getOrPut(node.pair) { NodeAnim(node.level, node.factorsPassed) } }
    val nucleusColorAnim = remember { ColorAnim(tintColor(state.nucleus.tint, colors)) }
    val ringColorAnims = remember { state.rings.map { ColorAnim(tintColor(it.tint, colors)) } }
    val breathing by rememberInfiniteTransition(label = "wheel-breathing").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathingAlpha",
    )

    LaunchedEffect(state.nodes) {
        val changedPairs = state.nodes.filter { node ->
            val existing = nodeAnims[node.pair]
            existing == null || existing.targetLevel != node.level || existing.targetFactorsPassed != node.factorsPassed
        }.map { it.pair }

        state.nodes.forEach { node ->
            val anim = nodeAnims.getOrPut(node.pair) { NodeAnim(node.level, node.factorsPassed) }
            val levelChanged = anim.targetLevel != node.level
            val factorsChanged = anim.targetFactorsPassed != node.factorsPassed
            val qualifiesForHalo = node.state == PotentialState.TRADEABLE || node.state == PotentialState.APLUS
            val haloChanged = anim.targetHaloQualifies != qualifiesForHalo
            if (!levelChanged && !factorsChanged && !haloChanged) return@forEach

            val staggerIndex = changedPairs.indexOf(node.pair).coerceAtLeast(0)
            val delayMs = effectiveDelay(reduceMotion, staggerIndex)
            val durationMs = effectiveDuration(reduceMotion, durationForLevelChange(anim.targetLevel, node.level))

            if (levelChanged) {
                anim.targetLevel = node.level
                launch {
                    delay(delayMs)
                    anim.level.animateTo(node.level.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
                }
            }
            if (factorsChanged) {
                anim.previousFactorsPassed = anim.targetFactorsPassed
                anim.targetFactorsPassed = node.factorsPassed
                launch {
                    delay(delayMs)
                    anim.factorBlend.snapTo(0f)
                    anim.factorBlend.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
                }
            }
            if (haloChanged) {
                anim.targetHaloQualifies = qualifiesForHalo
                launch {
                    val target = if (qualifiesForHalo) haloTargetAlpha(isDark) else 0f
                    anim.haloAlpha.animateTo(target, tween(effectiveDuration(reduceMotion, 300), easing = FastOutSlowInEasing))
                }
            }
        }
    }

    val nucleusTargetColor = tintColor(state.nucleus.tint, colors)
    LaunchedEffect(nucleusTargetColor) {
        nucleusColorAnim.transitionTo(nucleusTargetColor, effectiveDuration(reduceMotion, 500))
    }
    val ringTargetColors = state.rings.map { tintColor(it.tint, colors) }
    LaunchedEffect(ringTargetColors) {
        ringTargetColors.forEachIndexed { i, target -> ringColorAnims.getOrNull(i)?.transitionTo(target, effectiveDuration(reduceMotion, 500)) }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(state, density) {
                detectTapGestures(
                    onTap = { offset ->
                        if (canvasSize.width <= 0) return@detectTapGestures
                        val layout = computeLayout(canvasSize.width.toFloat(), textMeasurer, state, colors, density)
                        resolveTapTarget(offset, layout, state.nodes, density, textMeasurer)?.let(onTap)
                    },
                    onLongPress = { offset ->
                        if (canvasSize.width <= 0) return@detectTapGestures
                        val layout = computeLayout(canvasSize.width.toFloat(), textMeasurer, state, colors, density)
                        val target = resolveTapTarget(offset, layout, state.nodes, density, textMeasurer)
                        if (target is WheelTapTarget.Node) onLongPress(target.pair)
                    },
                )
            },
    ) {
        drawWheel(state, colors, isDark, textMeasurer, nodeAnims, nucleusColorAnim, ringColorAnims, breathing)
    }
}

private fun resolveTapTarget(
    offset: Offset,
    layout: WheelLayout,
    nodes: List<PairNode>,
    density: Density,
    textMeasurer: TextMeasurer,
): WheelTapTarget? {
    val distFromCenter = offset.distanceTo(layout.center)

    val nearestNode = nodes
        .map { it to layout.pointAt(it.index, layout.renderRadiusForLevel(it.level.toFloat())).distanceTo(offset) }
        .filter { (node, dist) -> dist <= with(density) { maxOf(22.dp.toPx(), layout.nodeRadiusFor(node.state) + 6.dp.toPx()) } }
        .minByOrNull { it.second }
    if (nearestNode != null) return WheelTapTarget.Node(nearestNode.first.pair)

    // A pair's rim label never moves the way its node does as potential animates, so it's a
    // second, more stable route into the same pair sheet (Pieter's design review).
    val labelHit = nodes.firstOrNull { node ->
        val laid = textMeasurer.measure(node.pair, RimLabelStyle)
        val point = layout.pointAt(node.index, layout.labelRadiusFor(node))
        val halfW = laid.size.width / 2f + with(density) { 6.dp.toPx() }
        val halfH = laid.size.height / 2f + with(density) { 6.dp.toPx() }
        abs(offset.x - point.x) <= halfW && abs(offset.y - point.y) <= halfH
    }
    if (labelHit != null) return WheelTapTarget.Node(labelHit.pair)

    val nucleusHitPad = with(density) { 16.dp.toPx() }
    if (distFromCenter <= layout.nucleusRadius + nucleusHitPad) return WheelTapTarget.Nucleus

    val ringBandHalf = with(density) { 12.dp.toPx() } // 24dp total touch band (§6.3)
    val closestRing = (1..6).minByOrNull { i -> abs(distFromCenter - layout.radiusForLevel(i)) }
    return if (closestRing != null && abs(distFromCenter - layout.radiusForLevel(closestRing)) <= ringBandHalf) {
        WheelTapTarget.Ring(Factor.entries[closestRing - 1])
    } else {
        null
    }
}

// Pieter: low-tier (levels 0-2) is now a small fixed dot, independent of this chain entirely.
// Watch and solid stay a two-step scale — solid 5% bigger than watch — so size still reads as
// "further out = more potential" without every level needing a visibly distinct disc. Actual
// pixel sizes are resolved in computeLayout() against the real geometry (ring pitch, angular
// spacing) so they can never overlap a ring or a neighbouring node — see [WheelLayout].
private const val TIER_GROWTH = 1.05f

private fun DrawScope.drawWheel(
    state: WheelUiState,
    colors: AtomColors,
    isDark: Boolean,
    textMeasurer: TextMeasurer,
    nodeAnims: Map<String, NodeAnim>,
    nucleusColorAnim: ColorAnim,
    ringColorAnims: List<ColorAnim>,
    breathing: Float,
) {
    val layout = computeLayout(size.minDimension, textMeasurer, state, colors, this)

    drawBackgroundField(colors, layout.center)
    drawRings(layout, colors, state, ringColorAnims)
    drawLegendSpoke(layout, colors)
    state.nodes.forEach { drawRadialPath(it, colors, layout, nodeAnims.getValue(it.pair)) }
    state.nodes.forEach { drawFactorMarkers(it, colors, layout, nodeAnims.getValue(it.pair)) }
    // Pieter: the large (higher-level) nodes must sit on top so nothing behind them shows —
    // draw smallest-first so bigger discs are painted last.
    state.nodes.sortedBy { it.level }.forEach { drawPairNode(it, colors, isDark, layout, textMeasurer, nodeAnims.getValue(it.pair), breathing) }
    state.nodes.forEach { drawRimLabel(it, colors, layout, textMeasurer) }
    state.nodes.forEach { drawHalo(it, colors, layout, nodeAnims.getValue(it.pair)) }
    drawRingLegend(layout, colors, textMeasurer)
    drawNucleus(state, colors, layout, textMeasurer, nucleusColorAnim)
}

/**
 * Pieter: a 13th radial, fixed at 12 o'clock, that isn't a pair — six small nodes numbered
 * 1..6, one sitting on each ring, so a glance at the wheel itself (not just the key row below
 * it) shows which physical ring is Regime, Flow, Breadth, and so on.
 */
private fun DrawScope.drawLegendSpoke(layout: WheelLayout, colors: AtomColors) {
    val start = layout.legendPointAt(layout.nucleusRadius)
    val edge = layout.legendPointAt(layout.outerRadius)
    drawLine(color = colors.hairline, start = start, end = edge, strokeWidth = 1.dp.toPx())
}

private fun DrawScope.drawRingLegend(layout: WheelLayout, colors: AtomColors, textMeasurer: TextMeasurer) {
    val radius = layout.ringLegendRadius
    // Pieter: grey, not a status colour — these read as structural (the ring numbering),
    // never as something tappable, unlike every other coloured element on the wheel.
    val style = TextStyle(fontWeight = FontWeight.Bold, fontSize = (radius * 1.5f).toSp(), color = colors.textMuted)
    val gap = 2.dp.toPx()
    val gradientRadius = size.minDimension * 0.75f
    for (i in 1..6) {
        val ringRadius = layout.radiusForLevel(i)
        val center = layout.legendPointAt(ringRadius)
        val layoutResult = textMeasurer.measure(i.toString(), style)

        // Pieter: the radial line and ring circles must never touch the number — mask them out
        // right behind it with a small disc matched to the background gradient at this exact
        // radius, rather than a flat colour that would visibly mismatch near the nucleus.
        val bgHere = lerp(colors.groundRadial, colors.ground, (ringRadius / gradientRadius).coerceIn(0f, 1f))
        val maskRadius = maxOf(layoutResult.size.width, layoutResult.size.height) / 2f + gap
        drawCircle(color = bgHere, radius = maskRadius, center = center)

        drawText(
            textLayoutResult = layoutResult,
            topLeft = Offset(center.x - layoutResult.size.width / 2f, center.y - layoutResult.size.height / 2f),
        )
    }
}

/**
 * Pieter: pair names always sit at the rim, clear of the outer ring and every node (including
 * the biggest, solid-tier disc — see the margin in [computeLayout]), regardless of the node's
 * own level: identity (name, angle) is permanent; only the node slides along the spoke as
 * potential changes. Direction now shows inside the node itself, next to the number.
 *
 * Design review: labels are rotated tangent to the rim (flush against it, following the
 * circle) rather than sitting horizontal — the standard circular-diagram technique, including
 * the auto-flip that keeps the bottom half right-side-up instead of reading upside down.
 */
private fun DrawScope.drawRimLabel(node: PairNode, colors: AtomColors, layout: WheelLayout, textMeasurer: TextMeasurer) {
    val rad = WheelGeometry.angleRad(node.index)
    val point = layout.pointAt(node.index, layout.labelRadiusFor(node))
    val laid = textMeasurer.measure(node.pair, rimLabelStyleFor(colors))
    val tangentDeg = WheelGeometry.angleDeg(node.index) + 90f
    val rotationDeg = if (sin(rad) > 0) tangentDeg + 180f else tangentDeg
    rotate(degrees = rotationDeg, pivot = point) {
        drawClampedText(laid, Offset(point.x - laid.size.width / 2f, point.y - laid.size.height / 2f))
    }
}

/**
 * `drawText` derives its own internal layout constraints from (topLeft, DrawScope.size) even
 * when handed an already-measured [TextLayoutResult] — a [topLeft] that falls outside the
 * canvas makes that derived constraint negative and crashes. Clamping is a safety net.
 */
private fun DrawScope.drawClampedText(layout: TextLayoutResult, desired: Offset) {
    val maxX = (size.width - layout.size.width).coerceAtLeast(0f)
    val maxY = (size.height - layout.size.height).coerceAtLeast(0f)
    val clamped = Offset(desired.x.coerceIn(0f, maxX), desired.y.coerceIn(0f, maxY))
    drawText(textLayoutResult = layout, topLeft = clamped)
}

private fun DrawScope.drawBackgroundField(colors: AtomColors, center: Offset) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(colors.groundRadial, colors.ground),
            center = center,
            radius = size.minDimension * 0.75f,
        ),
    )
}

private fun DrawScope.drawRings(layout: WheelLayout, colors: AtomColors, state: WheelUiState, ringColorAnims: List<ColorAnim>) {
    val hairlineWidth = 1.dp.toPx()
    for (i in 1..6) {
        val radius = layout.radiusForLevel(i)
        drawCircle(color = colors.hairline, radius = radius, center = layout.center, style = Stroke(hairlineWidth))

        if (state.rings.getOrNull(i - 1) == null) continue
        val tint = ringColorAnims[i - 1].current()
        val isOuter = i == 6
        drawCircle(
            color = tint.copy(alpha = if (isOuter) 0.22f else 0.12f),
            radius = radius,
            center = layout.center,
            style = Stroke(if (isOuter) hairlineWidth * 2.5f else hairlineWidth * 1.5f),
        )
    }
}

private fun DrawScope.drawRadialPath(node: PairNode, colors: AtomColors, layout: WheelLayout, anim: NodeAnim) {
    val start = layout.pointAt(node.index, layout.nucleusRadius)
    val nodeCenter = layout.pointAt(node.index, layout.renderRadiusForLevel(anim.level.value))
    val edge = layout.pointAt(node.index, layout.outerRadius)
    val dirColor = directionColor(node.direction, colors)

    drawLine(color = dirColor.copy(alpha = 0.30f), start = start, end = nodeCenter, strokeWidth = 1.5.dp.toPx())
    drawLine(color = colors.hairline.copy(alpha = 0.35f), start = nodeCenter, end = edge, strokeWidth = 1.dp.toPx())
}

/** Design §7.1: "the marker for the changed factor flips its brightness in sync" with the radial move. */
private fun DrawScope.drawFactorMarkers(node: PairNode, colors: AtomColors, layout: WheelLayout, anim: NodeAnim) {
    val dirColor = directionColor(node.direction, colors)
    val mutedColor = colors.textMuted.copy(alpha = 0.35f)
    Factor.entries.forEachIndexed { i, factor ->
        val point = layout.pointAt(node.index, layout.radiusForLevel(i + 1))
        val wasPassed = factor in anim.previousFactorsPassed
        val isPassed = factor in anim.targetFactorsPassed
        val color = lerp(if (wasPassed) dirColor else mutedColor, if (isPassed) dirColor else mutedColor, anim.factorBlend.value)
        val fromRadius = if (wasPassed) 3.5f else 2.5f
        val toRadius = if (isPassed) 3.5f else 2.5f
        val radius = fromRadius + (toRadius - fromRadius) * anim.factorBlend.value
        drawCircle(color = color, radius = radius.dp.toPx(), center = point)
    }
}

private fun DrawScope.drawPairNode(
    node: PairNode,
    colors: AtomColors,
    isDark: Boolean,
    layout: WheelLayout,
    textMeasurer: TextMeasurer,
    anim: NodeAnim,
    breathing: Float,
) {
    val center = layout.pointAt(node.index, layout.renderRadiusForLevel(anim.level.value))
    val dirColor = directionColor(node.direction, colors)
    val nodeRadius = layout.nodeRadiusFor(node.state)

    // Design review: low-tier is a small, empty, muted dot — no thesis, no false-precision
    // number. Watch and solid are both fully-opaque, direction-coloured discs with white text;
    // solid is bigger and (see drawHalo) always haloed, which is now the only visual line
    // between "developing" and "the real thing."
    val fillColor = when (node.state) {
        PotentialState.LOW -> colors.textMuted
        PotentialState.WATCH, PotentialState.TRADEABLE, PotentialState.APLUS -> dirColor
    }

    // Design §7.2/§2.4: a very subtle breathing glow, high-potential tiers only — drawn behind
    // the node's own opaque fill so it only ever reads outside the disc's edge.
    if (glowsAtRest(node.state)) {
        val baseAlpha = if (isDark) GLOW_ALPHA_DARK else GLOW_ALPHA_LIGHT
        val glowAlpha = baseAlpha * breathing
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(dirColor.copy(alpha = glowAlpha), dirColor.copy(alpha = 0f)),
                center = center,
                radius = nodeRadius * 2.2f,
            ),
            radius = nodeRadius * 2.2f,
            center = center,
        )
    }

    drawCircle(color = fillColor, radius = nodeRadius, center = center)

    if (node.state != PotentialState.LOW) {
        val numberStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = (nodeRadius * 0.95f).toSp())
        val numberLayout = textMeasurer.measure(node.potential.toString(), numberStyle)
        drawText(
            textLayoutResult = numberLayout,
            topLeft = Offset(center.x - numberLayout.size.width / 2f, center.y - numberLayout.size.height / 2f),
            color = Color.White,
        )
    }

    // Pieter: a stroke in a *different* grey than the low-tier dot's own fill read as a fuzzy
    // edge at that small a size — a plain fill has one crisp anti-aliased boundary instead.
    if (node.state != PotentialState.LOW) {
        drawCircle(color = colors.hairlineStrong, radius = nodeRadius, center = center, style = Stroke(1.dp.toPx()))
    }
}

/** Design §6.8/§50, revised: every solid-tier node gets a thin halo, fading in/out with its tier. */
private fun DrawScope.drawHalo(node: PairNode, colors: AtomColors, layout: WheelLayout, anim: NodeAnim) {
    val alpha = anim.haloAlpha.value
    if (alpha <= 0f) return
    val center = layout.pointAt(node.index, layout.renderRadiusForLevel(anim.level.value))
    val nodeRadius = layout.nodeRadiusFor(node.state)
    drawCircle(
        color = directionColor(node.direction, colors).copy(alpha = alpha),
        radius = nodeRadius + 3.dp.toPx(),
        center = center,
        style = Stroke(1.5.dp.toPx()),
    )
}

private fun DrawScope.drawNucleus(
    state: WheelUiState,
    colors: AtomColors,
    layout: WheelLayout,
    textMeasurer: TextMeasurer,
    nucleusColorAnim: ColorAnim,
) {
    val radius = layout.nucleusRadius
    val center = layout.center
    val tint = nucleusColorAnim.current()

    drawCircle(
        brush = Brush.radialGradient(colors = listOf(colors.groundRadial, colors.ground), center = center, radius = radius),
        radius = radius,
        center = center,
    )
    drawCircle(color = colors.hairlineStrong, radius = radius, center = center, style = Stroke(1.5.dp.toPx()))
    drawCircle(color = tint.copy(alpha = 0.5f), radius = radius, center = center, style = Stroke(1.dp.toPx()))

    val laidOut = nucleusLines(state.nucleus, colors, tint).map { (text, style) -> textMeasurer.measure(text, style) }
    val totalHeight = laidOut.sumOf { it.size.height }
    var y = center.y - totalHeight / 2f
    laidOut.forEach { line ->
        drawClampedText(line, Offset(center.x - line.size.width / 2f, y))
        y += line.size.height
    }
}
