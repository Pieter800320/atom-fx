package com.pieter.atomfx.ui.settings

import android.app.NotificationManager
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.firebase.messaging.FirebaseMessaging
import com.pieter.atomfx.R
import com.pieter.atomfx.SIGNALS_TOPIC
import com.pieter.atomfx.data.DEFAULT_REFRESH_MINUTES
import com.pieter.atomfx.data.DEFAULT_SIGNALS_URL
import com.pieter.atomfx.data.ThemeMode
import com.pieter.atomfx.data.UserPrefsState
import com.pieter.atomfx.data.UserPreferences
import com.pieter.atomfx.ui.sheets.SheetTabs
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.theme.AtomType
import com.pieter.atomfx.ui.wheel.Freshness
import com.pieter.atomfx.ui.wheel.WheelScreenState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Functional Spec §9 — reached from the header gear "on any tab" (Functional Spec §2/§3.1), shown
 * full-screen over whichever tab was active (spec: "full-screen or top sheet"). Six groups, in the
 * spec's own order: Theme, Notifications, Data source, Price-level alerts, Freshness/diagnostics,
 * About. No market-data or AI key fields anywhere — those live server-side (Architecture §8.1);
 * the only optional on-device credential is a GitHub PAT, and only inside Price-level alerts.
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    prefsState: UserPrefsState,
    loaded: WheelScreenState.Loaded?,
    colors: AtomColors,
    onRefreshNow: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(colors.ground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsHeader(colors, onClose)

            SettingsSection("THEME", colors) {
                ThemeControl(prefsState.themeMode, colors) { preferences.setThemeMode(it) }
            }

            SettingsSection("NOTIFICATIONS", colors) {
                NotificationsGroup(prefsState, colors, preferences)
            }

            SettingsSection("DATA SOURCE", colors) {
                DataSourceGroup(prefsState, colors, preferences)
            }

            SettingsSection("PRICE-LEVEL ALERTS", colors) {
                PriceLevelAlertsGroup(colors)
            }

            SettingsSection("FRESHNESS / DIAGNOSTICS", colors) {
                FreshnessGroup(loaded, colors, onRefreshNow)
            }

            SettingsSection("ABOUT", colors) {
                AboutGroup(colors)
            }
        }
    }
}

@Composable
private fun SettingsHeader(colors: AtomColors, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "SETTINGS", style = AtomType.Title.copy(color = colors.textPrimary))
        Text(
            text = "Close",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, colors: AtomColors, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 28.dp)) {
        Text(
            text = title,
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    colors: AtomColors,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = AtomType.Body.copy(color = colors.textPrimary))
            if (description != null) {
                Text(
                    text = description,
                    style = AtomType.Caption.copy(color = colors.textMuted),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing()
    }
}

/** A hand-rolled toggle (not Material3 `Switch`) so it reads from [AtomColors] tokens exactly
 *  like every other control in the app, with no separate Material colour scheme to reconcile. */
@Composable
private fun SettingsSwitch(checked: Boolean, colors: AtomColors, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val trackColor = when {
        !enabled -> colors.hairline
        checked -> colors.bull
        else -> colors.hairlineStrong
    }
    val thumbOffset by animateDpAsState(if (checked) 16.dp else 0.dp, label = "switchThumb")
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCheckedChange(!checked)
                    }
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.surface),
        )
    }
}

@Composable
private fun ThemeControl(mode: ThemeMode, colors: AtomColors, onSelect: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SheetTabs(
        tabs = options.map { it.name },
        selected = options.indexOf(mode),
        colors = colors,
        onSelect = { index -> onSelect(options[index]) },
    )
}

@Composable
private fun NotificationsGroup(prefsState: UserPrefsState, colors: AtomColors, preferences: UserPreferences) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val notif = prefsState.notifications

    SettingsRow("Push notifications", colors, "Gold signals and level alerts, via FCM") {
        SettingsSwitch(notif.enabled, colors) { enabled ->
            preferences.setNotificationsEnabled(enabled)
            if (enabled) {
                FirebaseMessaging.getInstance().subscribeToTopic(SIGNALS_TOPIC)
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(SIGNALS_TOPIC)
            }
        }
    }
    SettingsRow("Gold signal alerts", colors, enabled = notif.enabled, trailing = {
        SettingsSwitch(notif.goldSignal, colors, enabled = notif.enabled) { preferences.setGoldSignalEnabled(it) }
    })
    SettingsRow("Level alerts", colors, enabled = notif.enabled, trailing = {
        SettingsSwitch(notif.levelAlerts, colors, enabled = notif.enabled) { preferences.setLevelAlertsEnabled(it) }
    })
    Text(
        text = "Send test",
        style = AtomType.Caption.copy(color = if (notif.enabled) colors.textSecondary else colors.textMuted),
        modifier = Modifier
            .padding(top = 8.dp)
            .clickable(enabled = notif.enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendTestNotification(context)
            },
    )
}

// Overload with a per-row disabled look (Level/Gold rows are meaningless with push off).
@Composable
private fun SettingsRow(label: String, colors: AtomColors, enabled: Boolean, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AtomType.Body.copy(color = if (enabled) colors.textPrimary else colors.textMuted),
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        trailing()
    }
}

