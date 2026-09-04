package com.pieter.atomfx

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.messaging.FirebaseMessaging
import com.pieter.atomfx.data.NotificationHistoryStore
import com.pieter.atomfx.data.SignalsRepository
import com.pieter.atomfx.data.ThemeMode
import com.pieter.atomfx.data.UserPreferences
import com.pieter.atomfx.push.extractDeepLinkUri
import com.pieter.atomfx.push.parseDeepLink
import com.pieter.atomfx.ui.insights.InsightsScreen
import com.pieter.atomfx.ui.macro.MacroScreen
import com.pieter.atomfx.ui.reading.ReadingTarget
import com.pieter.atomfx.ui.reading.ReadingWindow
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
    // Aesthetics pass, 2026-09-03 — circle-with-a-dot (was the diamond ◈, which needed its own
    // 1.4x scale to visually match the house/star glyphs' box; a plain circle doesn't have that
    // quirk, so no override here).
    Macro("MACRO", "⊙"),
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
    // Single shared instance (same pattern as userPreferences above) — AtomFxMessagingService
    // constructs its own separate instance per message since a background service can't reach
    // into this Composable's remember scope, but both stay in sync via the SharedPreferences
    // change listener NotificationHistoryStore registers internally.
    val notificationHistory = remember { NotificationHistoryStore(context.applicationContext) }
    val notificationRecords by notificationHistory.state.collectAsState()
    val hasUnreadNotifications = notificationRecords.any { !it.read }

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
        // The Reading Window (2026-09-04, Pieter's own framing) — "sheets inspect data, a window
        // is for study and reading." Deliberately its own top-level state, not folded into
        // activeAppSheet: it needs to be able to open OVER a sheet, a tab, or the Settings panel
        // alike and close back to exactly what was underneath, so it's rendered last (highest
        // z-order) in the Box below, same layering reasoning as settingsOpen/activeAppSheet.
        var readingTarget by remember { mutableStateOf<ReadingTarget?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AtomGearBar(
                    colors = colors,
                    // Pieter, 2026-09-03 — Macro/Insights read their own tab name instead of the
                    // "ATOM FX" wordmark, matching the mockup's own per-screen header(...) calls
                    // (header("MACRO", …), header("INSIGHTS", …)). Wheel/Home keeps the wordmark.
                    wordmark = when (AppTab.entries.getOrNull(pagerState.currentPage)) {
                        AppTab.Macro -> "MACRO"
                        AppTab.Insights -> "INSIGHTS"
                        else -> "ATOM FX"
                    },
                    updated = loaded?.signals?.updated,
                    isFresh = loaded?.freshness == Freshness.FRESH,
                    hasUnreadNotifications = hasUnreadNotifications,
                    onCalendarClick = { activeAppSheet = SheetTarget.Calendar },
                    onSettingsClick = { settingsOpen = true },
                )
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    // Design §17: only the Wheel tab is the no-scroll landing screen — Macro/
                    // Insights may scroll internally; the pager itself never adds scroll of its own.
                    when (AppTab.entries[page]) {
                        AppTab.Wheel -> WheelScreen(
                            viewModel = viewModel,
                            isDark = isDark,
                            initialDeepLink = deepLink,
                            onOpenReading = { readingTarget = it },
                        )
                        AppTab.Macro -> MacroScreen(viewModel = viewModel, colors = colors, onOpenReading = { readingTarget = it })
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
                    notificationHistory = notificationHistory,
                    onRefreshNow = { viewModel.refresh() },
                    onClose = { settingsOpen = false },
                    onNavigate = { activeAppSheet = it },
                    onOpenReading = { readingTarget = it },
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
                    onOpenReading = { readingTarget = it },
                )
            }

            val reading = readingTarget
            if (reading != null) {
                // ModalBottomSheet (RegimeSheet's own host) renders in its own Popup window,
                // always above plain composition content — a Box here, however late in the tree,
                // would render BEHIND an open sheet, not over it (found on-device: the sheet was
                // visibly still on top). A Dialog gets the Reading Window its own top-level
                // window too, so it wins regardless of what's open underneath — a sheet, a tab,
                // or the Settings panel.
                Dialog(
                    onDismissRequest = { readingTarget = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    BackHandler { readingTarget = null }
                    ReadingWindow(target = reading, colors = colors, onClose = { readingTarget = null })
                }
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
    wordmark: String,
    updated: String?,
    isFresh: Boolean,
    hasUnreadNotifications: Boolean,
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
        Text(text = wordmark, style = AtomType.Title.copy(color = colors.textPrimary))
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
            CalendarGlyph(
                colors = colors,
                modifier = Modifier.padding(start = 16.dp).pressWash { tap(onCalendarClick) },
            )
            Box(modifier = Modifier.padding(start = 14.dp)) {
                GearGlyph(
                    colors = colors,
                    modifier = Modifier.pressWash { tap(onSettingsClick) },
                )
                if (hasUnreadNotifications) {
                    // Same 7dp CircleShape recipe as the freshness dot above — no new visual
                    // language for "something needs your attention."
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .background(colors.bull, CircleShape),
                    )
                }
            }
        }
    }
}

