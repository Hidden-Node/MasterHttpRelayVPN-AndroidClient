package com.masterhttprelay.vpn.service

import android.content.Context
import android.util.Log
import com.masterhttprelay.vpn.data.RustConfig
import com.masterhttprelay.vpn.util.VpnStateManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Manages the Rust mhrv-rs subprocess lifecycle.
 */
class RustProcessManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RustProcessManager"
        private const val RUST_BINARY_NAME = "mhrv-rs"
    }
    
    private var process: Process? = null
    private var logReaderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start the Rust process with the given configuration.
     */
    suspend fun start(config: RustConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            // Stop any existing process
            stop()
            
            // Get the correct ABI binary
            val abi = getDeviceAbi()
            val binaryPath = extractBinary(abi)
            
            if (!binaryPath.exists()) {
                Log.e(TAG, "Rust binary not found: ${binaryPath.absolutePath}")
                VpnStateManager.setError("Rust binary not found for ABI: $abi")
                return@withContext false
            }
            
            // Make binary executable
            binaryPath.setExecutable(true)
            
            // Write config to file
            val configFile = File(context.filesDir, "config.json")
            configFile.writeText(config.toJson())
            
            Log.i(TAG, "Starting Rust process: ${binaryPath.absolutePath}")
            VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "Starting Rust proxy...")
            
            // Start the process
            val processBuilder = ProcessBuilder(
                binaryPath.absolutePath,
                "--config", configFile.absolutePath
            )
            processBuilder.redirectErrorStream(true)
            
            process = processBuilder.start()
            
            // Start log reader
            startLogReader()
            
            // Wait a bit to check if process started successfully
            delay(1000)
            
            if (process?.isAlive == true) {
                Log.i(TAG, "Rust process started successfully")
                VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "Rust proxy started")
                return@withContext true
            } else {
                val exitCode = process?.exitValue() ?: -1
                Log.e(TAG, "Rust process failed to start, exit code: $exitCode")
                VpnStateManager.setError("Rust process failed to start (exit: $exitCode)")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Rust process", e)
            VpnStateManager.setError("Failed to start Rust: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Stop the Rust process.
     */
    fun stop() {
        logReaderJob?.cancel()
        logReaderJob = null
        
        process?.let { proc ->
            try {
                if (proc.isAlive) {
                    Log.i(TAG, "Stopping Rust process")
                    proc.destroy()
                    
                    // Wait up to 5 seconds for graceful shutdown
                    if (!proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        Log.w(TAG, "Force killing Rust process")
                        proc.destroyForcibly()
                    }
                    
                    Log.i(TAG, "Rust process stopped")
                    VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "Rust proxy stopped")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Rust process", e)
            }
        }
        
        process = null
    }
    
    /**
     * Check if the Rust process is running.
     */
    fun isRunning(): Boolean {
        return process?.isAlive == true
    }
    
    private fun startLogReader() {
        logReaderJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process?.inputStream))
                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            parseAndLogLine(line)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Error reading Rust logs", e)
                }
            }
        }
    }
    
    private fun parseAndLogLine(line: String) {
        // Parse Rust log format: "LEVEL message"
        val level = when {
            line.contains("ERROR", ignoreCase = true) -> VpnStateManager.LogLevel.ERROR
            line.contains("WARN", ignoreCase = true) -> VpnStateManager.LogLevel.WARN
            line.contains("INFO", ignoreCase = true) -> VpnStateManager.LogLevel.INFO
            else -> VpnStateManager.LogLevel.DEBUG
        }
        
        VpnStateManager.appendLog(level, line)
        Log.d(TAG, "Rust: $line")
    }
    
    private fun getDeviceAbi(): String {
        val supportedAbis = android.os.Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("armeabi-v7a") -> "armeabi-v7a"
            supportedAbis.contains("x86_64") -> "x86_64"
            else -> supportedAbis.firstOrNull() ?: "arm64-v8a"
        }
    }
    
    private fun extractBinary(abi: String): File {
        val targetDir = File(context.filesDir, "native/$abi")
        targetDir.mkdirs()
        
        val targetFile = File(targetDir, RUST_BINARY_NAME)
        
        // Copy from assets if not already extracted or if assets version is newer
        try {
            val assetPath = "$abi/$RUST_BINARY_NAME"
            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setExecutable(true)
            Log.i(TAG, "Extracted Rust binary to: ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract Rust binary", e)
        }
        
        return targetFile
    }
    
    fun cleanup() {
        stop()
        scope.cancel()
    }
}
