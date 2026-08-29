package com.pieter.atomfx.push

import android.net.Uri
import com.pieter.atomfx.ui.sheets.SheetTarget

/**
 * Parses `atomfx://pair/<PAIR>` and `atomfx://regime` (Architecture §7's notification
 * `deeplink` field) into the sheet they should open. Same URI shape whether it arrives as a
 * real `Uri` (adb, or a background-notification tap Android routes through the activity's own
 * intent-filter) or as the `deeplink` string extra FCM forwards onto that launch intent.
 */
fun parseDeepLink(uri: Uri?): SheetTarget? {
    if (uri == null || uri.scheme != "atomfx") return null
    return when (uri.host) {
        "pair" -> uri.pathSegments.firstOrNull()?.let { SheetTarget.Node(it) }
        "regime" -> SheetTarget.Nucleus
        else -> null
    }
}
