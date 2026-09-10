package com.pieter.atomfx.ui.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash

private val CARD_SHAPE = RoundedCornerShape(14.dp)
private val PICKER_BUTTON_SHAPE = RoundedCornerShape(12.dp)
private val PICKER_ITEM_SHAPE = RoundedCornerShape(10.dp)

// 2026-09-10 (Pieter's ask) — copied verbatim from this file's own prior instance of this exact
// recipe (and StatusStrip.kt before that — Item Library #3's proven Compose reflow pattern).
private const val PANEL_DURATION_MS = 260
private val PANEL_EASING = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// 2026-09-10 — real, settled names replacing the working titles (Rotation/Pulse/Thrust), Pieter's
// sign-off after asking for the actual technical terms these map to: Relative Rotation Graphs
// (Rotation), the Advance/Decline-line family incl. the Zweig Breadth Thrust (Thrust). Pulse has
// no real external name — it's genuinely bespoke — "Confidence Index" names what it measures
// without claiming a pedigree it doesn't have. Fixed now, not user-renameable (the on-device
// rename feature this superseded is removed — see UserPreferences.kt's own git history).
private val INDICATOR_NAMES = listOf("Relative Rotation", "Confidence Index", "Breadth Thrust", "%B")

/**
 * 2026-09-10 — four concept indicators tried live so Pieter can decide which earns a permanent
 * home. Originally a 4-tab `SheetTabs` switcher; replaced same day with a single "+ <current>"
 * picker button (Item Library #3's fan-out mechanic) once renaming made the tab row visibly too
 * small — six-wide was the original motivation, settled at four real names instead, but the
 * picker (one button + a tap-to-select list) scales better than tabs regardless of count and was
 * kept. Landing screen (`WheelScreen.kt`) has zero idle vertical budget, so this still lives on
 * the Insights tab (Pieter's own earlier placement call, unchanged by this restyle).
 */
@Composable
fun MarketIndicatorsCard(signals: Signals, colors: AtomColors, modifier: Modifier = Modifier) {
    var selected by remember { mutableIntStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth().background(colors.cardSurface, CARD_SHAPE).padding(14.dp)) {
        Text(
            text = "WHOLE MARKET INDICATORS",
            style = AtomType.Title.copy(color = colors.textPrimary),
            modifier = Modifier.padding(bottom = 14.dp),
        )

        PickerButton(label = INDICATOR_NAMES[selected], expanded = pickerOpen, colors = colors) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pickerOpen = !pickerOpen
        }

        AnimatedVisibility(
            visible = pickerOpen,
            enter = expandVertically(tween(PANEL_DURATION_MS, easing = PANEL_EASING)) +
                fadeIn(tween(PANEL_DURATION_MS, easing = PANEL_EASING)),
            exit = shrinkVertically(tween(PANEL_DURATION_MS, easing = PANEL_EASING)) +
                fadeOut(tween(PANEL_DURATION_MS, easing = PANEL_EASING)),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                INDICATOR_NAMES.forEachIndexed { index, name ->
                    PickerRow(name = name, active = index == selected, colors = colors) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selected = index
                        pickerOpen = false
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 14.dp)) {
            when (selected) {
                0 -> RotationTab(signals, colors)
                1 -> PulseTab(signals, colors)
                2 -> ThrustTab(signals, colors)
                else -> PercentBBoardChart(signals.percentBBoard, colors)
            }
        }
    }
}

// "Bump and settle" (Pieter's ask): a quick compress on press, spring-released on lift — the
// standard Compose recipe for this is reading collectIsPressedAsState() off the SAME
// InteractionSource driving the ripple, hence pressWash's new optional interactionSource param
// (PressWash.kt) rather than a second, disconnected click handler.
@Composable
private fun PickerButton(label: String, expanded: Boolean, colors: AtomColors, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pickerButtonBump",
    )
    val plusRotation by animateFloatAsState(targetValue = if (expanded) 45f else 0f, label = "pickerPlusRotation")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(colors.controlSurface, PICKER_BUTTON_SHAPE)
            .border(1.dp, colors.controlBorder, PICKER_BUTTON_SHAPE)
            .pressWash(PICKER_BUTTON_SHAPE, interactionSource = interactionSource) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "+",
            style = AtomType.Button.copy(color = colors.textPrimary),
            modifier = Modifier.graphicsLayer { rotationZ = plusRotation },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label.uppercase(), style = AtomType.Button.copy(color = colors.textPrimary))
    }
}

@Composable
private fun PickerRow(name: String, active: Boolean, colors: AtomColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(if (active) colors.controlSurfaceActive else colors.surface, PICKER_ITEM_SHAPE)
            .pressWash(PICKER_ITEM_SHAPE) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = name, style = AtomType.Body.copy(color = if (active) colors.textPrimary else colors.textSecondary))
        if (active) {
            Text(text = "✓", style = AtomType.Body.copy(color = colors.textPrimary))
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
