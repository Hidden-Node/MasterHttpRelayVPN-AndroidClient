package com.masterhttprelay.vpn.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.lifecycle.lifecycleScope
import com.masterhttprelay.vpn.util.VpnStateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VpnTileService : TileService() {
    
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }
    
    override fun onClick() {
        super.onClick()
        
        lifecycleScope.launch {
            val currentState = VpnStateManager.state.first()
            
            when (currentState) {
                VpnStateManager.VpnState.DISCONNECTED -> {
                    // Start VPN
                    val intent = Intent(this@VpnTileService, RustVpnService::class.java).apply {
                        action = RustVpnService.ACTION_CONNECT
                    }
                    startService(intent)
                }
                VpnStateManager.VpnState.CONNECTED -> {
                    // Stop VPN
                    val intent = Intent(this@VpnTileService, RustVpnService::class.java).apply {
                        action = RustVpnService.ACTION_DISCONNECT
                    }
                    startService(intent)
                }
                else -> {
                    // Do nothing if connecting/disconnecting
                }
            }
            
            updateTile()
        }
    }
    
    private fun updateTile() {
        lifecycleScope.launch {
            val currentState = VpnStateManager.state.first()
            
            qsTile?.apply {
                state = when (currentState) {
                    VpnStateManager.VpnState.CONNECTED -> Tile.STATE_ACTIVE
                    VpnStateManager.VpnState.DISCONNECTED -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_UNAVAILABLE
                }
                updateTile()
            }
        }
    }
}
