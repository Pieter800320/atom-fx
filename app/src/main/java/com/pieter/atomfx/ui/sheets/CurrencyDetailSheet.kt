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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.ConvictionEntry
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.components.Pill
import com.pieter.atomfx.ui.components.ScrollingPills
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

/**
 * Design's `CurrencyDetailSheet`: CSM 3-TF + breadth + drivers + expressing pairs. Reached by
 * tapping a specific currency (StatusStrip's Leader/Laggard values, `CurrencyFlowSheet`'s
 * leader/laggard rows) — distinct from the market-wide `CurrencyFlowSheet` (Flow ring tap).
 *
 * 2026-09-03 — visual pass to match Regime/Flow/Breadth: D1/H4/H1 strength+driver collapsed from
 * six plain rows into three tinted squares (`MomentumSheet`'s own label-above/value+delta
 * recipe), "Expressed by" became clickable Electric-Treatment pills instead of plain rows, and a
 * short static "Drivers" line was added (Pieter's own macro-driver shorthand per currency — real
 * reference copy, not derived from `signals.json`, same category as the CHoCH warning text).
 *
 * "Expressed by" lists every pair from `scanner/csm.py`'s 16-pair `STRENGTH_PAIRS` — the actual
 * CSM-calculation universe, not the 12-pair wheel and not "every currency in the FX market"
 * (Pieter corrected both narrower and broader misreadings of this in the same session). Only the
 * 10 of those 16 that are also wheel pairs have real bias data (`signals.pairs`); the other 6
 * (EURGBP/EURCHF/GBPCHF/AUDNZD/AUDCAD/GBPAUD) render as a quiet, non-tappable neutral pill.
 *
 * Deliberately NOT built: the mockup's "Show on wheel" CTA — no wheel-highlight capability exists
 * anywhere in the app yet to wire it to; flagged to Pieter rather than silently invented.
 */
@Composable
fun CurrencyDetailSheet(currency: String, signals: Signals, colors: AtomColors, onPairClick: (String) -> Unit = {}) {
    val h4Delta = signals.csmDelta["h4"]?.get(currency)
    val breadth = signals.breadth["h4"]?.get(currency)
    val bandColor = when (breadth?.band?.lowercase()) {
        "strong" -> colors.bull
        "moderate" -> colors.watch
        "weak" -> colors.bear
        else -> colors.textMuted
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = currency, style = AtomType.Display.copy(color = colors.textPrimary))
        Text(
            text = listOfNotNull(
                CCY_NAMES[currency],
                breadth?.band,
                h4Delta?.let { "flow ${deltaText(it)}" },
            ).joinToString(" · "),
            style = AtomType.Caption.copy(color = bandColor),
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CcyTfSquare("D1", signals.csm["d1"]?.get(currency), signals.csmDelta["d1"]?.get(currency), colors, Modifier.weight(1f))
            CcyTfSquare("H4", signals.csm["h4"]?.get(currency), signals.csmDelta["h4"]?.get(currency), colors, Modifier.weight(1f))
            CcyTfSquare("H1", signals.csm["h1"]?.get(currency), signals.csmDelta["h1"]?.get(currency), colors, Modifier.weight(1f))
        }

        SheetDivider(colors)
        if (breadth?.pct != null) {
            SheetRow("Breadth (H4)", "${breadth.support}/${breadth.total} · ${breadth.band ?: "—"}", colors, bandColor)
            BarMeter(
                fraction = breadth.pct.toFloat(),
                color = bandColor,
                colors = colors,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        } else {
            NotAvailableRow("Breadth (H4)", colors)
        }

        val convictionEntry = signals.conviction?.currencies?.get(currency)
        if (convictionEntry?.conviction != null) {
            SheetDivider(colors)
            ConvictionSection(convictionEntry, signals.conviction?.cotStale == true, colors)
        }

        SheetDivider(colors)
        Text(text = "DRIVERS", style = AtomType.Caption.copy(color = colors.textSecondary), modifier = Modifier.padding(bottom = 8.dp))
        Text(
            text = CCY_DRIVERS[currency] ?: "—",
            style = AtomType.Body.copy(color = colors.textSecondary),
        )

        val expressingPairs = CSM_STRENGTH_PAIRS.filter { it.take(3) == currency || it.takeLast(3) == currency }
        if (expressingPairs.isNotEmpty()) {
            SheetDivider(colors)
            Text(text = "EXPRESSED BY", style = AtomType.Caption.copy(color = colors.textSecondary), modifier = Modifier.padding(bottom = 8.dp))
            val pills = expressingPairs.map { pair ->
                val pairBlock = signals.pairs[pair]
                if (pairBlock != null) {
                    val tint = when (pairBlock.pills?.h4) {
                        "bull" -> colors.bull
                        "bear" -> colors.bear
                        else -> colors.neutral
                    }
                    Pill(text = pair, tint = tint, electric = true, onClick = { onPairClick(pair) })
                } else {
                    Pill(text = pair, tint = colors.textMuted, electric = true)
                }
            }
            ScrollingPills(pills = pills, colors = colors)
        }
    }
}

