package com.masterhttprelay.vpn.bridge

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

object PythonBridge {
    private var initialized = false
    private var vpnCoreModule: PyObject? = null
    private var callback: VpnCoreCallback? = null

    @Synchronized
    fun init(context: Context, callback: VpnCoreCallback) {
        if (initialized) return

        val module = getVpnCoreModule(context)

        // Pass the callback directly - Python will call methods on it
        module.callAttr("init", callback)
        
        vpnCoreModule = module
        this.callback = callback
        initialized = true
    }

    private fun getVpnCoreModule(context: Context): PyObject {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        val py = Python.getInstance()
        return py.getModule("vpn_core")
    }

    fun start(context: Context, configJson: String): Boolean {
        if (!initialized || vpnCoreModule == null) {
            throw IllegalStateException("PythonBridge not initialized")
        }

        // Get CA directory in app's internal storage
        val caDir = File(context.filesDir, "ca").absolutePath

        return try {
            val result = vpnCoreModule?.callAttr("start", configJson, caDir)
            result?.toBoolean() ?: false
        } catch (e: Exception) {
            callback?.onFatal("Failed to start Python core: ${e.message}")
            false
        }
    }

    fun stop() {
        if (!initialized || vpnCoreModule == null) return

        try {
            vpnCoreModule?.callAttr("stop")
        } catch (e: Exception) {
            callback?.onLog(4, "Error stopping Python core: ${e.message}")
        }
    }

    fun isRunning(): Boolean {
        if (!initialized || vpnCoreModule == null) return false

        return try {
            val result = vpnCoreModule?.callAttr("is_running")
            result?.toBoolean() ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun version(): String {
        if (!initialized || vpnCoreModule == null) return "Python Core (not initialized)"

        return try {
            vpnCoreModule?.callAttr("get_version")?.toString() ?: "Python Core v1.0.0"
        } catch (e: Exception) {
            "Python Core v1.0.0"
        }
    }

    fun getCaCertPath(context: Context): String {
        return File(context.filesDir, "ca/ca.crt").absolutePath
    }

    fun ensureCaCert(context: Context): String? {
        return try {
            val module = getVpnCoreModule(context)
            val caDir = File(context.filesDir, "ca").absolutePath
            module.callAttr("ensure_ca", caDir)?.toString()
        } catch (_: Exception) {
            null
        }
    }
}
