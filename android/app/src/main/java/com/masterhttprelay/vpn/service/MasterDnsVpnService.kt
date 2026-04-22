package com.masterhttprelay.vpn.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.masterhttprelay.vpn.App
import com.masterhttprelay.vpn.MainActivity
import com.masterhttprelay.vpn.R
import com.masterhttprelay.vpn.bridge.RustBridge
import com.masterhttprelay.vpn.bridge.RustBridgeCallback
import com.masterhttprelay.vpn.data.local.AppDatabase
import com.masterhttprelay.vpn.util.ConfigGenerator
import com.masterhttprelay.vpn.util.GlobalSettingsStore
import com.masterhttprelay.vpn.util.VpnManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MasterDnsVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.masterhttprelay.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.masterhttprelay.vpn.DISCONNECT"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val TAG = "MasterHttpRelayVPN"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_HTTP_PORT = 8085
        private const val DEFAULT_SOCKS_PORT = 8086
        private const val BRIDGE_START_TIMEOUT_MS = 20_000L

        private val BROWSER_COMPANION_PACKAGES = setOf(
            "com.google.android.webview",
            "com.android.webview",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.captiveportallogin"
        )

        private val KNOWN_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.UCMobile.intl",
            "com.kiwibrowser.browser"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val tun2SocksManager = Tun2SocksManager()

    @Volatile
    private var isStopping = false

    override fun onCreate() {
        super.onCreate()
        RustBridge.init(BridgeCallback())
        VpnManager.appendLog("Rust JNI bridge loaded: ${RustBridge.version()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1)
                if (profileId > 0) {
                    startVpn(profileId)
                }
            }
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(profileId: Long) {
        connectJob?.cancel()
        connectJob = serviceScope.launch {
            try {
                isStopping = false
                VpnManager.updateState(VpnManager.VpnState.CONNECTING)
                VpnManager.clearError()

                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_connecting)))
                acquireWakeLock()

                val db = AppDatabase.getInstance(this@MasterDnsVpnService)
                val profile = db.profileDao().getProfileById(profileId)
                    ?: throw IllegalStateException("Profile not found")
                val global = GlobalSettingsStore.load(this@MasterDnsVpnService)

                val socksPort = DEFAULT_SOCKS_PORT
                val httpPort = DEFAULT_HTTP_PORT

                val configJson = ConfigGenerator.generateRustConfig(profile, httpPort = httpPort, socksPort = socksPort)
                VpnManager.appendLog("Starting Rust core for profile: ${profile.name}")

                if (!RustBridge.start(configJson)) {
                    throw IllegalStateException("Rust core failed to start")
                }

                var waited = 0L
                while (!RustBridge.isRunning() && waited < BRIDGE_START_TIMEOUT_MS) {
                    delay(200)
                    waited += 200
                }
                if (!RustBridge.isRunning()) {
                    throw IllegalStateException("Rust core startup timeout")
                }

                val builder = Builder()
                    .setSession(getString(R.string.app_name))
                    .addAddress("10.7.0.2", 24)
                    .setMtu(1500)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("8.8.4.4")

                val proxyMode = global.connectionMode.equals("PROXY", ignoreCase = true)
                if (!proxyMode) {
                    builder.addRoute("0.0.0.0", 0)
                    builder.addRoute("::", 0)

                    val splitEnabled = global.splitTunnelingEnabled && global.splitPackagesCsv.isNotBlank()
                    if (splitEnabled) {
                        val userSelected = global.splitPackagesCsv
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .toSet()
                        val hasBrowser = userSelected.any { it in KNOWN_BROWSER_PACKAGES }
                        val effective = if (hasBrowser) userSelected + BROWSER_COMPANION_PACKAGES else userSelected
                        effective.forEach { pkg ->
                            runCatching { builder.addAllowedApplication(pkg) }
                                .onFailure { VpnManager.appendLog("Split tunnel skip '$pkg': ${it.message}") }
                        }
                        VpnManager.appendLog("Split tunnel enabled (${effective.size} apps)")
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        builder.setMetered(false)
                    }

                    vpnInterface = builder.establish()
                        ?: throw IllegalStateException("Failed to establish VPN interface")

                    val socksAddr = "127.0.0.1:$socksPort"
                    if (!tun2SocksManager.start(vpnInterface!!, socksAddr, 1500)) {
                        throw IllegalStateException("tun2socks failed to start")
                    }
                }

                VpnManager.updateState(VpnManager.VpnState.CONNECTED)
                VpnManager.startTrafficMonitor(this@MasterDnsVpnService)
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_connected)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN", e)
                VpnManager.setError(e.message ?: "Connection failed")
                VpnManager.appendLog("Connection failed: ${e.message}")
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        if (isStopping) return
        isStopping = true

        serviceScope.launch {
            try {
                VpnManager.updateState(VpnManager.VpnState.DISCONNECTING)
                tun2SocksManager.stop()
                vpnInterface?.close()
                vpnInterface = null
                RustBridge.stop()
                VpnManager.stopTrafficMonitor()
                releaseWakeLock()
                VpnManager.updateState(VpnManager.VpnState.DISCONNECTED)
                stopForegroundCompat()
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error while stopping VPN", e)
            } finally {
                isStopping = false
            }
        }
    }

    override fun onRevoke() {
        super.onRevoke()
        VpnManager.appendLog("VPN permission revoked")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectJob?.cancel()
        tun2SocksManager.stop()
        RustBridge.stop()
        releaseWakeLock()
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MasterHttpRelayVPN::VpnWakeLock"
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        wakeLock = null
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private inner class BridgeCallback : RustBridgeCallback {
        override fun onStateChanged(state: Int, message: String?) {
            when (state) {
                1 -> VpnManager.updateState(VpnManager.VpnState.CONNECTING)
                2 -> VpnManager.updateState(VpnManager.VpnState.CONNECTED)
                3 -> VpnManager.updateState(VpnManager.VpnState.DISCONNECTED)
                4 -> {
                    VpnManager.updateState(VpnManager.VpnState.ERROR)
                    message?.let { VpnManager.setError(it) }
                }
            }
            message?.let { VpnManager.appendCoreLog(it) }
        }

        override fun onLog(level: Int, message: String) {
            VpnManager.appendCoreLog(
                when (level) {
                    4 -> "ERROR $message"
                    3 -> "WARN $message"
                    2 -> "INFO $message"
                    else -> "DEBUG $message"
                }
            )
        }

        override fun onFatal(message: String) {
            VpnManager.setError(message)
            VpnManager.appendCoreLog("FATAL $message")
        }
    }
}
