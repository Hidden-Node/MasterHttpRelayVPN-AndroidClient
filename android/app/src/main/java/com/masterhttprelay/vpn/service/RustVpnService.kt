package com.masterhttprelay.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.masterhttprelay.vpn.MainActivity
import com.masterhttprelay.vpn.R
import com.masterhttprelay.vpn.data.ConfigStore
import com.masterhttprelay.vpn.data.RustConfig
import com.masterhttprelay.vpn.util.VpnStateManager
import kotlinx.coroutines.*

class RustVpnService : VpnService() {
    
    companion object {
        const val ACTION_CONNECT = "com.masterhttprelay.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.masterhttprelay.vpn.DISCONNECT"
        private const val TAG = "RustVpnService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "vpn_service"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var rustProcessManager: RustProcessManager
    private lateinit var tun2SocksManager: Tun2SocksManager
    private lateinit var configStore: ConfigStore
    
    @Volatile
    private var isStopping = false
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VPN Service created")
        
        rustProcessManager = RustProcessManager(this)
        tun2SocksManager = Tun2SocksManager()
        configStore = ConfigStore(this)
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startVpn()
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        connectJob?.cancel()
        connectJob = serviceScope.launch {
            try {
                isStopping = false
                VpnStateManager.updateState(VpnStateManager.VpnState.CONNECTING)
                VpnStateManager.clearError()
                
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_connecting)))
                acquireWakeLock()
                
                // Load configuration
                val config = configStore.getConfig()
                if (!config.isValid()) {
                    throw IllegalStateException("Invalid configuration: missing script_id or auth_key")
                }
                
                VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "Starting VPN connection...")
                
                // Start Rust process
                if (!rustProcessManager.start(config)) {
                    throw IllegalStateException("Failed to start Rust process")
                }
                
                // Wait for Rust to be ready
                delay(2000)
                
                if (!rustProcessManager.isRunning()) {
                    throw IllegalStateException("Rust process died unexpectedly")
                }
                
                // Establish VPN interface
                val tunFd = establishVpnInterface(config)
                if (tunFd == null) {
                    throw IllegalStateException("Failed to establish VPN interface")
                }
                
                vpnInterface = tunFd
                
                // Start tun2socks bridge
                val socksAddr = "${config.listenHost}:${config.socks5Port}"
                if (!tun2SocksManager.start(tunFd, socksAddr)) {
                    throw IllegalStateException("Failed to start tun2socks bridge")
                }
                
                // Connection successful
                VpnStateManager.updateState(VpnStateManager.VpnState.CONNECTED)
                VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "VPN connected successfully")
                
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_connected)))
                
            } catch (e: Exception) {
                Log.e(TAG, "VPN connection failed", e)
                VpnStateManager.setError(e.message ?: "Connection failed")
                VpnStateManager.appendLog(VpnStateManager.LogLevel.ERROR, "Connection failed: ${e.message}")
                stopVpn()
            }
        }
    }
    
    private fun stopVpn() {
        if (isStopping) return
        isStopping = true
        
        serviceScope.launch {
            try {
                VpnStateManager.updateState(VpnStateManager.VpnState.DISCONNECTING)
                VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "Disconnecting VPN...")
                
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_disconnecting)))
                
                // Stop tun2socks
                tun2SocksManager.stop()
                
                // Close VPN interface
                vpnInterface?.close()
                vpnInterface = null
                
                // Stop Rust process
                rustProcessManager.stop()
                
                // Release wake lock
                releaseWakeLock()
                
                VpnStateManager.updateState(VpnStateManager.VpnState.DISCONNECTED)
                VpnStateManager.appendLog(VpnStateManager.LogLevel.INFO, "VPN disconnected")
                
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnect", e)
            } finally {
                isStopping = false
            }
        }
    }
    
    private fun establishVpnInterface(config: RustConfig): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession("MasterHttpRelayVPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)
                .setBlocking(false)
                .establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            null
        }
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MasterHttpRelayVPN::VpnWakeLock"
        ).apply {
            acquire()
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onRevoke() {
        super.onRevoke()
        Log.i(TAG, "VPN permission revoked")
        VpnStateManager.appendLog(VpnStateManager.LogLevel.WARN, "VPN permission revoked")
        stopVpn()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "VPN Service destroyed")
        
        connectJob?.cancel()
        serviceScope.cancel()
        
        rustProcessManager.cleanup()
        tun2SocksManager.cleanup()
        
        releaseWakeLock()
    }
}
