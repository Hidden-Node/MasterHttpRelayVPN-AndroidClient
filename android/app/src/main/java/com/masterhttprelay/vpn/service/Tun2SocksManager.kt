package com.masterhttprelay.vpn.service

import android.os.ParcelFileDescriptor
import android.util.Log
import com.masterhttprelay.vpn.util.VpnStateManager
import java.lang.reflect.Method

/**
 * Manages the tun2socks bridge between Android VPN TUN interface and Rust SOCKS5 proxy.
 */
class Tun2SocksManager {
    
    companion object {
        private const val TAG = "Tun2SocksManager"
        private const val BRIDGE_CLASS = "com.masterhttprelay.tun2socks.Mobilebridge"
    }

    @Volatile
    private var running = false
    
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

            val bridgeClass = Class.forName(BRIDGE_CLASS)
            val startMethod = findMethod(bridgeClass, "StartTun")
            startMethod.invoke(null, tunFd.fd, socksAddr, mtu)

            running = true
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
        try {
            val bridgeClass = Class.forName(BRIDGE_CLASS)
            val stopMethod = findMethod(bridgeClass, "StopTun")
            stopMethod.invoke(null)
        } catch (e: Exception) {
            Log.w(TAG, "tun2socks stop ignored: ${e.message}")
        } finally {
            running = false
            Log.i(TAG, "tun2socks bridge stopped")
            VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "tun2socks bridge stopped")
        }
    }
    
    /**
     * Check if the bridge is running.
     */
    fun isRunning(): Boolean {
        return running
    }

    private fun findMethod(clazz: Class<*>, expectedName: String): Method {
        return clazz.methods.firstOrNull {
            it.name.equals(expectedName, ignoreCase = true)
        } ?: throw NoSuchMethodException("$expectedName not found in ${clazz.name}")
    }

    fun cleanup() = stop()
}
