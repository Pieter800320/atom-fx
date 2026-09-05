package com.pieter.atomfx.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors

/** The Recommendation glyph's own footprint (top-left of Home, 2026-09-06 — replaces the old
 *  always-visible "Summary" cascade card entirely). */
val RECOMMENDATION_GLYPH_SIZE = 40.dp

/**
 * 2026-09-06 (Pieter's ask) — deliberately NOT a sparkle/lightbulb icon: this button is
 * specifically the *non*-AI counterpart to Insights' own recommendation, so an "AI-generated"
 * coded glyph would send the wrong signal. Same radial-gradient + hairline-ring recipe as the
 * wheel's own hub (`WheelCanvas.drawHub`), at 40dp instead of the hub's own scale — a small needle
 * inside rotates to the recommendation's own direction (up-right for long, down-right for short,
 * a flat dash if neutral/no qualifying pair), so the badge is meaningful at rest, not just a
 * decorative trigger — colour and angle already tell you the bias before you tap anything.
 *
 * Mocked up as an Artifact first (three candidate glyphs shown side by side) — Pieter picked this
 * one over a bullseye ("Target," no way to hint at direction without extra colour logic) and a
 * plain rotated chevron ("reads as a generic expand affordance, not something worth tapping").
 */
@Composable
fun RecommendationGlyph(direction: String?, colors: AtomColors, modifier: Modifier = Modifier) {
    val tint = when (direction) {
        "bull" -> colors.bull
        "bear" -> colors.bear
        else -> colors.textMuted
    }
    // Up-right for bull, down-right for bear — the same "rises to the right / falls to the right"
    // reading convention a price chart already carries, not an arbitrary pair of angles. The
    // needle's own base points straight up; +45 deg (clockwise) lands it up-right, +135 lands it
    // down-right — verified geometrically, not eyeballed (a first pass here got the sign backwards
    // and would have pointed bull up-LEFT; caught before shipping, not after).
    val targetAngle = when (direction) {
        "bull" -> 45f
        "bear" -> 135f
        else -> 0f
    }
    val angle by animateFloatAsState(targetAngle, label = "recommendationNeedle")

    Canvas(modifier = modifier.size(RECOMMENDATION_GLYPH_SIZE)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 1.5.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(listOf(colors.surfaceRaised, colors.surface), center, r),
            radius = r,
            center = center,
        )
        drawCircle(color = colors.hairlineStrong, radius = r, center = center, style = Stroke(1.5.dp.toPx()))
        drawCircle(color = tint.copy(alpha = 0.55f), radius = r, center = center, style = Stroke(1.4.dp.toPx()))

        if (direction == "bull" || direction == "bear") {
            rotate(angle, pivot = center) {
                drawLine(
                    color = tint,
                    start = center,
                    end = Offset(center.x, center.y - r * 0.6f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(color = tint, radius = 2.3.dp.toPx(), center = center)
        } else {
            drawLine(
                color = tint,
                start = Offset(center.x - r * 0.3f, center.y),
                end = Offset(center.x + r * 0.3f, center.y),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
