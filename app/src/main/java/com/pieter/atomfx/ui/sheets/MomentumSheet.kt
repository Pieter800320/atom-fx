package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pieter.atomfx.data.model.Momentum
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/** Design §14.4 — `pairs.<PAIR>.mom` is frozen, so this tab is always fully populated. */
@Composable
fun MomentumTabContent(mom: Momentum?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (mom == null) {
            NotAvailableRow("Momentum", colors)
            return@Column
        }
        SheetRow("D1", momLine(mom.d1, mom.dd1), colors)
        SheetRow("H4", momLine(mom.h4, mom.dh4), colors)
        SheetRow("H1", momLine(mom.h1, mom.dh1), colors)
        SheetDivider(colors)
        val cmp = mom.cmp
        Text(
            text = "CMP ${cmp ?: "—"}",
            style = AtomType.Title.copy(color = colors.textPrimary),
        )
        Text(
            text = cmpStatus(cmp),
            style = AtomType.Caption.copy(color = cmpColor(cmp, colors)),
        )
    }
}

private fun momLine(value: Int?, delta: Int?): String {
    if (value == null) return "—"
    val d = delta ?: return "$value"
    val sign = if (d > 0) "+" else ""
    val arrow = if (d > 0) "↑" else if (d < 0) "↓" else "→"
    return "$value  $arrow $sign$d"
}

private fun cmpStatus(cmp: Int?): String = when {
    cmp == null -> "MOMENTUM UNKNOWN"
    cmp >= 60 -> "BULLISH MOMENTUM"
    cmp <= 40 -> "BEARISH MOMENTUM"
    else -> "NEUTRAL MOMENTUM"
}

private fun cmpColor(cmp: Int?, colors: AtomColors) = when {
    cmp == null -> colors.textMuted
    cmp >= 60 -> colors.bull
    cmp <= 40 -> colors.bear
    else -> colors.watch
}
