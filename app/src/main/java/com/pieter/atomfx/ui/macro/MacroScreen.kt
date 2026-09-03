package com.pieter.atomfx.ui.macro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.CurrencyBias
import com.pieter.atomfx.data.model.MacroAssetEntry
import com.pieter.atomfx.data.model.MacroEvidence
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.components.Pill
import com.pieter.atomfx.ui.components.ScrollingPills
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.WheelScreenState
import com.pieter.atomfx.ui.wheel.WheelViewModel

// Non-private: also reused by InsightsScreen's theme-tagged headline chips (same
// five macro evidence axes, Functional Spec §7).
internal val AXIS_LABELS = mapOf(
    "risk" to "Risk", "rates" to "Rates", "usd" to "USD",
    "commodity" to "Commodity", "safe_haven" to "Safe-haven",
)

// Mirrors Home's CARD_SHAPE (TradeableNow.kt / StatusStrip.kt) — the one standard card radius.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * Design §19.2. Full content when `macro_regime` exists (the archetype engine's own key —
 * absent on live data today, same known EXTEND-step gap as `potential`/`recommendation`);
 * otherwise falls back to the frozen W1 regime label so the screen is never blank.
 *
 * Rebuilt 2026-09-03 against `docs/mockups/atom-fx-screen-kit.html`'s `scrMacro()` — the
 * archetype banner, evidence axes, and cross-asset dashboard now follow that layout, and every
 * card/pill uses the same tokens as Home (Color.kt's control/card distinction, ScrollingPills'
 * Electric Treatment) rather than bare text rows.
 */
@Composable
fun MacroScreen(viewModel: WheelViewModel, colors: AtomColors, modifier: Modifier = Modifier) {
    val screenState by viewModel.screenState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            // Top is owned by MainActivity's persistent gear bar — see WheelScreen.kt's same note.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        when (val state = screenState) {
            WheelScreenState.Loading -> CenteredMessage("LOADING…", colors)
            WheelScreenState.Unavailable -> CenteredMessage("DATA UNAVAILABLE", colors)
            is WheelScreenState.Loaded -> MacroContent(state.signals, colors)
        }
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AtomColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = AtomType.Body.copy(color = colors.textSecondary))
    }
}

