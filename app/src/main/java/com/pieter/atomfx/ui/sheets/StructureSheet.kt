package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.StructureBlock
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/** Design §14.5 — a counter-CHoCH gets the prominent warning treatment; everything else is a straight read of `pairs.<PAIR>.structure`. */
@Composable
fun StructureTabContent(structure: StructureBlock?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (structure == null) {
            NotAvailableRow("Structure", colors)
            return@Column
        }
        SheetRow("D1", directionLine(structure.d1?.direction), colors)
        SheetRow("H4", directionLine(structure.h4?.direction), colors)
        SheetDivider(colors)

        val h4 = structure.h4
        SheetRow("Last event (H4)", h4?.event?.uppercase() ?: "—", colors)
        SheetRow("Strength (H4)", h4?.strength?.let { "%.2f".format(it) } ?: "—", colors)

        if (h4?.event == "CHoCH") {
            Text(
                text = "Counter-trend change of character on H4 — a live warning against this thesis.",
                style = AtomType.Caption.copy(color = colors.bear),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun directionLine(direction: String?): String = when (direction) {
    "bull" -> "Bullish"
    "bear" -> "Bearish"
    "neutral" -> "Neutral"
    else -> "—"
}
