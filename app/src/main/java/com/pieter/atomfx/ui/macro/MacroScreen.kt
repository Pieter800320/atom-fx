package com.pieter.atomfx.ui.macro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.CurrencyBias
import com.pieter.atomfx.data.model.MacroAssetEntry
import com.pieter.atomfx.data.model.MacroEvidence
import com.pieter.atomfx.data.model.MacroRegimeBlock
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.push.buildRegimeExplanation
import com.pieter.atomfx.ui.components.BookPlusGlyph
import com.pieter.atomfx.ui.components.EvidenceDot
import com.pieter.atomfx.ui.components.Pill
import com.pieter.atomfx.ui.components.ScrollingPills
import com.pieter.atomfx.ui.reading.ReadingTarget
import com.pieter.atomfx.ui.sheets.ASSET_AXES
import com.pieter.atomfx.ui.sheets.CrossAssetRow
import com.pieter.atomfx.ui.sheets.SheetDivider
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import com.pieter.atomfx.ui.wheel.WheelGeometry
import com.pieter.atomfx.ui.wheel.WheelScreenState
import com.pieter.atomfx.ui.wheel.WheelViewModel

// Non-private: also reused by InsightsScreen's theme-tagged headline chips (same
// five macro evidence axes, Functional Spec §7).
internal val AXIS_LABELS = mapOf(
    "risk" to "Risk", "rates" to "Rates", "usd" to "USD",
    "commodity" to "Commodity", "safe_haven" to "Safe-haven",
)

