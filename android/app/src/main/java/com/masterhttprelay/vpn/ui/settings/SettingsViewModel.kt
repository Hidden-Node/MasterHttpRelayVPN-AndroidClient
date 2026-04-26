package com.masterhttprelay.vpn.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.masterhttprelay.vpn.data.local.ProfileEntity
import com.masterhttprelay.vpn.data.repository.ProfileRepository
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

    private fun buildUpdatedProfile(profile: ProfileEntity, values: Map<String, String>): ProfileEntity {
        val scriptIds = parseScriptIds(values["script_id"]).ifEmpty { parseDomains(profile.domains) }
        val authKey = values["auth_key"]?.trim().orEmpty().ifBlank { profile.encryptionKey }

        val advanced = mapOf(
            "mode" to (values["mode"]?.trim().takeUnless { it.isNullOrBlank() } ?: "apps_script"),
            "google_ip" to (values["google_ip"]?.trim().takeUnless { it.isNullOrBlank() } ?: "216.239.38.120"),
            "front_domain" to (values["front_domain"]?.trim().takeUnless { it.isNullOrBlank() } ?: "www.google.com"),
            "listen_host" to (values["listen_host"]?.trim().takeUnless { it.isNullOrBlank() } ?: "127.0.0.1"),
            "socks5_enabled" to normalizeBool(values["socks5_enabled"], true),
            "socks5_port" to normalizeInt(values["socks5_port"], 1080),
            "verify_ssl" to normalizeBool(values["verify_ssl"], true),
            "lan_sharing" to normalizeBool(values["lan_sharing"], true),
            "relay_timeout" to normalizeInt(values["relay_timeout"], 25),
            "tls_connect_timeout" to normalizeInt(values["tls_connect_timeout"], 15),
            "tcp_connect_timeout" to normalizeInt(values["tcp_connect_timeout"], 10),
            "max_response_body_bytes" to normalizeLong(values["max_response_body_bytes"], 209715200),
            "parallel_relay" to normalizeInt(values["parallel_relay"], 1),
            "chunked_download_extensions" to normalizeMultiline(
                values["chunked_download_extensions"],
                defaultLines = listOf(
                    ".bin",
                    ".zip",
                    ".tar",
                    ".gz",
                    ".bz2",
                    ".xz",
                    ".7z",
                    ".rar",
                    ".exe",
                    ".msi",
                    ".dmg",
                    ".deb",
                    ".rpm",
                    ".apk",
                    ".iso",
                    ".img",
                    ".mp4",
                    ".mkv",
                    ".avi",
                    ".mov",
                    ".webm",
                    ".mp3",
                    ".flac",
                    ".wav",
                    ".aac",
                    ".pdf",
                    ".doc",
                    ".docx",
                    ".ppt",
                    ".pptx",
                    ".wasm"
                )
            ),
            "chunked_download_min_size" to normalizeLong(values["chunked_download_min_size"], 5242880),
            "chunked_download_chunk_size" to normalizeLong(values["chunked_download_chunk_size"], 524288),
            "chunked_download_max_parallel" to normalizeInt(values["chunked_download_max_parallel"], 8),
            "chunked_download_max_chunks" to normalizeInt(values["chunked_download_max_chunks"], 256),
            "block_hosts" to normalizeMultiline(values["block_hosts"]),
            "bypass_hosts" to normalizeMultiline(
                values["bypass_hosts"],
                defaultLines = listOf("localhost", ".local", ".lan", ".home.arpa")
            ),
            "direct_google_exclude" to normalizeMultiline(
                values["direct_google_exclude"],
                defaultLines = listOf(
                    "gemini.google.com",
                    "aistudio.google.com",
                    "notebooklm.google.com",
                    "labs.google.com",
                    "meet.google.com",
                    "accounts.google.com",
                    "ogs.google.com",
                    "mail.google.com",
                    "calendar.google.com",
                    "drive.google.com",
                    "docs.google.com",
                    "chat.google.com",
                    "maps.google.com",
                    "play.google.com",
                    "translate.google.com",
                    "assistant.google.com",
                    "lens.google.com"
                )
            ),
            "direct_google_allow" to normalizeMultiline(
                values["direct_google_allow"],
                defaultLines = listOf("www.google.com", "safebrowsing.google.com")
            ),
            "youtube_via_relay" to normalizeBool(values["youtube_via_relay"], false),
            "hosts" to normalizeMultiline(values["hosts"])
        )

        return profile.copy(
            domains = gson.toJson(scriptIds),
            encryptionKey = authKey,
            listenPort = values["listen_port"]?.trim()?.toIntOrNull()?.coerceIn(1, 65535) ?: profile.listenPort,
            logLevel = values["log_level"]?.trim().takeUnless { it.isNullOrBlank() }?.uppercase() ?: profile.logLevel,
            advancedJson = gson.toJson(advanced)
        )
    }

    private fun parseDomains(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        } catch (_: Exception) {
            json.lineSequence().map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }.toList()
        }
    }

    private fun parseScriptIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .lineSequence()
            .flatMap { line -> line.split(',').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun normalizeBool(raw: String?, defaultValue: Boolean): String {
        return raw?.trim()?.toBooleanStrictOrNull()?.toString() ?: defaultValue.toString()
    }

    private fun normalizeInt(raw: String?, defaultValue: Int): String {
        return raw?.trim()?.toIntOrNull()?.toString() ?: defaultValue.toString()
    }

    private fun normalizeLong(raw: String?, defaultValue: Long): String {
        return raw?.trim()?.toLongOrNull()?.toString() ?: defaultValue.toString()
    }

    private fun normalizeMultiline(raw: String?, defaultLines: List<String> = emptyList()): String {
        val lines = raw
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultLines
        return lines.joinToString("\n")
    }
}
