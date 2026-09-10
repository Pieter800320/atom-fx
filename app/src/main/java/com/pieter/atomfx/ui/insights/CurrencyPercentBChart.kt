package com.pieter.atomfx.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.chart.PercentBOscillator
import com.pieter.atomfx.ui.components.Pill
import com.pieter.atomfx.ui.components.ScrollingPills
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.WheelGeometry

/**
 * 2026-09-10 (Pieter's ask) — "Currency %B": `percent_b_currency`, the base/quote-corrected
 * sibling to Board %B (`PercentBBoardChart.kt`). Pieter's own catch: Board %B averages every
 * pair's raw %B with no regard for which side is base vs. quote, so a falling reading can mean
 * opposite things pair to pair (EUR/USD falling = USD strong, USD/CAD falling = USD weak). This
 * fixes that per-currency, the same way `csm.py` corrects currency strength from a mixed pair
 * set — see `bb_touch.py`'s `compute_currency_percent_b` for the exact base/quote mirroring.
 *
 * Requested specifically to sit next to Board %B so the two can be compared directly — same
 * `ui/chart/PercentBOscillator.kt` drawing, same legend, only the data source (and the extra
 * currency picker) differ. `WheelGeometry.CCY_ORDER` for the picker row so currency order matches
 * the CSM bar strip elsewhere in the app, not a re-invented order.
 */
@Composable
fun CurrencyPercentBChart(signals: Signals, colors: AtomColors) {
    var selected by remember { mutableStateOf("USD") }
    val block = signals.percentBCurrency[selected]

    ScrollingPills(
        pills = WheelGeometry.CCY_ORDER.map { currency ->
            Pill(
                text = currency,
                tint = if (currency == selected) colors.textPrimary else colors.textMuted,
                emphasized = currency == selected,
                onClick = { selected = currency },
            )
        },
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )

    PercentBOscillator(block?.line.orEmpty(), block?.signal.orEmpty(), colors, dates = block?.dates.orEmpty())
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem("%B", colors.textSecondary, colors)
        LegendItem("Signal (SMA 12)", colors.watch, colors)
    }
    Text(
        text = "$selected's own %B, corrected for which side of each pair it's on — unlike Board %B, a falling reading always means $selected weakening here.",
        style = AtomType.Caption.copy(color = colors.textMuted),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LegendItem(label: String, color: Color, colors: AtomColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).background(color, CircleShape))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
    }
}