private val CCY_NAMES = mapOf(
    "USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound", "JPY" to "Japanese Yen",
    "CHF" to "Swiss Franc", "AUD" to "Australian Dollar", "CAD" to "Canadian Dollar", "NZD" to "New Zealand Dollar",
)

// Mirrors scanner/csm.py's STRENGTH_PAIRS exactly (frozen, read-only reference — this is the
// actual 16-pair universe the CSM strength calculation is built from, not a Kotlin re-derivation
// of it). Kept local rather than in WheelGeometry since it's a CSM-domain fact, not wheel geometry.
private val CSM_STRENGTH_PAIRS = listOf(
    "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "USDCAD", "NZDUSD",
    "AUDJPY", "NZDJPY", "CADJPY", "EURGBP", "EURCHF", "GBPCHF", "AUDNZD", "AUDCAD", "GBPAUD",
)

// Pieter's own macro-driver shorthand per currency — short, static reference copy (same category
// as the Structure tab's CHoCH warning text), not derived from signals.json. Not "the handbook"
// verbatim — a short paraphrase in its style, dot-separated the way the mockup's USD line was.
private val CCY_DRIVERS = mapOf(
    "USD" to "Fed policy & US rate expectations · US growth · global risk sentiment (safe-haven demand) · global USD liquidity.",
    "EUR" to "ECB policy · Eurozone growth · periphery/political risk · trade balance.",
    "GBP" to "BoE policy · UK growth & inflation · fiscal/political risk · current account deficit sensitivity.",
    "JPY" to "BoJ policy vs. global yields (rate differential) · safe-haven demand · carry-trade unwind risk.",
    "CHF" to "SNB policy · safe-haven demand · Swiss current account surplus.",
    "AUD" to "RBA policy · commodity prices (iron ore) · China growth linkage · risk sentiment.",
    "CAD" to "BoC policy · oil prices · US growth linkage (major trade partner).",
    "NZD" to "RBNZ policy · dairy/commodity prices · China growth linkage · risk sentiment.",
)

// Same squircle/height/wash formula as MomentumSheet's MomBar — this is the general "3 tinted
// readouts in a row" pattern (also used by RegimeSheet's D1/H4/H1), duplicated locally rather
// than shared since each caller's data shape (value+delta here, vs a single word in Regime)
// differs enough that a shared composable would need its own awkward parameterisation.
private val CCY_TF_SHAPE = RoundedCornerShape(11.dp)
private val CCY_TF_HEIGHT = 52.dp
private const val CCY_TF_LIT_AMOUNT = 0.08f

@Composable
private fun CcyTfSquare(label: String, value: Double?, delta: Double?, colors: AtomColors, modifier: Modifier = Modifier) {
    val hue = directionHue(delta, colors)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(CCY_TF_HEIGHT)
                .background(lerp(colors.surfaceRaised, hue, CCY_TF_LIT_AMOUNT), CCY_TF_SHAPE)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value?.let { it.toInt().toString() } ?: "—", style = AtomType.Body.copy(color = colors.textPrimary))
            if (delta != null) {
                Text(text = deltaText(delta), style = AtomType.Caption.copy(color = hue))
            }
        }
    }
}

private fun directionHue(delta: Double?, colors: AtomColors): Color = when {
    delta == null -> colors.neutral
    delta > 0 -> colors.bull
    delta < 0 -> colors.bear
    else -> colors.neutral
}

private fun deltaText(delta: Double): String {
    val sign = if (delta > 0) "+" else ""
    return "$sign${delta.toInt()}"
}

/** Signals Roadmap §4 — the COT-based Conviction/crowding overlay, added 2026-09-04. Same
 *  SheetRow + BarMeter language the Breadth row above already uses: signed score as the row
 *  value (bull/bear tinted), magnitude as the bar length. A |score| >= 80 reading gets the
 *  same "unmistakable warning" register as the Structure tab's CHoCH callout — this is a
 *  crowded/contrarian-risk read, not a routine number. Absent entirely (caller already guards
 *  on `conviction != null`) rather than a placeholder — this is a weekly overlay, not always
 *  fresh the moment a currency's own data exists. */
@Composable
private fun ConvictionSection(entry: ConvictionEntry?, cotStale: Boolean, colors: AtomColors) {
    val score = entry?.conviction ?: return
    val tint = when {
        score > 0 -> colors.bull
        score < 0 -> colors.bear
        else -> colors.textMuted
    }
    SheetRow("Conviction", "%+d".format(java.util.Locale.US, score), colors, tint)
    BarMeter(
        fraction = (kotlin.math.abs(score) / 100f).coerceIn(0f, 1f),
        color = tint,
        colors = colors,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
    if (kotlin.math.abs(score) >= 80) {
        Text(
            text = "Crowded — contrarian risk",
            style = AtomType.Caption.copy(color = colors.bear),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (cotStale) {
        Text(
            text = "COT data stale",
            style = AtomType.Caption.copy(color = colors.textMuted),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
