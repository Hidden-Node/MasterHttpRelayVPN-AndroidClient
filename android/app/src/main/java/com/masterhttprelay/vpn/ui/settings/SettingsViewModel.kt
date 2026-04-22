package com.masterhttprelay.vpn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.masterhttprelay.vpn.data.local.ProfileEntity
import com.masterhttprelay.vpn.data.repository.ProfileRepository
import com.masterhttprelay.vpn.util.ConfigGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gson = Gson()

    private val profileIdArg: Long? = savedStateHandle.get<String>("profileId")?.toLongOrNull()

    val selectedProfile: StateFlow<ProfileEntity?> = (
        if (profileIdArg != null) profileRepository.getProfileByIdFlow(profileIdArg)
        else profileRepository.getSelectedProfileFlow()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveSettings(profile: ProfileEntity, values: Map<String, String>) {
        viewModelScope.launch {
            profileRepository.updateProfile(buildUpdatedProfile(profile, values))
        }
    }

    fun exportConfigToml(profile: ProfileEntity, values: Map<String, String>): String {
        val updated = buildUpdatedProfile(profile, values)
        return ConfigGenerator.generateConfig(
            profile = updated,
            listenPort = updated.listenPort
        )
    }

    fun importTomlValues(
        tomlContent: String,
        currentValues: Map<String, String>
    ): Map<String, String> {
        val result = currentValues.toMutableMap()
        tomlContent.lineSequence().forEach { raw ->
            val line = raw.substringBefore("#").trim()
            if (line.isEmpty() || "=" !in line) return@forEach
            val key = line.substringBefore("=").trim()
            val valueRaw = line.substringAfter("=").trim()
            if (key !in TOML_IMPORT_KEYS) return@forEach

            val parsed = when {
                key == "SCRIPT_IDS" -> valueRaw
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                valueRaw.startsWith("\"") && valueRaw.endsWith("\"") ->
                    valueRaw.removeSurrounding("\"")
                else -> valueRaw
            }
            result[key] = parsed
        }
        return result
    }

    fun importResolvers(profile: ProfileEntity, resolversText: String) {
        viewModelScope.launch {
            profileRepository.updateProfile(profile.copy(resolvers = resolversText.trim()))
        }
    }

    private fun parseAdvanced(json: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun normalizeProtocol(value: String?, fallback: String): String {
        val normalized = value?.trim()?.uppercase()
        return when (normalized) {
            "SOCKS5", "TCP" -> normalized
            else -> fallback
        }
    }

    private fun normalizeResolverBalancingStrategy(value: String?, fallback: Int): Int {
        val parsed = value?.trim()?.toIntOrNull()
        if (parsed != null && parsed in 1..8) return parsed
        if (parsed == 0) return 2
        return if (fallback in 1..8) fallback else 2
    }

    private fun buildUpdatedProfile(profile: ProfileEntity, values: Map<String, String>): ProfileEntity {
        val mergedAdvanced = parseAdvanced(profile.advancedJson).toMutableMap()
        values.forEach { (key, value) ->
            if (key in ADVANCED_SETTING_KEYS) {
                mergedAdvanced[key] = value.trim()
            }
        }

        val mode = values["MODE"]?.trim().orEmpty()
        if (mode.isNotEmpty()) {
            mergedAdvanced["MODE"] = mode
        }

        return profile.copy(
            domains = domainsToJson(values["SCRIPT_IDS"], profile.domains),
            encryptionKey = values["AUTH_KEY"]?.trim().takeUnless { it.isNullOrBlank() }
                ?: profile.encryptionKey,
            listenPort = values["LISTEN_PORT"]?.toIntOrNull()
                ?.coerceIn(1, 65535) ?: profile.listenPort,
            logLevel = values["LOG_LEVEL"]?.trim().takeUnless { it.isNullOrBlank() }
                ?: profile.logLevel,
            advancedJson = gson.toJson(mergedAdvanced)
        )
    }


    private fun domainsToJson(value: String?, fallbackJson: String): String {
        val domains = value
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        return if (domains.isEmpty()) fallbackJson else gson.toJson(domains)
    }

    companion object {
        val TOML_IMPORT_KEYS = setOf(
            "MODE",
            "SCRIPT_IDS",
            "AUTH_KEY",
            "GOOGLE_IP",
            "FRONT_DOMAIN",
            "LISTEN_HOST",
            "LISTEN_PORT",
            "SOCKS5_PORT",
            "LOG_LEVEL",
            "VERIFY_SSL",
            "HOSTS",
            "ENABLE_BATCHING",
            "UPSTREAM_SOCKS5",
            "PARALLEL_RELAY",
            "SNI_HOSTS"
        )

        val ADVANCED_SETTING_KEYS = setOf(
            "MODE",
            "GOOGLE_IP",
            "FRONT_DOMAIN",
            "LISTEN_HOST",
            "SOCKS5_PORT",
            "VERIFY_SSL",
            "HOSTS",
            "ENABLE_BATCHING",
            "UPSTREAM_SOCKS5",
            "PARALLEL_RELAY",
            "SNI_HOSTS"
        )
    }
}
