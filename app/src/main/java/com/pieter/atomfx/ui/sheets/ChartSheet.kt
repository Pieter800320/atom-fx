package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

// Same card shape/fill Pair sheet's own Spark3Row cards use.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

// Pieter, 2026-09-06 — "let the sheet come up slightly higher, maybe 8mm": ModalBottomSheet sizes
// itself to content (BottomSheetHost's own doc comment), so there's no separate "sheet height" to
// set — padding the content out is how you make it rise further. 8mm ≈ 50dp at the density-
// independent 160dp/inch dp is defined against (25.4mm/inch ÷ 160dp/inch).
private val EXTRA_RISE = 50.dp

/**
 * 2026-09-10 (Pieter's ask) — long-press a wheel node now opens the per-pair %B oscillator here,
 * not the D1/H4/H1 close-price line this sheet showed before. Price itself isn't lost — Pair
 * Sheet's own header (`Spark3Row`) already shows D1/H4/H1 mini sparklines, always visible, no tap
 * needed — this sheet's own bigger LineChart was a genuine duplicate of that, just larger; %B was
 * three taps deep (tap pair → Breakdown tab → scroll) and is a chart-shaped read, a better fit for
 * the wheel's own "long-press for the technical view" gesture. No D1/H4/H1 switcher any more —
 * %B is D1-only by design (`bb_touch.py`'s own 12-period config), unlike price; keeping a
 * switcher pointing at nothing would be worse than removing it.
 */
@Composable
fun ChartSheet(pair: String, signals: Signals, colors: AtomColors) {
    val bbD1 = signals.pairs[pair]?.bbD1

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(pair, colors)

        if (bbD1 == null || bbD1.pctb.isEmpty()) {
            NotAvailableRow("Bollinger %B", colors)
        } else {
            Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "%B ${bbD1.pctb.last().toInt()}", style = AtomType.Body.copy(color = colors.textPrimary))
                    val touchWord = when (bbD1.touching) {
                        "upper" -> "Still touching upper"
                        "lower" -> "Still touching lower"
                        else -> null
                    }
                    if (touchWord != null) {
                        Text(text = touchWord, style = AtomType.Body.copy(color = touchColor(bbD1.touching, colors)))
                    }
                }
                PercentBChart(bbD1, colors, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(EXTRA_RISE))
    }
}

// Same "small local copy, not shared" house style StatusStrip.kt/WatchlistScreen.kt's own
// directionColor uses. "upper" touch reads bear (price stretched up, due to revert), "lower" bull.
private fun touchColor(touching: String?, colors: AtomColors): Color = when (touching) {
    "upper" -> colors.bear
    "lower" -> colors.bull
    else -> colors.textSecondary
}
