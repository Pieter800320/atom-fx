package com.pieter.atomfx.ui.wheel

/**
 * The one thing about the wheel that is truly fixed regardless of device or content:
 * angular identity (Design §6.1). Radii are computed in pixels at draw time in
 * [WheelCanvas] from actual measured text (nucleus content, rim labels) so the wheel fits
 * the screen and its own text exactly, rather than scaling a fixed proportional constant.
 *
 * Pieter's layout: 13 equally-spaced radials, not 12 — slot 0 (12 o'clock) is the ring-number
 * legend (six small numbered nodes, one per ring), and the 12 pairs occupy slots 1..12
 * clockwise from there, in this specific order (not the frozen-engine pair order).
 */
object WheelGeometry {

    const val SLOT_COUNT = 13

    /** The 12 tradable pairs, clockwise starting just past 12 o'clock (slot 1) — slot 0 is the ring legend. */
    val PAIR_ORDER = listOf(
        "EURUSD", "GBPUSD", "AUDUSD", "NZDUSD", "USDCAD", "USDCHF",
        "EURJPY", "GBPJPY", "AUDJPY", "NZDJPY", "CADJPY", "USDJPY",
    )

    /** Slot 0 — the ring-number legend, fixed at 12 o'clock. */
    const val LEGEND_SLOT = 0

    private fun slotAngleDeg(slot: Int): Float = slot * (360f / SLOT_COUNT) - 90f

    /** A pair's slot is its position in [PAIR_ORDER] shifted by one to leave slot 0 for the legend. */
    private fun pairSlot(pairIndex: Int): Int = pairIndex + 1

    fun angleDeg(pairIndex: Int): Float = slotAngleDeg(pairSlot(pairIndex))

    fun angleRad(pairIndex: Int): Double = Math.toRadians(angleDeg(pairIndex).toDouble())

    val legendAngleRad: Double = Math.toRadians(slotAngleDeg(LEGEND_SLOT).toDouble())
}
