package com.pieter.atomfx.ui.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pieter.atomfx.data.SignalsRepository
import com.pieter.atomfx.ui.sheets.BottomSheetHost
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType

private val RING_KEY_ROW_HEIGHT = 28.dp
private val RING_KEY_ROW_SPACING = 8.dp

/**
 * The wheel fetches and maps real `signals.json` via [WheelViewModel] — no header, status
 * strip, or nav yet (those are Phase 5). A tap on a ring/node/nucleus opens the matching
 * bottom sheet (Design §13.1, §14). The ring key ("1 Regime · 2 Flow · …") sits below the
 * wheel per the mockup, rather than crowding the canvas itself.
 */
@Composable
fun WheelScreen(isDark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: WheelViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer { WheelViewModel(SignalsRepository(context.applicationContext)) }
            }
        },
    )
    val screenState by viewModel.screenState.collectAsState()
    val colors = AtomTheme.colors
    var activeSheet by remember { mutableStateOf<SheetTarget?>(null) }

    Scaffold(modifier = modifier, containerColor = colors.ground) {
        // No top/bottom bar yet, so there's no inner padding to apply — the content handles
        // its own safe-drawing insets below.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.ground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            when (val current = screenState) {
                WheelScreenState.Loading -> CenteredMessage("LOADING…", colors)
                WheelScreenState.Unavailable -> CenteredMessage("DATA UNAVAILABLE", colors)
                is WheelScreenState.Loaded -> LoadedWheel(
                    loaded = current,
                    isDark = isDark,
                    colors = colors,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    onTap = { target -> activeSheet = target.toSheetTarget() },
                )
            }

            val loaded = screenState as? WheelScreenState.Loaded
            val sheet = activeSheet
            if (sheet != null && loaded != null) {
                BottomSheetHost(
                    target = sheet,
                    wheelState = loaded.state,
                    signals = loaded.signals,
                    colors = colors,
                    onDismiss = { activeSheet = null },
                )
            }
        }
    }
}

private fun WheelTapTarget.toSheetTarget(): SheetTarget = when (this) {
    is WheelTapTarget.Nucleus -> SheetTarget.Nucleus
    is WheelTapTarget.Ring -> SheetTarget.Ring(factor)
    is WheelTapTarget.Node -> SheetTarget.Node(pair)
}

@Composable
private fun LoadedWheel(
    loaded: WheelScreenState.Loaded,
    isDark: Boolean,
    colors: AtomColors,
    maxWidth: Dp,
    maxHeight: Dp,
    onTap: (WheelTapTarget) -> Unit,
) {
    val wheelSide = minOf(maxWidth, maxHeight - RING_KEY_ROW_HEIGHT - RING_KEY_ROW_SPACING)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loaded.freshness == Freshness.STALE) {
            Text(
                text = "DATA STALE",
                style = AtomType.Caption.copy(color = colors.bear),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(modifier = Modifier.size(wheelSide)) {
            WheelCanvas(
                state = loaded.state,
                colors = colors,
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
                onTap = onTap,
            )
        }
        Spacer(modifier = Modifier.height(RING_KEY_ROW_SPACING))
        RingKeyRow(rings = loaded.state.rings, colors = colors, modifier = Modifier.width(wheelSide))
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AtomColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = AtomType.Body.copy(color = colors.textSecondary))
    }
}

@Composable
private fun RingKeyRow(rings: List<RingDescriptor>, colors: AtomColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(RING_KEY_ROW_HEIGHT),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rings.forEachIndexed { i, ring ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(tintColor(ring.tint, colors), CircleShape),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${i + 1} ${ring.factor.shortLabel}",
                    style = AtomType.Caption.copy(color = colors.textSecondary),
                )
            }
        }
    }
}
