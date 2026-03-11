package dev.korryr.koreal.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.korryr.koreal.data.model.AppUsageStats
import dev.korryr.koreal.data.repository.NetworkStatsRepository
import dev.korryr.koreal.data.model.NetworkPacketInfo
import dev.korryr.koreal.data.repository.PacketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkMonitorViewModel(
    private val networkStatsRepository: NetworkStatsRepository,
    private val packetRepository: PacketRepository
) : ViewModel() {

    private val _usageStats = MutableStateFlow<List<AppUsageStats>>(emptyList())
    val usageStats: StateFlow<List<AppUsageStats>> = _usageStats.asStateFlow()
    
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _recentPackets = MutableStateFlow<List<NetworkPacketInfo>>(emptyList())
    val recentPackets: StateFlow<List<NetworkPacketInfo>> = _recentPackets.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    companion object {
        private const val TAG = "NetworkMonitorViewModel"
    }

    init {
        viewModelScope.launch {
            packetRepository.packetFlow.collect { packet ->
                // Keep only the 50 most recent packets to avoid memory bloat
                val currentList = _recentPackets.value.toMutableList()
                currentList.add(0, packet) // Add to top
                if (currentList.size > 50) {
                    currentList.removeAt(currentList.lastIndex)
                }
                _recentPackets.value = currentList
            }
        }
    }

    fun loadUsageStats() {
        viewModelScope.launch {
            _error.value = null
            try {
                _usageStats.value = networkStatsRepository.getUsageStatsForToday()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading usage stats", e)
                _error.value = "Failed to load usage stats: ${e.message}"
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun setVpnState(active: Boolean) {
        _isVpnActive.value = active
    }
}
