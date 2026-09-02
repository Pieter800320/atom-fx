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
import com.pieter.atomfx.data.UserPreferences

private const val CHANNEL_ID = "atomfx_signals"
private const val DEEPLINK_EXTRA = "deeplink"

/**
 * Architecture §7: topic messaging on `atomfx-signals`, no per-device token registry — so
 * `onNewToken` has nothing to do. `onMessageReceived` only fires while the app process is
 * alive (foreground, or backgrounded but not killed); FCM's own system tray already handles
 * display when the process isn't running, forwarding the `data` payload as intent extras onto
 * the notification tap. Building the notification ourselves here just makes the foreground case
 * (which FCM does NOT auto-display) look and behave the same as that background path.
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

        val title = message.notification?.title ?: type ?: "ATOM FX"
        val body = message.notification?.body ?: return
        val deeplink = message.data["deeplink"]

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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
