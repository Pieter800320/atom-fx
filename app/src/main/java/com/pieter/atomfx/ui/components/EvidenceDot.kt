package com.pieter.atomfx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Pieter, 2026-09-03 — the evidence dot was a "●" text glyph (Macro's EvidenceAxes, Pair sheet's
// WhyChecklist, Cross-Asset sheet's confirm dot); on-device it rendered noticeably larger and
// higher than the adjacent body text, since a bullet glyph's font metrics don't line up with
// Latin text metrics the way they look in a code editor. A real drawn circle, precisely sized,
// fixes both complaints at once and centers correctly against a sibling Text via a plain
// `Alignment.CenterVertically` Row instead of fighting font ascent/descent.
private val EVIDENCE_DOT_SIZE = 7.dp

/** The small "does this item support/confirm" indicator — reused everywhere the app shows a
 *  dot + card + subtle wash for a boolean evidence read. */
@Composable
fun EvidenceDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(EVIDENCE_DOT_SIZE).background(color, CircleShape))
}
