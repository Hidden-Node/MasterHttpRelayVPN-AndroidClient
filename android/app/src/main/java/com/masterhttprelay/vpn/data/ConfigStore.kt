package com.masterhttprelay.vpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rust_config")

class ConfigStore(private val context: Context) {
    
    private val CONFIG_KEY = stringPreferencesKey("rust_config_json")
    
    val configFlow: Flow<RustConfig> = context.dataStore.data.map { preferences ->
        val json = preferences[CONFIG_KEY]
        json?.let { RustConfig.fromJson(it) } ?: RustConfig()
    }
    
    suspend fun saveConfig(config: RustConfig) {
        context.dataStore.edit { preferences ->
            preferences[CONFIG_KEY] = config.toJson()
        }
    }
    
    suspend fun getConfig(): RustConfig {
        var result = RustConfig()
        context.dataStore.data.collect { preferences ->
            val json = preferences[CONFIG_KEY]
            result = json?.let { RustConfig.fromJson(it) } ?: RustConfig()
        }
        return result
    }
}
