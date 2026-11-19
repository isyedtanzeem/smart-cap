package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM detected_orders ORDER BY detectedAtEpochMs DESC")
    fun observeAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM detected_orders WHERE dayKey = :dayKey ORDER BY detectedAtEpochMs DESC")
    fun observeOrdersForDay(dayKey: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM detected_orders WHERE dedupeKey = :dedupeKey AND detectedAtEpochMs >= :sinceEpochMs LIMIT 1")
    suspend fun findRecentByDedupeKey(dedupeKey: String, sinceEpochMs: Long): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Query("UPDATE detected_orders SET acceptanceStatus = :status, actualCompletedEarnings = :completedEarnings WHERE id = :id")
    suspend fun updateAcceptanceStatus(id: Long, status: String, completedEarnings: Double?)

    @Query("DELETE FROM detected_orders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM detected_orders")
    suspend fun clearAll()
}
