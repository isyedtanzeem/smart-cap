package com.example.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.services.RapidoNotificationListenerService

data class PermissionStatusSnapshot(
    val notificationsGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationListenerGranted: Boolean,
    val mediaProjectionRequiresPromptPerSession: Boolean = true
)

class PermissionManager(private val context: Context) {

    fun getStatusSnapshot(): PermissionStatusSnapshot {
        return PermissionStatusSnapshot(
            notificationsGranted = isPostNotificationsGranted(),
            overlayGranted = isOverlayGranted(),
            notificationListenerGranted = isNotificationListenerEnabled()
        )
    }

    fun isPostNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isOverlayGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, RapidoNotificationListenerService::class.java).flattenToString()
        return flat.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    fun createOverlaySettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
