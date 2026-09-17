package com.pieter.atomfx.ui.sessions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.domain.SessionState
import com.pieter.atomfx.domain.TradingSession
import com.pieter.atomfx.domain.allSessionStatesAt
import com.pieter.atomfx.domain.formatCountdown
import com.pieter.atomfx.domain.occurrencesOverlapping
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Item Library #05 — same slide-in side panel recipe Settings/Watchlist/Calendar already use.
private const val PANEL_WIDTH_FRACTION = 0.82f

// 2026-09-17 (2nd pass, Pieter's own redesign) — a FIXED device-local calendar day (today's own
// midnight to midnight), not a rolling "now +/- some window". Sessions recur at the same local
// time every day, so on a fixed axis every session's bar sits at a constant position and any
// overlap between two sessions is just two bars visibly sharing horizontal space -- no highlight
// colour needed, no overlap-range computation needed, and it can't misrepresent a session's true
// length the way clipping a rolling window could. The one thing a fixed day-axis has to handle
// explicitly: a session whose own local hours cross the device's own midnight (with these
// standard hours, that's Sydney's evening-opening session against most Western device timezones)
// needs drawing as two segments, one at each edge of the bar -- see occurrencesOverlapping's own
// doc comment. Was a rolling now-relative window with green overlap highlighting in the first two
// passes; Pieter's own catch that neither actually made an overlap intuitively visible.
private const val DAY_HOURS = 24f

private val LOCAL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/**
 * The four FX trading sessions (Pieter's ask, 2026-09-17) — a header-glyph-triggered side panel,
 * sibling to Settings/Watchlist/Calendar (see MainActivity.kt's own `sessionsOpen` state). Pure
 * clock feature: no `signals.json` involvement at all (see TradingSessions.kt's own doc comment),
 * so unlike every other sheet in this app this one needs no `Signals`/`loaded` argument and works
 * even before the first fetch ever completes.
 */
@Composable
fun SessionsSheet(colors: AtomColors, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220)) }
    val dismiss: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClose()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val panelWidthPx = with(density) { (maxWidth * PANEL_WIDTH_FRACTION).toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = 0.6f * entrance.value))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = dismiss,
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(PANEL_WIDTH_FRACTION)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .graphicsLayer { translationX = (1f - entrance.value) * panelWidthPx }
                .background(colors.ground)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                SessionsContent(colors, onClose)
            }
        }
    }
}

