package com.example.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.SmartCaptainApp

/**
 * Optional supplementary listener that detects new Rapido Captain order alerts and triggers
 * an immediate local OCR screen scan when monitoring is active. Never stores or transmits
 * notification contents.
 */
class RapidoNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (!pkg.contains("rapido", ignoreCase = true)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val combined = "$title $text"

        if (
            combined.contains("order", ignoreCase = true) ||
            combined.contains("delivery", ignoreCase = true) ||
            combined.contains("₹")
        ) {
            (application as? SmartCaptainApp)?.rapidoScreenMonitor?.triggerImmediateScan()
        }
    }
}
