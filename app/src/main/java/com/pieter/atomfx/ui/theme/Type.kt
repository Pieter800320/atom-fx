package com.pieter.atomfx.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pieter.atomfx.R

/**
 * The four-level type scale (Design doc §3). Tabular figures are on for every size so
 * numbers don't jitter when they update.
 *
 * Aesthetics pass, 2026-09-03 — Inter, per §3 ("a technical grotesque"), previously deferred as
 * "cosmetic polish" and left on the OS system font. One variable font resource
 * (`res/font/inter_variable.ttf`, the `wght` axis) covering every weight the app actually uses,
 * not four static files — each entry below pins the `wght` axis for its [FontWeight] via
 * [FontVariation]. A `TextStyle.copy(fontWeight = ...)` call site (ScrollingPills' electric-pill
 * Normal, CrossAssetSheet's pinned-row SemiBold) resolves against whichever entry matches, same as
 * a normal multi-file font family — this is why weights live in one shared [InterFamily] rather
 * than baked individually per style. minSdk 26 supports variable-font weight selection.
 */
private const val TABULAR_FIGURES = "tnum"

@OptIn(ExperimentalTextApi::class)
private fun interFont(weight: FontWeight) =
    Font(R.font.inter_variable, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val InterFamily = FontFamily(
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
)

object AtomType {
    val Display = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val Title = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val Body = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val Caption = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.06.em,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    // 2026-09-10 (Pieter's ask, explicitly experimental — "I might revert this") — matches the
    // wheel's own 4 wing labels (SETUP/TREND/MOMENTUM/VOLATILITY) exactly: `WheelCanvas.curvedLabel`
    // draws them on a raw Canvas `Paint` with `Typeface.create(Typeface.DEFAULT, NORMAL)` at 11sp,
    // never through AtomType/Inter at all (a Canvas Paint needs a real `android.graphics.Typeface`,
    // not a Compose `FontFamily`, so it never got folded into the 2026-09-03 Inter pass above).
    // FontFamily.Default is Compose's own equivalent of Typeface.DEFAULT — the system sans, not
    // Inter — so using this style anywhere puts a second, different typeface into the app,
    // deliberately, on purpose, as a preview. Every call site pairs this with `.uppercase()` on the
    // text itself (Compose has no CSS-style text-transform) — the wing labels are already typed as
    // literal uppercase strings ("SETUP", not "Setup"), so matching that convention needs the
    // transform applied explicitly wherever the source text isn't already all-caps.
    val WingLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    )
}
