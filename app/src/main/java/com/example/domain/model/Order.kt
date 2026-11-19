package com.example.domain.model

enum class OrderType(val displayLabel: String) {
    DELIVERY("Delivery"),
    OWNLY("Ownly"),
    FOOD("Food"),
    PARCEL("Parcel"),
    MOTO("Moto"),
    AUTO("Auto"),
    CAB("Cab"),
    UNKNOWN("Order")
}

enum class FoodReadyStatus(val label: String) {
    READY("Food Ready"),
    NOT_INDICATED("Not Indicated")
}

enum class RankingMode(val title: String, val shortLabel: String, val description: String) {
    HIGHEST_TOTAL_FARE(
        title = "Highest Total Fare",
        shortLabel = "Top Fare",
        description = "Prioritizes maximum gross payout (base fare + eligible bonus)."
    ),
    HIGHEST_FARE_PER_KM(
        title = "Highest Fare Per KM",
        shortLabel = "Best ₹/km",
        description = "Prioritizes payout efficiency relative to pickup + delivery distance."
    ),
    HIGHEST_NET_EARNINGS_PER_HOUR(
        title = "Highest Net Earnings / Hr",
        shortLabel = "Best ₹/hr",
        description = "Prioritizes net hourly profit after fuel, expenses, and estimated trip time."
    )
}

enum class OrderFieldUncertainty(val label: String) {
    BASE_FARE_UNCERTAIN("Base fare OCR uncertain"),
    BONUS_UNCERTAIN("Bonus OCR uncertain"),
    PICKUP_DISTANCE_MISSING("Pickup distance missing"),
    DELIVERY_DISTANCE_MISSING("Delivery distance missing"),
    PICKUP_ADDRESS_INCOMPLETE("Pickup address incomplete"),
    DELIVERY_ADDRESS_INCOMPLETE("Delivery address truncated"),
    PARTIAL_CARD_OCR("Card partially visible on screen")
}

enum class AcceptanceStatus(val label: String) {
    DETECTED("Detected Offer"),
    ACCEPTED_BY_CAPTAIN("Accepted by Captain"),
    COMPLETED_BY_CAPTAIN("Completed Trip"),
    SKIPPED("Skipped")
}

data class Order(
    val orderId: String,
    val orderNumber: Int,
    val orderType: OrderType,
    val baseFare: Double?,
    val bonus: Double?,
    val totalExpectedFare: Double?,
    val pickupDistance: Double?,
    val deliveryDistance: Double?,
    val pickupAddress: String?,
    val deliveryAddress: String?,
    val foodReadyStatus: FoodReadyStatus,
    val confidenceScore: Float,
    val detectedAt: Long,
    val platformTag: String = "Rapido",
    val uncertainFields: Set<OrderFieldUncertainty> = emptySet(),
    val rawOcrSnippet: String = ""
) {
    val totalDistanceKm: Double?
        get() = if (pickupDistance != null && deliveryDistance != null) {
            pickupDistance + deliveryDistance
        } else {
            null
        }

    val needsManualVerification: Boolean
        get() = uncertainFields.isNotEmpty() || confidenceScore < 0.75f ||
            baseFare == null || pickupDistance == null || deliveryDistance == null
}

data class SelectorStubOrder(
    val index: Int,
    val baseFare: Double,
    val bonus: Double?,
    val rawText: String
) {
    val totalWithBonus: Double get() = baseFare + (bonus ?: 0.0)
}

data class CaptainSettings(
    val minExpectedFare: Double = 35.0,
    val minFarePerKm: Double = 10.0,
    val maxPickupDistanceKm: Double = 3.5,
    val minExpectedNetEarnings: Double = 25.0,
    val fuelCostPerKm: Double = 2.2,
    val otherExpensePerOrder: Double = 2.0,
    val averageSpeedKmh: Double = 22.0,
    val pickupWaitMinutes: Double = 5.0,
    val includeBonusInTotal: Boolean = true,
    val preferredRankingMode: RankingMode = RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR,
    val ocrScanningIntervalMs: Long = 2000L,
    val floatingOverlayEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notificationListenerEnabled: Boolean = false
)

data class OrderEvaluation(
    val order: Order,
    val totalExpectedFare: Double?,
    val totalDistanceKm: Double?,
    val farePerKm: Double?,
    val estimatedFuelCost: Double?,
    val otherExpenses: Double,
    val netEstimatedEarnings: Double?,
    val estimatedTripDurationMinutes: Double?,
    val estimatedEarningsPerHour: Double?,
    val meetsMinimumThresholds: Boolean,
    val thresholdWarnings: List<String>,
    val isRecommended: Boolean = false,
    val recommendationRank: Int = 0,
    val recommendationReason: String = ""
)

data class RecommendationResult(
    val detectedOrderCountLabel: Int?,
    val rankedEvaluations: List<OrderEvaluation>,
    val recommendedOrder: OrderEvaluation?,
    val selectorStubs: List<SelectorStubOrder>,
    val rankingMode: RankingMode,
    val comparisonSummary: String,
    val scannedAtEpochMs: Long
)

data class DailySummary(
    val dayKey: String,
    val totalDetectedOrders: Int,
    val acceptedOrCompletedCount: Int,
    val completedCount: Int,
    val highestFare: Double,
    val averageFarePerKm: Double,
    val estimatedPotentialEarnings: Double,
    val recordedCompletedEarnings: Double
)
