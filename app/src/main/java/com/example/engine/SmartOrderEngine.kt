package com.example.engine

import com.example.domain.model.CaptainSettings
import com.example.domain.model.FoodReadyStatus
import com.example.domain.model.Order
import com.example.domain.model.OrderEvaluation
import com.example.domain.model.RankingMode
import com.example.domain.model.RecommendationResult
import com.example.domain.model.SelectorStubOrder
import java.util.Locale
import kotlin.math.max

class SmartOrderEngine {

    fun evaluateOrder(order: Order, settings: CaptainSettings): OrderEvaluation {
        val base = order.baseFare
        val eligibleBonus = if (settings.includeBonusInTotal) (order.bonus ?: 0.0) else 0.0
        val totalExpectedFare = base?.let { it + eligibleBonus }

        val totalDistanceKm = order.totalDistanceKm
        val farePerKm = if (totalExpectedFare != null && totalDistanceKm != null && totalDistanceKm > 0.0) {
            totalExpectedFare / totalDistanceKm
        } else {
            null
        }

        val estimatedFuelCost = totalDistanceKm?.let { it * settings.fuelCostPerKm }
        val otherExpenses = settings.otherExpensePerOrder

        val netEstimatedEarnings = if (totalExpectedFare != null && estimatedFuelCost != null) {
            totalExpectedFare - estimatedFuelCost - otherExpenses
        } else {
            null
        }

        val effectiveWaitMinutes = when (order.foodReadyStatus) {
            FoodReadyStatus.READY -> max(1.5, settings.pickupWaitMinutes * 0.4)
            FoodReadyStatus.NOT_INDICATED -> settings.pickupWaitMinutes
        }

        val safeSpeedKmh = max(8.0, settings.averageSpeedKmh)
        val estimatedTripDurationMinutes = totalDistanceKm?.let { dist ->
            ((dist / safeSpeedKmh) * 60.0) + effectiveWaitMinutes
        }

        val estimatedEarningsPerHour = if (
            netEstimatedEarnings != null &&
            estimatedTripDurationMinutes != null &&
            estimatedTripDurationMinutes > 0.0
        ) {
            (netEstimatedEarnings / estimatedTripDurationMinutes) * 60.0
        } else {
            null
        }

        val warnings = buildList {
            if (totalExpectedFare != null && totalExpectedFare < settings.minExpectedFare) {
                add("Fare ₹${formatNum(totalExpectedFare)} below min ₹${formatNum(settings.minExpectedFare)}")
            }
            if (farePerKm != null && farePerKm < settings.minFarePerKm) {
                add("₹${formatNum(farePerKm)}/km below min ₹${formatNum(settings.minFarePerKm)}/km")
            }
            if (order.pickupDistance != null && order.pickupDistance > settings.maxPickupDistanceKm) {
                add("Pickup ${formatNum(order.pickupDistance)} km exceeds max ${formatNum(settings.maxPickupDistanceKm)} km")
            }
            if (netEstimatedEarnings != null && netEstimatedEarnings < settings.minExpectedNetEarnings) {
                add("Net ₹${formatNum(netEstimatedEarnings)} below min ₹${formatNum(settings.minExpectedNetEarnings)}")
            }
            if (order.needsManualVerification) {
                add("Some OCR fields need quick visual verification")
            }
        }

        val meetsMinimums = (totalExpectedFare == null || totalExpectedFare >= settings.minExpectedFare) &&
            (farePerKm == null || farePerKm >= settings.minFarePerKm) &&
            (order.pickupDistance == null || order.pickupDistance <= settings.maxPickupDistanceKm) &&
            (netEstimatedEarnings == null || netEstimatedEarnings >= settings.minExpectedNetEarnings)

        return OrderEvaluation(
            order = order,
            totalExpectedFare = totalExpectedFare,
            totalDistanceKm = totalDistanceKm,
            farePerKm = farePerKm,
            estimatedFuelCost = estimatedFuelCost,
            otherExpenses = otherExpenses,
            netEstimatedEarnings = netEstimatedEarnings,
            estimatedTripDurationMinutes = estimatedTripDurationMinutes,
            estimatedEarningsPerHour = estimatedEarningsPerHour,
            meetsMinimumThresholds = meetsMinimums,
            thresholdWarnings = warnings
        )
    }

    fun rankOrders(
        orders: List<Order>,
        selectorStubs: List<SelectorStubOrder> = emptyList(),
        detectedOrderCountLabel: Int? = null,
        settings: CaptainSettings,
        rankingMode: RankingMode = settings.preferredRankingMode,
        scannedAtEpochMs: Long = System.currentTimeMillis()
    ): RecommendationResult {
        if (orders.isEmpty()) {
            val stubHint = if (selectorStubs.isNotEmpty()) {
                val maxStub = selectorStubs.maxByOrNull { it.totalWithBonus }
                "Detected ${selectorStubs.size} selector rail fares (up to ₹${formatNum(maxStub?.totalWithBonus ?: 0.0)}). Scroll Rapido to scan card distances."
            } else {
                "Waiting for Rapido Captain order cards on screen."
            }
            return RecommendationResult(
                detectedOrderCountLabel = detectedOrderCountLabel,
                rankedEvaluations = emptyList(),
                recommendedOrder = null,
                selectorStubs = selectorStubs,
                rankingMode = rankingMode,
                comparisonSummary = stubHint,
                scannedAtEpochMs = scannedAtEpochMs
            )
        }

        val evaluated = orders.map { evaluateOrder(it, settings) }

        val sorted = evaluated.sortedWith(
            compareByDescending<OrderEvaluation> { it.hasCompleteCoreMetrics() }
                .thenByDescending { it.meetsMinimumThresholds }
                .thenByDescending { primaryMetricFor(it, rankingMode) ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.netEstimatedEarnings ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.order.confidenceScore.toDouble() }
        )

        val winner = sorted.firstOrNull()
        val runnerUp = sorted.getOrNull(1)

        val annotated = sorted.mapIndexed { idx, item ->
            val rank = idx + 1
            val isTop = idx == 0 && item.totalExpectedFare != null
            val reason = buildReason(item, runnerUp, rank, rankingMode)
            item.copy(
                isRecommended = isTop,
                recommendationRank = rank,
                recommendationReason = reason
            )
        }

        val recommended = annotated.firstOrNull { it.isRecommended }
        val summary = buildComparisonSummary(recommended, runnerUp, selectorStubs, rankingMode)

        return RecommendationResult(
            detectedOrderCountLabel = detectedOrderCountLabel,
            rankedEvaluations = annotated,
            recommendedOrder = recommended,
            selectorStubs = selectorStubs,
            rankingMode = rankingMode,
            comparisonSummary = summary,
            scannedAtEpochMs = scannedAtEpochMs
        )
    }

