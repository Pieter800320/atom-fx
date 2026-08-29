package com.pieter.atomfx

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

private const val SIGNALS_TOPIC = "atomfx-signals"

private enum class AppScreen { Wheel, Macro }

/** Hosts the Energy Wheel and (Phase 9) the Macro screen, switched by a slim bottom strip. */
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
        var screen by remember { mutableStateOf(AppScreen.Wheel) }
        val colors = AtomTheme.colors

        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                when (screen) {
                    AppScreen.Wheel -> WheelScreen(viewModel = viewModel, isDark = isDark, initialDeepLink = deepLink)
                    AppScreen.Macro -> MacroScreen(viewModel = viewModel, colors = colors)
                }
            }
            ScreenSwitchStrip(screen = screen, colors = colors) { screen = it }
        }
    }
}

/**
 * A deliberately minimal two-destination switcher (Phase 9's Context: only the Macro screen is
 * in scope — Currency wheel/Insights/Settings aren't — so a full `AppScaffold`/Navigation-Compose
 * setup would be scaffolding ahead of need).
 */
@Composable
private fun ScreenSwitchStrip(screen: AppScreen, colors: AtomColors, onSelect: (AppScreen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AppScreen.entries.forEach { entry ->
            Text(
                text = entry.name.uppercase(),
                style = AtomType.Caption.copy(color = if (entry == screen) colors.textPrimary else colors.textMuted),
                modifier = Modifier.clickable { onSelect(entry) },
            )
        }
    }
}
