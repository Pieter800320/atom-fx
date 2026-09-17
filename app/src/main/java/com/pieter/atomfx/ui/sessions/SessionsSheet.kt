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
import com.pieter.atomfx.domain.overlapRange
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

// The rolling timeline window. LOOKBACK must cover the longest session's full duration (+1h
// margin) -- 2026-09-17 bugfix: a fixed 1h lookback clipped a currently-OPEN session's true start
// (e.g. New York, 6+ hours into its own session) off the left edge of the visible window, making
// its bar look artificially short next to sessions that haven't opened yet and so show in full.
// LOOKAHEAD is separate, generous enough that every session's NEXT occurrence is visible too.
private val LOOKBACK = Duration.ofHours(TradingSession.entries.maxOf { it.closeHour - it.openHour } + 1L)
private val LOOKAHEAD = Duration.ofHours(24)
private val WINDOW_HOURS = (LOOKBACK + LOOKAHEAD).toMinutes() / 60f

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
    val axisStart = now.minus(LOOKBACK)

    fun hoursFromAxisStart(instant: Instant): Float =
        (Duration.between(axisStart, instant).toMinutes() / 60.0)
            .coerceIn(0.0, WINDOW_HOURS.toDouble())
            .toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {
        states.forEach { state ->
            // 2026-09-17 bugfix — every OTHER session's window this one overlaps, so the shared
            // stretch can be drawn in its own colour. Without this, an overlap (the highest-
            // volume windows, the entire reason the header dot exists) was invisible on the
            // graphic itself — only inferable by separately noticing two rows both say "Open".
            val overlaps = states
                .filter { it.session != state.session }
                .mapNotNull { other -> overlapRange(state.windowStart, state.windowEnd, other.windowStart, other.windowEnd) }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    text = state.session.displayName,
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                    modifier = Modifier.width(64.dp),
                )
                Canvas(modifier = Modifier.weight(1f).height(14.dp)) {
                    val corner = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(color = colors.hairline, cornerRadius = corner)

                    val startH = hoursFromAxisStart(state.windowStart)
                    val endH = hoursFromAxisStart(state.windowEnd)
                    if (endH > startH) {
                        drawRoundRect(
                            color = if (state.isOpen) colors.textPrimary else colors.textMuted,
                            topLeft = Offset(size.width * (startH / WINDOW_HOURS), 0f),
                            size = Size(size.width * ((endH - startH) / WINDOW_HOURS), size.height),
                            cornerRadius = corner,
                        )
                    }

                    // Overlap segments drawn on top, in the same green the header dot itself uses
                    // for "worth a look" (HeaderGlyphWithDot's own colors.bull) — same meaning,
                    // same colour, just on this graphic instead of the header.
                    overlaps.forEach { overlap ->
                        val oStartH = hoursFromAxisStart(overlap.first)
                        val oEndH = hoursFromAxisStart(overlap.second)
                        if (oEndH > oStartH) {
                            drawRect(
                                color = colors.bull,
                                topLeft = Offset(size.width * (oStartH / WINDOW_HOURS), 0f),
                                size = Size(size.width * ((oEndH - oStartH) / WINDOW_HOURS), size.height),
                            )
                        }
                    }

                    // "now" marker — always at the same fixed fraction (LOOKBACK / WINDOW_HOURS)
                    // since the axis itself rolls forward with `now` every tick.
                    val nowX = size.width * (LOOKBACK.toMinutes() / 60f / WINDOW_HOURS)
                    drawLine(color = colors.watch, start = Offset(nowX, 0f), end = Offset(nowX, size.height), strokeWidth = 2.5f)
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
