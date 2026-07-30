package com.fintracker.portfolioos.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintracker.portfolioos.data.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val desktopIp: String = "127.0.0.1",
    val isSyncing: Boolean = false,
    val syncStatusMsg: String = "",
    val isOffline: Boolean = true
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            PortfolioRepository.isOfflineState.collect { offline ->
                _uiState.value = _uiState.value.copy(isOffline = offline)
            }
        }
    }

    fun updateDesktopIp(ip: String) {
        _uiState.value = _uiState.value.copy(desktopIp = ip)
    }

    fun testConnectionAndSync() {
        val ip = _uiState.value.desktopIp
        _uiState.value = _uiState.value.copy(
            isSyncing = true,
            syncStatusMsg = "Syncing with $ip..."
        )

        viewModelScope.launch {
            val success = PortfolioRepository.syncWithBackend(ip)
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncStatusMsg = if (success) "✓ Connected & Synced with Desktop Node!" else "⚡ Disconnected — Local Offline Domain Active"
            )
        }
    }
}
