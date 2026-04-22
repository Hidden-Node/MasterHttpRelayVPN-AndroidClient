package com.masterhttprelay.vpn.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton state manager for VPN connection state and logs.
 */
object VpnStateManager {
    
    enum class VpnState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val message: String
    )
    
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private val _bytesIn = MutableStateFlow(0L)
    val bytesIn: StateFlow<Long> = _bytesIn.asStateFlow()
    
    private val _bytesOut = MutableStateFlow(0L)
    val bytesOut: StateFlow<Long> = _bytesOut.asStateFlow()
    
    fun updateState(newState: VpnState) {
        _state.value = newState
    }
    
    fun setError(message: String?) {
        _error.value = message
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun appendLog(level: LogLevel, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message
        )
        _logs.value = (_logs.value + entry).takeLast(500) // Keep last 500 entries
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
    
    fun updateTraffic(bytesIn: Long, bytesOut: Long) {
        _bytesIn.value = bytesIn
        _bytesOut.value = bytesOut
    }
    
    fun reset() {
        _state.value = VpnState.DISCONNECTED
        _error.value = null
        _bytesIn.value = 0L
        _bytesOut.value = 0L
    }
}
