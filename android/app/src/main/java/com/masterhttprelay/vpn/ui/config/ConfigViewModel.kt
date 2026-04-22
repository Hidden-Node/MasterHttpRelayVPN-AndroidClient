package com.masterhttprelay.vpn.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masterhttprelay.vpn.data.ConfigStore
import com.masterhttprelay.vpn.data.RustConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val configStore: ConfigStore
) : ViewModel() {
    
    private val _config = MutableStateFlow(RustConfig())
    val config: StateFlow<RustConfig> = _config.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()
    
    init {
        loadConfig()
    }
    
    private fun loadConfig() {
        viewModelScope.launch {
            configStore.configFlow.collect { loadedConfig ->
                _config.value = loadedConfig
            }
        }
    }
    
    fun saveConfig(config: RustConfig) {
        viewModelScope.launch {
            configStore.saveConfig(config)
            _saveSuccess.value = true
            
            // Reset success message after 3 seconds
            kotlinx.coroutines.delay(3000)
            _saveSuccess.value = false
        }
    }
}
