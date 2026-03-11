package dev.korryr.koreal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.korryr.koreal.data.model.AppUsageStats
import dev.korryr.koreal.data.repository.NetworkStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkMonitorViewModel(
    private val repository: NetworkStatsRepository
) : ViewModel() {

    private val _usageStats = MutableStateFlow<List<AppUsageStats>>(emptyList())
    val usageStats: StateFlow<List<AppUsageStats>> = _usageStats.asStateFlow()
    
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    fun loadUsageStats() {
        viewModelScope.launch {
            try {
                _usageStats.value = repository.getUsageStatsForToday()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun setVpnState(active: Boolean) {
        _isVpnActive.value = active
    }
}
