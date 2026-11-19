package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.OrderRepository
import com.example.database.SmartCaptainDatabase
import com.example.domain.model.AcceptanceStatus
import com.example.domain.model.CaptainSettings
import com.example.domain.model.FoodReadyStatus
import com.example.domain.model.Order
import com.example.domain.model.OrderType
import com.example.domain.model.RankingMode
import com.example.engine.SmartOrderEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart Captain", appName)
    }

    @Test
    fun `room repository separates detected offers from captain completed orders`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SmartCaptainDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = OrderRepository(db.orderDao())
            val engine = SmartOrderEngine()
            val settings = CaptainSettings()

            val order = Order(
                orderId = "ord_test_1",
                orderNumber = 1,
                orderType = OrderType.DELIVERY,
                baseFare = 41.0,
                bonus = 3.0,
                totalExpectedFare = 44.0,
                pickupDistance = 1.9,
                deliveryDistance = 2.0,
                pickupAddress = "National College - California Burrito",
                deliveryAddress = "Basavanagudi - Sri veerabhadra residency",
                foodReadyStatus = FoodReadyStatus.NOT_INDICATED,
                confidenceScore = 0.96f,
                detectedAt = System.currentTimeMillis()
            )
            val result = engine.rankOrders(listOf(order), settings = settings)
            val inserted = repo.recordEvaluatedScan(result.rankedEvaluations, RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR)
            assertEquals(1, inserted)

            // Repeated identical scan within dedupe window should not duplicate
            val dupInserted = repo.recordEvaluatedScan(result.rankedEvaluations, RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR)
            assertEquals(0, dupInserted)

            val summaryBefore = repo.observeTodaySummary().first()
            assertEquals(1, summaryBefore.totalDetectedOrders)
            assertEquals(0, summaryBefore.completedCount)
            assertEquals(0.0, summaryBefore.recordedCompletedEarnings, 0.01)

            val savedId = repo.observeAllHistory().first().first().id
            repo.markOrderStatus(savedId, AcceptanceStatus.COMPLETED_BY_CAPTAIN, 35.0)

            val summaryAfter = repo.observeTodaySummary().first()
            assertEquals(1, summaryAfter.completedCount)
            assertEquals(35.0, summaryAfter.recordedCompletedEarnings, 0.01)
            assertTrue(summaryAfter.highestFare >= 44.0)
        } finally {
            db.close()
        }
    }
}