@Composable
private fun SessionsContent(colors: AtomColors, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    // Live-ticking, unlike CalendarSheet's one-shot countdown — the whole point of a session
    // timer is watching it count down, so this one warrants it. Only runs while this sheet is
    // actually composed (LaunchedEffect cancels on dismiss), so it costs nothing the rest of
    // the time.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }
    val states = remember(now) { allSessionStatesAt(now) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "SESSIONS", style = AtomType.Title.copy(color = colors.textPrimary))
        Text(
            text = "Close",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.pressWash {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
        )
    }

    Text(
        text = sessionsSummary(states, now),
        style = AtomType.Body.copy(color = colors.textPrimary),
        modifier = Modifier.padding(bottom = 20.dp),
    )

    SessionTimeline(states = states, now = now, colors = colors)

    Spacer(modifier = Modifier.height(20.dp))

    states.forEach { state ->
        SessionRow(state = state, now = now, colors = colors)
        Spacer(modifier = Modifier.height(14.dp))
    }

    Text(
        text = "Hours shown are the standard retail convention, in your device's own local time. " +
            "Each session follows its own city's real clock (including daylight saving) — not a " +
            "fixed UTC offset — so these stay exact year-round.",
        style = AtomType.Caption.copy(color = colors.textMuted),
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun sessionsSummary(states: List<SessionState>, now: Instant): String {
    val open = states.filter { it.isOpen }
    return when {
        open.size >= 2 ->
            "${open.joinToString(" + ") { it.session.displayName }} overlap — highest activity window"
        open.size == 1 -> {
            val s = open[0]
            "${s.session.displayName} session — closes in ${formatCountdown(Duration.between(now, s.nextTransition))}"
        }
        else -> {
            val next = states.minByOrNull { it.nextTransition }
            if (next == null) "—"
            else "No session open — ${next.session.displayName} opens in " +
                formatCountdown(Duration.between(now, next.nextTransition))
        }
    }
}

@Composable
private fun SessionTimeline(states: List<SessionState>, now: Instant, colors: AtomColors) {
    val deviceZone = ZoneId.systemDefault()
    val dayStart = now.atZone(deviceZone).toLocalDate().atStartOfDay(deviceZone).toInstant()
    val dayEnd = dayStart.plus(Duration.ofHours(24))

    fun hourOfDay(instant: Instant): Float =
        (Duration.between(dayStart, instant).toMinutes() / 60.0).toFloat()

    val nowX = hourOfDay(now).coerceIn(0f, DAY_HOURS) / DAY_HOURS

    Column(modifier = Modifier.fillMaxWidth()) {
        states.forEach { state ->
            // Every occurrence of THIS session touching today's device-local calendar day —
            // usually one, but two when this session's own local hours cross the device's own
            // midnight (see occurrencesOverlapping's own doc comment for why, and which session
            // that is with the standard hours in use).
            val occurrences = state.session.occurrencesOverlapping(dayStart, dayEnd)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    text = state.session.displayName,
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                    modifier = Modifier.width(64.dp),
                )
                Canvas(modifier = Modifier.weight(1f).height(14.dp)) {
                    val corner = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(color = colors.hairline, cornerRadius = corner)

                    occurrences.forEach { occurrence ->
                        val occStart = occurrence.first
                        val occEnd = occurrence.second
                        val startH = hourOfDay(occStart).coerceIn(0f, DAY_HOURS)
                        val endH = hourOfDay(occEnd).coerceIn(0f, DAY_HOURS)
                        if (endH > startH) {
                            // Currently-open only reads as "open" (bright) for the occurrence
                            // actually covering `now` -- with two occurrences (the midnight-
                            // crossing case), at most one of them is the live one.
                            val isThisOccurrenceOpen = state.isOpen && !now.isBefore(occStart) && now.isBefore(occEnd)
                            drawRoundRect(
                                color = if (isThisOccurrenceOpen) colors.textPrimary else colors.textMuted,
                                topLeft = Offset(size.width * (startH / DAY_HOURS), 0f),
                                size = Size(size.width * ((endH - startH) / DAY_HOURS), size.height),
                                cornerRadius = corner,
                            )
                        }
                    }

                    drawLine(color = colors.watch, start = Offset(size.width * nowX, 0f), end = Offset(size.width * nowX, size.height), strokeWidth = 2.5f)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(state: SessionState, now: Instant, colors: AtomColors) {
    val deviceZone = ZoneId.systemDefault()
    val openLocal = state.windowStart.atZone(deviceZone).format(LOCAL_TIME_FORMAT)
    val closeLocal = state.windowEnd.atZone(deviceZone).format(LOCAL_TIME_FORMAT)
    val statusColor = if (state.isOpen) colors.textPrimary else colors.textMuted
    val statusWord = if (state.isOpen) "Open" else "Closed"
    val transitionWord = if (state.isOpen) "closes" else "opens"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = state.session.displayName, style = AtomType.Body.copy(color = colors.textPrimary))
            Text(text = statusWord, style = AtomType.Body.copy(color = statusColor))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$openLocal–$closeLocal your time",
                style = AtomType.Caption.copy(color = colors.textSecondary),
            )
            Text(
                text = "$transitionWord in ${formatCountdown(Duration.between(now, state.nextTransition))}",
                style = AtomType.Caption.copy(color = colors.textSecondary),
            )
        }
    }
}