// Mirrors Home's CARD_SHAPE (StatusStrip.kt) — the one standard card radius.
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
fun MacroScreen(
    viewModel: WheelViewModel,
    colors: AtomColors,
    modifier: Modifier = Modifier,
    onOpenReading: (ReadingTarget) -> Unit = {},
) {
    val screenState by viewModel.screenState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            // Top is owned by MainActivity's persistent gear bar, bottom by its real AtomBottomNav
            // row below this pager page — see WheelScreen.kt's same note. Reserving safeDrawing's
            // own Bottom inset HERE TOO (2026-09-06 bug fix, Pieter's ask) double-counted it: the
            // nav bar already consumes that inset by sitting below the pager, so this was padding
            // scrollable content up by a second nav-bar-height gap, clipping it well above the
            // real nav row rather than right above it.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        when (val state = screenState) {
            WheelScreenState.Loading -> CenteredMessage("LOADING…", colors)
            WheelScreenState.Unavailable -> CenteredMessage("DATA UNAVAILABLE", colors)
            is WheelScreenState.Loaded -> MacroContent(state.signals, colors, onOpenReading)
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
private fun MacroContent(signals: Signals, colors: AtomColors, onOpenReading: (ReadingTarget) -> Unit) {
    val regime = signals.macroRegime

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (regime?.primary != null) {
            MacroBannerCard(regime, colors, onOpenReading)
            EvidenceAxes(regime.evidence, colors)
            SheetDivider(colors)
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

        CrossAssetTable(signals.macroAssets, regime, colors)
    }
}

/** The archetype banner — mockup's `.mbanner`: code, name, chips (confidence/USD/gold), the
 *  narrative, and the strong/weak bias baskets, all on one standard card.
 *
 *  2026-09-04 (Pieter's "living handbook" vision) — the "REGIME {code}" kicker row carries a
 *  book+ icon, right-aligned, that opens the Reading Window with the handbook's own theory for
 *  this exact live regime, assembled from which evidence axes are actually confirming it right
 *  now (`buildRegimeExplanation`), not a static reference. Superseded the original inline-expand
 *  block the same day — see ReadingWindow.kt's doc comment for why. */
@Composable
private fun MacroBannerCard(regime: MacroRegimeBlock, colors: AtomColors, onOpenReading: (ReadingTarget) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val primary = regime.primary
    val bias = regime.currencyBias
    val explanation = buildRegimeExplanation(
        primary?.code,
        regime.evidence.filter { it.supports }.mapNotNull { it.axis },
        regime.conflicts,
    )

    Column(modifier = Modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "REGIME ${primary?.code ?: "—"}", style = AtomType.Caption.copy(color = colors.textMuted))
            if (explanation != null) {
                BookPlusGlyph(
                    colors = colors,
                    modifier = Modifier.pressWash {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenReading(ReadingTarget.MacroArchetype(explanation))
                    },
                )
            }
        }
        Text(
            text = primary?.name ?: "—",
            style = AtomType.Title.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
        )
        ScrollingPills(
            pills = listOfNotNull(
                primary?.confidence?.let { Pill("Confidence $it · ${primary.distinctAxes ?: 0} axes", colors.bull, electric = true) },
                regime.usdRegime?.let { Pill("USD: ${it.replace('_', ' ')}", colors.neutral, electric = true) },
                regime.goldOverlay?.let { Pill("Gold: ${it.replace('_', ' ')}", colors.watch, electric = true) },
            ),
            colors = colors,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (!regime.narrative.isNullOrBlank()) {
            Text(text = regime.narrative, style = AtomType.Body.copy(color = colors.textSecondary))
        }
        if (bias != null && (bias.strong.isNotEmpty() || bias.weak.isNotEmpty())) {
            BiasBaskets(bias, colors, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Strong/weak currency baskets, side by side inside the banner card — a nested grouping, not a
 *  card of its own, so `surfaceRaised` (not `cardSurface`) and no border.
 *
 *  2026-09-04 (Pieter's ask) — always the same size as each other, not just the same width. The
 *  archetype table lets STRONG hold up to 4 currencies (regime C) and WEAK up to 5 (regime E), so
 *  whichever side wraps to more lines used to stretch the row unevenly. `IntrinsicSize.Max` on the
 *  Row + `fillMaxHeight()` on each box makes both stretch to match whichever is taller. */
@Composable
private fun BiasBaskets(bias: CurrencyBias, colors: AtomColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BiasBox("STRONG", bias.strong, colors.bull, colors, Modifier.weight(1f).fillMaxHeight())
        BiasBox("WEAK", bias.weak, colors.bear, colors, Modifier.weight(1f).fillMaxHeight())
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
 *  [EVIDENCE_LIT_AMOUNT].
 *
 *  2026-09-06 (Pieter's ask, the Macro rework) — `supports` alone used to be the only signal;
 *  now each card also carries a `confirmsToday` line ("Today confirms / is fighting / is quiet
 *  on this trend"). `supports` says whether this axis's OWN W1 trend backs the archetype that
 *  won; `confirmsToday` says whether TODAY's daily move is continuing or reversing that trend —
 *  the handbook's own "the path matters, not just the level" discipline (§4.3 / Mistake 4), made
 *  into an actual per-axis decision cue instead of a static list. See `macro_regime.py`'s own
 *  module doc comment for the full two-clock design this is built on. */
@Composable
private fun EvidenceAxes(evidence: List<MacroEvidence>, colors: AtomColors) {
    if (evidence.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "EVIDENCE", style = AtomType.Caption.copy(color = colors.textSecondary))
        evidence.forEach { e ->
            val fill = if (e.supports) lerp(colors.cardSurface, colors.bull, EVIDENCE_LIT_AMOUNT) else colors.cardSurface
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(fill, CARD_SHAPE)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EvidenceDot(color = if (e.supports) colors.bull else colors.textMuted)
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
                confirmsTodayLabel(e.confirmsToday)?.let { label ->
                    Text(
                        text = label,
                        style = AtomType.Caption.copy(color = confirmsTodayColor(e.confirmsToday, colors)),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun confirmsTodayLabel(tag: String?): String? = when (tag) {
    "confirming" -> "Today confirms this trend"
    "diverging" -> "Today is fighting this trend"
    "quiet" -> "No fresh move today"
    else -> null
}

private fun confirmsTodayColor(tag: String?, colors: AtomColors): Color = when (tag) {
    "confirming" -> colors.bull
    "diverging" -> colors.watch
    else -> colors.textMuted
}

/** The cross-asset dashboard (Functional Spec §19.2 Appendix-A table) — one card per asset, same
 *  [CrossAssetRow] look as the wheel's old cross-asset wedge sheet (`CrossAssetSheet.kt`).
 *
 *  2026-09-06 (Pieter's ask) — was a plain divided 3-column table; restyled to match the bottom
 *  sheet exactly (direction-tinted fill, impact/confirms caption, value+delta on the right) now
 *  that the outer cross-asset ring is gone from the wheel itself (see `WheelCanvas.kt`) and this
 *  is the dashboard's own permanent home, not just a mirror of it.
 *
 *  2026-09-06 follow-up — no longer tappable (Pieter's call: opening the identical-looking
 *  `CrossAssetSheet` from a screen that already shows the same cards was redundant, not a real
 *  second view). Sorted instead: assets actually moving (direction up/down) float above flat/no-data
 *  ones — a stable sort, so ties keep [WheelGeometry.XASSET_ORDER]'s own canonical order rather than
 *  jittering between scans. */
@Composable
private fun CrossAssetTable(
    macroAssets: Map<String, MacroAssetEntry>,
    regime: MacroRegimeBlock?,
    colors: AtomColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "CROSS-ASSET", style = AtomType.Caption.copy(color = colors.textSecondary))
        if (macroAssets.isEmpty()) {
            Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
            return
        }
        val supportingAxes = regime?.evidence?.filter { it.supports }?.mapNotNull { it.axis }?.toSet() ?: emptySet()
        val ordered = WheelGeometry.XASSET_ORDER.sortedByDescending { (key, _) ->
            macroAssets[key]?.direction in listOf("up", "down")
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ordered.forEach { (key, fallbackLabel) ->
                CrossAssetRow(
                    key = key,
                    fallbackLabel = fallbackLabel,
                    entry = macroAssets[key],
                    pinned = false,
                    confirms = (ASSET_AXES[key] ?: emptyList()).any { it in supportingAxes },
                    colors = colors,
                    // 2026-09-06 (Pieter's ask) — same white as the Evidence cards above, not the
                    // bottom sheet's own surfaceRaised.
                    baseFill = colors.cardSurface,
                )
            }
        }
    }
}
