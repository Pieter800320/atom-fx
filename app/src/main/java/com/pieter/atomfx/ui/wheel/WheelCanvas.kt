package com.pieter.atomfx.ui.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    val labelHalfDiagonal: Float,
    val labelGap: Float,
    val inertNodeRadius: Float,
    val watchNodeRadius: Float,
    val solidNodeRadius: Float,
)

private fun WheelLayout.radiusForLevel(level: Int): Float = nucleusRadius + level.coerceIn(0, 6) * ringPitch

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
    PotentialState.LOW -> inertNodeRadius
    PotentialState.WATCH -> watchNodeRadius
    PotentialState.TRADEABLE, PotentialState.APLUS -> solidNodeRadius
}

/**
 * A label only ever needs to clear *its own* node — the rim is one pair per angle — so a
 * small (low/watch tier) node lets its label sit closer to the ring than a big (solid tier)
 * one needs. Using one worst-case radius for every label (the previous version) left an
 * oversized, uniform gap wherever the actual node was smaller than that worst case.
 */
private fun WheelLayout.labelRadiusFor(node: PairNode): Float =
    outerRadius + nodeRadiusFor(node.state) + labelHalfDiagonal + labelGap

/** Half the angular chord between two adjacent radials (13 equally-spaced slots, incl. the legend). */
private val HALF_STEP_SIN = sin(Math.toRadians(180.0 / WheelGeometry.SLOT_COUNT)).toFloat()

private val RimLabelStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 9.sp)

/** Pieter: the rim label is identity only (the pair code) — direction now shows inside the node. */
private fun rimLabelStyleFor(colors: AtomColors) = RimLabelStyle.copy(color = colors.textPrimary)

// Pieter: the nucleus needs its own, smaller type scale (not the shared AtomType tokens,
// which stay at the Design doc's sizes for use elsewhere) so both the circle and its text can
// shrink together rather than the text forcing a bigger circle. 10% smaller again on Pieter's
// last pass.
private val NucleusTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
private val NucleusBody = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp)
private val NucleusDisplay = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
private val NucleusConfidence = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 7.sp)

/**
 * Single source of truth for the nucleus's lines. The confidence line is the one genuinely
 * long string in here — "High confidence" is not even the worst case (`regime_h4.confidence`
 * is High/Medium/Low, and "Medium confidence" is the longest of the three) — so rather than
 * size the circle around that width, it's wrapped onto two lines (the value, then the word
 * "confidence"), which is what actually let the circle shrink.
 */
private fun nucleusLines(nucleus: NucleusState, colors: AtomColors): List<Pair<String, TextStyle>> {
    val tint = tintColor(nucleus.tint, colors)
    val confidenceStyle = NucleusConfidence.copy(color = colors.textSecondary)
    return listOf(
        nucleus.strengthWord to NucleusBody.copy(color = colors.textSecondary),
        nucleus.regimeLabel to NucleusTitle.copy(color = tint),
        (if (nucleus.score >= 0) "+" else "") + "%.1f".format(nucleus.score) to
            NucleusDisplay.copy(color = colors.textPrimary),
        nucleus.confidence to confidenceStyle,
        "confidence" to confidenceStyle,
    )
}

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
    // node needs one half-diagonal of headroom on the *inward* side; staying on the canvas
    // needs a second half-diagonal on the *outward* side (both at once, not the same one
    // twice — that was the earlier bug where a label touching the node and bleeding off the
    // shortest screen edge were actually one and the same). Margin is sized against the
    // biggest (solid) node, the worst case that can occur at any rim position.
    val gap = with(density) { 1.dp.toPx() }
    val safety = with(density) { 1.dp.toPx() }
    val labelLayouts = state.nodes.map { textMeasurer.measure(it.pair, RimLabelStyle) }
    val halfDiagonal = labelLayouts.maxOf { l -> sqrt((l.size.width / 2f).pow(2) + (l.size.height / 2f).pow(2)) }
    val margin = marginNodeRadius + 2f * halfDiagonal + gap + safety

    val outerRadius = (canvasSidePx / 2f - margin).coerceAtLeast(nucleusRadius + 10f)
    val ringPitch = (outerRadius - nucleusRadius) / 6f

    // Two constraints, applied to the tier that actually needs each one — not the same one
    // constraint shrinking the whole scale. The angular chord at the smallest occupied radius
    // is only ever tight for whichever tier actually sits at an inner level (in practice, the
    // inert/low tier — the tradeable tier only ever reaches level 6, out where the chord is
    // wide open), so it bounds inert directly; the ring-pitch check bounds the biggest
    // (solid) tier, since that's the one whose reach toward a neighbouring ring matters.
    val desiredInert = with(density) { 38.dp.toPx() }
    val minOccupiedRadius = nucleusRadius + state.nodes.minOf { it.level }.coerceIn(0, 6) * ringPitch
    val chordCap = minOccupiedRadius * HALF_STEP_SIN * 0.96f
    val pitchCap = ringPitch * 0.495f
    val chordSafeInert = minOf(desiredInert, chordCap)
    val solidFromChord = chordSafeInert * TIER_GROWTH * TIER_GROWTH
    val solidNodeRadius = minOf(solidFromChord, pitchCap)
    val shrink = if (solidFromChord > 0f) solidNodeRadius / solidFromChord else 1f
    val watchNodeRadius = chordSafeInert * TIER_GROWTH * shrink
    val inertNodeRadius = chordSafeInert * shrink

    return WheelLayout(
        center = center,
        nucleusRadius = nucleusRadius,
        ringPitch = ringPitch,
        outerRadius = outerRadius,
        labelHalfDiagonal = halfDiagonal,
        labelGap = gap,
        inertNodeRadius = inertNodeRadius,
        watchNodeRadius = watchNodeRadius,
        solidNodeRadius = solidNodeRadius,
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

/**
 * The Energy Wheel (Design §6). Draws the fixed z-stack (§6.2): background field, rings,
 * radial paths, factor markers, nodes, nucleus. No motion yet (Phase 8) — this is the static
 * geometry/theming/touch pass (Architecture §9 Phase 2).
 */
@Composable
fun WheelCanvas(
    state: WheelUiState,
    colors: AtomColors,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onTap: (WheelTapTarget) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(state, density) {
                detectTapGestures { offset ->
                    if (canvasSize.width <= 0) return@detectTapGestures
                    val layout = computeLayout(canvasSize.width.toFloat(), textMeasurer, state, colors, density)
                    resolveTapTarget(offset, layout, state.nodes, density)?.let(onTap)
                }
            },
    ) {
        drawWheel(state, colors, isDark, textMeasurer)
    }
}

