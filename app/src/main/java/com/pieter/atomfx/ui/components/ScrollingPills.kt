package com.pieter.atomfx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.DarkColors
import com.pieter.atomfx.ui.theme.lighten
import com.pieter.atomfx.ui.theme.pressWash

// Item Library #07 — Electric Treatment pill sizing, matched to the wheel's own Currency Flow
// Ticker chips (WheelScreen.kt: chipHeightPx = ticker row height * 0.86, cornerRadiusPx =
// chipHeightPx * 0.42 — "a soft squircle, not a full capsule"). TICKER_HEIGHT there is 30dp, so
// 30 * 0.86 = 25.8dp height, * 0.42 = ~10.8dp corner radius.
private val ELECTRIC_PILL_HEIGHT = 26.dp
private val ELECTRIC_PILL_SHAPE = RoundedCornerShape(11.dp)

/**
 * Design §15 — a reusable horizontally-scrollable pill row: Tradeable Now / Watch, calendar
 * chips, and (later phases) pair-sheet tabs and momentum TF toggles all use this same shape.
 */
data class Pill(
    val text: String,
    val tint: Color,
    val emphasized: Boolean = false, // Design §11: A+ pills get a brighter rim (non-electric pills only)
    // Item Library #07 — Electric Treatment, opt-in per pill list (Pieter, 2026-09-03: Tradeable
    // Now/Watch use it; other ScrollingPills call sites are untouched, so this defaults off).
    val electric: Boolean = false,
    val withBorder: Boolean = false, // meaningful only when electric — the with-border variant
    val onClick: (() -> Unit)? = null,
)

@Composable
fun ScrollingPills(
    pills: List<Pill>,
    colors: AtomColors,
    // Defaults from `colors` itself (Dark/LightColors are the only two instances ever passed —
    // see Theme.kt) so call sites that don't already thread an explicit isDark (Macro,
    // RecommendationSheet) don't need to.
    isDark: Boolean = colors == DarkColors,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // 2026-09-04 — this row lives inside MainActivity's HorizontalPager (Home/Macro/Insights are
    // its three pages), and a same-axis nested scrollable loses gesture ownership to the Pager's
    // own drag detector by default. Three custom-gesture attempts (a NestedScrollConnection, a
    // reactive Pager userScrollEnabled lock, a hand-rolled raw-consumption drag with its own
    // fling) all either didn't stop the tab-swipe conflict or made the row feel heavy/sticky
    // compared to native scrolling. Pieter's call once he'd tested each on-device (2026-09-04):
    // keep swipe-to-change-tab working everywhere, so this stays plain, unmodified
    // `Modifier.horizontalScroll` — any surface that genuinely needs to avoid the conflict
    // (Home's Potential strip) is redesigned not to need horizontal scrolling at all, rather than
    // this shared component fighting the Pager for gesture ownership.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        pills.forEach { pill ->
            // Item Library #07 — Electric Treatment: wash + brightened text always; the border
            // is the with/without-border switch. Non-electric pills keep the original look
            // (flat 13%-alpha wash, plain tint text, emphasized-only border, full pill shape)
            // entirely unchanged.
            val shape = if (pill.electric) ELECTRIC_PILL_SHAPE else RoundedCornerShape(999.dp)
            val wash = if (pill.electric) pill.tint.copy(alpha = 0.18f) else pill.tint.copy(alpha = 0.13f)
            // Aesthetics pass, 2026-09-03 — lighten(tint, 0.45) only reads correctly against a
            // near-black electric-pill fill. Reused unconditionally in light theme it washed the
            // colour toward white on a fill that's already pale — measured ~1.5-1.9:1 text
            // contrast, well under WCAG AA's 4.5:1. Light theme's tint is already tuned to sit on
            // white (Design §2.4), so it needs no lightening at all.
            val textColor = if (pill.electric) { if (isDark) lighten(pill.tint, 0.45f) else pill.tint } else pill.tint
            val hasBorder = if (pill.electric) pill.withBorder else pill.emphasized
            // Pieter, 2026-09-03 — thinner border + lighter text, matched to the wheel's own
            // cross-asset cell treatment (1.0px stroke, un-bolded/regular-weight text) rather
            // than the earlier 1.6dp/SemiBold. Non-electric pills untouched (still Caption's
            // default SemiBold, and never get this border width since hasBorder only fires via
            // legacy `emphasized`, unused by any current caller).
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .let { if (pill.electric) it.height(ELECTRIC_PILL_HEIGHT) else it }
                    .background(wash, shape)
                    .let { if (hasBorder) it.border(if (pill.electric) 1.dp else 1.6.dp, pill.tint.copy(alpha = 0.9f), shape) else it }
                    // Item Library #04 — pressWash() (clip + clickable) comes AFTER the fill/
                    // border, not before: the ripple must draw on top of the background, and
                    // clip needs to wrap only the ripple, not hide it underneath an opaque fill
                    // drawn later in the chain. It's a no-op when the pill has no onClick.
                    .let { base ->
                        pill.onClick?.let { onClick ->
                            base.pressWash(shape) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClick()
                            }
                        } ?: base.clip(shape)
                    }
                    // Pieter, 2026-09-03 follow-up (real bug, not device-specific) — the fixed
                    // ELECTRIC_PILL_HEIGHT (26dp) was still getting the OLD wrap-content pill's
                    // 8dp vertical padding on top of it (8+8=16dp), leaving ~10dp for the text —
                    // not enough, so it clipped top and bottom (confirmed on-device screenshot).
                    // Electric pills rely on verticalAlignment.CenterVertically to centre the
                    // text within the fixed height instead — no vertical padding needed or
                    // wanted there. Non-electric (wrap-content) pills keep their original 8dp.
                    .padding(horizontal = 12.dp, vertical = if (pill.electric) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pill.text,
                    style = AtomType.Caption.copy(
                        color = textColor,
                        fontWeight = if (pill.electric) FontWeight.Normal else AtomType.Caption.fontWeight,
                    ),
                )
            }
        }
    }
}
