package com.masterhttprelay.vpn.ui.home

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.masterhttprelay.vpn.service.RustVpnService
import com.masterhttprelay.vpn.util.VpnStateManager

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val vpnState by VpnStateManager.state.collectAsState()
    val error by VpnStateManager.error.collectAsState()
    val bytesIn by VpnStateManager.bytesIn.collectAsState()
    val bytesOut by VpnStateManager.bytesOut.collectAsState()
    
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted, start VPN
            val intent = Intent(context, RustVpnService::class.java).apply {
                action = RustVpnService.ACTION_CONNECT
            }
            context.startService(intent)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status indicator
        StatusCard(vpnState = vpnState)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Connect/Disconnect button
        ConnectButton(
            vpnState = vpnState,
            onConnect = {
                // Request VPN permission
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    // Permission already granted
                    val serviceIntent = Intent(context, RustVpnService::class.java).apply {
                        action = RustVpnService.ACTION_CONNECT
                    }
                    context.startService(serviceIntent)
                }
            },
            onDisconnect = {
                val intent = Intent(context, RustVpnService::class.java).apply {
                    action = RustVpnService.ACTION_DISCONNECT
                }
                context.startService(intent)
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Traffic stats
        if (vpnState == VpnStateManager.VpnState.CONNECTED) {
            TrafficStats(bytesIn = bytesIn, bytesOut = bytesOut)
        }
        
        // Error message
        error?.let { errorMsg ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMsg,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun StatusCard(vpnState: VpnStateManager.VpnState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (statusText, statusColor) = when (vpnState) {
                VpnStateManager.VpnState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
                VpnStateManager.VpnState.CONNECTING -> "Connecting..." to MaterialTheme.colorScheme.secondary
                VpnStateManager.VpnState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.tertiary
                VpnStateManager.VpnState.DISCONNECTING -> "Disconnecting..." to MaterialTheme.colorScheme.secondary
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineMedium,
                color = statusColor
            )
        }
    }
}

@Composable
fun ConnectButton(
    vpnState: VpnStateManager.VpnState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = vpnState == VpnStateManager.VpnState.CONNECTED
    val isTransitioning = vpnState == VpnStateManager.VpnState.CONNECTING || 
                         vpnState == VpnStateManager.VpnState.DISCONNECTING
    
    Button(
        onClick = {
            if (isConnected) onDisconnect() else onConnect()
        },
        enabled = !isTransitioning,
        modifier = Modifier
            .size(120.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.PowerOff else Icons.Default.Power,
            contentDescription = if (isConnected) "Disconnect" else "Connect",
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun TrafficStats(bytesIn: Long, bytesOut: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Download",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatBytes(bytesIn),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Upload",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatBytes(bytesOut),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
