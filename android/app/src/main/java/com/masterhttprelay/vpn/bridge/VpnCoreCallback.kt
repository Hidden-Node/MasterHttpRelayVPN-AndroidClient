package com.masterhttprelay.vpn.bridge

/**
 * Callback interface for VPN core state and log updates.
 * Formerly named RustBridgeCallback for historical reasons,
 * now used with Python core via Chaquopy.
 */
interface VpnCoreCallback {
    fun onStateChanged(state: Int, message: String?)
    fun onLog(level: Int, message: String)
    fun onFatal(message: String)
}
