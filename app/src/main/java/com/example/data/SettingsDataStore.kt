package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.CaptainSettings
import com.example.domain.model.RankingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "captain_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val MIN_EXPECTED_FARE = doublePreferencesKey("min_expected_fare")
        val MIN_FARE_PER_KM = doublePreferencesKey("min_fare_per_km")
        val MAX_PICKUP_DISTANCE = doublePreferencesKey("max_pickup_distance")
        val MIN_NET_EARNINGS = doublePreferencesKey("min_net_earnings")
        val FUEL_COST_PER_KM = doublePreferencesKey("fuel_cost_per_km")
        val OTHER_EXPENSE = doublePreferencesKey("other_expense_per_order")
        val AVERAGE_SPEED_KMH = doublePreferencesKey("average_speed_kmh")
        val PICKUP_WAIT_MIN = doublePreferencesKey("pickup_wait_min")
        val INCLUDE_BONUS = booleanPreferencesKey("include_bonus_in_total")
        val RANKING_MODE = stringPreferencesKey("preferred_ranking_mode")
        val OCR_INTERVAL_MS = longPreferencesKey("ocr_interval_ms")
        val FLOATING_OVERLAY = booleanPreferencesKey("floating_overlay_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_LISTENER = booleanPreferencesKey("notification_listener_enabled")
    }

    val settingsFlow: Flow<CaptainSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = CaptainSettings()
        val modeName = prefs[Keys.RANKING_MODE] ?: defaults.preferredRankingMode.name
        val rankingMode = runCatching { RankingMode.valueOf(modeName) }
            .getOrDefault(defaults.preferredRankingMode)

        CaptainSettings(
            minExpectedFare = prefs[Keys.MIN_EXPECTED_FARE] ?: defaults.minExpectedFare,
            minFarePerKm = prefs[Keys.MIN_FARE_PER_KM] ?: defaults.minFarePerKm,
            maxPickupDistanceKm = prefs[Keys.MAX_PICKUP_DISTANCE] ?: defaults.maxPickupDistanceKm,
            minExpectedNetEarnings = prefs[Keys.MIN_NET_EARNINGS] ?: defaults.minExpectedNetEarnings,
            fuelCostPerKm = prefs[Keys.FUEL_COST_PER_KM] ?: defaults.fuelCostPerKm,
            otherExpensePerOrder = prefs[Keys.OTHER_EXPENSE] ?: defaults.otherExpensePerOrder,
            averageSpeedKmh = prefs[Keys.AVERAGE_SPEED_KMH] ?: defaults.averageSpeedKmh,
            pickupWaitMinutes = prefs[Keys.PICKUP_WAIT_MIN] ?: defaults.pickupWaitMinutes,
            includeBonusInTotal = prefs[Keys.INCLUDE_BONUS] ?: defaults.includeBonusInTotal,
            preferredRankingMode = rankingMode,
            ocrScanningIntervalMs = prefs[Keys.OCR_INTERVAL_MS] ?: defaults.ocrScanningIntervalMs,
            floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY] ?: defaults.floatingOverlayEnabled,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: defaults.notificationsEnabled,
            notificationListenerEnabled = prefs[Keys.NOTIFICATION_LISTENER] ?: defaults.notificationListenerEnabled
        )
    }

    suspend fun updateSettings(transform: (CaptainSettings) -> CaptainSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = CaptainSettings(
                minExpectedFare = prefs[Keys.MIN_EXPECTED_FARE] ?: 35.0,
                minFarePerKm = prefs[Keys.MIN_FARE_PER_KM] ?: 10.0,
                maxPickupDistanceKm = prefs[Keys.MAX_PICKUP_DISTANCE] ?: 3.5,
                minExpectedNetEarnings = prefs[Keys.MIN_NET_EARNINGS] ?: 25.0,
                fuelCostPerKm = prefs[Keys.FUEL_COST_PER_KM] ?: 2.2,
                otherExpensePerOrder = prefs[Keys.OTHER_EXPENSE] ?: 2.0,
                averageSpeedKmh = prefs[Keys.AVERAGE_SPEED_KMH] ?: 22.0,
                pickupWaitMinutes = prefs[Keys.PICKUP_WAIT_MIN] ?: 5.0,
                includeBonusInTotal = prefs[Keys.INCLUDE_BONUS] ?: true,
                preferredRankingMode = runCatching {
                    RankingMode.valueOf(prefs[Keys.RANKING_MODE] ?: RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR.name)
                }.getOrDefault(RankingMode.HIGHEST_NET_EARNINGS_PER_HOUR),
                ocrScanningIntervalMs = prefs[Keys.OCR_INTERVAL_MS] ?: 2000L,
                floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY] ?: false,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                notificationListenerEnabled = prefs[Keys.NOTIFICATION_LISTENER] ?: false
            )
            val updated = transform(current)
            prefs[Keys.MIN_EXPECTED_FARE] = updated.minExpectedFare
            prefs[Keys.MIN_FARE_PER_KM] = updated.minFarePerKm
            prefs[Keys.MAX_PICKUP_DISTANCE] = updated.maxPickupDistanceKm
            prefs[Keys.MIN_NET_EARNINGS] = updated.minExpectedNetEarnings
            prefs[Keys.FUEL_COST_PER_KM] = updated.fuelCostPerKm
            prefs[Keys.OTHER_EXPENSE] = updated.otherExpensePerOrder
            prefs[Keys.AVERAGE_SPEED_KMH] = updated.averageSpeedKmh
            prefs[Keys.PICKUP_WAIT_MIN] = updated.pickupWaitMinutes
            prefs[Keys.INCLUDE_BONUS] = updated.includeBonusInTotal
            prefs[Keys.RANKING_MODE] = updated.preferredRankingMode.name
            prefs[Keys.OCR_INTERVAL_MS] = updated.ocrScanningIntervalMs
            prefs[Keys.FLOATING_OVERLAY] = updated.floatingOverlayEnabled
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            prefs[Keys.NOTIFICATION_LISTENER] = updated.notificationListenerEnabled
        }
    }

    suspend fun setRankingMode(mode: RankingMode) {
        updateSettings { it.copy(preferredRankingMode = mode) }
    }

    suspend fun resetDefaults() {
        val defaults = CaptainSettings()
        updateSettings { defaults }
    }
}
