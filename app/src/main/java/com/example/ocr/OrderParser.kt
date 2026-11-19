package com.example.ocr

import com.example.domain.model.FoodReadyStatus
import com.example.domain.model.Order
import com.example.domain.model.OrderFieldUncertainty
import com.example.domain.model.OrderType
import com.example.domain.model.SelectorStubOrder
import kotlin.math.abs

data class ParsedRapidoScreen(
    val detectedOrderCount: Int?,
    val orders: List<Order>,
    val selectorStubs: List<SelectorStubOrder>,
    val isLikelyRapidoScreen: Boolean,
    val rawTextSignature: String
)

class OrderParser {

    private val orderCountRegex = Regex("""(\d{1,2})\s*Orders?\b""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""(\d+(?:\.\d+)?)\s*(km|kms|m)\b""", RegexOption.IGNORE_CASE)
    private val farePlusBonusRegex = Regex(
        """([₹RrFf?]|Rs\.?)?\s*(\d{2,4}(?:\.\d+)?)\s*\+\s*([₹RrFf?]|Rs\.?)?\s*(\d{1,4}(?:\.\d+)?)"""
    )
    private val singleFareRegex = Regex("""([₹Rr]|Rs\.?)\s*(\d{2,4}(?:\.\d+)?)""")
    private val plainNumberFareRegex = Regex("""^(\d{2,4})\s*(?:\(Rapido\))?$""", RegexOption.IGNORE_CASE)
    private val bonusLineRegex = Regex("""^\+\s*([₹RrFf?]|Rs\.?)?\s*(\d{1,4}(?:\.\d+)?)$""")

    fun parse(document: OcrDocument, detectedAtEpochMs: Long = System.currentTimeMillis()): ParsedRapidoScreen {
        if (document.lines.isEmpty() && document.rawText.isBlank()) {
            return ParsedRapidoScreen(
                detectedOrderCount = null,
                orders = emptyList(),
                selectorStubs = emptyList(),
                isLikelyRapidoScreen = false,
                rawTextSignature = ""
            )
        }

        val lines = if (document.lines.isNotEmpty()) {
            document.lines
        } else {
            document.rawText.lines()
                .mapIndexedNotNull { idx, raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) null
                    else OcrLine(
                        text = trimmed,
                        bounds = OcrBounds(300, idx * 40, 900, (idx + 1) * 40)
                    )
                }
        }

        val width = document.imageWidth.coerceAtLeast(1080)
        val railBoundaryX = width * 0.22f

        val topAndAllText = lines.joinToString("\n") { it.text }
        val orderCount = orderCountRegex.find(topAndAllText)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val hasRapidoMarkers = topAndAllText.contains("Rapido", ignoreCase = true) ||
            topAndAllText.contains("Orders", ignoreCase = true) ||
            topAndAllText.contains("Accept", ignoreCase = true) ||
            topAndAllText.contains("Food Ready", ignoreCase = true) ||
            topAndAllText.contains("Ownly", ignoreCase = true)

        val selectorLines = lines.filter { it.bounds.centerX < railBoundaryX && it.bounds.top > document.imageHeight * 0.10f }
        val mainLines = lines.filter { it.bounds.centerX >= railBoundaryX }
            .sortedBy { it.bounds.top }

        val selectorStubsAll = parseSelectorRail(selectorLines)
        val cardChunks = segmentMainLinesIntoCards(mainLines)

        val parsedOrders = cardChunks.mapIndexedNotNull { index, chunk ->
            parseCardChunk(
                orderNumber = index + 1,
                lines = chunk,
                detectedAtEpochMs = detectedAtEpochMs
            )
        }

        // Only surface selector stubs for orders that are NOT already represented by full cards on screen
        val visiblePairs = parsedOrders.mapNotNull { order ->
            order.baseFare?.let { base -> Pair(base, order.bonus ?: 0.0) }
        }.toMutableList()

