package com.example

import android.app.Application
import com.example.data.OrderRepository
import com.example.data.SettingsDataStore
import com.example.database.SmartCaptainDatabase
import com.example.engine.RecommendationManager
import com.example.engine.SmartOrderEngine
import com.example.monitoring.RapidoScreenMonitor
import com.example.monitoring.ScreenCaptureManager
import com.example.ocr.OrderParser
import com.example.ocr.OrderTextRecognizer
import com.example.permissions.PermissionManager

class SmartCaptainApp : Application() {

    val database: SmartCaptainDatabase by lazy {
        SmartCaptainDatabase.getInstance(this)
    }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(this)
    }

    val orderRepository: OrderRepository by lazy {
        OrderRepository(database.orderDao())
    }

    val permissionManager: PermissionManager by lazy {
        PermissionManager(this)
    }

    val smartOrderEngine: SmartOrderEngine by lazy {
        SmartOrderEngine()
    }

    val recommendationManager: RecommendationManager by lazy {
        RecommendationManager(
            context = this,
            smartOrderEngine = smartOrderEngine,
            orderRepository = orderRepository,
            settingsDataStore = settingsDataStore,
            permissionManager = permissionManager
        )
    }

    val screenCaptureManager: ScreenCaptureManager by lazy {
        ScreenCaptureManager(this)
    }

    val orderTextRecognizer: OrderTextRecognizer by lazy {
        OrderTextRecognizer()
    }

    val orderParser: OrderParser by lazy {
        OrderParser()
    }

    val rapidoScreenMonitor: RapidoScreenMonitor by lazy {
        RapidoScreenMonitor(
            context = this,
            screenCaptureManager = screenCaptureManager,
            textRecognizer = orderTextRecognizer,
            orderParser = orderParser,
            recommendationManager = recommendationManager,
            settingsDataStore = settingsDataStore
        )
    }
}
