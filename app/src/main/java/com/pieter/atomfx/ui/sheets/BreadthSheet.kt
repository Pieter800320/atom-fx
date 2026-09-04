package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * Design §14.3 — bands compared by `pct`, never raw `support` count (Architecture §5.3's
 * denominator note). The colour is read from the backend's own `band` string, not
 * recomputed from `pct` here: `breadth.py` owns that Strong/Moderate/Weak threshold, and a
 * second copy of it in Kotlin can silently disagree with the backend's own classification.
 */
@Composable
fun BreadthSheet(signals: Signals, colors: AtomColors) {
    val h4 = signals.breadth.h4

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle("CURRENCY BREADTH", colors)

        if (h4.isEmpty()) {
            NotAvailableRow("Breadth (H4)", colors)
            return@Column
        }

        h4.entries.sortedByDescending { it.value.pct ?: 0.0 }.forEach { (ccy, entry) ->
            val pct = entry.pct ?: 0.0
            val bandColor = when (entry.band?.lowercase()) {
                "strong" -> colors.bull
                "moderate" -> colors.watch
                "weak" -> colors.bear
                else -> colors.textMuted
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ccy,
                    style = AtomType.Body.copy(color = colors.textPrimary),
                    modifier = Modifier.width(36.dp),
                )
                BarMeter(
                    fraction = pct.toFloat(),
                    color = bandColor,
                    colors = colors,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    text = "${entry.support ?: "—"}/${entry.total ?: "—"}",
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp).padding(end = 8.dp),
                )
                Text(
                    text = entry.band ?: "—",
                    style = AtomType.Caption.copy(color = bandColor),
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}
