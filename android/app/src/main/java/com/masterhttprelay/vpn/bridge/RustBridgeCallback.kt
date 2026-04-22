package com.masterhttprelay.vpn.bridge

interface RustBridgeCallback {
    fun onStateChanged(state: Int, message: String?)
    fun onLog(level: Int, message: String)
    fun onFatal(message: String)
}
