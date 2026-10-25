package com.example.capsule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService

// Helps Capsule stay alive. Android phones, HyperOS especially, can stop a background service or switch an
// accessibility service off. This keeps a record of it, asks Android to reconnect, and, if the user allowed
// it once with a single adb command, switches the accessibility service back on by itself.
object KeepAlive {
    const val ACTION = "com.example.capsulelite.KEEPALIVE"
    private const val ALARM_REQUEST = 4711
    private const val REPAIR_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    @Volatile var listenerConnected = false
    @Volatile var accessibilityConnected = false

    private fun enabled(ctx: Context, key: String): List<ComponentName> =
        (Settings.Secure.getString(ctx.contentResolver, key) ?: "")
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }

    fun isListenerEnabled(ctx: Context): Boolean =
        enabled(ctx, "enabled_notification_listeners").any { it.packageName == ctx.packageName }

    fun isAccessibilityEnabled(ctx: Context): Boolean =
        enabled(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).any { it.packageName == ctx.packageName }

    fun canSelfRepair(ctx: Context): Boolean =
        ctx.checkSelfPermission(REPAIR_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun adbCommand(ctx: Context): String = "adb shell pm grant ${ctx.packageName} $REPAIR_PERMISSION"

    // Switches the accessibility service back on if Android turned it off. Needs the one-time adb permission.
    fun ensureAccessibility(ctx: Context): String {
        if (isAccessibilityEnabled(ctx)) return "on"
        if (!canSelfRepair(ctx)) return "OFF (self-repair permission not given)"
        return runCatching {
            val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val flat = ComponentName(ctx, CapsuleWatchdog::class.java).flattenToString()
            val next = if (cur.isBlank()) flat else "$cur:$flat"
            Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            "was OFF, switched back on"
        }.getOrElse { "OFF, repair failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    // Same for the notification listener
    fun ensureListener(ctx: Context): String {
        if (isListenerEnabled(ctx)) return "on"
        if (!canSelfRepair(ctx)) return "OFF (self-repair permission not given)"
        return runCatching {
            val cur = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
            val flat = ComponentName(ctx, CapsuleService::class.java).flattenToString()
            Settings.Secure.putString(ctx.contentResolver, "enabled_notification_listeners", if (cur.isBlank()) flat else "$cur:$flat")
            "was OFF, switched back on"
        }.getOrElse { "OFF, repair failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    fun schedule(ctx: Context, delayMs: Long = 15 * 60 * 1000L) {
        runCatching {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, ALARM_REQUEST,
                Intent(ctx, KeepAliveReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
        }
    }

    fun status(ctx: Context): String {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val prefs = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
        return buildString {
            append("overlay permission: ").append(Settings.canDrawOverlays(ctx)).append('\n')
            append("notification listener: enabled=").append(isListenerEnabled(ctx))
                .append(", connected=").append(listenerConnected).append('\n')
            append("accessibility keep-alive: enabled=").append(isAccessibilityEnabled(ctx))
                .append(", connected=").append(accessibilityConnected).append('\n')
            append("battery optimisation ignored: ").append(pm.isIgnoringBatteryOptimizations(ctx.packageName)).append('\n')
            append("self-repair permission given: ").append(canSelfRepair(ctx)).append('\n')
            append("keep-running notification: ").append(prefs.getBoolean("keepNotif", true)).append('\n')
        }
    }
}

// Wakes up now and then, even if the app's process was stopped, writes a line to the log,
// asks Android to reconnect the notification listener, and repairs accessibility if allowed.
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            DiagLog.installCrashHandler(context)
            val acc = KeepAlive.ensureAccessibility(context)
            val lis = KeepAlive.ensureListener(context)
            DiagLog.add(
                context,
                "keep-alive check: listener=$lis (connected=${KeepAlive.listenerConnected}), accessibility=$acc " +
                    "(connected=${KeepAlive.accessibilityConnected})"
            )
            if (KeepAlive.isListenerEnabled(context)) {
                NotificationListenerService.requestRebind(ComponentName(context, CapsuleService::class.java))
            }
        }
        KeepAlive.schedule(context)
    }
}
