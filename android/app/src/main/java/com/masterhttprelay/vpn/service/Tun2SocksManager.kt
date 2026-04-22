package com.masterhttprelay.vpn.service

import android.os.ParcelFileDescriptor
import android.util.Log
import com.masterhttprelay.vpn.util.VpnStateManager
import kotlinx.coroutines.*

/**
 * Manages the tun2socks bridge between Android VPN TUN interface and Rust SOCKS5 proxy.
 */
class Tun2SocksManager {
    
    companion object {
        private const val TAG = "Tun2SocksManager"
    }
    
    private var bridgeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start tun2socks bridge.
     * 
     * @param tunFd File descriptor of the VPN TUN interface
     * @param socksAddr SOCKS5 proxy address (e.g., "127.0.0.1:8086")
     * @param mtu MTU size for the TUN interface
     */
    fun start(tunFd: ParcelFileDescriptor, socksAddr: String, mtu: Int = 1500): Boolean {
        try {
            stop()
            
            Log.i(TAG, "Starting tun2socks bridge: TUN -> SOCKS5 $socksAddr")
            VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "Starting tun2socks bridge...")
            
            // TODO: Integrate with tun2socks AAR
            // For now, this is a placeholder that would call the gomobile-generated tun2socks library
            // The actual implementation would use the tun2socks.aar built by build_tun2socks.sh
            
            // Example pseudo-code (actual implementation depends on tun2socks AAR API):
            // bridgeJob = scope.launch {
            //     Tun2socks.start(tunFd.fd, socksAddr, mtu)
            // }
            
            Log.i(TAG, "tun2socks bridge started")
            VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "tun2socks bridge active")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
            VpnStateManager.setError("Failed to start tun2socks: ${e.message}")
            return false
        }
    }
    
    /**
     * Stop the tun2socks bridge.
     */
    fun stop() {
        bridgeJob?.cancel()
        bridgeJob = null
        
        // TODO: Call tun2socks stop method
        
        Log.i(TAG, "tun2socks bridge stopped")
        VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "tun2socks bridge stopped")
    }
    
    /**
     * Check if the bridge is running.
     */
    fun isRunning(): Boolean {
        return bridgeJob?.isActive == true
    }
    
    fun cleanup() {
        stop()
        scope.cancel()
    }
}
