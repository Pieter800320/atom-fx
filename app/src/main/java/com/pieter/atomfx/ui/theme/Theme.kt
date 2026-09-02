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
 * Resolves [AtomColors] from [isDark] — the caller has already folded in the stored
 * `system | dark | light` override (Design §2.1, Functional Spec §9) with [isSystemInDarkTheme]
 * as the `system` case, so every consumer (this, the wheel `Canvas`, etc.) agrees on one value.
 */
@Composable
fun AtomFxTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val colors = if (isDark) DarkColors else LightColors
    val materialScheme = if (isDark) {
        darkColorScheme(background = colors.ground, surface = colors.surface)
    } else {
        lightColorScheme(background = colors.ground, surface = colors.surface)
    }
    CompositionLocalProvider(LocalAtomColors provides colors) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
