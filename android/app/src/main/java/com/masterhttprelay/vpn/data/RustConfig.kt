package com.masterhttprelay.vpn.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Data class matching Rust's config.json schema.
 * Must stay in sync with Rust/src/config.rs
 */
data class RustConfig(
    @SerializedName("mode")
    val mode: String = "apps_script",
    
    @SerializedName("google_ip")
    val googleIp: String = "216.239.38.120",
    
    @SerializedName("front_domain")
    val frontDomain: String = "www.google.com",
    
    @SerializedName("script_id")
    val scriptId: String = "",
    
    @SerializedName("auth_key")
    val authKey: String = "",
    
    @SerializedName("listen_host")
    val listenHost: String = "127.0.0.1",
    
    @SerializedName("listen_port")
    val listenPort: Int = 8085,
    
    @SerializedName("socks5_port")
    val socks5Port: Int = 8086,
    
    @SerializedName("log_level")
    val logLevel: String = "info",
    
    @SerializedName("verify_ssl")
    val verifySsl: Boolean = true,
    
    @SerializedName("hosts")
    val hosts: Map<String, String> = emptyMap(),
    
    @SerializedName("enable_batching")
    val enableBatching: Boolean = false,
    
    @SerializedName("upstream_socks5")
    val upstreamSocks5: String? = null,
    
    @SerializedName("parallel_relay")
    val parallelRelay: Int = 0,
    
    @SerializedName("sni_hosts")
    val sniHosts: List<String>? = null
) {
    fun toJson(): String = Gson().toJson(this)
    
    fun isValid(): Boolean {
        return scriptId.isNotBlank() && authKey.isNotBlank()
    }
    
    companion object {
        fun fromJson(json: String): RustConfig? {
            return try {
                Gson().fromJson(json, RustConfig::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
