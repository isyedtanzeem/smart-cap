package com.example.presentation.dashboard

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CaptainSettings
import com.example.domain.model.RankingMode
import com.example.domain.model.RecommendationResult
import com.example.engine.RecommendationManager
import com.example.monitoring.MonitoringStatus
import com.example.monitoring.RapidoScreenMonitor
import com.example.permissions.PermissionManager
import com.example.permissions.PermissionStatusSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val monitoringStatus: MonitoringStatus = MonitoringStatus(),
    val recommendation: RecommendationResult,
    val settings: CaptainSettings = CaptainSettings(),
    val permissions: PermissionStatusSnapshot = PermissionStatusSnapshot(
        notificationsGranted = false,
        overlayGranted = false,
        notificationListenerGranted = false
    )
)

class DashboardViewModel(
    private val screenMonitor: RapidoScreenMonitor,
    private val recommendationManager: RecommendationManager,
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val permissionsTrigger = MutableStateFlow(permissionManager.getStatusSnapshot())

    val uiState: StateFlow<DashboardUiState> = combine(
        screenMonitor.status,
        recommendationManager.recommendationState,
        recommendationManager.settingsFlow,
        permissionsTrigger
    ) { monitoring, recommendation, settings, permissions ->
        DashboardUiState(
            monitoringStatus = monitoring,
            recommendation = recommendation,
            settings = settings,
            permissions = permissions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            recommendation = recommendationManager.recommendationState.value
        )
    )

    fun refreshPermissions() {
        permissionsTrigger.value = permissionManager.getStatusSnapshot()
    }

    fun startMonitoringWithConsent(resultCode: Int, data: Intent) {
        screenMonitor.startMonitoringWithConsent(resultCode, data)
    }

    fun stopMonitoring() {
        screenMonitor.stopMonitoring()
    }

    fun scanScreenshot(uri: Uri) {
        viewModelScope.launch {
            screenMonitor.scanPickedScreenshot(uri)
        }
    }

    fun selectRankingMode(mode: RankingMode) {
        viewModelScope.launch {
            recommendationManager.setRankingMode(mode)
        }
    }

    fun clearCurrentResults() {
        recommendationManager.clearActiveOrders()
    }

    class Factory(
        private val screenMonitor: RapidoScreenMonitor,
        private val recommendationManager: RecommendationManager,
        private val permissionManager: PermissionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(screenMonitor, recommendationManager, permissionManager) as T
        }
    }
}
