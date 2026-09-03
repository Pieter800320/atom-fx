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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
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
import com.pieter.atomfx.ui.sheets.BottomSheetHost
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomFxTheme
import com.pieter.atomfx.ui.theme.AtomTheme
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.theme.pressWash
import com.pieter.atomfx.ui.wheel.Freshness
import com.pieter.atomfx.ui.wheel.WheelScreen
import com.pieter.atomfx.ui.wheel.WheelScreenState
import com.pieter.atomfx.ui.wheel.WheelViewModel
import kotlinx.coroutines.launch

const val SIGNALS_TOPIC = "atomfx-signals"

/** The 3-tab nav (Build Status "Decision 1 RESOLVED"): Currency strength lives in the wheel's
 *  own Currencies/Pairs toggle (wheel v2), so there's no separate Currency tab here — this
 *  supersedes Functional Spec §2's original 4-tab `Wheel · Currency · Macro · Insights` list. */
private enum class AppTab(val label: String, val glyph: String, val glyphScale: Float = 1f) {
    Wheel("HOME", "⌂"),
    // Pieter, 2026-09-03 — the diamond glyph's own glyph box carries a lot of built-in
    // whitespace vs. the house/star glyphs, so it reads smaller than them at the same
    // font size; scaled up on its own to visually match.
    Macro("MACRO", "◈", glyphScale = 1.4f),
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
        val loaded = screenState as? WheelScreenState.Loaded
        val pagerState = rememberPagerState(initialPage = AppTab.Wheel.ordinal) { AppTab.entries.size }
        val scope = rememberCoroutineScope()
        var settingsOpen by remember { mutableStateOf(false) }
        // Pieter, 2026-09-03 follow-up — the calendar affordance moved here from the Wheel-tab-
        // only HeaderBar, next to the gear. BottomSheetHost handles SheetTarget.Calendar as a
        // pure leaf (CalendarSheet(signals, colors), no onNavigate, wheelState unused for this
        // branch) — reused directly rather than duplicating sheet-rendering logic, and it now
        // opens correctly from any tab, matching the gear's own already-established "any tab"
        // reach (Functional Spec §2/§3.1) instead of being silently Wheel-only.
        var activeAppSheet by remember { mutableStateOf<SheetTarget?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AtomGearBar(
                    colors = colors,
                    updated = loaded?.signals?.updated,
                    isFresh = loaded?.freshness == Freshness.FRESH,
                    onCalendarClick = { activeAppSheet = SheetTarget.Calendar },
                    onSettingsClick = { settingsOpen = true },
                )
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
                    loaded = loaded,
                    colors = colors,
                    onRefreshNow = { viewModel.refresh() },
                    onClose = { settingsOpen = false },
                )
            }

            val appSheet = activeAppSheet
            if (appSheet != null && loaded != null) {
                BottomSheetHost(
                    target = appSheet,
                    wheelState = loaded.state,
                    signals = loaded.signals,
                    colors = colors,
                    onDismiss = { activeAppSheet = null },
                    onNavigate = { activeAppSheet = it },
                )
            }
        }
    }
}

/** Functional Spec §2/§3.1: "Settings is reached from the header gear on any tab." A slim,
 *  always-visible strip above the pager (rather than duplicated per-tab) is the smallest change
 *  that's true on all three tabs without restructuring Macro/Insights' own layouts.
 *
 *  Pieter, 2026-09-03 follow-up — the whole header masthead now lives here, not split across this
 *  strip and the Wheel-tab-only `HeaderBar` (deleted): the wordmark, `Updated HH:mm` + the
 *  freshness dot, and a calendar glyph, all next to the gear — bigger than the old plain-Body
 *  gear glyph was, per Pieter's request. All of it now reads on Macro/Insights too, not just
 *  Wheel; "brief" is gone outright (its content is Cascade item #2 now). No `DATA STALE` text —
 *  the dot + timestamp already say it. */
@Composable
private fun AtomGearBar(
    colors: AtomColors,
    updated: String?,
    isFresh: Boolean,
    onCalendarClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "ATOM FX", style = AtomType.Title.copy(color = colors.textPrimary))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(7.dp)
                    .background(if (isFresh) colors.bull else colors.bear, CircleShape),
            )
            Text(
                text = "Updated ${formatUpdated(updated)}",
                style = AtomType.Caption.copy(color = colors.textMuted),
            )
            Text(
                text = "▦",
                style = AtomType.Body.copy(color = colors.textSecondary, fontSize = ICON_GLYPH_SIZE),
                modifier = Modifier.padding(start = 16.dp).pressWash { tap(onCalendarClick) },
            )
            Text(
                text = "⚙",
                style = AtomType.Body.copy(color = colors.textSecondary, fontSize = ICON_GLYPH_SIZE),
                modifier = Modifier.padding(start = 14.dp).pressWash { tap(onSettingsClick) },
            )
        }
    }
}

// Icon-sized, not body-copy-sized — deliberately outside the 4-level type scale (Design §3),
// same reasoning as the Summary glyph's own independent sizing: these are glyphs, not language.
private val ICON_GLYPH_SIZE = 22.sp
private val NAV_GLYPH_SIZE = 22.sp

private fun formatUpdated(updated: String?): String {
    val timestamp = updated ?: return "—"
    return runCatching {
        java.time.OffsetDateTime.parse(timestamp).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("—")
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
                        Text(
                            text = tab.glyph,
                            style = AtomType.Body.copy(
                                color = if (selected) colors.textPrimary else colors.textMuted,
                                fontSize = NAV_GLYPH_SIZE * tab.glyphScale,
                            ),
                        )
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