    private fun OrderEvaluation.hasCompleteCoreMetrics(): Boolean {
        return totalExpectedFare != null && totalDistanceKm != null && totalDistanceKm > 0.0
    }

    private fun primaryMetricFor(eval: OrderEvaluation, mode: RankingMode): Double? {
        return when (mode) {
            RankingMode.HIGHEST_TOTAL_FARE -> eval.totalExpectedFare
            RankingMode.HIGHEST_FARE_PER_KM -> eval.farePerKm
            RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR -> eval.estimatedEarningsPerHour
        }
    }

    private fun buildReason(
        eval: OrderEvaluation,
        runnerUp: OrderEvaluation?,
        rank: Int,
        mode: RankingMode
    ): String {
        if (rank > 1) {
            return when (mode) {
                RankingMode.HIGHEST_TOTAL_FARE ->
                    "Rank #$rank by gross payout (${eval.totalExpectedFare?.let { "₹${formatNum(it)}" } ?: "incomplete fare"})"
                RankingMode.HIGHEST_FARE_PER_KM ->
                    "Rank #$rank at ${eval.farePerKm?.let { "₹${formatNum(it)}/km" } ?: "missing distance"}"
                RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR ->
                    "Rank #$rank at ${eval.estimatedEarningsPerHour?.let { "₹${formatNum(it)}/hr net" } ?: "missing distance"}"
            }
        }

        val parts = mutableListOf<String>()
        when (mode) {
            RankingMode.HIGHEST_TOTAL_FARE -> {
                val fare = eval.totalExpectedFare?.let { "₹${formatNum(it)}" } ?: "highest visible fare"
                parts += "Top payout at $fare"
                if (runnerUp?.totalExpectedFare != null && eval.totalExpectedFare != null) {
                    val diff = eval.totalExpectedFare - runnerUp.totalExpectedFare
                    if (diff > 0.5) parts += "+₹${formatNum(diff)} over #2"
                }
            }
            RankingMode.HIGHEST_FARE_PER_KM -> {
                val rate = eval.farePerKm?.let { "₹${formatNum(it)}/km" } ?: "best rate"
                parts += "Best efficiency at $rate"
                if (runnerUp?.farePerKm != null && eval.farePerKm != null) {
                    val diff = eval.farePerKm - runnerUp.farePerKm
                    if (diff > 0.1) parts += "+₹${formatNum(diff)}/km over #2"
                }
            }
            RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR -> {
                val hourly = eval.estimatedEarningsPerHour?.let { "₹${formatNum(it)}/hr net" } ?: "best hourly profit"
                parts += "Highest hourly net at $hourly"
                if (runnerUp?.estimatedEarningsPerHour != null && eval.estimatedEarningsPerHour != null) {
                    val diff = eval.estimatedEarningsPerHour - runnerUp.estimatedEarningsPerHour
                    if (diff > 1.0) parts += "+₹${formatNum(diff)}/hr vs #2"
                }
            }
        }

        if (eval.order.foodReadyStatus == FoodReadyStatus.READY) {
            parts += "Food Ready reduces pickup wait"
        }
        if ((eval.order.bonus ?: 0.0) > 0.0) {
            parts += "includes ₹${formatNum(eval.order.bonus ?: 0.0)} bonus"
        }
        if (!eval.meetsMinimumThresholds && eval.thresholdWarnings.isNotEmpty()) {
            parts += "note: ${eval.thresholdWarnings.first()}"
        }
        return parts.joinToString(" • ")
    }

    private fun buildComparisonSummary(
        recommended: OrderEvaluation?,
        runnerUp: OrderEvaluation?,
        stubs: List<SelectorStubOrder>,
        mode: RankingMode
    ): String {
        if (recommended == null) {
            return "Unable to compute a full recommendation from incomplete OCR values."
        }
        val baseSummary = recommended.recommendationReason
        val higherStub = stubs.maxByOrNull { it.totalWithBonus }
        val recFare = recommended.totalExpectedFare ?: 0.0
        return if (higherStub != null && higherStub.totalWithBonus > recFare) {
            "$baseSummary. Tip: Rapido left rail shows a ₹${formatNum(higherStub.totalWithBonus)} order — scroll to compare its distance under ${mode.shortLabel}."
        } else {
            baseSummary
        }
    }

    private fun formatNum(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }
}
