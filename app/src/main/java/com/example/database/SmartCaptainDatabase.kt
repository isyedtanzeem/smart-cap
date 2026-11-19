package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OrderEntity::class], version = 1, exportSchema = false)
abstract class SmartCaptainDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var instance: SmartCaptainDatabase? = null

        fun getInstance(context: Context): SmartCaptainDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmartCaptainDatabase::class.java,
                    "smart_captain.db"
                ).build().also { instance = it }
            }
        }
    }
}
