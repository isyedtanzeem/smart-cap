package com.example.presentation.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.permissions.PermissionManager
import com.example.presentation.components.RankingModeSelector
import com.example.presentation.components.formatValue
import com.example.ui.theme.NumericCompactStyle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    permissionManager: PermissionManager,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateNotificationsEnabled(granted)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Captain Settings",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Customize thresholds, fuel expenses, and alert behavior",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { viewModel.resetDefaults() },
                        modifier = Modifier.testTag("reset_settings_button")
                    ) {
                        Text("Reset")
                    }
                }
            }

            // Preferred Ranking Mode
            item {
                SettingsGroupCard(title = "Ranking & Bonus Rules") {
                    RankingModeSelector(
                        selectedMode = settings.preferredRankingMode,
                        onModeSelected = { viewModel.updateRankingMode(it) }
                    )
                    Text(
                        text = settings.preferredRankingMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ToggleRow(
                        title = "Include Rapido Bonus in Total Fare",
                        subtitle = "Bonuses are always displayed separately; disable if incentives have strict conditions.",
                        checked = settings.includeBonusInTotal,
                        onCheckedChange = { viewModel.updateIncludeBonus(it) },
                        testTag = "toggle_include_bonus"
                    )
                }
            }

            // Captain Minimum Thresholds
            item {
                SettingsGroupCard(title = "Order Profitability Thresholds") {
                    SliderSettingRow(
                        label = "Minimum Expected Fare",
                        valueText = "₹${formatValue(settings.minExpectedFare)}",
                        value = settings.minExpectedFare.toFloat(),
                        range = 15f..150f,
                        onValueChange = { viewModel.updateMinExpectedFare((it / 5f).roundToInt() * 5.0) }
                    )
                    SliderSettingRow(
                        label = "Minimum Fare Per KM",
                        valueText = "₹${formatValue(settings.minFarePerKm)}/km",
                        value = settings.minFarePerKm.toFloat(),
                        range = 5f..30f,
                        onValueChange = { viewModel.updateMinFarePerKm((it * 2f).roundToInt() / 2.0) }
                    )
                    SliderSettingRow(
                        label = "Maximum Pickup Distance",
                        valueText = "${formatValue(settings.maxPickupDistanceKm)} km",
                        value = settings.maxPickupDistanceKm.toFloat(),
                        range = 0.5f..8.0f,
                        onValueChange = { viewModel.updateMaxPickupDistance((it * 2f).roundToInt() / 2.0) }
                    )
                    SliderSettingRow(
                        label = "Minimum Expected Net Earnings",
                        valueText = "₹${formatValue(settings.minExpectedNetEarnings)}",
                        value = settings.minExpectedNetEarnings.toFloat(),
                        range = 10f..120f,
                        onValueChange = { viewModel.updateMinNetEarnings((it / 5f).roundToInt() * 5.0) }
                    )
                }
            }

            // Fuel & Trip Duration Assumptions
            item {
                SettingsGroupCard(title = "Expenses & Hourly Speed Model") {
                    SliderSettingRow(
                        label = "Fuel Cost Per KM",
                        valueText = "₹${formatValue(settings.fuelCostPerKm)}/km",
                        value = settings.fuelCostPerKm.toFloat(),
                        range = 0.5f..6.0f,
                        onValueChange = { viewModel.updateFuelCostPerKm((it * 10f).roundToInt() / 10.0) }
                    )
                    SliderSettingRow(
                        label = "Other Expense Per Order",
                        valueText = "₹${formatValue(settings.otherExpensePerOrder)}",
                        value = settings.otherExpensePerOrder.toFloat(),
                        range = 0f..15f,
                        onValueChange = { viewModel.updateOtherExpense(it.roundToInt().toDouble()) }
                    )
                    SliderSettingRow(
                        label = "Average Riding Speed",
                        valueText = "${formatValue(settings.averageSpeedKmh)} km/h",
                        value = settings.averageSpeedKmh.toFloat(),
                        range = 10f..45f,
                        onValueChange = { viewModel.updateAverageSpeed(it.roundToInt().toDouble()) }
                    )
                    SliderSettingRow(
                        label = "Pickup Wait Buffer",
                        valueText = "${formatValue(settings.pickupWaitMinutes)} min",
                        value = settings.pickupWaitMinutes.toFloat(),
                        range = 1f..15f,
                        onValueChange = { viewModel.updatePickupWait(it.roundToInt().toDouble()) }
                    )
                }
            }

            // Monitoring, Overlay & Alerts
            item {
                SettingsGroupCard(title = "OCR Scanning, Overlay & Alerts") {
                    SliderSettingRow(
                        label = "OCR Scanning Interval",
                        valueText = "${String.format("%.1f", settings.ocrScanningIntervalMs / 1000.0)}s",
                        value = (settings.ocrScanningIntervalMs / 1000f),
                        range = 1.0f..5.0f,
                        onValueChange = { viewModel.updateOcrIntervalMs(((it * 2f).roundToInt() * 500L)) }
                    )

                    ToggleRow(
                        title = "Floating Recommendation Overlay",
                        subtitle = "Shows a compact, draggable top-right card over Rapido without blocking Accept.",
                        checked = settings.floatingOverlayEnabled,
                        onCheckedChange = { enable ->
                            if (enable && !permissionManager.isOverlayGranted()) {
                                context.startActivity(permissionManager.createOverlaySettingsIntent())
                            }
                            viewModel.updateFloatingOverlay(enable)
                        },
                        testTag = "toggle_floating_overlay"
                    )

                    ToggleRow(
                        title = "Best Order Notification Alerts",
                        subtitle = "Posts a high-priority local notification when a better order appears.",
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !permissionManager.isPostNotificationsGranted()
                            ) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateNotificationsEnabled(enable)
                            }
                        },
                        testTag = "toggle_notifications"
                    )

                    OutlinedButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Manage Permissions & Notification Listener")
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = NumericCompactStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