// Icon-sized, not body-copy-sized — deliberately outside the 4-level type scale (Design §3),
// same reasoning as the Summary glyph's own independent sizing: these are glyphs, not language.
private val NAV_GLYPH_SIZE = 22.sp

// Pieter, 2026-09-04 — the calendar/gear header icons were "▦"/"⚙" Unicode text glyphs at a
// shared font-size, which LOOKS like "same size" but isn't: two arbitrary pictographic glyphs at
// one declared font-size render at different optical sizes and baselines (font-metric quirks, not
// a bug in this app), which is exactly why the calendar glyph read smaller and slightly off-kilter
// next to the gear. Both are now hand-drawn on a Canvas at this one fixed box, same stroke width,
// same centring — "same size, precisely aligned" by construction instead of by font luck.
private val HEADER_ICON_SIZE = 22.dp

@Composable
private fun GearGlyph(colors: AtomColors, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(HEADER_ICON_SIZE)) {
        val stroke = size.minDimension * 0.11f
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = size.minDimension * 0.28f
        val toothLength = size.minDimension * 0.15f
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = center) {
                drawLine(
                    color = colors.textSecondary,
                    start = Offset(center.x, center.y - ringRadius),
                    end = Offset(center.x, center.y - ringRadius - toothLength),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(color = colors.textSecondary, radius = ringRadius, center = center, style = Stroke(width = stroke))
        drawCircle(color = colors.textSecondary, radius = size.minDimension * 0.1f, center = center)
    }
}

@Composable
private fun CalendarGlyph(colors: AtomColors, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(HEADER_ICON_SIZE)) {
        val stroke = size.minDimension * 0.11f
        val bodyTop = size.height * 0.24f
        val left = size.width * 0.08f
        val right = size.width * 0.92f
        val bottom = size.height * 0.92f
        val cornerRadius = size.minDimension * 0.14f
        drawRoundRect(
            color = colors.textSecondary,
            topLeft = Offset(left, bodyTop),
            size = Size(right - left, bottom - bodyTop),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = stroke),
        )
        val headerY = bodyTop + (bottom - bodyTop) * 0.32f
        drawLine(
            color = colors.textSecondary,
            start = Offset(left, headerY),
            end = Offset(right, headerY),
            strokeWidth = stroke,
        )
        val tabTop = bodyTop - size.height * 0.12f
        listOf(0.28f, 0.72f).forEach { xFrac ->
            drawLine(
                color = colors.textSecondary,
                start = Offset(size.width * xFrac, tabTop),
                end = Offset(size.width * xFrac, bodyTop + stroke / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

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
