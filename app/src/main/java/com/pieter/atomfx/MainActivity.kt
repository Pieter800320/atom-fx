package com.pieter.atomfx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.pieter.atomfx.ui.theme.AtomFxTheme
import com.pieter.atomfx.ui.wheel.MockWheelData
import com.pieter.atomfx.ui.wheel.WheelScreen

/** Phase 2: hosts only the Energy Wheel (Architecture §9). Bottom nav/tabs/sheets are later phases. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtomFxApp()
        }
    }
}

@Composable
private fun AtomFxApp() {
    val isDark = isSystemInDarkTheme()
    AtomFxTheme {
        WheelScreen(state = MockWheelData.state, isDark = isDark)
    }
}
