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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pieter.atomfx.data.SignalsRepository
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType
import kotlinx.coroutines.launch

private val RING_KEY_ROW_HEIGHT = 28.dp
private val RING_KEY_ROW_SPACING = 8.dp

/**
 * Phase 3: the wheel fetches and maps real `signals.json` via [WheelViewModel] — no header,
 * status strip, nav, or sheets yet (those are Phases 4/5). A tap on a ring/node/nucleus
 * surfaces a Snackbar naming what was hit, enough to prove the hit-testing geometry without
 * building navigation early. The ring key ("1 Regime · 2 Flow · …") sits below the wheel per
 * the mockup, rather than crowding the canvas itself.
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        containerColor = colors.ground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
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
                    onTap = { label -> scope.launch { snackbarHostState.showSnackbar(label) } },
                )
            }
        }
    }
}

@Composable
private fun LoadedWheel(
    loaded: WheelScreenState.Loaded,
    isDark: Boolean,
    colors: AtomColors,
    maxWidth: Dp,
    maxHeight: Dp,
    onTap: (String) -> Unit,
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
                onTap = { target ->
                    val label = when (target) {
                        is WheelTapTarget.Nucleus -> "Tapped: nucleus (${loaded.state.nucleus.regimeLabel})"
                        is WheelTapTarget.Node -> "Tapped: ${target.pair}"
                        is WheelTapTarget.Ring -> "Tapped ring: ${target.factor.ringLabel}"
                    }
                    onTap(label)
                },
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
