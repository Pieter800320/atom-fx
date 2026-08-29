package com.pieter.atomfx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAtomColors = staticCompositionLocalOf { DarkColors }

object AtomTheme {
    val colors: AtomColors
        @Composable get() = LocalAtomColors.current
}

/**
 * Resolves [AtomColors] from the system theme (Design §2.1). A manual system/dark/light
 * override belongs to Settings, a later phase — this reads [isSystemInDarkTheme] only.
 */
@Composable
fun AtomFxTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    val materialScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(background = colors.ground, surface = colors.surface)
    } else {
        lightColorScheme(background = colors.ground, surface = colors.surface)
    }
    CompositionLocalProvider(LocalAtomColors provides colors) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
