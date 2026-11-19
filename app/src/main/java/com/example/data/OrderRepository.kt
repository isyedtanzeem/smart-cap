package com.example.data

import com.example.database.OrderDao
import com.example.database.OrderEntity
import com.example.domain.model.AcceptanceStatus
import com.example.domain.model.DailySummary
import com.example.domain.model.FoodReadyStatus
import com.example.domain.model.OrderEvaluation
import com.example.domain.model.RankingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderRepository(private val orderDao: OrderDao) {

    fun observeAllHistory(): Flow<List<OrderEntity>> = orderDao.observeAllOrders()

    fun observeTodayHistory(): Flow<List<OrderEntity>> =
        orderDao.observeOrdersForDay(currentDayKey())

    fun observeTodaySummary(): Flow<DailySummary> = observeTodayHistory().map { list ->
        computeDailySummary(currentDayKey(), list)
    }

    fun observeAllDailySummaries(): Flow<List<DailySummary>> = observeAllHistory().map { list ->
        list.groupBy { it.dayKey }
            .entries
            .sortedByDescending { it.key }
            .map { (dayKey, items) -> computeDailySummary(dayKey, items) }
    }

    suspend fun recordEvaluatedScan(
        evaluations: List<OrderEvaluation>,
        rankingMode: RankingMode,
        dedupeWindowMs: Long = 120_000L
    ): Int {
        if (evaluations.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val dayKey = formatDayKey(now)
        val since = now - dedupeWindowMs

        val toInsert = mutableListOf<OrderEntity>()
        for (eval in evaluations) {
            val order = eval.order
            val dedupeKey = buildDedupeKey(eval)
            val existing = orderDao.findRecentByDedupeKey(dedupeKey, since)
            if (existing == null) {
                toInsert += OrderEntity(
                    dedupeKey = dedupeKey,
                    orderNumber = order.orderNumber,
                    orderType = order.orderType.displayLabel,
                    baseFare = order.baseFare,
                    bonus = order.bonus,
                    totalExpectedFare = eval.totalExpectedFare,
                    pickupDistanceKm = order.pickupDistance,
                    deliveryDistanceKm = order.deliveryDistance,
                    totalDistanceKm = eval.totalDistanceKm,
                    farePerKm = eval.farePerKm,
                    netEstimatedEarnings = eval.netEstimatedEarnings,
                    estimatedEarningsPerHour = eval.estimatedEarningsPerHour,
                    pickupAddress = order.pickupAddress,
                    deliveryAddress = order.deliveryAddress,
                    foodReady = order.foodReadyStatus == FoodReadyStatus.READY,
                    confidenceScore = order.confidenceScore,
                    rankingModeUsed = rankingMode.name,
                    isRecommended = eval.isRecommended,
                    recommendationReason = eval.recommendationReason,
                    acceptanceStatus = AcceptanceStatus.DETECTED.name,
                    actualCompletedEarnings = null,
                    detectedAtEpochMs = order.detectedAt,
                    dayKey = dayKey
                )
            }
        }

        if (toInsert.isNotEmpty()) {
            orderDao.insertOrders(toInsert)
        }
        return toInsert.size
    }

    suspend fun markOrderStatus(
        entityId: Long,
        status: AcceptanceStatus,
        completedEarnings: Double? = null
    ) {
        orderDao.updateAcceptanceStatus(
            id = entityId,
            status = status.name,
            completedEarnings = completedEarnings
        )
    }

    suspend fun deleteOrder(entityId: Long) {
        orderDao.deleteById(entityId)
    }

    suspend fun clearAllHistory() {
        orderDao.clearAll()
    }

    private fun buildDedupeKey(eval: OrderEvaluation): String {
        val o = eval.order
        val fareKey = o.baseFare?.toInt()?.toString() ?: "x"
        val bonusKey = o.bonus?.toInt()?.toString() ?: "0"
        val pickupKey = o.pickupDistance?.let { String.format(Locale.US, "%.1f", it) } ?: "x"
        val deliveryKey = o.deliveryDistance?.let { String.format(Locale.US, "%.1f", it) } ?: "x"
        val addrKey = (o.pickupAddress ?: "").lowercase().take(16)
        return "${o.orderType.name}_${fareKey}_${bonusKey}_${pickupKey}_${deliveryKey}_$addrKey"
    }

    private fun computeDailySummary(dayKey: String, items: List<OrderEntity>): DailySummary {
        val fares = items.mapNotNull { it.totalExpectedFare }
        val rates = items.mapNotNull { it.farePerKm }
        val netEstimates = items.mapNotNull { it.netEstimatedEarnings }
        val acceptedOrCompleted = items.count {
            it.acceptanceStatus == AcceptanceStatus.ACCEPTED_BY_CAPTAIN.name ||
                it.acceptanceStatus == AcceptanceStatus.COMPLETED_BY_CAPTAIN.name
        }
        val completed = items.filter { it.acceptanceStatus == AcceptanceStatus.COMPLETED_BY_CAPTAIN.name }
        val completedEarnings = completed.sumOf {
            it.actualCompletedEarnings ?: it.netEstimatedEarnings ?: it.totalExpectedFare ?: 0.0
        }

        return DailySummary(
            dayKey = dayKey,
            totalDetectedOrders = items.size,
            acceptedOrCompletedCount = acceptedOrCompleted,
            completedCount = completed.size,
            highestFare = fares.maxOrNull() ?: 0.0,
            averageFarePerKm = if (rates.isNotEmpty()) rates.average() else 0.0,
            estimatedPotentialEarnings = netEstimates.sum(),
            recordedCompletedEarnings = completedEarnings
        )
    }

    fun currentDayKey(): String = formatDayKey(System.currentTimeMillis())

    private fun formatDayKey(epochMs: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(epochMs))
    }
}
