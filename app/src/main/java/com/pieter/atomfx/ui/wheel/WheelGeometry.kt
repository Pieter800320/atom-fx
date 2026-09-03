package com.pieter.atomfx.ui.wheel

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wheel v2 dial geometry (docs/ATOM_FX_WHEEL_V2_SPEC.md). Angular identity is fixed: each
 * segment owns a permanent wedge; only its radial fill changes with the data (Design §6.1).
 * Radii are fractions of the canvas half-side, resolved to pixels at draw time in [WheelCanvas].
 *
 * Degrees here are "compass" degrees: 0° = 12 o'clock, increasing clockwise (matches the mockup's
 * polar()). [polar] converts to screen pixels.
 */
object WheelGeometry {

    /** 12 pairs, clockwise from just past 12 o'clock (angle = index). */
    val PAIR_ORDER = listOf(
        "EURUSD", "GBPUSD", "AUDUSD", "NZDUSD", "USDCAD", "USDCHF",
        "EURJPY", "GBPJPY", "AUDJPY", "NZDJPY", "CADJPY", "USDJPY",
    )

    /** 8 currencies — risk-off bloc first so a "bloom" reads risk-on/off at a glance (Design §6A). */
    val CCY_ORDER = listOf("USD", "JPY", "CHF", "EUR", "GBP", "CAD", "AUD", "NZD")

    // Pieter, 2026-09-03 — reordered to match the confirm-rule's own axis groupings
    // (WHEEL_V2_SPEC.md §3: risk / rates / usd / commodity·safe-haven) instead of the previous
    // near-arbitrary order, which had BTC stranded at the very end instead of with the rest of
    // its own risk cluster.
    /** 10 cross-assets: (macro_assets key, display label). Order = outer-ring position, grouped
     *  risk (VIX, SPX, BTC) → rates (US10Y, US3M, 10Y-3M curve) → USD (DXY) →
     *  commodity/safe-haven (WTI, Copper, Gold). */
    val XASSET_ORDER = listOf(
        "vix" to "VIX", "spx" to "SPX", "btc" to "BTC",
        "us10y" to "US10Y", "us3m" to "US3M", "curve" to "10Y-3M",
        "dxy" to "DXY",
        "wti" to "WTI", "copper" to "COPPER", "gold" to "GOLD",
    )

    // Pieter, 2026-09-03 — the fixed 15° "nothing starts exactly at 12 o'clock" rotation is gone:
    // USD (currencies) and EURUSD (pairs) are meant to be the wheel's own anchors — the reserve
    // currency and the world's most-traded pair — and a shared flat offset put neither of them
    // there (their wedge CENTRES landed at ~37.5°/~30° respectively, not 0°). See
    // [centerOffsetDeg] below for why one shared constant could never have centred both anyway.

    // Radius fractions of the canvas half-side (mockup viewBox 800, centre 400).
    const val HUB_FRAC = 0.275f      // hub outer radius (110/400)
    const val RING_R0_FRAC = 0.31f   // middle ring inner (124/400)
    const val RING_R1_FRAC = 0.74f   // middle ring outer (296/400)
    const val XA_R0_FRAC = 0.765f    // outer cross-asset ring inner (306/400)
    const val XA_R1_FRAC = 0.905f    // outer cross-asset ring outer (362/400)

    // The four corner buttons — a 4th ring, but deliberately different from every data cell:
    // tapered trapezoids (wide at the hub-facing edge, narrower at the tip — [TOGGLE_INNER_SPAN_DEG]
    // vs [TOGGLE_OUTER_SPAN_DEG]) with curved top/bottom edges (following the dial's own radii, not
    // flat chords) and only the two outer corners rounded. Bottom-left is the single Pairs/
    // Currencies mode toggle (thumb zone in portrait grip; Pieter, 2026-09-03 — was two separate
    // buttons at bottom-left/bottom-right, merged into one so bottom-right could become a third
    // timeframe button). Top corners + bottom-right are the Currencies-mode D1/H4/H1 timeframe
    // toggle — inert in Pairs mode, since pair `potential` isn't computed per-timeframe.
    const val TOGGLE_R0_FRAC = 0.935f    // gap from XA_R1_FRAC ~0.03 — matches the other ring gaps
                                          // (hub→ring 0.035, ring→XA 0.025), not the near-zero gap before
    const val TOGGLE_R1_FRAC = 1.18f     // thickness ~0.245 vs the XA ring's 0.14 — ~75% thicker
    const val TOGGLE_INNER_SPAN_DEG = 34f // angular width at r0 (wide base)
    const val TOGGLE_OUTER_SPAN_DEG = 18f // angular width at r1 (tapered tip)

