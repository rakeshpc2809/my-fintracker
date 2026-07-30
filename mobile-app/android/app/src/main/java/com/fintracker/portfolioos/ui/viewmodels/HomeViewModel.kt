package com.fintracker.portfolioos.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintracker.portfolioos.data.PortfolioRepository
import com.fintracker.portfolioos.data.PortfolioSnapshotDto
import com.fintracker.valuation.fire.FireSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val snapshot: PortfolioSnapshotDto? = null,
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val fireSummary: FireSummary? = null,
    val returnMetric: String = "XIRR"
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeRepository()
        refreshData()
    }

    private fun observeRepository() {
        viewModelScope.launch {
            PortfolioRepository.snapshotState.collect { snap ->
                val fireSum = PortfolioRepository.computeLocalFireSummary()
                _uiState.value = _uiState.value.copy(
                    snapshot = snap,
                    fireSummary = fireSum,
                    isLoading = snap == null
                )
            }
        }
        viewModelScope.launch {
            PortfolioRepository.isOfflineState.collect { offline ->
                _uiState.value = _uiState.value.copy(isOffline = offline)
            }
        }
    }

    fun setReturnMetric(metric: String) {
        _uiState.value = _uiState.value.copy(returnMetric = metric)
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            PortfolioRepository.syncWithBackend()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
