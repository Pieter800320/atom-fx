package com.pieter.atomfx.ui.theme

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Every Material3 `ModalBottomSheet` and every Compose `Dialog` (the Reading Window) renders in
 * its OWN Android window, separate from the Activity's — `MainActivity`'s own system-bar
 * `SideEffect` only ever reaches `(view.context as? Activity)?.window`, never a dialog's window,
 * so a sheet/Reading-Window kept the OS default (light) nav/status bar regardless of app theme
 * (2026-09-10, Pieter's report). Call this from inside the sheet/dialog's own content — anywhere
 * `LocalView.current` resolves to a view actually hosted in that window.
 *
 * Took three real passes to land, confirmed each time with an actual on-device screenshot
 * (`adb shell screencap`) rather than assuming a fix worked — the first two both compiled clean
 * and *looked* right in code but changed nothing visually:
 * 1. Appearance flags alone (`isAppearanceLight*`) — not enough on their own.
 * 2. Added `setDecorFitsSystemWindows(false)` + `setLayout(MATCH_PARENT, MATCH_PARENT)` — still
 *    not enough.
 * 3. Logcat'd this window's own `attrs.flags` against `ModalBottomSheetDialogLayout`'s (which
 *    already worked correctly) and diffed the two bitmasks directly — found `FLAG_DIM_BEHIND` set
 *    on the Dialog's window that the working sheet's window didn't have. Clearing it (plus adding
 *    `FLAG_LAYOUT_NO_LIMITS`) was the actual fix — confirmed by the two windows' flag bitmasks
 *    matching exactly afterward, then confirmed again visually. `FLAG_DIM_BEHIND` is also just
 *    correct to drop on its own terms: the Reading Window's own doc comment says it deliberately
 *    wants "no scrim showing the screen behind it," and a dim-behind flag is exactly a scrim.
 */
@Composable
fun DarkenSystemBarsForDialog(isDark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val dialogWindow = findDialogWindow(view) ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        @Suppress("DEPRECATION")
        run {
            dialogWindow.statusBarColor = android.graphics.Color.TRANSPARENT
            dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        val insetsController = WindowCompat.getInsetsController(dialogWindow, view)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }
}

private tailrec fun findDialogWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    val parent = view.parent as? View ?: return null
    return findDialogWindow(parent)
}
