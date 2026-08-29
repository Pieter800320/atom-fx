package com.pieter.atomfx.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The four-level type scale (Design doc §3). Tabular figures are on for every size so
 * numbers don't jitter when they update. Font family is the system default for now;
 * swapping in Inter/IBM Plex Sans is cosmetic polish, not geometry, so it's deferred.
 */
private const val TABULAR_FIGURES = "tnum"

object AtomType {
    val Display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val Title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val Caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.06.em,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}