private fun resolveTapTarget(
    offset: Offset,
    layout: WheelLayout,
    nodes: List<PairNode>,
    density: Density,
): WheelTapTarget? {
    val distFromCenter = offset.distanceTo(layout.center)

    val nearestNode = nodes
        .map { it to layout.pointAt(it.index, layout.radiusForLevel(it.level)).distanceTo(offset) }
        .filter { (node, dist) -> dist <= with(density) { maxOf(22.dp.toPx(), layout.nodeRadiusFor(node.state) + 6.dp.toPx()) } }
        .minByOrNull { it.second }
    if (nearestNode != null) return WheelTapTarget.Node(nearestNode.first.pair)

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

// Pieter: three fixed sizes by tier, not a continuous per-level scale — inert (low) nodes are
// the smallest, the opaque (watch) tier is 5% bigger, and the solid (tradeable/A+) tier is a
// further 5% bigger again, so size still reads as "further out = more potential" without
// every one of the seven levels needing a visibly distinct disc size. The actual pixel sizes
// are resolved in computeLayout() against the real geometry (ring pitch, angular spacing) so
// they can never overlap a ring or a neighbouring node — see [WheelLayout].
private const val TIER_GROWTH = 1.05f

private fun DrawScope.drawWheel(
    state: WheelUiState,
    colors: AtomColors,
    isDark: Boolean,
    textMeasurer: TextMeasurer,
) {
    val layout = computeLayout(size.minDimension, textMeasurer, state, colors, this)

    drawBackgroundField(colors, layout.center)
    drawRings(layout, colors, state)
    drawLegendSpoke(layout, colors)
    state.nodes.forEach { drawRadialPath(it, colors, layout) }
    state.nodes.forEach { drawFactorMarkers(it, colors, layout) }
    // Pieter: the large (higher-level) nodes must sit on top so nothing behind them shows —
    // draw smallest-first so bigger discs are painted last.
    state.nodes.sortedBy { it.level }.forEach { drawPairNode(it, colors, isDark, layout, textMeasurer) }
    state.nodes.forEach { drawRimLabel(it, colors, layout, textMeasurer) }
    drawRingLegend(layout, colors, textMeasurer)
    drawNucleus(state, colors, layout, textMeasurer)
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
    val radius = layout.inertNodeRadius
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
 */
private fun DrawScope.drawRimLabel(node: PairNode, colors: AtomColors, layout: WheelLayout, textMeasurer: TextMeasurer) {
    val point = layout.pointAt(node.index, layout.labelRadiusFor(node))
    val laid = textMeasurer.measure(node.pair, rimLabelStyleFor(colors))
    drawClampedText(laid, Offset(point.x - laid.size.width / 2f, point.y - laid.size.height / 2f))
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

private fun DrawScope.drawRings(layout: WheelLayout, colors: AtomColors, state: WheelUiState) {
    val hairlineWidth = 1.dp.toPx()
    for (i in 1..6) {
        val radius = layout.radiusForLevel(i)
        drawCircle(color = colors.hairline, radius = radius, center = layout.center, style = Stroke(hairlineWidth))

        val ring = state.rings.getOrNull(i - 1) ?: continue
        val tint = tintColor(ring.tint, colors)
        val isOuter = i == 6
        drawCircle(
            color = tint.copy(alpha = if (isOuter) 0.22f else 0.12f),
            radius = radius,
            center = layout.center,
            style = Stroke(if (isOuter) hairlineWidth * 2.5f else hairlineWidth * 1.5f),
        )
    }
}

private fun DrawScope.drawRadialPath(node: PairNode, colors: AtomColors, layout: WheelLayout) {
    val start = layout.pointAt(node.index, layout.nucleusRadius)
    val nodeCenter = layout.pointAt(node.index, layout.radiusForLevel(node.level))
    val edge = layout.pointAt(node.index, layout.outerRadius)
    val dirColor = directionColor(node.direction, colors)

    drawLine(color = dirColor.copy(alpha = 0.30f), start = start, end = nodeCenter, strokeWidth = 1.5.dp.toPx())
    drawLine(color = colors.hairline.copy(alpha = 0.35f), start = nodeCenter, end = edge, strokeWidth = 1.dp.toPx())
}

private fun DrawScope.drawFactorMarkers(node: PairNode, colors: AtomColors, layout: WheelLayout) {
    val dirColor = directionColor(node.direction, colors)
    Factor.entries.forEachIndexed { i, factor ->
        val point = layout.pointAt(node.index, layout.radiusForLevel(i + 1))
        val passed = factor in node.factorsPassed
        drawCircle(
            color = if (passed) dirColor else colors.textMuted.copy(alpha = 0.35f),
            radius = if (passed) 3.5.dp.toPx() else 2.5.dp.toPx(),
            center = point,
        )
    }
}

private fun DrawScope.drawPairNode(
    node: PairNode,
    colors: AtomColors,
    isDark: Boolean,
    layout: WheelLayout,
    textMeasurer: TextMeasurer,
) {
    val center = layout.pointAt(node.index, layout.radiusForLevel(node.level))
    val dirColor = directionColor(node.direction, colors)
    val nodeRadius = layout.nodeRadiusFor(node.state)

    // Pieter: opaque and solid nodes must never let the background show through — no alpha
    // fills, no glow. The watch ("opaque") tier is a fully-opaque blend toward the accent
    // colour rather than the accent colour at partial alpha.
    val fillColor = when (node.state) {
        PotentialState.LOW -> colors.surfaceRaised
        PotentialState.WATCH -> lerp(colors.surfaceRaised, dirColor, 0.55f)
        PotentialState.TRADEABLE, PotentialState.APLUS -> dirColor
    }
    val numberColor = when (node.state) {
        PotentialState.LOW, PotentialState.WATCH -> colors.textPrimary
        PotentialState.TRADEABLE, PotentialState.APLUS -> if (isDark) colors.ground else colors.surface
    }

    drawCircle(color = fillColor, radius = nodeRadius, center = center)

    // Pieter: the chevron moves out of the node (it'll live next to the scrollable pills once
    // those exist, per the mockup) — that frees the whole node for the number, sized to fit it.
    val numberStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = (nodeRadius * 0.95f).toSp())
    val numberLayout = textMeasurer.measure(node.potential.toString(), numberStyle)
    drawText(
        textLayoutResult = numberLayout,
        topLeft = Offset(center.x - numberLayout.size.width / 2f, center.y - numberLayout.size.height / 2f),
        color = numberColor,
    )

    drawCircle(color = colors.hairlineStrong, radius = nodeRadius, center = center, style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawNucleus(
    state: WheelUiState,
    colors: AtomColors,
    layout: WheelLayout,
    textMeasurer: TextMeasurer,
) {
    val radius = layout.nucleusRadius
    val center = layout.center
    val tint = tintColor(state.nucleus.tint, colors)

    drawCircle(
        brush = Brush.radialGradient(colors = listOf(colors.groundRadial, colors.ground), center = center, radius = radius),
        radius = radius,
        center = center,
    )
    drawCircle(color = colors.hairlineStrong, radius = radius, center = center, style = Stroke(1.5.dp.toPx()))
    drawCircle(color = tint.copy(alpha = 0.5f), radius = radius, center = center, style = Stroke(1.dp.toPx()))

    val laidOut = nucleusLines(state.nucleus, colors).map { (text, style) -> textMeasurer.measure(text, style) }
    val totalHeight = laidOut.sumOf { it.size.height }
    var y = center.y - totalHeight / 2f
    laidOut.forEach { line ->
        drawClampedText(line, Offset(center.x - line.size.width / 2f, y))
        y += line.size.height
    }
}
