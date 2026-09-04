package com.pieter.atomfx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.ui.theme.AtomColors

// Same box-drawn-on-Canvas discipline as MainActivity's GearGlyph/CalendarGlyph (2026-09-04 fix
// for Unicode glyphs rendering at inconsistent optical sizes) — this is the Reading Window's own
// entry-point glyph, so it needs the same "same size, precisely aligned" guarantee, not a text "📖".
private val BOOK_ICON_SIZE = 22.dp

/**
 * The Reading Window's entry point (Pieter, 2026-09-04): a closed book (cover + spine) with a
 * small "+" badge at the top-right corner — placed at heading level, right-aligned, on every
 * surface that offers a Playbook (RegimeSheet, MacroScreen's archetype banner, a Notification
 * History card). Deliberately a different glyph from Gear/Calendar's line-icon family so it reads
 * as its own thing, not another settings-style control.
 */
@Composable
fun BookPlusGlyph(colors: AtomColors, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(BOOK_ICON_SIZE)) {
        val stroke = size.minDimension * 0.10f

        // Book cover — left-biased so the "+" has room in the top-right corner.
        val bookLeft = size.width * 0.06f
        val bookRight = size.width * 0.62f
        val bookTop = size.height * 0.14f
        val bookBottom = size.height * 0.94f
        val cornerRadius = size.minDimension * 0.09f
        drawRoundRect(
            color = colors.textSecondary,
            topLeft = Offset(bookLeft, bookTop),
            size = Size(bookRight - bookLeft, bookBottom - bookTop),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = stroke),
        )

        // Spine crease, a third of the way in.
        val spineX = bookLeft + (bookRight - bookLeft) * 0.36f
        drawLine(
            color = colors.textSecondary,
            start = Offset(spineX, bookTop + stroke),
            end = Offset(spineX, bookBottom - stroke),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round,
        )

        // "+", top-right, outside the cover's own bounds — no ring around it (Pieter, 2026-09-04:
        // read as muddled), just the mark itself, pushed further right and sized up a step
        // (same feedback, second pass) — the book's own right edge moved in to make room.
        val badgeCenter = Offset(size.width * 0.86f, size.height * 0.24f)
        val badgeRadius = size.minDimension * 0.24f
        val arm = badgeRadius * 0.55f
        drawLine(
            color = colors.textSecondary,
            start = Offset(badgeCenter.x - arm, badgeCenter.y),
            end = Offset(badgeCenter.x + arm, badgeCenter.y),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colors.textSecondary,
            start = Offset(badgeCenter.x, badgeCenter.y - arm),
            end = Offset(badgeCenter.x, badgeCenter.y + arm),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round,
        )
    }
}
