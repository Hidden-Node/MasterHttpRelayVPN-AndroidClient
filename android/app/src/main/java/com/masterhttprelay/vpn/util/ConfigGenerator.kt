package com.masterhttprelay.vpn.util

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.masterhttprelay.vpn.data.local.ProfileEntity

object ConfigGenerator {
    private val gson = Gson()

    // Kept for compatibility with existing settings export/import entrypoints.
    fun generateConfig(
        profile: ProfileEntity,
        listenPort: Int,
        listenIpOverride: String? = null,
        protocolOverride: String? = null,
        localDnsEnabledOverride: Boolean? = null,
        localDnsIpOverride: String? = null,
        localDnsPortOverride: Int? = null
    ): String {
        return generateRustConfig(profile, httpPort = listenPort, socksPort = listenPort + 1, listenHost = listenIpOverride)
    }

    fun generateRustConfig(
        profile: ProfileEntity,
        httpPort: Int,
        socksPort: Int,
        listenHost: String? = null
    ): String {
        val advanced = parseAdvanced(profile.advancedJson)
        val scriptIds = parseDomains(profile.domains).ifEmpty {
            advanced["SCRIPT_IDS"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: advanced["SCRIPT_ID"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

        val root = JsonObject().apply {
            addProperty("mode", advanced["MODE"] ?: "apps_script")
            addProperty("google_ip", advanced["GOOGLE_IP"] ?: "216.239.38.120")
            addProperty("front_domain", advanced["FRONT_DOMAIN"] ?: "www.google.com")
            addProperty("auth_key", profile.encryptionKey)
            addProperty("listen_host", listenHost ?: advanced["LISTEN_HOST"] ?: "127.0.0.1")
            addProperty("listen_port", httpPort)
            addProperty("socks5_port", socksPort)
            addProperty("log_level", profile.logLevel.lowercase())
            addProperty("verify_ssl", advanced["VERIFY_SSL"]?.toBooleanStrictOrNull() ?: true)

            if (scriptIds.size <= 1) {
                addProperty("script_id", scriptIds.firstOrNull().orEmpty())
            } else {
                val arr = JsonArray()
                scriptIds.forEach { arr.add(it) }
                add("script_id", arr)
            }

            val hostsObj = JsonObject()
            parseHosts(advanced["HOSTS"]).forEach { (k, v) -> hostsObj.addProperty(k, v) }
            add("hosts", hostsObj)

            val sni = advanced["SNI_HOSTS"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            if (sni.isNotEmpty()) {
                val arr = JsonArray()
                sni.forEach { arr.add(it) }
                add("sni_hosts", arr)
            }

            advanced["ENABLE_BATCHING"]?.toBooleanStrictOrNull()?.let { addProperty("enable_batching", it) }
            advanced["UPSTREAM_SOCKS5"]?.takeIf { it.isNotBlank() }?.let { addProperty("upstream_socks5", it) }
            advanced["PARALLEL_RELAY"]?.toIntOrNull()?.let { addProperty("parallel_relay", it) }
        }

        return gson.toJson(root)
    }

    fun generateResolvers(profile: ProfileEntity): String {
        return profile.resolvers.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun parseDomains(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        } catch (_: Exception) {
            json.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
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

    private fun parseHosts(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { token ->
                val parts = token.split("=", limit = 2)
                if (parts.size != 2) null else parts[0].trim() to parts[1].trim()
            }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            .toMap()
    }
}