        val unmatchedStubs = selectorStubsAll.filter { stub ->
            val matchIndex = visiblePairs.indexOfFirst { (b, bonus) ->
                abs(b - stub.baseFare) < 0.5 && abs(bonus - (stub.bonus ?: 0.0)) < 0.5
            }
            if (matchIndex >= 0) {
                visiblePairs.removeAt(matchIndex)
                false
            } else {
                true
            }
        }

        val signature = parsedOrders.joinToString("|") {
            "${it.orderType.name}:${it.baseFare}:${it.bonus}:${it.pickupDistance}:${it.deliveryDistance}:${it.pickupAddress?.take(18)}"
        }

        return ParsedRapidoScreen(
            detectedOrderCount = orderCount,
            orders = parsedOrders,
            selectorStubs = unmatchedStubs,
            isLikelyRapidoScreen = hasRapidoMarkers || parsedOrders.isNotEmpty(),
            rawTextSignature = signature
        )
    }

    private fun parseSelectorRail(railLines: List<OcrLine>): List<SelectorStubOrder> {
        if (railLines.isEmpty()) return emptyList()
        val sorted = railLines.sortedBy { it.bounds.top }
        val groups = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val lastGroup = groups.lastOrNull()
            if (lastGroup == null || (line.bounds.top - lastGroup.last().bounds.bottom) > 95) {
                groups += mutableListOf(line)
            } else {
                lastGroup += line
            }
        }

        val stubs = mutableListOf<SelectorStubOrder>()
        groups.forEachIndexed { idx, group ->
            val joined = group.joinToString(" ") { it.text }
            val plusMatch = farePlusBonusRegex.find(joined)
            if (plusMatch != null) {
                val base = plusMatch.groupValues[2].toDoubleOrNull()
                val bonus = plusMatch.groupValues[4].toDoubleOrNull()
                if (base != null && base in 10.0..5000.0) {
                    stubs += SelectorStubOrder(idx + 1, base, bonus, joined)
                }
            } else {
                var baseFare: Double? = null
                var bonusFare: Double? = null
                for (l in group) {
                    val bMatch = bonusLineRegex.find(l.text.trim())
                    if (bMatch != null) {
                        bonusFare = bMatch.groupValues[2].toDoubleOrNull()
                        continue
                    }
                    val fMatch = singleFareRegex.find(l.text) ?: plainNumberFareRegex.find(l.text.trim())
                    if (fMatch != null && baseFare == null) {
                        val value = fMatch.groupValues.last().toDoubleOrNull()
                        if (value != null && value in 10.0..5000.0) {
                            baseFare = value
                        }
                    }
                }
                if (baseFare != null) {
                    stubs += SelectorStubOrder(idx + 1, baseFare, bonusFare, joined)
                }
            }
        }
        return stubs
    }

    private fun segmentMainLinesIntoCards(mainLines: List<OcrLine>): List<List<OcrLine>> {
        val filtered = mainLines.filterNot { line ->
            val t = line.text.trim()
            t.equals("Services", ignoreCase = true) ||
                t.equals("Go To", ignoreCase = true) ||
                t.equals("Stay In", ignoreCase = true) ||
                t.equals("Home", ignoreCase = true) ||
                t.equals("Orders", ignoreCase = true) ||
                t.equals("ON", ignoreCase = true) ||
                orderCountRegex.matches(t)
        }

        val cards = mutableListOf<MutableList<OcrLine>>()
        var current = mutableListOf<OcrLine>()

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                cards += current
                current = mutableListOf()
            }
        }

        for (line in filtered) {
            val text = line.text.trim()
            val isOrderTypeHeader = detectOrderTypeBadge(text) != null && text.length <= 20
            val isFareLine = isCardFareLine(text)

            val currentHasFare = current.any { isCardFareLine(it.text) }
            val currentHasDistance = current.any { distanceRegex.containsMatchIn(it.text) }

            if (isOrderTypeHeader && (currentHasFare || currentHasDistance)) {
                flushCurrent()
            } else if (isFareLine && currentHasFare && currentHasDistance) {
                flushCurrent()
            }

            current += line

            if (text.equals("Accept", ignoreCase = true)) {
                flushCurrent()
            }
        }
        flushCurrent()

        return cards.filter { chunk ->
            val combined = chunk.joinToString(" ") { it.text }
            isCardFareLine(combined) ||
                farePlusBonusRegex.containsMatchIn(combined) ||
                (singleFareRegex.containsMatchIn(combined) && distanceRegex.containsMatchIn(combined))
        }
    }

    private fun parseCardChunk(
        orderNumber: Int,
        lines: List<OcrLine>,
        detectedAtEpochMs: Long
    ): Order? {
        val cleanedLines = lines.filterNot {
            val t = it.text.trim()
            t.equals("Accept", ignoreCase = true) || t == "-" || t == "•"
        }
        if (cleanedLines.isEmpty()) return null

        val joined = cleanedLines.joinToString("\n") { it.text }
        val uncertainFields = mutableSetOf<OrderFieldUncertainty>()

        // 1. Order Type
        var orderType = OrderType.UNKNOWN
        for (line in cleanedLines) {
            val detected = detectOrderTypeBadge(line.text)
            if (detected != null) {
                orderType = detected
                break
            }
        }

        // 2. Food Ready Status
        val foodReadyStatus = if (joined.contains("Food Ready", ignoreCase = true)) {
            FoodReadyStatus.READY
        } else {
            FoodReadyStatus.NOT_INDICATED
        }

        // 3. Base Fare & Bonus
        var baseFare: Double? = null
        var bonus: Double? = null
        var fareUncertain = false

        for (line in cleanedLines) {
            val t = line.text.trim()
            if (t.contains("km", ignoreCase = true) || orderCountRegex.matches(t)) continue

            val plusMatch = farePlusBonusRegex.find(t)
            if (plusMatch != null) {
                val prefix1 = plusMatch.groupValues[1]
                val b = plusMatch.groupValues[2].toDoubleOrNull()
                val bn = plusMatch.groupValues[4].toDoubleOrNull()
                if (b != null && b in 10.0..5000.0) {
                    baseFare = b
                    bonus = bn
                    if (prefix1.isBlank() || (!prefix1.contains("₹") && !prefix1.contains("Rs", ignoreCase = true))) {
                        fareUncertain = true
                    }
                    break
                }
            }

            if (t.contains("(Rapido)", ignoreCase = true) || t.contains("₹") || t.contains("Rs", ignoreCase = true)) {
                val single = singleFareRegex.find(t)
                if (single != null) {
                    val value = single.groupValues[2].toDoubleOrNull()
                    if (value != null && value in 10.0..5000.0) {
                        baseFare = value
                        if (!single.groupValues[1].contains("₹") && !single.groupValues[1].contains("Rs", ignoreCase = true)) {
                            fareUncertain = true
                        }
                        break
                    }
                } else {
                    val fallbackNum = Regex("""(\d{2,4})""").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
                    if (fallbackNum != null && fallbackNum in 10.0..5000.0) {
                        baseFare = fallbackNum
                        fareUncertain = true
                        break
                    }
                }
            }
        }

        if (baseFare == null) {
            uncertainFields += OrderFieldUncertainty.BASE_FARE_UNCERTAIN
            return null
        }
        if (fareUncertain) {
            uncertainFields += OrderFieldUncertainty.BASE_FARE_UNCERTAIN
        }

        // 4. Distances & Addresses
        val distanceIndices = mutableListOf<Pair<Int, Double>>()
        cleanedLines.forEachIndexed { idx, line ->
            val t = line.text.trim()
            val match = distanceRegex.find(t)
            if (match != null && t.length <= 18) {
                val rawVal = match.groupValues[1].toDoubleOrNull()
                val unit = match.groupValues[2].lowercase()
                if (rawVal != null) {
                    val km = if (unit == "m") rawVal / 1000.0 else rawVal
                    if (km in 0.05..150.0) {
                        distanceIndices += idx to km
                    }
                }
            }
        }

        val pickupDistance = distanceIndices.getOrNull(0)?.second
        val deliveryDistance = distanceIndices.getOrNull(1)?.second

        if (pickupDistance == null) uncertainFields += OrderFieldUncertainty.PICKUP_DISTANCE_MISSING
        if (deliveryDistance == null) {
            uncertainFields += OrderFieldUncertainty.DELIVERY_DISTANCE_MISSING
            uncertainFields += OrderFieldUncertainty.PARTIAL_CARD_OCR
        }

        val pickupAddress = if (distanceIndices.isNotEmpty()) {
            val start = distanceIndices[0].first + 1
            val end = distanceIndices.getOrNull(1)?.first ?: cleanedLines.size
            extractAddress(cleanedLines.subList(start.coerceAtMost(cleanedLines.size), end.coerceAtMost(cleanedLines.size)))
        } else null

        val deliveryAddress = if (distanceIndices.size >= 2) {
            val start = distanceIndices[1].first + 1
            extractAddress(cleanedLines.subList(start.coerceAtMost(cleanedLines.size), cleanedLines.size))
        } else null

        if (pickupAddress.isNullOrBlank()) uncertainFields += OrderFieldUncertainty.PICKUP_ADDRESS_INCOMPLETE
        if (deliveryAddress.isNullOrBlank() || deliveryAddress.endsWith("...")) {
            uncertainFields += OrderFieldUncertainty.DELIVERY_ADDRESS_INCOMPLETE
        }

        // 5. Confidence Score (never invent missing fields)
        var score = 1.0f
        if (fareUncertain) score -= 0.18f
        if (pickupDistance == null) score -= 0.25f
        if (deliveryDistance == null) score -= 0.28f
        if (pickupAddress.isNullOrBlank()) score -= 0.12f
        if (deliveryAddress.isNullOrBlank()) score -= 0.12f
        else if (deliveryAddress.endsWith("...")) score -= 0.05f
        val clampedConfidence = score.coerceIn(0.2f, 0.99f)

        val stableKey = "${orderType.name}_${baseFare.toInt()}_${bonus?.toInt() ?: 0}_${pickupAddress?.take(14) ?: orderNumber}"
        val orderId = "ord_${stableKey.hashCode().toUInt()}"

        return Order(
            orderId = orderId,
            orderNumber = orderNumber,
            orderType = orderType,
            baseFare = baseFare,
            bonus = bonus,
            totalExpectedFare = baseFare + (bonus ?: 0.0),
            pickupDistance = pickupDistance,
            deliveryDistance = deliveryDistance,
            pickupAddress = pickupAddress,
            deliveryAddress = deliveryAddress,
            foodReadyStatus = foodReadyStatus,
            confidenceScore = clampedConfidence,
            detectedAt = detectedAtEpochMs,
            platformTag = "Rapido",
            uncertainFields = uncertainFields,
            rawOcrSnippet = joined.take(280)
        )
    }

    private fun extractAddress(lines: List<OcrLine>): String? {
        val addressLines = lines.map { it.text.trim() }
            .filterNot { t ->
                t.equals("Food Ready", ignoreCase = true) ||
                    t.equals("Accept", ignoreCase = true) ||
                    t.contains("(Rapido)", ignoreCase = true) ||
                    detectOrderTypeBadge(t) != null
            }
        val joined = addressLines.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
        return joined.ifBlank { null }
    }

    private fun detectOrderTypeBadge(text: String): OrderType? {
        val normalized = text.trim().lowercase()
        return when {
            normalized == "delivery" || normalized.startsWith("delivery ") -> OrderType.DELIVERY
            normalized == "ownly" || normalized.contains("ownly") -> OrderType.OWNLY
            normalized == "food" || normalized == "food delivery" -> OrderType.FOOD
            normalized == "parcel" -> OrderType.PARCEL
            normalized == "moto" -> OrderType.MOTO
            normalized == "auto" -> OrderType.AUTO
            normalized == "cab" -> OrderType.CAB
            else -> null
        }
    }

    private fun isCardFareLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.contains("km", ignoreCase = true)) return false
        return farePlusBonusRegex.containsMatchIn(trimmed) ||
            (trimmed.contains("(Rapido)", ignoreCase = true) && Regex("""\d{2,4}""").containsMatchIn(trimmed)) ||
            singleFareRegex.containsMatchIn(trimmed)
    }
}
