package com.example

import com.example.domain.model.CaptainSettings
import com.example.domain.model.FoodReadyStatus
import com.example.domain.model.OrderFieldUncertainty
import com.example.domain.model.OrderType
import com.example.domain.model.RankingMode
import com.example.engine.SmartOrderEngine
import com.example.ocr.OcrBounds
import com.example.ocr.OcrDocument
import com.example.ocr.OcrLine
import com.example.ocr.OrderParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderParserAndEngineTest {

    private val parser = OrderParser()
    private val engine = SmartOrderEngine()

    @Test
    fun `parses rapido screenshot layout with 4 orders label, left selector stubs, and 2 main cards`() {
        val lines = listOf(
            OcrLine("4 Orders", OcrBounds(60, 120, 290, 165)),
            // Left selector rail (x < 238 for width 1080)
            OcrLine("₹41", OcrBounds(45, 290, 130, 325)),
            OcrLine("+₹3", OcrBounds(45, 330, 130, 360)),
            OcrLine("₹46", OcrBounds(45, 580, 130, 615)),
            OcrLine("₹67", OcrBounds(45, 810, 130, 845)),
            OcrLine("+₹6", OcrBounds(45, 850, 130, 880)),
            OcrLine("₹76", OcrBounds(45, 1050, 130, 1085)),
            OcrLine("+₹18", OcrBounds(45, 1090, 130, 1120)),
            // Main Card 1
            OcrLine("Delivery", OcrBounds(290, 340, 440, 375)),
            OcrLine("₹41 + ₹3 (Rapido)", OcrBounds(250, 410, 640, 465)),
            OcrLine("1.9 km", OcrBounds(300, 520, 450, 560)),
            OcrLine("National College - California Burrito, 97, Surya Gandhi Market Main Road", OcrBounds(300, 570, 960, 660)),
            OcrLine("2 km", OcrBounds(300, 800, 430, 840)),
            OcrLine("Basavanagudi - Sri veerabhadra residency, 5 Th floor, Ashok Nagar", OcrBounds(300, 850, 960, 960)),
            OcrLine("Accept", OcrBounds(560, 1150, 740, 1200)),
            // Main Card 2
            OcrLine("Ownly", OcrBounds(290, 1380, 430, 1415)),
            OcrLine("Food Ready", OcrBounds(820, 1420, 990, 1470)),
            OcrLine("₹46 (Rapido)", OcrBounds(250, 1470, 540, 1525)),
            OcrLine("2.1 km", OcrBounds(300, 1590, 450, 1630)),
            OcrLine("Jayanagar - 52, 1st Floor, 33rd Cross Rd, 4th Block", OcrBounds(300, 1640, 950, 1740)),
            OcrLine("3.9 km", OcrBounds(300, 1860, 450, 1900)),
            OcrLine("Kumaraswamy Layout - #38/33/2nd floor, Banashankari nilaya...", OcrBounds(300, 1910, 950, 2000))
        )

        val doc = OcrDocument(
            rawText = lines.joinToString("\n") { it.text },
            lines = lines,
            imageWidth = 1080,
            imageHeight = 2400
        )

        val parsed = parser.parse(doc)
        assertEquals(4, parsed.detectedOrderCount)
        assertEquals(2, parsed.orders.size)
        // Left rail has 2 additional unseen orders (67+6 and 76+18)
        assertEquals(2, parsed.selectorStubs.size)

        val first = parsed.orders[0]
        assertEquals(OrderType.DELIVERY, first.orderType)
        assertEquals(41.0, first.baseFare!!, 0.01)
        assertEquals(3.0, first.bonus!!, 0.01)
        assertEquals(1.9, first.pickupDistance!!, 0.01)
        assertEquals(2.0, first.deliveryDistance!!, 0.01)
        assertEquals(FoodReadyStatus.NOT_INDICATED, first.foodReadyStatus)

        val second = parsed.orders[1]
        assertEquals(OrderType.OWNLY, second.orderType)
        assertEquals(46.0, second.baseFare!!, 0.01)
        assertNull(second.bonus)
        assertEquals(2.1, second.pickupDistance!!, 0.01)
        assertEquals(3.9, second.deliveryDistance!!, 0.01)
        assertEquals(FoodReadyStatus.READY, second.foodReadyStatus)

        // Compare under Highest Fare Per KM: Order #1 (44 / 3.9 = 11.28/km) beats Order #2 (46 / 6.0 = 7.67/km)
        val settings = CaptainSettings(preferredRankingMode = RankingMode.HIGHEST_FARE_PER_KM)
        val rateResult = engine.rankOrders(parsed.orders, parsed.selectorStubs, parsed.detectedOrderCount, settings)
        assertNotNull(rateResult.recommendedOrder)
        assertEquals(1, rateResult.recommendedOrder!!.order.orderNumber)

        // Under default minFarePerKm = 10.0, Order #2 (46 / 6.0 = 7.67/km) fails threshold so Order #1 still stays recommended
        val thresholdProtectedResult = engine.rankOrders(
            parsed.orders,
            parsed.selectorStubs,
            parsed.detectedOrderCount,
            settings.copy(preferredRankingMode = RankingMode.HIGHEST_TOTAL_FARE)
        )
        assertEquals(1, thresholdProtectedResult.recommendedOrder!!.order.orderNumber)

        // When both orders meet Captain thresholds (minFarePerKm = 5.0), Highest Total Fare picks Order #2 (₹46 > ₹44)
        val fareResult = engine.rankOrders(
            parsed.orders,
            parsed.selectorStubs,
            parsed.detectedOrderCount,
            settings.copy(
                preferredRankingMode = RankingMode.HIGHEST_TOTAL_FARE,
                minFarePerKm = 5.0
            )
        )
        assertEquals(2, fareResult.recommendedOrder!!.order.orderNumber)
    }

    @Test
    fun `partial card never invents missing delivery distance`() {
        val lines = listOf(
            OcrLine("Parcel", OcrBounds(290, 400, 420, 440)),
            OcrLine("₹85 (Rapido)", OcrBounds(250, 460, 560, 510)),
            OcrLine("1.4 km", OcrBounds(300, 540, 450, 580)),
            OcrLine("Indiranagar 100ft Rd", OcrBounds(300, 600, 820, 650))
        )
        val doc = OcrDocument(lines.joinToString("\n") { it.text }, lines, 1080, 2400)
        val parsed = parser.parse(doc)

        assertEquals(1, parsed.orders.size)
        val partial = parsed.orders.first()
        assertEquals(85.0, partial.baseFare!!, 0.01)
        assertEquals(1.4, partial.pickupDistance!!, 0.01)
        assertNull(partial.deliveryDistance)
        assertTrue(partial.uncertainFields.contains(OrderFieldUncertainty.DELIVERY_DISTANCE_MISSING))
        assertTrue(partial.needsManualVerification)
    }
}
