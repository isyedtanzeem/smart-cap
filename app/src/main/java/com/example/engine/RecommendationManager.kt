package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.OrderRepository
import com.example.data.SettingsDataStore
import com.example.domain.model.CaptainSettings
import com.example.domain.model.Order
import com.example.domain.model.OrderEvaluation
import com.example.domain.model.RankingMode
import com.example.domain.model.RecommendationResult
import com.example.domain.model.SelectorStubOrder
import com.example.ocr.ParsedRapidoScreen
import com.example.permissions.PermissionManager
import com.example.services.FloatingOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class RecommendationManager(
    private val context: Context,
    private val smartOrderEngine: SmartOrderEngine,
    private val orderRepository: OrderRepository,
    private val settingsDataStore: SettingsDataStore,
    private val permissionManager: PermissionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentSettings: CaptainSettings = CaptainSettings()
    private var lastParsedScreen: ParsedRapidoScreen? = null
    private var lastNotifiedSignature: String = ""

    private val _recommendationState = MutableStateFlow(
        RecommendationResult(
            detectedOrderCountLabel = null,
            rankedEvaluations = emptyList(),
            recommendedOrder = null,
            selectorStubs = emptyList(),
            rankingMode = CaptainSettings().preferredRankingMode,
            comparisonSummary = "Start monitoring or scan a Rapido screenshot to compare orders.",
            scannedAtEpochMs = 0L
        )
    )
    val recommendationState: StateFlow<RecommendationResult> = _recommendationState.asStateFlow()

    val settingsFlow = settingsDataStore.settingsFlow

    init {
        ensureAlertChannel()
        scope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                lastParsedScreen?.let { parsed ->
                    recomputeFromParsed(parsed, persist = false, notify = false)
                } ?: run {
                    _recommendationState.value = _recommendationState.value.copy(
                        rankingMode = settings.preferredRankingMode
                    )
                }
                if (!settings.floatingOverlayEnabled) {
                    FloatingOverlayService.hide(context)
                } else {
                    _recommendationState.value.recommendedOrder?.let { top ->
                        if (permissionManager.isOverlayGranted()) {
                            FloatingOverlayService.showOrUpdate(context, top)
                        }
                    }
                }
            }
        }
    }

    suspend fun onScreenParsed(parsed: ParsedRapidoScreen) {
        lastParsedScreen = parsed
        recomputeFromParsed(parsed, persist = true, notify = true)
    }

    suspend fun setRankingMode(mode: RankingMode) {
        settingsDataStore.setRankingMode(mode)
    }

    fun clearActiveOrders() {
        lastParsedScreen = null
        lastNotifiedSignature = ""
        _recommendationState.value = RecommendationResult(
            detectedOrderCountLabel = null,
            rankedEvaluations = emptyList(),
            recommendedOrder = null,
            selectorStubs = emptyList(),
            rankingMode = currentSettings.preferredRankingMode,
            comparisonSummary = "Monitoring cleared. Ready for next Rapido order screen.",
            scannedAtEpochMs = System.currentTimeMillis()
        )
        FloatingOverlayService.hide(context)
    }

    private suspend fun recomputeFromParsed(
        parsed: ParsedRapidoScreen,
        persist: Boolean,
        notify: Boolean
    ) {
        val result = smartOrderEngine.rankOrders(
            orders = parsed.orders,
            selectorStubs = parsed.selectorStubs,
            detectedOrderCountLabel = parsed.detectedOrderCount,
            settings = currentSettings,
            rankingMode = currentSettings.preferredRankingMode
        )
        _recommendationState.value = result

        if (persist && result.rankedEvaluations.isNotEmpty()) {
            orderRepository.recordEvaluatedScan(
                evaluations = result.rankedEvaluations,
                rankingMode = currentSettings.preferredRankingMode
            )
        }

        val top = result.recommendedOrder
        if (top != null) {
            if (currentSettings.floatingOverlayEnabled && permissionManager.isOverlayGranted()) {
                FloatingOverlayService.showOrUpdate(context, top)
            }
            if (notify && currentSettings.notificationsEnabled && permissionManager.isPostNotificationsGranted()) {
                val sig = "${top.order.orderId}_${result.rankingMode.name}_${result.rankedEvaluations.size}"
                if (sig != lastNotifiedSignature) {
                    lastNotifiedSignature = sig
                    postBestOrderNotification(top, result.rankingMode)
                }
            }
        }
    }

    private fun postBestOrderNotification(top: OrderEvaluation, mode: RankingMode) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            202,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fareText = top.totalExpectedFare?.let { "₹${fmt(it)}" } ?: "N/A"
        val distText = top.totalDistanceKm?.let { "${fmt(it)} km" } ?: "? km"
        val rateText = top.farePerKm?.let { "₹${fmt(it)}/km" } ?: ""
        val content = "Order #${top.order.orderNumber} (${top.order.orderType.displayLabel}) • $fareText • $distText $rateText"

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Best Value (${mode.shortLabel}): $fareText")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n${top.recommendationReason}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun ensureAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alerts_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun fmt(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)
    }

    companion object {
        const val ALERT_CHANNEL_ID = "smart_captain_best_order_alerts"
        private const val ALERT_NOTIFICATION_ID = 4092
    }
}
