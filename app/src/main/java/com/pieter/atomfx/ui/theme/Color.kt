package com.pieter.atomfx.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.ceil

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
    bull = Color(0xFF159E5B),
    bullSoft = Color(0xFF159E5B).copy(alpha = 0.09f),
    bear = Color(0xFFD0383D),
    bearSoft = Color(0xFFD0383D).copy(alpha = 0.08f),
    watch = Color(0xFFB27A16),
    watchSoft = Color(0xFFB27A16).copy(alpha = 0.08f),
    neutral = Color(0xFF93A0AD),
)

// ── Wheel v2 step ramps — derived from tokens, so light + dark both work (no literal hex) ─────

/** Which colour ramp a wedge fills with. */
enum class Ramp { BULL, BEAR, NEUTRAL }

/**
 * The colour for step [step] (1..6) on a [ramp]. Interpolates the token's soft→full colour so a
 * level-1 band is muted and a level-6 band is the full accent — the mockup's 6-stop green/red
 * ladder, but expressed through [AtomColors] so it re-tints correctly in light mode.
 */
fun stepColor(step: Int, ramp: Ramp, c: AtomColors): Color {
    val s = step.coerceIn(1, 6) / 6f
    val full = when (ramp) {
        Ramp.BULL -> c.bull
        Ramp.BEAR -> c.bear
        Ramp.NEUTRAL -> c.neutral
    }
    val lo = lerp(c.surfaceRaised, full, 0.25f)
    return lerp(lo, full, 0.15f + 0.85f * s)
}

/** Currency-strength colour (0..100) — mirrors the mockup's csColor: green ramp at/above 50, else red. */
fun csColor(value: Int, c: AtomColors): Color {
    val ramp = if (value >= 50) Ramp.BULL else Ramp.BEAR
    val step = ceil(value / 16.67).toInt().coerceIn(1, 6)
    return stepColor(step, ramp, c)
}
