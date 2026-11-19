package com.example.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.OrderRepository
import com.example.database.OrderEntity
import com.example.domain.model.AcceptanceStatus
import com.example.domain.model.DailySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter(val label: String) {
    ALL("All Detected"),
    ACCEPTED("Accepted"),
    COMPLETED("Completed")
}

data class OrderHistoryUiState(
    val todaySummary: DailySummary = DailySummary(
        dayKey = "",
        totalDetectedOrders = 0,
        acceptedOrCompletedCount = 0,
        completedCount = 0,
        highestFare = 0.0,
        averageFarePerKm = 0.0,
        estimatedPotentialEarnings = 0.0,
        recordedCompletedEarnings = 0.0
    ),
    val allOrders: List<OrderEntity> = emptyList(),
    val filteredOrders: List<OrderEntity> = emptyList(),
    val selectedFilter: HistoryFilter = HistoryFilter.ALL
)

class OrderHistoryViewModel(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<OrderHistoryUiState> = combine(
        orderRepository.observeTodaySummary(),
        orderRepository.observeAllHistory(),
        filterFlow
    ) { summary, orders, filter ->
        val filtered = when (filter) {
            HistoryFilter.ALL -> orders
            HistoryFilter.ACCEPTED -> orders.filter {
                it.acceptanceStatus == AcceptanceStatus.ACCEPTED_BY_CAPTAIN.name
            }
            HistoryFilter.COMPLETED -> orders.filter {
                it.acceptanceStatus == AcceptanceStatus.COMPLETED_BY_CAPTAIN.name
            }
        }
        OrderHistoryUiState(
            todaySummary = summary,
            allOrders = orders,
            filteredOrders = filtered,
            selectedFilter = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OrderHistoryUiState()
    )

    fun selectFilter(filter: HistoryFilter) {
        filterFlow.value = filter
    }

    fun markAccepted(entityId: Long) {
        viewModelScope.launch {
            orderRepository.markOrderStatus(entityId, AcceptanceStatus.ACCEPTED_BY_CAPTAIN)
        }
    }

    fun markCompleted(entityId: Long, actualEarnings: Double?) {
        viewModelScope.launch {
            orderRepository.markOrderStatus(
                entityId = entityId,
                status = AcceptanceStatus.COMPLETED_BY_CAPTAIN,
                completedEarnings = actualEarnings
            )
        }
    }

    fun deleteEntry(entityId: Long) {
        viewModelScope.launch {
            orderRepository.deleteOrder(entityId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            orderRepository.clearAllHistory()
        }
    }

    class Factory(private val orderRepository: OrderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OrderHistoryViewModel(orderRepository) as T
        }
    }
}
