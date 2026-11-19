package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsDataStore
import com.example.domain.model.CaptainSettings
import com.example.domain.model.RankingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settingsState: StateFlow<CaptainSettings> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CaptainSettings()
        )

    fun updateMinExpectedFare(value: Double) = update { it.copy(minExpectedFare = value.coerceAtLeast(0.0)) }
    fun updateMinFarePerKm(value: Double) = update { it.copy(minFarePerKm = value.coerceAtLeast(0.0)) }
    fun updateMaxPickupDistance(value: Double) = update { it.copy(maxPickupDistanceKm = value.coerceAtLeast(0.2)) }
    fun updateMinNetEarnings(value: Double) = update { it.copy(minExpectedNetEarnings = value.coerceAtLeast(0.0)) }
    fun updateFuelCostPerKm(value: Double) = update { it.copy(fuelCostPerKm = value.coerceAtLeast(0.0)) }
    fun updateOtherExpense(value: Double) = update { it.copy(otherExpensePerOrder = value.coerceAtLeast(0.0)) }
    fun updateAverageSpeed(value: Double) = update { it.copy(averageSpeedKmh = value.coerceIn(8.0, 60.0)) }
    fun updatePickupWait(value: Double) = update { it.copy(pickupWaitMinutes = value.coerceIn(0.0, 30.0)) }
    fun updateIncludeBonus(enabled: Boolean) = update { it.copy(includeBonusInTotal = enabled) }
    fun updateRankingMode(mode: RankingMode) = update { it.copy(preferredRankingMode = mode) }
    fun updateOcrIntervalMs(ms: Long) = update { it.copy(ocrScanningIntervalMs = ms.coerceIn(1000L, 10000L)) }
    fun updateFloatingOverlay(enabled: Boolean) = update { it.copy(floatingOverlayEnabled = enabled) }
    fun updateNotificationsEnabled(enabled: Boolean) = update { it.copy(notificationsEnabled = enabled) }

    fun resetDefaults() {
        viewModelScope.launch {
            settingsDataStore.resetDefaults()
        }
    }

    private fun update(transform: (CaptainSettings) -> CaptainSettings) {
        viewModelScope.launch {
            settingsDataStore.updateSettings(transform)
        }
    }

    class Factory(private val settingsDataStore: SettingsDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsDataStore) as T
        }
    }
}
