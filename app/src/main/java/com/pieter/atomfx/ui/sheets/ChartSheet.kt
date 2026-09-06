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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.chart.LineChart
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

private val TABS = listOf("D1", "H4", "H1")
// Same card shape/fill Pair sheet's own Spark3Row cards use — this chart is the same content
// (LineChart, D1/H4/H1), just one at a time and full-size instead of three small previews.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

// Pieter, 2026-09-06 — "let the sheet come up slightly higher, maybe 8mm": ModalBottomSheet sizes
// itself to content (BottomSheetHost's own doc comment), so there's no separate "sheet height" to
// set — padding the content out is how you make it rise further. 8mm ≈ 50dp at the density-
// independent 160dp/inch dp is defined against (25.4mm/inch ÷ 160dp/inch). Also gives the
// bottom-relocated TF row (below) clearance from the gesture-nav area instead of sitting flush
// against it.
private val EXTRA_RISE = 50.dp

/** Design §16/§19.1 — long-press a node opens this: the native 3-TF close-price line, no candles. */
@Composable
fun ChartSheet(pair: String, signals: Signals, colors: AtomColors) {
    var selectedTab by remember(pair) { mutableIntStateOf(0) }
    val spark = signals.spark[pair]

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(pair, colors)

        val closes = when (selectedTab) {
            0 -> spark?.d1
            1 -> spark?.h4
            else -> spark?.h1
        }.orEmpty()

        if (spark == null) {
            NotAvailableRow("Price history", colors)
            return@Column
        }

        if (closes.size < 2) {
            NotAvailableRow("Price history (${TABS[selectedTab]})", colors)
        } else {
            val pct = (closes.last() - closes.first()) / closes.first() * 100.0
            Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceRaised, CARD_SHAPE).padding(horizontal = 14.dp, vertical = 14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = TABS[selectedTab], style = AtomType.Body.copy(color = colors.textMuted))
                    Text(
                        text = "%+.1f%%".format(java.util.Locale.US, pct),
                        style = AtomType.Body.copy(color = if (pct >= 0) colors.bull else colors.bear),
                    )
                }
                LineChart(closes, colors, modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp))
            }
        }

        // 2026-09-06 (Pieter's ask) — the TF row moved from above the chart to below it, styled
        // exactly like HOME's own D1/H4/H1 row ([ControlButtonRow], shared from WheelScreen's
        // `TimeframeButtons`) instead of the small scrolling-pill `SheetTabs` every other sheet uses.
        ControlButtonRow(
            labels = TABS,
            selected = selectedTab,
            colors = colors,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            onSelect = { selectedTab = it },
        )
        Spacer(modifier = Modifier.height(EXTRA_RISE))
    }
}
