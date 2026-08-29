package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

private val TABS = listOf("D1", "H4", "H1")

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

        SheetTabs(TABS, selectedTab, colors) { selectedTab = it }
        if (closes.size < 2) {
            NotAvailableRow("Price history (${TABS[selectedTab]})", colors)
        } else {
            LineChart(closes, colors, modifier = Modifier.fillMaxWidth().height(160.dp))
        }
    }
}
