package com.pieter.atomfx.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The one token set the whole app reads (Design doc §2). Every component/Canvas reads
 * [AtomColors] fields — never a literal hex — so dark/light stay a single instrument.
 */
data class AtomColors(
    val ground: Color,
    val groundRadial: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val bull: Color,
    val bullSoft: Color,
    val bear: Color,
    val bearSoft: Color,
    val watch: Color,
    val watchSoft: Color,
    val neutral: Color,
)

val DarkColors = AtomColors(
    ground = Color(0xFF0A0F16),
    groundRadial = Color(0xFF121C29),
    surface = Color(0xFF0E141B),
    surfaceRaised = Color(0xFF161F29),
    hairline = Color(0xFF1E2833),
    hairlineStrong = Color(0xFF2C3947),
    textPrimary = Color(0xFFEAF0F6),
    textSecondary = Color(0xFF9DB0C0),
    textMuted = Color(0xFF61707E),
    // Reverted 2026-09-03 (aesthetics pass): the same-day "darker, more gravitas" experiment
    // (24995C/B74246/B98F3A) measurably hurt legibility everywhere these tokens are drawn as a
    // translucent fill rather than flat text — the Potential/Strength ring bands in particular
    // dropped below the 3:1 WCAG non-text contrast floor at low levels. Back to the design doc's
    // own §2.4 values, which is also the single source of truth per CLAUDE.md. If the "settled"
    // mood is still wanted, get it from glow/alpha restraint elsewhere rather than desaturating
    // the one channel every ring depends on to read at a glance.
    bull = Color(0xFF2FBF71),
    bullSoft = Color(0xFF2FBF71).copy(alpha = 0.13f),
    bear = Color(0xFFE5484D),
    bearSoft = Color(0xFFE5484D).copy(alpha = 0.13f),
    watch = Color(0xFFE7AE3A),
    watchSoft = Color(0xFFE7AE3A).copy(alpha = 0.13f),
    neutral = Color(0xFF61707E),
)

val LightColors = AtomColors(
    ground = Color(0xFFEDF1F6),
    groundRadial = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF2F6FA),
    hairline = Color(0xFFDDE4EC),
    hairlineStrong = Color(0xFFC6D1DD),
    textPrimary = Color(0xFF0E141B),
    textSecondary = Color(0xFF4B5A6B),
    textMuted = Color(0xFF8A98A8),
    // Reverted 2026-09-03 alongside DarkColors — back to the design doc's §2.4 values.
    bull = Color(0xFF159E5B),
    bullSoft = Color(0xFF159E5B).copy(alpha = 0.09f),
    bear = Color(0xFFD0383D),
    bearSoft = Color(0xFFD0383D).copy(alpha = 0.08f),
    watch = Color(0xFFB27A16),
    watchSoft = Color(0xFFB27A16).copy(alpha = 0.08f),
    neutral = Color(0xFF93A0AD),
)

// ── Wheel v2 helpers — derived from tokens, so light + dark both work (no literal hex) ─────────

/** Lightens [color] toward white by [amount] (0..1) — dark-theme-only "electric" text treatment
 *  (a saturated colour reads brighter against a near-black fill); never use in light theme, where
 *  the raw token is already tuned to sit on a light surface. */
fun lighten(color: Color, amount: Float): Color = lerp(color, Color.White, amount.coerceIn(0f, 1f))
