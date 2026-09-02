package com.pieter.atomfx.ui.macro

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val AXIS_LABELS = mapOf(
    "risk" to "Risk", "rates" to "Rates", "usd" to "USD",
    "commodity" to "Commodity", "safe_haven" to "Safe-haven",
)

/**
 * Design §19.2. Full content when `macro_regime` exists (the archetype engine's own key —
 * absent on live data today, same known EXTEND-step gap as `potential`/`recommendation`);
 * otherwise falls back to the frozen W1 regime label so the screen is never blank.
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
    ) {
        if (regime?.primary != null) {
            Text(text = regime.primary.name ?: "—", style = AtomType.Title.copy(color = colors.textPrimary))
            Text(
                text = "Confidence: ${regime.primary.confidence ?: "—"}",
                style = AtomType.Caption.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            if (!regime.narrative.isNullOrBlank()) {
                Text(text = regime.narrative, style = AtomType.Body.copy(color = colors.textSecondary))
            }

            Row(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                ScrollingPills(
                    pills = listOfNotNull(
                        regime.goldOverlay?.let { Pill("Gold: ${it.replace('_', ' ')}", colors.watch) },
                        regime.usdRegime?.let { Pill("USD: ${it.replace('_', ' ')}", colors.neutral) },
                    ),
                    colors = colors,
                )
            }

            BiasBaskets(regime.currencyBias, colors)
            EvidenceAxes(regime.evidence, colors)
        } else {
            Text(text = "MARKET REGIME", style = AtomType.Caption.copy(color = colors.textSecondary))
            Text(
                text = signals.macro?.label ?: signals.regimeW1?.regime ?: "—",
                style = AtomType.Title.copy(color = colors.textPrimary),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Confidence: ${signals.macro?.confidence ?: signals.regimeW1?.confidence ?: "—"}",
                style = AtomType.Caption.copy(color = colors.textSecondary),
            )
            Text(
                text = "The macro archetype read isn't available yet — showing the weekly regime instead.",
                style = AtomType.Caption.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
        }

        CrossAssetTable(signals.macroAssets, colors)
    }
}

@Composable
private fun BiasBaskets(bias: CurrencyBias?, colors: AtomColors) {
    if (bias == null || (bias.strong.isEmpty() && bias.weak.isEmpty())) return
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = "STRONG", style = AtomType.Caption.copy(color = colors.bull), modifier = Modifier.padding(bottom = 4.dp))
        ScrollingPills(bias.strong.map { Pill(it, colors.bull) }, colors, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = "WEAK", style = AtomType.Caption.copy(color = colors.bear), modifier = Modifier.padding(bottom = 4.dp))
        ScrollingPills(bias.weak.map { Pill(it, colors.bear) }, colors)
    }
}

@Composable
private fun EvidenceAxes(evidence: List<MacroEvidence>, colors: AtomColors) {
    if (evidence.isEmpty()) return
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = "EVIDENCE", style = AtomType.Caption.copy(color = colors.textSecondary), modifier = Modifier.padding(bottom = 8.dp))
        evidence.forEach { e ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = if (e.supports) "✓" else "✗",
                    style = AtomType.Body.copy(color = if (e.supports) colors.bull else colors.textMuted),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column {
                    Text(text = AXIS_LABELS[e.axis] ?: e.axis ?: "—", style = AtomType.Body.copy(color = colors.textPrimary))
                    Text(text = e.read ?: "—", style = AtomType.Caption.copy(color = colors.textSecondary))
                }
            }
        }
    }
}

@Composable
private fun CrossAssetTable(macroAssets: Map<String, MacroAssetEntry>, colors: AtomColors) {
    Text(text = "CROSS-ASSET", style = AtomType.Caption.copy(color = colors.textSecondary), modifier = Modifier.padding(bottom = 8.dp))
    if (macroAssets.isEmpty()) {
        Text(text = "Not available yet", style = AtomType.Body.copy(color = colors.textMuted))
        return
    }
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            TableHeaderRow(colors)
            macroAssets.values.forEach { asset -> TableRow(asset, colors) }
        }
    }
}

@Composable
private fun TableHeaderRow(colors: AtomColors) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        listOf("Label", "Value", "Dir", "Δ").forEach { header ->
            Text(
                text = header,
                style = AtomType.Caption.copy(color = colors.textMuted),
                modifier = Modifier.width(90.dp),
            )
        }
    }
}

@Composable
private fun TableRow(asset: MacroAssetEntry, colors: AtomColors) {
    val delta = asset.deltaPct?.let { "%+.1f%%".format(it) } ?: asset.deltaBp?.let { "%+.1fbp".format(it) } ?: "—"
    val dirColor = when (asset.direction) {
        "up" -> colors.bull
        "down" -> colors.bear
        else -> colors.textSecondary
    }
    Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.Start) {
        Text(asset.label ?: "—", style = AtomType.Body.copy(color = colors.textPrimary), modifier = Modifier.width(90.dp))
        Text(asset.value?.let { "%.2f".format(it) } ?: "—", style = AtomType.Body.copy(color = colors.textPrimary), modifier = Modifier.width(90.dp))
        Text(asset.direction ?: "—", style = AtomType.Body.copy(color = dirColor), modifier = Modifier.width(90.dp))
        Text(delta, style = AtomType.Body.copy(color = dirColor), modifier = Modifier.width(90.dp))
    }
}