@Composable
private fun MacroContent(signals: Signals, colors: AtomColors) {
    val regime = signals.macroRegime

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (regime?.primary != null) {
            MacroBannerCard(regime.primary.code, regime.primary.name, regime.primary.confidence, regime.primary.distinctAxes, regime.usdRegime, regime.goldOverlay, regime.narrative, regime.currencyBias, colors)
            EvidenceAxes(regime.evidence, colors)
        } else {
            // No archetype read yet — same card position/shape as the real banner, so the layout
            // doesn't jump once `macro_regime` lands.
            Column(modifier = Modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
                Text(text = "MARKET REGIME", style = AtomType.Caption.copy(color = colors.textSecondary))
                Text(
                    text = signals.macro?.label ?: signals.regimeW1?.regime ?: "—",
                    style = AtomType.Title.copy(color = colors.textPrimary),
                    modifier = Modifier.padding(top = 3.dp, bottom = 6.dp),
                )
                Text(
                    text = "Confidence: ${signals.macro?.confidence ?: signals.regimeW1?.confidence ?: "—"}",
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                )
                Text(
                    text = "The macro archetype read isn't available yet — showing the weekly regime instead.",
                    style = AtomType.Caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        CrossAssetTable(signals.macroAssets, colors)
    }
}

/** The archetype banner — mockup's `.mbanner`: code, name, chips (confidence/USD/gold), the
 *  narrative, and the strong/weak bias baskets, all on one standard card. */
@Composable
private fun MacroBannerCard(
    code: String?, name: String?, confidence: String?, distinctAxes: Int?,
    usdRegime: String?, goldOverlay: String?, narrative: String?, bias: CurrencyBias?, colors: AtomColors,
) {
    Column(modifier = Modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
        Text(text = "REGIME ${code ?: "—"}", style = AtomType.Caption.copy(color = colors.textMuted))
        Text(
            text = name ?: "—",
            style = AtomType.Title.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
        )
        ScrollingPills(
            pills = listOfNotNull(
                confidence?.let { Pill("Confidence $it · ${distinctAxes ?: 0} axes", colors.bull, electric = true) },
                usdRegime?.let { Pill("USD: ${it.replace('_', ' ')}", colors.neutral, electric = true) },
                goldOverlay?.let { Pill("Gold: ${it.replace('_', ' ')}", colors.watch, electric = true) },
            ),
            colors = colors,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (!narrative.isNullOrBlank()) {
            Text(text = narrative, style = AtomType.Body.copy(color = colors.textSecondary))
        }
        if (bias != null && (bias.strong.isNotEmpty() || bias.weak.isNotEmpty())) {
            BiasBaskets(bias, colors, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Strong/weak currency baskets, side by side inside the banner card — a nested grouping, not a
 *  card of its own, so `surfaceRaised` (not `cardSurface`) and no border. */
@Composable
private fun BiasBaskets(bias: CurrencyBias, colors: AtomColors, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BiasBox("STRONG", bias.strong, colors.bull, colors, Modifier.weight(1f))
        BiasBox("WEAK", bias.weak, colors.bear, colors, Modifier.weight(1f))
    }
}

@Composable
private fun BiasBox(label: String, currencies: List<String>, labelColor: Color, colors: AtomColors, modifier: Modifier = Modifier) {
    if (currencies.isEmpty()) return
    // Pieter, 2026-09-03 — a cutout, not a raised nested box: `ground`, not `surfaceRaised` (which
    // was, in dark theme, the exact same colour as the banner's own `cardSurface` behind it —
    // invisible). Matching the page background reads as a hole punched through the card instead.
    Column(modifier = modifier.background(colors.ground, RoundedCornerShape(10.dp)).padding(9.dp)) {
        Text(text = label, style = AtomType.Caption.copy(color = labelColor), modifier = Modifier.padding(bottom = 4.dp))
        Text(text = currencies.joinToString(" · "), style = AtomType.Body.copy(color = colors.textPrimary))
    }
}

// How far a supporting-evidence card's fill leans toward `bull`, dark and light theme alike — the
// same restraint as the design doc's own glow alphas (§2.4/§7.4: 8-18% dark, ~12% light): present
// on close inspection, never a wash of colour. A card never gets a border (Pieter's rule), so the
// "lit" cue has to live in the fill itself rather than a coloured rim.
private const val EVIDENCE_LIT_AMOUNT = 0.08f

/** Each evidence axis on its own standard card (Pieter, 2026-09-03 — the mockup's `.axis-row` is
 *  a plain divided list; cards read better here, matching every other Home/Macro info row).
 *  Supporting evidence ("up") gets a subtly bull-tinted card, not just a coloured dot — see
 *  [EVIDENCE_LIT_AMOUNT]. */
@Composable
private fun EvidenceAxes(evidence: List<MacroEvidence>, colors: AtomColors) {
    if (evidence.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "EVIDENCE", style = AtomType.Caption.copy(color = colors.textSecondary))
        evidence.forEach { e ->
            val fill = if (e.supports) lerp(colors.cardSurface, colors.bull, EVIDENCE_LIT_AMOUNT) else colors.cardSurface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(fill, CARD_SHAPE)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "●",
                    style = AtomType.Body.copy(color = if (e.supports) colors.bull else colors.textMuted),
                )
                Text(
                    text = AXIS_LABELS[e.axis] ?: e.axis ?: "—",
                    style = AtomType.Caption.copy(color = colors.textMuted),
                    modifier = Modifier.width(76.dp),
                )
                Text(
                    text = e.read ?: "—",
                    style = AtomType.Body.copy(color = colors.textSecondary),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The cross-asset dashboard (Functional Spec §19.2 Appendix-A table) — a plain divided list per
 *  the mockup's `.xtab` (hairline under every row), not cards. Three columns, evenly spread across
 *  the full width (Pieter, 2026-09-03 — was fixed-width columns packed to the left inside a
 *  horizontal scroll; three columns fit the screen on their own, no scrolling needed). */
@Composable
private fun CrossAssetTable(macroAssets: Map<String, MacroAssetEntry>, colors: AtomColors) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "CROSS-ASSET", style = AtomType.Caption.copy(color = colors.textSecondary))
        if (macroAssets.isEmpty()) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
            return
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            TableHeaderRow(colors)
            Divider(colors)
            macroAssets.values.forEach { asset ->
                TableRow(asset, colors)
                Divider(colors)
            }
        }
    }
}

@Composable
private fun Divider(colors: AtomColors) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
}

@Composable
private fun TableHeaderRow(colors: AtomColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "Asset", style = AtomType.Caption.copy(color = colors.textMuted),
            modifier = Modifier.weight(1f),
        )
        listOf("Value", "Δ").forEach { header ->
            Text(
                text = header,
                style = AtomType.Caption.copy(color = colors.textMuted),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TableRow(asset: MacroAssetEntry, colors: AtomColors) {
    // Locale.US explicitly — the default locale's decimal separator (e.g. a comma) isn't what a
    // trading number should ever render with, regardless of device region.
    val delta = asset.deltaPct?.let { "%+.1f%%".format(java.util.Locale.US, it) }
        ?: asset.deltaBp?.let { "%+.1fbp".format(java.util.Locale.US, it) } ?: "—"
    val dirColor = when (asset.direction) {
        "up" -> colors.bull
        "down" -> colors.bear
        else -> colors.textSecondary
    }
    // More breathing room between divider lines than a table row usually needs (Pieter, 2026-09-03
    // — was 8dp, read as cramped once the columns spread full-width).
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(
            text = asset.label ?: "—", style = AtomType.Body.copy(color = colors.textPrimary),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = asset.value?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—",
            style = AtomType.Body.copy(color = colors.textPrimary),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = delta, style = AtomType.Body.copy(color = dirColor),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
