package com.pieter.atomfx

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.messaging.FirebaseMessaging
import com.pieter.atomfx.push.extractDeepLinkUri
import com.pieter.atomfx.push.parseDeepLink
import com.pieter.atomfx.ui.sheets.SheetTarget
import com.pieter.atomfx.ui.theme.AtomFxTheme
import com.pieter.atomfx.ui.wheel.WheelScreen

private const val SIGNALS_TOPIC = "atomfx-signals"

/** Hosts only the Energy Wheel (Architecture §9). Bottom nav/tabs/sheets are later phases. */
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
        WheelScreen(isDark = isDark, initialDeepLink = deepLink)
    }
}
