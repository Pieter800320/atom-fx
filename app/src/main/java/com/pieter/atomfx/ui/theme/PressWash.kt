package com.pieter.atomfx.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Item Library #04 — app-wide standard (Pieter, 2026-09-03): every tappable element's press wash
 * (Compose's default ripple/indication) must be masked to its own shape, never left to bleed past
 * a rounded/circular background to the element's plain rectangular touch bounds. Use this instead
 * of a bare `Modifier.clickable` anywhere in this app, going forward.
 *
 * [shape] defaults to a small rounded rect for plain text/row targets that draw no background of
 * their own — pass the real shape (e.g. `RoundedCornerShape(999.dp)` for a pill, `CircleShape` for
 * a badge) whenever the element has a visible background, so the wash matches exactly what's
 * drawn underneath it. `clip` must precede `clickable` in the chain — reversing the order (or
 * omitting `clip`) is what causes the bleed.
 */
fun Modifier.pressWash(
    shape: Shape = RoundedCornerShape(4.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clip(shape).clickable(enabled = enabled, onClick = onClick)
