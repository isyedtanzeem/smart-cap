package com.example.presentation.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.OrderEntity
import com.example.domain.model.AcceptanceStatus
import com.example.presentation.components.formatValue
import com.example.ui.theme.NumericCompactStyle
import com.example.ui.theme.NumericMediumStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrderHistoryScreen(
    viewModel: OrderHistoryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var completingEntity by remember { mutableStateOf<OrderEntity?>(null) }
    var earningsInput by remember { mutableStateOf("") }

    completingEntity?.let { entity ->
        AlertDialog(
            onDismissRequest = { completingEntity = null },
            title = { Text("Record Completed Trip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Confirm your actual net or payout for #${entity.orderNumber} (${entity.orderType}). Detected offers are never marked completed automatically.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = earningsInput,
                        onValueChange = { earningsInput = it },
                        label = { Text("Recorded Earnings (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = earningsInput.toDoubleOrNull()
                    viewModel.markCompleted(entity.id, amount)
                    completingEntity = null
                }) {
                    Text("Save Completed")
                }
            },
            dismissButton = {
                TextButton(onClick = { completingEntity = null }) {
                    Text("Cancel")
                }
            }
        )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Order History",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Today: ${uiState.todaySummary.dayKey.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.allOrders.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Text("Clear All")
                        }
                    }
                }
            }

            // Daily Summary Card
            item {
                val summary = uiState.todaySummary
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Daily Summary",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryStat("Detected Offers", "${summary.totalDetectedOrders}")
                            SummaryStat("Highest Fare", "₹${formatValue(summary.highestFare)}")
                            SummaryStat("Avg ₹/km", "₹${formatValue(summary.averageFarePerKm)}")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryStat(
                                "Offer Net Est.",
                                "₹${formatValue(summary.estimatedPotentialEarnings)}"
                            )
                            SummaryStat(
                                "Completed Trips",
                                "${summary.completedCount}"
                            )
                            SummaryStat(
                                "Recorded Earned",
                                "₹${formatValue(summary.recordedCompletedEarnings)}",
                                highlight = true
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Detected offers are kept separate from accepted or completed trips unless you mark them below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.selectedFilter == filter,
                            onClick = { viewModel.selectFilter(filter) },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (uiState.filteredOrders.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No orders in this view yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Scanned Rapido offers will appear here automatically with fare, distance, and net estimates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.filteredOrders, key = { it.id }) { item ->
                    HistoryOrderCard(
                        entity = item,
                        onMarkAccepted = { viewModel.markAccepted(item.id) },
                        onMarkCompletedClick = {
                            earningsInput = (item.netEstimatedEarnings ?: item.totalExpectedFare ?: 0.0)
                                .let { formatValue(it) }
                            completingEntity = item
                        },
                        onDelete = { viewModel.deleteEntry(item.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = NumericMediumStyle,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HistoryOrderCard(
    entity: OrderEntity,
    onMarkAccepted: () -> Unit,
    onMarkCompletedClick: () -> Unit,
    onDelete: () -> Unit
) {
    val status = runCatching { AcceptanceStatus.valueOf(entity.acceptanceStatus) }
        .getOrDefault(AcceptanceStatus.DETECTED)
    val timeText = remember(entity.detectedAtEpochMs) {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(entity.detectedAtEpochMs))
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (entity.isRecommended) 1.5.dp else 1.dp,
            color = if (entity.isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${entity.orderNumber} ${entity.orderType}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Surface(
                        color = when (status) {
                            AcceptanceStatus.COMPLETED_BY_CAPTAIN -> MaterialTheme.colorScheme.primaryContainer
                            AcceptanceStatus.ACCEPTED_BY_CAPTAIN -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (status) {
                                AcceptanceStatus.COMPLETED_BY_CAPTAIN -> MaterialTheme.colorScheme.onPrimaryContainer
                                AcceptanceStatus.ACCEPTED_BY_CAPTAIN -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete history entry",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val base = entity.baseFare?.let { "₹${formatValue(it)}" } ?: "?"
                val bonus = entity.bonus?.let { " + ₹${formatValue(it)}" } ?: ""
                Text(
                    text = "Fare: $base$bonus",
                    style = NumericCompactStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entity.totalDistanceKm?.let { "${formatValue(it)} km" } ?: "? km",
                    style = NumericCompactStyle
                )
                Text(
                    text = entity.farePerKm?.let { "₹${formatValue(it)}/km" } ?: "—",
                    style = NumericCompactStyle
                )
                Text(
                    text = entity.netEstimatedEarnings?.let { "Net ₹${formatValue(it)}" } ?: "—",
                    style = NumericCompactStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!entity.pickupAddress.isNullOrBlank() || !entity.deliveryAddress.isNullOrBlank()) {
                Text(
                    text = "${entity.pickupAddress ?: "?"} → ${entity.deliveryAddress ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (status == AcceptanceStatus.DETECTED) {
                    OutlinedButton(
                        onClick = onMarkAccepted,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Mark Accepted")
                    }
                }
                if (status != AcceptanceStatus.COMPLETED_BY_CAPTAIN) {
                    Button(
                        onClick = onMarkCompletedClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Mark Completed")
                    }
                } else if (entity.actualCompletedEarnings != null) {
                    Text(
                        text = "Captain Recorded Payout: ₹${formatValue(entity.actualCompletedEarnings)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
