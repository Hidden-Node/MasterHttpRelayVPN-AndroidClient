package com.masterhttprelay.vpn.util

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.masterhttprelay.vpn.data.local.ProfileEntity

object ConfigGenerator {
    private val gson = Gson()

    fun generateConfig(
        profile: ProfileEntity,
        listenPort: Int,
        listenIpOverride: String? = null,
        protocolOverride: String? = null,
        localDnsEnabledOverride: Boolean? = null,
        localDnsIpOverride: String? = null,
        localDnsPortOverride: Int? = null
    ): String {
        return generatePythonConfig(profile, listenPort = listenPort, listenHostOverride = listenIpOverride)
    }

    fun generateRustConfig(
        profile: ProfileEntity,
        httpPort: Int,
        socksPort: Int,
        listenHost: String? = null
    ): String {
        val merged = parseAdvanced(profile.advancedJson).toMutableMap()
        merged["socks5_port"] = socksPort.toString()
        val profileWithMerged = profile.copy(advancedJson = gson.toJson(merged))
        return generatePythonConfig(profileWithMerged, listenPort = httpPort, listenHostOverride = listenHost)
    }

    fun generatePythonConfig(
        profile: ProfileEntity,
        listenPort: Int,
        listenHostOverride: String? = null
    ): String {
        val advanced = parseAdvanced(profile.advancedJson)
        val scriptIds = parseDomains(profile.domains)

        val root = JsonObject().apply {
            addProperty("mode", advanced["mode"] ?: "apps_script")
            addProperty("google_ip", advanced["google_ip"] ?: "216.239.38.120")
            addProperty("front_domain", advanced["front_domain"] ?: "www.google.com")

            if (scriptIds.size <= 1) {
                addProperty("script_id", scriptIds.firstOrNull().orEmpty())
            } else {
                val arr = JsonArray()
                scriptIds.forEach { arr.add(it) }
                add("script_id", arr)
            }

            addProperty("auth_key", profile.encryptionKey)
            addProperty("listen_host", listenHostOverride ?: advanced["listen_host"] ?: "127.0.0.1")
            addProperty("socks5_enabled", (advanced["socks5_enabled"] ?: "true").toBooleanStrictOrNull() ?: true)
            addProperty("listen_port", listenPort)
            addProperty("socks5_port", (advanced["socks5_port"] ?: "1080").toIntOrNull() ?: 1080)
            addProperty("log_level", profile.logLevel.ifBlank { "INFO" }.uppercase())
            addProperty("verify_ssl", (advanced["verify_ssl"] ?: "true").toBooleanStrictOrNull() ?: true)
            addProperty("lan_sharing", (advanced["lan_sharing"] ?: "true").toBooleanStrictOrNull() ?: true)
            addProperty("relay_timeout", (advanced["relay_timeout"] ?: "25").toIntOrNull() ?: 25)
            addProperty("tls_connect_timeout", (advanced["tls_connect_timeout"] ?: "15").toIntOrNull() ?: 15)
            addProperty("tcp_connect_timeout", (advanced["tcp_connect_timeout"] ?: "10").toIntOrNull() ?: 10)
            addProperty("max_response_body_bytes", (advanced["max_response_body_bytes"] ?: "209715200").toLongOrNull() ?: 209715200L)
            addProperty("parallel_relay", (advanced["parallel_relay"] ?: "1").toIntOrNull() ?: 1)
            add(
                "chunked_download_extensions",
                parseArrayLines(
                    advanced["chunked_download_extensions"],
                    default = listOf(
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
                )
            )
            addProperty("chunked_download_min_size", (advanced["chunked_download_min_size"] ?: "5242880").toLongOrNull() ?: 5242880L)
            addProperty("chunked_download_chunk_size", (advanced["chunked_download_chunk_size"] ?: "524288").toLongOrNull() ?: 524288L)
            addProperty("chunked_download_max_parallel", (advanced["chunked_download_max_parallel"] ?: "8").toIntOrNull() ?: 8)
            addProperty("chunked_download_max_chunks", (advanced["chunked_download_max_chunks"] ?: "256").toIntOrNull() ?: 256)

            add("block_hosts", parseArrayLines(advanced["block_hosts"]))
            add(
                "bypass_hosts",
                parseArrayLines(advanced["bypass_hosts"], default = listOf("localhost", ".local", ".lan", ".home.arpa"))
            )
            add(
                "direct_google_exclude",
                parseArrayLines(
                    advanced["direct_google_exclude"],
                    default = listOf(
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
                )
            )
            add(
                "direct_google_allow",
                parseArrayLines(advanced["direct_google_allow"], default = listOf("www.google.com", "safebrowsing.google.com"))
            )
            addProperty("youtube_via_relay", (advanced["youtube_via_relay"] ?: "false").toBooleanStrictOrNull() ?: false)
            add("hosts", parseHostsObject(advanced["hosts"]))
        }

        return gson.toJson(root)
    }

    private fun parseDomains(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        } catch (_: Exception) {
            json.lineSequence().map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }.toList()
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

    private fun parseArrayLines(raw: String?, default: List<String> = emptyList()): JsonArray {
        val values = raw
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: default

        return JsonArray().apply { values.forEach { add(it) } }
    }

    private fun parseHostsObject(raw: String?): JsonObject {
        val obj = JsonObject()
        raw
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it.contains('=') }
            ?.forEach { line ->
                val parts = line.split('=', limit = 2)
                val host = parts[0].trim()
                val ip = parts[1].trim()
                if (host.isNotEmpty() && ip.isNotEmpty()) obj.addProperty(host, ip)
            }
        return obj
    }
}
