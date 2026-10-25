package com.example.capsule

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

// Keep-alive. When the user has turned on navigation info, it also reads text from the Google Maps
// screen only. It never reads other apps' screens, and it doesn't save or send what it reads.
class CapsuleWatchdog : AccessibilityService() {

    private var lastRead = 0L
    private var lastErrorAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        KeepAlive.accessibilityConnected = true
        DiagLog.installCrashHandler(this)
        DiagLog.add(this, "accessibility keep-alive connected")
        NotificationListenerService.requestRebind(ComponentName(this, CapsuleService::class.java))
        KeepAlive.schedule(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handle(event)
        } catch (t: Throwable) {
            // Never let an error here stop the service: Android can switch off a service that keeps failing
            val now = SystemClock.uptimeMillis()
            if (now - lastErrorAt > 30000) {
                lastErrorAt = now
                DiagLog.add(this, "accessibility event error: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun handle(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.packageName?.toString() != CapsulePrefs.MAPS_PKG) {
            // Leaving Maps: the screen text is stale now, unless the Maps notification keeps feeding the data
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !MapsNav.notifAlive) MapsNav.active = false
            return
        }
        val on = getSharedPreferences("capsule", MODE_PRIVATE).getBoolean("mapsInfo", false)
        if (!on) return
        val now = SystemClock.uptimeMillis()
        if (now - lastRead < 500) return
        lastRead = now
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collect(root, texts, 0)
        MapsNav.update(texts)
    }

    // Reads the texts on screen, with limits so a huge screen can never slow the service down
    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > 600) return
        node.text?.let { out.add(it.toString()) }
        node.contentDescription?.let { out.add(it.toString()) }
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
    }

    override fun onInterrupt() {
        DiagLog.add(this, "accessibility keep-alive interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        KeepAlive.accessibilityConnected = false
        DiagLog.add(this, "accessibility keep-alive unbound (Android disconnected it)")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        KeepAlive.accessibilityConnected = false
        DiagLog.add(this, "accessibility keep-alive destroyed")
        super.onDestroy()
    }
}