    const val TOGGLE_MODE_CENTER_DEG = 225f // bottom-left — Pairs/Currencies (single toggle)
    const val TOGGLE_H1_CENTER_DEG = 135f   // bottom-right
    const val TOGGLE_H4_CENTER_DEG = 45f    // top-right
    const val TOGGLE_D1_CENTER_DEG = 315f   // top-left

    /** A corner button's curved-and-tapered boundary: wide at r0, narrow at r1. */
    data class CornerAngles(val innerA0: Float, val innerA1: Float, val outerA0: Float, val outerA1: Float)

    fun cornerButtonAngles(centerDeg: Float): CornerAngles {
        val innerHalf = TOGGLE_INNER_SPAN_DEG / 2f
        val outerHalf = TOGGLE_OUTER_SPAN_DEG / 2f
        return CornerAngles(
            innerA0 = centerDeg - innerHalf, innerA1 = centerDeg + innerHalf,
            outerA0 = centerDeg - outerHalf, outerA1 = centerDeg + outerHalf,
        )
    }

    /** The wide (inner) angular range, used for hit-testing — generous tap zone (Design §16). */
    fun cornerHitRange(centerDeg: Float): Pair<Float, Float> {
        val half = TOGGLE_INNER_SPAN_DEG / 2f
        return (centerDeg - half) to (centerDeg + half)
    }

    fun sliceDeg(count: Int): Float = 360f / count

    /**
     * The rotation that puts segment 0's CENTRE exactly at 12 o'clock (0°) for a ring of [count]
     * segments — segment 0 spans `0..slice` with no offset, so its centre sits at `slice/2`;
     * this cancels that out. Computed per-[count], not a single shared constant, because rings
     * of different sizes need different offsets to each achieve this — currencies (8, 45°/slice)
     * need −22.5°, pairs (12, 30°/slice) need −15°; no one value centres both at once.
     */
    fun centerOffsetDeg(count: Int): Float = -180f / count

    /** The (a0, a1) compass-degree span of segment [index] of [count], leaving a [gapDeg] gutter. */
    fun segAngles(count: Int, index: Int, gapDeg: Float): Pair<Float, Float> {
        val slice = sliceDeg(count)
        val offset = centerOffsetDeg(count)
        val a0 = offset + index * slice + gapDeg
        val a1 = offset + (index + 1) * slice - gapDeg
        return a0 to a1
    }

    fun midDeg(count: Int, index: Int): Float {
        val (a0, a1) = segAngles(count, index, 0f)
        return (a0 + a1) / 2f
    }

    /** Compass degrees (0 = top, clockwise) → screen pixel point. */
    fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
        val a = Math.toRadians((deg - 90f).toDouble())
        return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }

    /** Screen point → compass degrees (0 = top, clockwise), 0..360. */
    fun compassDeg(cx: Float, cy: Float, p: Offset): Float {
        val deg = Math.toDegrees(kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat() + 90f
        return ((deg % 360f) + 360f) % 360f
    }

    /** Which segment index a compass degree falls in, for a ring of [count] segments. */
    fun segIndexAt(count: Int, deg: Float): Int {
        val slice = sliceDeg(count)
        val offset = centerOffsetDeg(count)
        val rel = ((deg - offset) % 360f + 360f) % 360f
        return (rel / slice).toInt().coerceIn(0, count - 1)
    }
}
