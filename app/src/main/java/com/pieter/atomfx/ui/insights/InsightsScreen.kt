package com.pieter.atomfx.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.sheets.NotAvailableRow
import com.pieter.atomfx.ui.sheets.CalendarEventRow
import com.pieter.atomfx.ui.sheets.RecommendationContent
import com.pieter.atomfx.ui.sheets.SheetDivider
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.WheelScreenState
import com.pieter.atomfx.ui.wheel.WheelViewModel

/**
 * Architecture §8.2 `ui/insights/InsightsScreen.kt` — Functional Spec §7 + quick-reference rows
 * 46–50: recommendation, breaking headlines, the adversarial catalyst check, the calendar, and
 * the daily brief, aggregated onto one screen (Design §19.2's sibling entry: "recommendation card
 * + theme-tagged news + calendar + brief"). Pure consumer of `signals.json` — every section reads
 * a field that's already there; nothing here is computed. Headline theme-tagging (`news_themes`)
 * isn't produced by the backend yet (Build Status — optional/polish), so headlines show without a
 * theme chip for now; the AI narration behind `deep_analysis` is a separate outstanding item, so
 * "Daily brief" reads whatever's there today, including its own honest "not available yet" state.
 */
@Composable
fun InsightsScreen(viewModel: WheelViewModel, colors: AtomColors, modifier: Modifier = Modifier) {
    val screenState by viewModel.screenState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (val state = screenState) {
            WheelScreenState.Loading -> CenteredMessage("LOADING…", colors)
            WheelScreenState.Unavailable -> CenteredMessage("DATA UNAVAILABLE", colors)
            is WheelScreenState.Loaded -> InsightsContent(state.signals, colors)
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
private fun InsightsContent(signals: Signals, colors: AtomColors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionLabel("RECOMMENDATION", colors)
        val rec = signals.recommendation
        if (rec != null) RecommendationContent(rec, colors) else NotAvailableRow("Recommendation", colors)
        SheetDivider(colors, modifier = Modifier.padding(top = 12.dp))

        SectionLabel("BREAKING", colors)
        BreakingHeadlines(signals.breaking?.headlines.orEmpty(), colors)
        SheetDivider(colors, modifier = Modifier.padding(top = 12.dp))

        SectionLabel("CATALYST CHECK", colors)
        val catalyst = signals.catalyst?.text
        if (!catalyst.isNullOrBlank()) {
            Text(text = catalyst, style = AtomType.Body.copy(color = colors.watch))
        } else {
            NotAvailableRow("Catalyst check", colors)
        }
        SheetDivider(colors, modifier = Modifier.padding(top = 12.dp))

        SectionLabel("CALENDAR", colors)
        val events = signals.calendar?.events.orEmpty()
        if (events.isEmpty()) {
            Text(text = "No upcoming events", style = AtomType.Body.copy(color = colors.textMuted))
        } else {
            events.forEach { event -> CalendarEventRow(event, colors) }
        }
        SheetDivider(colors, modifier = Modifier.padding(top = 12.dp))

        SectionLabel("DAILY BRIEF", colors)
        val brief = signals.deepAnalysis?.text
        if (!brief.isNullOrBlank()) {
            Text(text = brief, style = AtomType.Body.copy(color = colors.textPrimary))
        } else {
            NotAvailableRow("Daily brief", colors)
        }
    }
}

@Composable
private fun SectionLabel(text: String, colors: AtomColors) {
    Text(
        text = text,
        style = AtomType.Caption.copy(color = colors.textSecondary),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun BreakingHeadlines(headlines: List<String>, colors: AtomColors) {
    if (headlines.isEmpty()) {
        NotAvailableRow("Breaking headlines", colors)
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        headlines.forEach { headline ->
            Text(
                text = "· $headline",
                style = AtomType.Body.copy(color = colors.textPrimary),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
