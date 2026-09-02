package com.pieter.atomfx

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.messaging.FirebaseMessaging
import com.pieter.atomfx.data.SignalsRepository
import com.pieter.atomfx.push.extractDeepLinkUri
import com.pieter.atomfx.push.parseDeepLink
import com.pieter.atomfx.ui.macro.MacroScreen
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomFxTheme
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.WheelScreen
import com.pieter.atomfx.ui.wheel.WheelViewModel
import kotlinx.coroutines.launch

private const val SIGNALS_TOPIC = "atomfx-signals"

/** The 3-tab nav (Build Status "Decision 1 RESOLVED"): Currency strength lives in the wheel's
 *  own Currencies/Pairs toggle (wheel v2), so there's no separate Currency tab here — this
 *  supersedes Functional Spec §2's original 4-tab `Wheel · Currency · Macro · Insights` list. */
private enum class AppTab(val label: String, val glyph: String) {
    Wheel("WHEEL", "◎"),
    Macro("MACRO", "◭"),
    Insights("INSIGHTS", "✦"),
}

/** Hosts the Energy Wheel, Macro, and Insights screens as swipeable peers (Design §5.1: a
 *  Material 3 bottom nav bar + `HorizontalPager`, nav highlight in sync with the swipe). */
class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    private var deepLink by mutableStateOf<SheetTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        FirebaseMessaging.getInstance().subscribeToTopic(SIGNALS_TOPIC)
        deepLink = parseDeepLink(intent.extractDeepLinkUri())

        setContent {
            AtomFxApp(deepLink)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink = parseDeepLink(intent.extractDeepLinkUri())
    }
}

@Composable
private fun AtomFxApp(deepLink: SheetTarget?) {
    val isDark = isSystemInDarkTheme()
    AtomFxTheme {
        val context = LocalContext.current
        val viewModel: WheelViewModel = viewModel(
            factory = remember {
                viewModelFactory {
                    initializer { WheelViewModel(SignalsRepository(context.applicationContext)) }
                }
            },
        )
        val colors = AtomTheme.colors
        val pagerState = rememberPagerState(initialPage = AppTab.Wheel.ordinal) { AppTab.entries.size }
        val scope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                // Design §17: only the Wheel tab is the no-scroll landing screen — Macro/Insights
                // may scroll internally; the pager itself never adds scroll of its own.
                when (AppTab.entries[page]) {
                    AppTab.Wheel -> WheelScreen(viewModel = viewModel, isDark = isDark, initialDeepLink = deepLink)
                    AppTab.Macro -> MacroScreen(viewModel = viewModel, colors = colors)
                    AppTab.Insights -> InsightsPlaceholder(colors = colors)
                }
            }
            AtomBottomNav(pagerState = pagerState, colors = colors) { tab ->
                scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
            }
        }
    }
}

/** Design §5.1 — a Material 3 bottom nav bar on `surface` with a hairline top border, simple
 *  glyph icons + Caption labels, active tab in `textPrimary`; stays in sync with pager swipes
 *  both ways (the nav highlight follows [pagerState], taps drive the pager). */
@Composable
private fun AtomBottomNav(
    pagerState: androidx.compose.foundation.pager.PagerState,
    colors: AtomColors,
    onSelect: (AppTab) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
            AppTab.entries.forEach { tab ->
                val selected = pagerState.currentPage == tab.ordinal
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(tab)
                    },
                    icon = {
                        Text(text = tab.glyph, style = AtomType.Body.copy(color = if (selected) colors.textPrimary else colors.textMuted))
                    },
                    label = {
                        Text(text = tab.label, style = AtomType.Caption.copy(color = if (selected) colors.textPrimary else colors.textMuted))
                    },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = colors.surfaceRaised),
                )
            }
        }
    }
}

/** Build Status item 2 — the aggregated recommendation/news/calendar/brief surface (Functional
 *  Spec §7) is a separate build step; this is only the pager destination + nav entry existing. */
@Composable
private fun InsightsPlaceholder(colors: AtomColors) {
    Box(
        modifier = Modifier.fillMaxSize().background(colors.ground),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "INSIGHTS — coming soon", style = AtomType.Body.copy(color = colors.textSecondary))
    }
}
