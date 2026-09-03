package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pieter.atomfx.data.model.PairBlock
import com.pieter.atomfx.ui.theme.AtomColors

/**
 * Design §14.6. `Setup Score` = the frozen `rank.py` score, surfaced via `potential.<PAIR>.setup_rank`
 * (Architecture §5.4) — not recomputed here.
 *
 * 2026-09-03 — Reset score and ATR percentile were previously "not available": `scan_h1.py`
 * computed both every hour (they feed `compute_cont()` and the WHY checklist's ENTRY gate) but
 * never wrote them to `signals.json`. Now additive keys `pairs.<PAIR>.reset_score`/`.atr_pct` —
 * no frozen calculation touched, just also surfacing values that already existed.
 */
@Composable
fun EntryTabContent(setupRank: Double?, pairBlock: PairBlock?, colors: AtomColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Locale.US explicitly — the default locale's decimal separator (e.g. a comma) isn't what
        // a trading number should ever render with, regardless of device region.
        SheetRow("Setup score", setupRank?.let { "%.1f / 10".format(java.util.Locale.US, it) } ?: "—", colors)
        SheetRow("Continuation", pairBlock?.cont?.let { "$it%" } ?: "—", colors)
        SheetRow("ADX", pairBlock?.adx?.let { "%.1f".format(java.util.Locale.US, it) } ?: "—", colors)
        SheetDivider(colors)
        pairBlock?.resetScore?.let { SheetRow("Reset score", "$it / 100", colors) } ?: NotAvailableRow("Reset score", colors)
        pairBlock?.atrPct?.let { SheetRow("ATR percentile", "$it%", colors) } ?: NotAvailableRow("ATR percentile", colors)
    }
}