private fun sendTestNotification(context: android.content.Context) {
    val channelId = "atomfx_signals"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(channelId, "ATOM FX signals", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("ATOM FX")
        .setContentText("Test notification — push is working.")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    context.getSystemService<NotificationManager>()?.notify(System.currentTimeMillis().toInt(), notification)
}

@Composable
private fun DataSourceGroup(prefsState: UserPrefsState, colors: AtomColors, preferences: UserPreferences) {
    var urlText by remember(prefsState.signalsUrl) { mutableStateOf(prefsState.signalsUrl) }
    var minutesText by remember(prefsState.refreshMinutes) { mutableStateOf(prefsState.refreshMinutes.toString()) }

    Text(text = "signals.json URL", style = AtomType.Body.copy(color = colors.textPrimary), modifier = Modifier.padding(bottom = 4.dp))
    SettingsTextField(
        value = urlText,
        colors = colors,
        onValueChange = { urlText = it },
        onDone = { preferences.setSignalsUrl(urlText) },
    )
    if (prefsState.signalsUrl != DEFAULT_SIGNALS_URL) {
        Text(
            text = "Reset to default",
            style = AtomType.Caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(top = 4.dp).clickable {
                urlText = DEFAULT_SIGNALS_URL
                preferences.setSignalsUrl(DEFAULT_SIGNALS_URL)
            },
        )
    }

    SettingsRow("Refresh cadence (minutes, foreground-only)", colors) {
        SettingsTextField(
            value = minutesText,
            colors = colors,
            widthDp = 64,
            onValueChange = { minutesText = it.filter(Char::isDigit).take(3) },
            onDone = {
                preferences.setRefreshMinutes(minutesText.toIntOrNull() ?: DEFAULT_REFRESH_MINUTES)
            },
        )
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    colors: AtomColors,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    widthDp: Int? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = AtomType.Body.copy(color = colors.textPrimary),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.textPrimary),
        modifier = (if (widthDp != null) Modifier.size(width = widthDp.dp, height = 36.dp) else Modifier.fillMaxWidth().height(36.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceRaised)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun PriceLevelAlertsGroup(colors: AtomColors) {
    SettingsRow(
        "Sync alerts to GitHub",
        colors,
        "Not available yet — needs an on-device \"set alert\" row on the pair sheet, tracked separately",
    ) {
        SettingsSwitch(checked = false, colors = colors, enabled = false) {}
    }
}

@Composable
private fun FreshnessGroup(loaded: WheelScreenState.Loaded?, colors: AtomColors, onRefreshNow: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val updated = loaded?.signals?.updated?.let {
        runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) }.getOrNull()
    } ?: "—"
    val freshnessWord = when (loaded?.freshness) {
        Freshness.FRESH -> "Fresh"
        Freshness.STALE -> "Stale"
        null -> "—"
    }
    val schema = loaded?.signals?.schemaVersion?.toString() ?: "—"

    DiagRow("Last updated", updated, colors)
    DiagRow("Status", freshnessWord, colors, if (loaded?.freshness == Freshness.STALE) colors.bear else colors.bull)
    DiagRow("Schema version", schema, colors)
    Text(
        text = "Force refresh",
        style = AtomType.Caption.copy(color = colors.textSecondary),
        modifier = Modifier.padding(top = 8.dp).clickable {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRefreshNow()
        },
    )
}

@Composable
private fun DiagRow(label: String, value: String, colors: AtomColors, valueColor: androidx.compose.ui.graphics.Color = colors.textPrimary) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = AtomType.Body.copy(color = colors.textSecondary))
        Text(text = value, style = AtomType.Body.copy(color = valueColor))
    }
}

/** Sourced directly from `docs/GLOSSARY.md` — the app's own canonical term definitions, not
 *  re-explained from scratch. */
@Composable
private fun AboutGroup(colors: AtomColors) {
    Text(
        text = "No API keys live on this device. Market-data and AI keys stay server-side; " +
            "the app only reads signals.json and receives push.",
        style = AtomType.Caption.copy(color = colors.textMuted),
        modifier = Modifier.padding(bottom = 12.dp),
    )
    AboutEntry("Potential", "0–100, radius on the pair wheel. How many of the six confluence factors a pair has passed.", colors)
    AboutEntry("Rings 1–6", "Regime · Currency Flow · Breadth · Momentum · Structure · Entry Setup, in this order.", colors)
    AboutEntry("Level / state", "0–6 factors passed. LOW (0–2) · WATCH (3–5) · TRADEABLE (6) · A+ (6 and Setup Rank ≥ 8.5).", colors)
    AboutEntry("CSM / CSM Delta", "Currency Strength, 0–100 per currency. Delta is the flow signal — getting stronger or weaker.", colors)
    AboutEntry("Breadth", "How many of a currency's relationships agree with its move — broad vs narrow.", colors)
    AboutEntry("Macro archetype", "One of ten handbook regimes (A–J). Confidence = how many distinct evidence axes agree.", colors)
}

@Composable
private fun AboutEntry(term: String, definition: String, colors: AtomColors) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(text = term, style = AtomType.Body.copy(color = colors.textPrimary))
        Text(text = definition, style = AtomType.Caption.copy(color = colors.textSecondary), modifier = Modifier.padding(top = 2.dp))
    }
}
