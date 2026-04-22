package com.masterhttprelay.vpn.bridge

object RustBridge {
    private var initialized = false

    init {
        runCatching { System.loadLibrary("rust_jni_bridge") }
            .onFailure { throw UnsatisfiedLinkError("Failed to load rust_jni_bridge: ${it.message}") }
    }

    @Synchronized
    fun init(callback: RustBridgeCallback) {
        if (initialized) return
        nativeInit(callback)
        initialized = true
    }

    fun start(configJson: String): Boolean = nativeStart(configJson)

    fun stop() {
        nativeStop()
    }

    fun isRunning(): Boolean = nativeIsRunning()

    fun version(): String = nativeGetVersion()

    private external fun nativeInit(callback: RustBridgeCallback)
    private external fun nativeStart(configJson: String): Boolean
    private external fun nativeStop()
    private external fun nativeIsRunning(): Boolean
    private external fun nativeGetVersion(): String
}
