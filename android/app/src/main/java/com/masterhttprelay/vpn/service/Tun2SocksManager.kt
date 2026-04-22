package com.masterhttprelay.vpn.service

import android.os.ParcelFileDescriptor
import android.util.Log
import java.lang.reflect.Method

class Tun2SocksManager {
    companion object {
        private const val TAG = "Tun2SocksManager"
        private const val BRIDGE_CLASS = "com.masterhttprelay.tun2socks.Mobilebridge"
    }

    @Volatile
    private var running = false

    fun start(tunFd: ParcelFileDescriptor, socksAddr: String, mtu: Int = 1500): Boolean {
        return try {
            stop()
            val bridgeClass = Class.forName(BRIDGE_CLASS)
            val startMethod = findMethod(bridgeClass, "StartTun")
            startMethod.invoke(null, tunFd.fd, socksAddr, mtu)
            running = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
            false
        }
    }

    fun stop() {
        try {
            val bridgeClass = Class.forName(BRIDGE_CLASS)
            val stopMethod = findMethod(bridgeClass, "StopTun")
            stopMethod.invoke(null)
        } catch (_: Exception) {
        } finally {
            running = false
        }
    }

    fun isRunning(): Boolean = running

    private fun findMethod(clazz: Class<*>, expectedName: String): Method {
        return clazz.methods.firstOrNull {
            it.name.equals(expectedName, ignoreCase = true)
        } ?: throw NoSuchMethodException("$expectedName not found in ${clazz.name}")
    }
}
