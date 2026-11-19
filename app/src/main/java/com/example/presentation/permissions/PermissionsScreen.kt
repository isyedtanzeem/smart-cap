package com.example.presentation.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.permissions.PermissionManager

@Composable
fun PermissionsScreen(
    permissionManager: PermissionManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(permissionManager.getStatusSnapshot()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = permissionManager.getStatusSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        snapshot = permissionManager.getStatusSnapshot()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Privacy & Permissions",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Transparent explanations for every Android permission Smart Captain uses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Local-Only Privacy Guarantee
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "100% On-Device Processing",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "• Captured frames are analyzed locally with ML Kit OCR and immediately discarded.\n• No screenshots, customer addresses, OTPs, or credentials ever leave your phone.\n• Smart Captain never auto-clicks Accept or Reject in Rapido.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.Cast,
                    title = "Screen Capture (MediaProjection)",
                    description = "Required only while monitoring is active to read visible Rapido Captain order cards. Android requires your explicit confirmation each session.",
                    statusLabel = "Prompted on Start Monitor",
                    isGranted = true,
                    actionLabel = null,
                    onAction = {}
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Notifications (Android 13+)",
                    description = "Displays the mandatory foreground service indicator while capturing and notifies you when a Best Value order is detected.",
                    statusLabel = if (snapshot.notificationsGranted) "Granted" else "Needed",
                    isGranted = snapshot.notificationsGranted,
                    actionLabel = if (snapshot.notificationsGranted) null else "Grant Notification Permission",
                    testTag = "grant_notifications_button",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.Layers,
                    title = "Display Over Other Apps (Optional)",
                    description = "Allows Smart Captain to float a compact, draggable Best Value pill at the top-right over Rapido without blocking Rapido's Accept button.",
                    statusLabel = if (snapshot.overlayGranted) "Granted" else "Optional • Off",
                    isGranted = snapshot.overlayGranted,
                    actionLabel = if (snapshot.overlayGranted) "Manage Overlay Setting" else "Enable Overlay Permission",
                    testTag = "grant_overlay_button",
                    onAction = {
                        context.startActivity(permissionManager.createOverlaySettingsIntent())
                    }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.NotificationsNone,
                    title = "Notification Listener (Optional)",
                    description = "Supplementary helper that detects incoming Rapido order notifications and triggers an immediate OCR scan while monitoring is active.",
                    statusLabel = if (snapshot.notificationListenerGranted) "Enabled" else "Optional • Off",
                    isGranted = snapshot.notificationListenerGranted,
                    actionLabel = "Open Notification Access Settings",
                    testTag = "grant_notification_listener_button",
                    onAction = {
                        context.startActivity(permissionManager.createNotificationListenerSettingsIntent())
                    }
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    statusLabel: String,
    isGranted: Boolean,
    actionLabel: String?,
    testTag: String? = null,
    onAction: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Surface(
                    color = if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (actionLabel != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .let { if (testTag != null) it.testTag(testTag) else it }
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
