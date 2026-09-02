package com.pieter.atomfx.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.macro.AXIS_LABELS
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
 * 46–50: recommendation, breaking headlines, the adversarial catalyst check, the calendar, the
 * daily brief, and (when present) the week-ahead brief, aggregated onto one screen (Design §19.2's
 * sibling entry: "recommendation card + theme-tagged news + calendar + brief"). Pure consumer of
 * `signals.json` — every section reads a field that's already there; nothing here is computed.
 * Breaking headlines carry a theme chip (one of the same five macro evidence axes `MacroScreen`
 * uses) when the backend tagged one — `news_themes`, Functional Spec §7.
 */
@Composable
fun InsightsScreen(viewModel: WheelViewModel, colors: AtomColors, modifier: Modifier = Modifier) {
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
        BreakingHeadlines(signals.breaking?.headlines.orEmpty(), signals.breaking?.themes.orEmpty(), colors)
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

        // Functional Spec §7 row 50 — generated Sunday evenings only, persisted ~24h server-side.
        // Its absence the rest of the week is normal, so the section itself only appears when
        // there's something to show, rather than reading "Not available yet" as a standing gap.
        val weekAhead = signals.weekAhead?.text
        if (!weekAhead.isNullOrBlank()) {
            SheetDivider(colors, modifier = Modifier.padding(top = 12.dp))
            SectionLabel("WEEK AHEAD", colors)
            Text(text = weekAhead, style = AtomType.Body.copy(color = colors.textPrimary))
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
private fun BreakingHeadlines(headlines: List<String>, themes: List<String?>, colors: AtomColors) {
    if (headlines.isEmpty()) {
        NotAvailableRow("Breaking headlines", colors)
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        headlines.forEachIndexed { index, headline ->
            val axis = themes.getOrNull(index)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (axis != null) {
                    Text(
                        text = (AXIS_LABELS[axis] ?: axis).uppercase(),
                        style = AtomType.Caption.copy(color = colors.textSecondary),
                        modifier = Modifier
                            .padding(end = 8.dp, top = 1.dp)
                            .background(colors.surfaceRaised, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = "· $headline",
                    style = AtomType.Body.copy(color = colors.textPrimary),
                )
            }
        }
    }
}
