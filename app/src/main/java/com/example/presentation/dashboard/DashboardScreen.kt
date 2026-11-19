package com.example.presentation.dashboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monitoring.MonitoringPhase
import com.example.presentation.components.MonitoringStatusPill
import com.example.presentation.components.OrderEvaluationCard
import com.example.presentation.components.RankingModeSelector
import com.example.presentation.components.SelectorStubCard
import com.example.presentation.components.SmartCaptainLogo
import com.example.presentation.components.SummaryMetricCard
import com.example.presentation.components.formatValue

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.startMonitoringWithConsent(result.resultCode, data)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
        val pm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(pm.createScreenCaptureIntent())
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.scanScreenshot(uri)
        }
    }

    fun launchMonitoringFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.permissions.notificationsGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val pm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(pm.createScreenCaptureIntent())
        }
    }

    val isMonitoring = uiState.monitoringStatus.phase == MonitoringPhase.ACTIVE
    val evaluations = uiState.recommendation.rankedEvaluations
    val recommended = uiState.recommendation.recommendedOrder
    val highestFareEval = evaluations.maxByOrNull { it.totalExpectedFare ?: 0.0 }
    val bestRateEval = evaluations.maxByOrNull { it.farePerKm ?: 0.0 }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWide = maxWidth >= 600.dp

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartCaptainLogo()
                            MonitoringStatusPill(phase = uiState.monitoringStatus.phase)
                        }

                        Text(
                            text = uiState.monitoringStatus.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { launchMonitoringFlow() },
                                enabled = !isMonitoring,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("start_monitoring_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Start Monitor", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { viewModel.stopMonitoring() },
                                enabled = isMonitoring,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("stop_monitoring_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Stop Monitor", maxLines = 1)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_screenshot_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ImageSearch,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Scan Screenshot (OCR)")
                            }

                            TextButton(
                                onClick = onOpenPermissions,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("privacy_status_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Permissions and privacy",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text("Permissions")
                            }
                        }
                    }
                }
            }

            // Summary Cards Grid (2x2 on phone, 4x1 on tablet)
            item {
                val countLabel = uiState.recommendation.detectedOrderCountLabel
                val detectedValue = if (evaluations.isEmpty()) {
                    countLabel?.toString() ?: "0"
                } else if (countLabel != null && countLabel > evaluations.size) {
                    "${evaluations.size}/$countLabel"
                } else {
                    evaluations.size.toString()
                }
                val detectedSubtitle = if (countLabel != null) {
                    "Rapido shows $countLabel total"
                } else {
                    "On current scan"
                }

                val highestFareVal = highestFareEval?.totalExpectedFare?.let { "₹${formatValue(it)}" } ?: "—"
                val highestFareSub = highestFareEval?.let { "Order #${it.order.orderNumber}" } ?: "No scan yet"

                val bestRateVal = bestRateEval?.farePerKm?.let { "₹${formatValue(it)}/km" } ?: "—"
                val bestRateSub = bestRateEval?.totalDistanceKm?.let { "${formatValue(it)} km total" } ?: "Waiting"

                val bestProfitVal = recommended?.estimatedEarningsPerHour?.let { "₹${formatValue(it)}/hr" }
                    ?: recommended?.totalExpectedFare?.let { "₹${formatValue(it)}" }
                    ?: "—"
                val bestProfitSub = recommended?.let { "Order #${it.order.orderNumber} Pick" } ?: "No pick yet"

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryMetricCard(
                            title = "Orders Detected",
                            value = detectedValue,
                            subtitle = detectedSubtitle,
                            icon = Icons.Filled.ReceiptLong,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "Highest Fare",
                            value = highestFareVal,
                            subtitle = highestFareSub,
                            icon = Icons.Filled.AttachMoney,
                            accentContent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "Best ₹ / KM",
                            value = bestRateVal,
                            subtitle = bestRateSub,
                            icon = Icons.Filled.Speed,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "Most Profitable",
                            value = bestProfitVal,
                            subtitle = bestProfitSub,
                            icon = Icons.Filled.TrendingUp,
                            accentContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryMetricCard(
                                title = "Orders Detected",
                                value = detectedValue,
                                subtitle = detectedSubtitle,
                                icon = Icons.Filled.ReceiptLong,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricCard(
                                title = "Highest Fare",
                                value = highestFareVal,
                                subtitle = highestFareSub,
                                icon = Icons.Filled.AttachMoney,
                                accentContent = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryMetricCard(
                                title = "Best ₹ / KM",
                                value = bestRateVal,
                                subtitle = bestRateSub,
                                icon = Icons.Filled.Speed,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricCard(
                                title = "Most Profitable",
                                value = bestProfitVal,
                                subtitle = bestProfitSub,
                                icon = Icons.Filled.TrendingUp,
                                accentContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Ranking Priority Switcher + Comparison Insight
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ranking Priority",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (evaluations.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearCurrentResults() }) {
                                    Text("Clear Scan")
                                }
                            }
                        }

                        RankingModeSelector(
                            selectedMode = uiState.recommendation.rankingMode,
                            onModeSelected = { viewModel.selectRankingMode(it) }
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = uiState.recommendation.comparisonSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Selector Rail Stubs (off-screen Rapido orders detected in left strip)
            if (uiState.recommendation.selectorStubs.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Additional Orders in Rapido Left Rail",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(uiState.recommendation.selectorStubs) { stub ->
                                SelectorStubCard(stub = stub)
                            }
                        }
                    }
                }
            }

            // Available Orders Section or Onboarding Empty State
            if (evaluations.isEmpty()) {
                item {
                    EmptyDashboardCard(
                        overlayEnabled = uiState.settings.floatingOverlayEnabled,
                        overlayGranted = uiState.permissions.overlayGranted,
                        onStartMonitoring = { launchMonitoringFlow() },
                        onScanScreenshot = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Available Orders (${evaluations.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Sorted by ${uiState.recommendation.rankingMode.shortLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(
                    items = evaluations,
                    key = { "${it.order.orderId}_${it.order.orderNumber}" }
                ) { eval ->
                    OrderEvaluationCard(evaluation = eval)
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun EmptyDashboardCard(
    overlayEnabled: Boolean,
    overlayGranted: Boolean,
    onStartMonitoring: () -> Unit,
    onScanScreenshot: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = "How Smart Captain Works",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "100% on-device OCR • Zero Rapido credentials needed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            StepItem("1", "Tap Start Monitor and approve Android screen capture.")
            StepItem("2", "Switch to Rapido Captain when multiple orders appear.")
            StepItem("3", "Smart Captain calculates ₹/km, fuel, and net ₹/hr locally and highlights BEST VALUE.")

            AnimatedVisibility(visible = overlayEnabled && !overlayGranted) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Floating Overlay is enabled in Settings, but Display Over Other Apps permission is not granted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartMonitoring,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Start Live Monitor")
                }
                OutlinedButton(
                    onClick = onScanScreenshot,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Test Screenshot")
                }
            }
        }
    }
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
