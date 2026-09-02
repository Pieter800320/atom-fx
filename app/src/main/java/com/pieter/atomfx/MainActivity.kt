package com.pieter.atomfx

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.pieter.atomfx.data.ThemeMode
import com.pieter.atomfx.data.UserPreferences
import com.pieter.atomfx.push.extractDeepLinkUri
import com.pieter.atomfx.push.parseDeepLink
import com.pieter.atomfx.ui.insights.InsightsScreen
import com.pieter.atomfx.ui.macro.MacroScreen
import com.pieter.atomfx.ui.settings.SettingsScreen
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomFxTheme
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.WheelScreen
import com.pieter.atomfx.ui.wheel.WheelViewModel
import kotlinx.coroutines.launch

const val SIGNALS_TOPIC = "atomfx-signals"

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
        // Functional Spec §9 "Notifications → Toggle push": honour a stored off before ever
        // subscribing — SettingsScreen flips the subscription live on every later change.
        if (UserPreferences(applicationContext).state.value.notifications.enabled) {
            FirebaseMessaging.getInstance().subscribeToTopic(SIGNALS_TOPIC)
        }
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
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context.applicationContext) }
    val prefsState by userPreferences.state.collectAsState()

    // Design §2.1: system by default, overridable by the stored preference — resolved once, here,
    // so every consumer (this theme, WheelCanvas's own isDark param) agrees on one value.
    val systemDark = isSystemInDarkTheme()
    val isDark = when (prefsState.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    AtomFxTheme(isDark = isDark) {
        val viewModel: WheelViewModel = viewModel(
            factory = remember {
                viewModelFactory {
                    initializer {
                        WheelViewModel(
                            SignalsRepository(context.applicationContext) { userPreferences.state.value.signalsUrl },
                            userPreferences,
                        )
                    }
                }
            },
        )
        val colors = AtomTheme.colors
        val screenState by viewModel.screenState.collectAsState()
        val pagerState = rememberPagerState(initialPage = AppTab.Wheel.ordinal) { AppTab.entries.size }
        val scope = rememberCoroutineScope()
        var settingsOpen by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AtomGearBar(colors = colors) { settingsOpen = true }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    // Design §17: only the Wheel tab is the no-scroll landing screen — Macro/
                    // Insights may scroll internally; the pager itself never adds scroll of its own.
                    when (AppTab.entries[page]) {
                        AppTab.Wheel -> WheelScreen(viewModel = viewModel, isDark = isDark, initialDeepLink = deepLink)
                        AppTab.Macro -> MacroScreen(viewModel = viewModel, colors = colors)
                        AppTab.Insights -> InsightsScreen(viewModel = viewModel, colors = colors)
                    }
                }
                AtomBottomNav(pagerState = pagerState, colors = colors) { tab ->
                    scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                }
            }

            if (settingsOpen) {
                BackHandler { settingsOpen = false }
                SettingsScreen(
                    preferences = userPreferences,
                    prefsState = prefsState,
                    loaded = screenState as? com.pieter.atomfx.ui.wheel.WheelScreenState.Loaded,
                    colors = colors,
                    onRefreshNow = { viewModel.refresh() },
                    onClose = { settingsOpen = false },
                )
            }
        }
    }
}

/** Functional Spec §2/§3.1: "Settings is reached from the header gear on any tab." A slim,
 *  always-visible strip above the pager (rather than duplicated per-tab) is the smallest change
 *  that's true on all three tabs without restructuring Macro/Insights' own layouts. */
@Composable
private fun AtomGearBar(colors: AtomColors, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "⚙",
            style = AtomType.Body.copy(color = colors.textSecondary),
            modifier = Modifier.clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
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
