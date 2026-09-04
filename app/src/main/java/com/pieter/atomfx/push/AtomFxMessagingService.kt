package com.pieter.atomfx.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pieter.atomfx.MainActivity
import com.pieter.atomfx.R
import com.pieter.atomfx.data.NotificationHistoryStore
import com.pieter.atomfx.data.UserPreferences

private const val CHANNEL_ID = "atomfx_signals"
private const val DEEPLINK_EXTRA = "deeplink"

/**
 * Architecture §7: topic messaging on `atomfx-signals`, no per-device token registry — so
 * `onNewToken` has nothing to do.
 *
 * 2026-09-04 (Notification History feature) — the backend now sends a **data-only** FCM
 * message (`push/send_push.py`), specifically so `onMessageReceived` fires every time, in
 * every app state (foreground, background, or killed — short of a fully force-stopped app,
 * an OS limit, not an FCM one). A combined notification+data message would have Android's own
 * system tray auto-display it whenever the app isn't foregrounded, bypassing this method
 * entirely — which would make the history record below miss most real deliveries. `title`/
 * `body` now live in `message.data`, not `message.notification`.
 */
class AtomFxMessagingService : FirebaseMessagingService() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // No-op: topic messaging only, no per-device token registry (Architecture §7).
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Functional Spec §9's per-type toggles — the backend still sends both gold-signal and
        // level-alert to one shared topic (Architecture §7), so this client-side check is the
        // only place a per-type "off" can actually be honoured today.
        val notif = UserPreferences(applicationContext).state.value.notifications
        if (!notif.enabled) return
        val type = message.data["type"]
        if (type == "gold_signal" && !notif.goldSignal) return
        if (type == "level_alert" && !notif.levelAlerts) return
        // Signals Roadmap §2 (Phase 1) — Structure covers structure_event (BOS + CHoCH, one
        // toggle); Regime covers both regime_flip and archetype_change (one toggle).
        if (type == "potential_state" && !notif.setupAlerts) return
        if (type == "structure_event" && !notif.structureAlerts) return
        if ((type == "regime_flip" || type == "archetype_change") && !notif.regimeAlerts) return
        if (type == "volatility_spike" && !notif.volatilityAlerts) return
        if (type == "tf_alignment" && !notif.alignmentAlerts) return
        // Signals Roadmap §4 (Phase 3).
        if (type == "conviction_extreme" && !notif.positioningAlerts) return

        val title = message.data["title"] ?: type ?: "ATOM FX"
        val body = message.data["body"] ?: return
        val deeplink = message.data["deeplink"]
        val direction = message.data["direction"]

        if (type != null) {
            NotificationHistoryStore(applicationContext).record(type, title, body, deeplink, direction)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deeplink != null) putExtra(DEEPLINK_EXTRA, deeplink)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        getSystemService<NotificationManager>()?.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ATOM FX signals",
            NotificationManager.IMPORTANCE_HIGH,
        )
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}

/** Reads the deep link Android attaches to a launch [Intent], from either source (see [parseDeepLink]). */
fun Intent.extractDeepLinkUri(): Uri? = data ?: getStringExtra(DEEPLINK_EXTRA)?.let(Uri::parse)
