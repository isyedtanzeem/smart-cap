package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.AcceptanceStatus

@Entity(tableName = "detected_orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val orderNumber: Int,
    val orderType: String,
    val baseFare: Double?,
    val bonus: Double?,
    val totalExpectedFare: Double?,
    val pickupDistanceKm: Double?,
    val deliveryDistanceKm: Double?,
    val totalDistanceKm: Double?,
    val farePerKm: Double?,
    val netEstimatedEarnings: Double?,
    val estimatedEarningsPerHour: Double?,
    val pickupAddress: String?,
    val deliveryAddress: String?,
    val foodReady: Boolean,
    val confidenceScore: Float,
    val rankingModeUsed: String,
    val isRecommended: Boolean,
    val recommendationReason: String,
    val acceptanceStatus: String = AcceptanceStatus.DETECTED.name,
    val actualCompletedEarnings: Double? = null,
    val detectedAtEpochMs: Long,
    val dayKey: String
)
