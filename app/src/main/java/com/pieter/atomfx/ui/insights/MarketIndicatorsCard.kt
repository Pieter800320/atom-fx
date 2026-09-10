package com.pieter.atomfx.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.SheetTabs
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType

// = InsightsScreen.kt's own CARD_SHAPE — duplicated per-file, matching that file's own comment
// ("Mirrors Home/Macro's CARD_SHAPE") that this radius is copied per call site, not shared.
private val CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * 2026-09-10 — three concept indicators (Rotation/Pulse/Thrust, see the artifact mockup this was
 * built from) tried live so Pieter can decide which earns a permanent home, per his own ask
 * ("I want to try all 3... start planning and building"). Landing screen (`WheelScreen.kt`) has
 * zero idle vertical budget (confirmed via research — today's 4 elements already exactly fill the
 * no-scroll viewport), so this lives on the Insights tab instead, which already scrolls freely and
 * is architecturally the "broad, non-per-instrument read" screen (Pieter's own choice between the
 * two open placement options).
 */
@Composable
fun MarketIndicatorsCard(signals: Signals, colors: AtomColors, modifier: Modifier = Modifier) {
    var selected by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
        Text(text = "MARKET INDICATORS", style = AtomType.Caption.copy(color = colors.textMuted))
        Text(
            text = "Three ways to read the whole market at a glance",
            style = AtomType.Title.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        SheetTabs(tabs = listOf("Rotation", "Pulse", "Thrust"), selected = selected, colors = colors) { selected = it }

        when (selected) {
            0 -> RotationTab(signals, colors)
            1 -> PulseTab(signals, colors)
            else -> ThrustTab(signals, colors)
        }
    }
}

@Composable
private fun RotationTab(signals: Signals, colors: AtomColors) {
    RotationChart(signals.rotation, colors)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LegendItem("Leading", colors.bull, colors)
        LegendItem("Weakening", colors.watch, colors)
        LegendItem("Lagging", colors.bear, colors)
        LegendItem("Improving", colors.neutral, colors)
    }
    Text(
        text = "Currency strength (H4 CSM) against the momentum of that strength (CSM Delta).",
        style = AtomType.Caption.copy(color = colors.textMuted),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PulseTab(signals: Signals, colors: AtomColors) {
    val pulse = signals.pulse
    PulseChart(pulse, colors)
    if (pulse?.score != null) {
        val bandWord = when (pulse.band) {
            "confirmed" -> "confirmed"
            "mixed" -> "mixed"
            else -> "noise"
        }
        Text(
            text = "${pulse.score.toInt()} — $bandWord",
            style = AtomType.Body.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Text(
        text = "Is today's market broad and confirmed enough to trust a signal, or thin and contradictory?",
        style = AtomType.Caption.copy(color = colors.textMuted),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ThrustTab(signals: Signals, colors: AtomColors) {
    val thrust = signals.breadthThrust
    ThrustChart(thrust, colors)
    if (thrust?.h4 != null) {
        val sign = if (thrust.h4 > 0) "+" else ""
        Text(
            text = "$sign${thrust.h4} of 8 currencies polarised",
            style = AtomType.Body.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Text(
        text = "Currencies reading breadth-strong minus breadth-weak — is the market splitting into clear winners and losers, or staying flat?",
        style = AtomType.Caption.copy(color = colors.textMuted),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun LegendItem(label: String, color: Color, colors: AtomColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).background(color, CircleShape))
        Text(text = label, style = AtomType.Caption.copy(color = colors.textMuted))
    }
}
