package com.pieter.atomfx.push

import android.net.Uri
import com.pieter.atomfx.ui.sheets.SheetTarget

/**
 * Parses `atomfx://pair/<PAIR>`, `atomfx://regime` and `atomfx://currency/<CCY>`
 * (Architecture §7's notification `deeplink` field) into the sheet they should open. Same
 * URI shape whether it arrives as a real `Uri` (adb, or a background-notification tap Android
 * routes through the activity's own intent-filter) or as the `deeplink` string extra FCM
 * forwards onto that launch intent.
 *
 * `currency` added 2026-09-04 (Signals Roadmap §4) for the `conviction_extreme` alert, whose
 * natural destination is the Currency Detail sheet (`SheetTarget.Currency`), not a pair or the
 * regime nucleus.
 */
fun parseDeepLink(uri: Uri?): SheetTarget? {
    if (uri == null || uri.scheme != "atomfx") return null
    return when (uri.host) {
        "pair" -> uri.pathSegments.firstOrNull()?.let { SheetTarget.Node(it) }
        "regime" -> SheetTarget.Nucleus
        "currency" -> uri.pathSegments.firstOrNull()?.let { SheetTarget.Currency(it) }
        else -> null
    }
}
