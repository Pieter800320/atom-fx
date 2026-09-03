package com.pieter.atomfx.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Item Library #04 — app-wide standard (Pieter, 2026-09-03): every tappable element's press wash
 * must be masked to its own shape, never left to bleed past a rounded/circular background to the
 * element's plain rectangular touch bounds. Use this instead of a bare `Modifier.clickable`
 * anywhere in this app, going forward.
 *
 * 2026-09-03 follow-up #1 (real bug, not device-specific) — the first version relied on Compose's
 * default `LocalIndication`. `AtomFxTheme` never provides `LocalContentColor`, so that default
 * resolved to Compose's own hardcoded absolute fallback, `Color.Black`, in BOTH themes — visible
 * as a dark wash on `LightColors.surfaceRaised` (light background), invisible on
 * `DarkColors.surfaceRaised` (already dark). Confirmed by Pieter noticing exactly that split.
 *
 * 2026-09-03 follow-up #2 — the first fix over-corrected to a hardcoded `Color.White`, which just
 * flipped the same bug (visible in dark mode, invisible in light mode — Pieter caught this too).
 * The actual fix is theme-aware, not a fixed colour either way: [AtomTheme.colors.textPrimary] is
 * near-white in dark mode and near-black in light mode — precisely the light-on-dark/dark-on-light
 * contrast a ripple needs, and it's the same job `LocalContentColor`/`onSurface` would have done
 * automatically had this theme ever populated them. `composed {}` is needed because
 * `AtomTheme.colors`/`rememberRipple`/`remember` are all `@Composable`.
 *
 * [shape] defaults to a small rounded rect for plain text/row targets that draw no background of
 * their own — pass the real shape (e.g. `RoundedCornerShape(999.dp)` for a pill, `CircleShape` for
 * a badge) whenever the element has a visible background, so the wash matches exactly what's
 * drawn underneath it. `clip` must precede `clickable` in the chain — reversing the order (or
 * omitting `clip`) is what causes the bleed.
 *
 * `rememberRipple` (not the newer `androidx.compose.material3.ripple.ripple()`) because this
 * project's BOM (2025.06.01) resolves material3 to 1.3.2 — the version that introduced `ripple()`
 * is 1.4.0+. `rememberRipple` is hard-deprecated (error level) in the resolved material-ripple
 * 1.8.3, not removed; it still renders correctly, so suppressing is the pragmatic call over
 * bumping material3 app-wide just for this. Revisit once the BOM moves past material3 1.4.
 */
@Suppress("DEPRECATION", "DEPRECATION_ERROR")
fun Modifier.pressWash(
    shape: Shape = RoundedCornerShape(4.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val colors = AtomTheme.colors
    this.clip(shape).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = rememberRipple(color = colors.textPrimary),
        enabled = enabled,
        onClick = onClick,
    )
}
