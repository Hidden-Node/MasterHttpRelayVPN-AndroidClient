package com.masterhttprelay.vpn.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.masterhttprelay.vpn.R
import com.masterhttprelay.vpn.data.local.ProfileEntity
import com.masterhttprelay.vpn.ui.components.mdv.cards.MdvSectionCard
import com.masterhttprelay.vpn.ui.components.mdv.cards.MdvSettingFieldCard
import com.masterhttprelay.vpn.ui.components.mdv.controls.MdvBackTopAppBar
import com.masterhttprelay.vpn.ui.components.mdv.controls.MdvPrimaryActionButton
import com.masterhttprelay.vpn.ui.components.mdv.controls.MdvTopAppBar
import com.masterhttprelay.vpn.ui.theme.MdvColor
import com.masterhttprelay.vpn.ui.theme.MdvSpace
import kotlinx.coroutines.launch

private enum class FieldType { TEXT, BOOL, OPTION }

private data class SettingField(
    val section: String,
    val key: String,
    val label: String,
    val helper: String,
    val type: FieldType = FieldType.TEXT,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val options: List<String> = emptyList(),
    val minLines: Int = 1
)

private val configFields = listOf(
    SettingField("Core", "mode", "mode", "apps_script", type = FieldType.OPTION, options = listOf("apps_script")),
    SettingField("Core", "script_id", "script_id", "One Script ID per line", minLines = 3),
    SettingField("Core", "auth_key", "auth_key", "Required", minLines = 1),
    SettingField("Network", "google_ip", "google_ip", "Default: 216.239.38.120"),
    SettingField("Network", "front_domain", "front_domain", "Default: www.google.com"),
    SettingField("Network", "listen_host", "listen_host", "Default: 127.0.0.1"),
    SettingField("Network", "socks5_enabled", "socks5_enabled", "true/false", type = FieldType.BOOL),
    SettingField("Network", "listen_port", "listen_port", "Default: 8085", keyboardType = KeyboardType.Number),
    SettingField("Network", "socks5_port", "socks5_port", "Default: 1080", keyboardType = KeyboardType.Number),
    SettingField("Network", "log_level", "log_level", "INFO", type = FieldType.OPTION, options = listOf("INFO", "DEBUG", "WARNING", "ERROR")),
    SettingField("Network", "verify_ssl", "verify_ssl", "true/false", type = FieldType.BOOL),
    SettingField("Network", "lan_sharing", "lan_sharing", "true/false", type = FieldType.BOOL),
    SettingField("Timeouts", "relay_timeout", "relay_timeout", "Default: 25", keyboardType = KeyboardType.Number),
    SettingField("Timeouts", "tls_connect_timeout", "tls_connect_timeout", "Default: 15", keyboardType = KeyboardType.Number),
    SettingField("Timeouts", "tcp_connect_timeout", "tcp_connect_timeout", "Default: 10", keyboardType = KeyboardType.Number),
    SettingField("Limits", "max_response_body_bytes", "max_response_body_bytes", "Default: 209715200", keyboardType = KeyboardType.Number),
    SettingField("Routing", "parallel_relay", "parallel_relay", "Default: 1", keyboardType = KeyboardType.Number),
    SettingField("Downloads", "chunked_download_extensions", "chunked_download_extensions", "One extension per line", minLines = 8),
    SettingField("Downloads", "chunked_download_min_size", "chunked_download_min_size", "Default: 5242880", keyboardType = KeyboardType.Number),
    SettingField("Downloads", "chunked_download_chunk_size", "chunked_download_chunk_size", "Default: 524288", keyboardType = KeyboardType.Number),
    SettingField("Downloads", "chunked_download_max_parallel", "chunked_download_max_parallel", "Default: 8", keyboardType = KeyboardType.Number),
    SettingField("Downloads", "chunked_download_max_chunks", "chunked_download_max_chunks", "Default: 256", keyboardType = KeyboardType.Number),
    SettingField("Routing", "block_hosts", "block_hosts", "One host per line", minLines = 3),
    SettingField("Routing", "bypass_hosts", "bypass_hosts", "One host per line", minLines = 4),
    SettingField("Routing", "direct_google_exclude", "direct_google_exclude", "One host per line", minLines = 6),
    SettingField("Routing", "direct_google_allow", "direct_google_allow", "One host per line", minLines = 2),
    SettingField("Routing", "youtube_via_relay", "youtube_via_relay", "true/false", type = FieldType.BOOL),
    SettingField("Routing", "hosts", "hosts", "host=ip per line", minLines = 3)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val profile by viewModel.selectedProfile.collectAsState()
    val fieldsState = remember { mutableStateMapOf<String, String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = remember { configFields.groupBy { it.section } }
    val sectionOrder = remember { configFields.map { it.section }.distinct() }
    val sectionExpanded = remember {
        mutableStateMapOf<String, Boolean>().apply {
            sectionOrder.forEach { put(it, it == "Core") }
        }
    }

    LaunchedEffect(profile?.id) {
        fieldsState.clear()
        profile?.let { fieldsState.putAll(defaultValuesFor(it)) }
    }

    Scaffold(
        containerColor = MdvColor.Background,
        topBar = {
            val topActions: @Composable RowScope.() -> Unit = {
                val selected = profile
                if (selected != null) {
                    IconButton(
                        onClick = {
                            if (fieldsState["script_id"].isNullOrBlank() || fieldsState["auth_key"].isNullOrBlank()) {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.profile_required_keys_msg)) }
                                return@IconButton
                            }
                            viewModel.saveSettings(selected, fieldsState.toMap())
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_saved_msg)) }
                        }
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.action_save))
                    }
                }
            }
            if (onBack != null) {
                MdvBackTopAppBar(
                    title = stringResource(R.string.profile_settings_title),
                    onBack = onBack,
                    actions = topActions
                )
            } else {
                MdvTopAppBar(
                    title = stringResource(R.string.profile_settings_title),
                    actions = topActions
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val maxContentWidth = when {
                maxWidth >= 1200.dp -> 980.dp
                maxWidth >= 840.dp -> 840.dp
                else -> Dp.Unspecified
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val selected = profile
                if (selected == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                            .padding(MdvSpace.S6),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.settings_no_profile_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(MdvSpace.S2))
                        Text(
                            stringResource(R.string.settings_no_profile_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MdvColor.OnSurfaceVariant
                        )
                    }
                    return@Box
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth),
                    contentPadding = PaddingValues(MdvSpace.S4),
                    verticalArrangement = Arrangement.spacedBy(MdvSpace.S3),
                    state = listState
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_editing_profile, selected.name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MdvColor.OnSurface
                        )
                        Spacer(modifier = Modifier.height(MdvSpace.S2))
                    }

                    items(sectionOrder, key = { "section_$it" }) { section ->
                        val expanded = sectionExpanded[section] ?: false
                        MdvSectionCard(
                            title = section,
                            expanded = expanded,
                            onToggle = { sectionExpanded[section] = !expanded }
                        )
                        if (!expanded) return@items

                        Spacer(modifier = Modifier.height(MdvSpace.S1))
                        sections[section].orEmpty().forEach { field ->
                            ConfigFieldCard(
                                field = field,
                                value = fieldsState[field.key].orEmpty(),
                                onChange = { fieldsState[field.key] = it }
                            )
                            Spacer(modifier = Modifier.height(MdvSpace.S2))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(MdvSpace.S2))
                        MdvPrimaryActionButton(
                            text = stringResource(R.string.action_save_settings),
                            onClick = {
                                if (fieldsState["script_id"].isNullOrBlank() || fieldsState["auth_key"].isNullOrBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.profile_required_keys_msg)) }
                                    return@MdvPrimaryActionButton
                                }
                                viewModel.saveSettings(selected, fieldsState.toMap())
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_saved_msg)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Filled.Save
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigFieldCard(
    field: SettingField,
    value: String,
    onChange: (String) -> Unit
) {
    MdvSettingFieldCard {
        Column {
            when (field.type) {
                FieldType.BOOL -> {
                    val checked = value.equals("true", ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(field.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                field.helper,
                                style = MaterialTheme.typography.bodySmall,
                                color = MdvColor.OnSurfaceVariant
                            )
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { onChange(if (it) "true" else "false") }
                        )
                    }
                }

                FieldType.OPTION -> {
                    var expanded by remember(field.key) { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(field.label) },
                            supportingText = { Text(field.helper) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            field.options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        onChange(option)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                FieldType.TEXT -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        label = { Text(field.label) },
                        supportingText = { Text(field.helper) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
                        minLines = field.minLines
                    )
                }
            }
        }
    }
}

private fun defaultValuesFor(profile: ProfileEntity): Map<String, String> {
    val advanced = parseAdvanced(profile.advancedJson)
    fun adv(key: String, fallback: String): String = advanced[key]?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback

    return buildMap {
        put("mode", adv("mode", "apps_script"))
        put("script_id", parseDomains(profile.domains).joinToString("\n"))
        put("auth_key", profile.encryptionKey)
        put("google_ip", adv("google_ip", "216.239.38.120"))
        put("front_domain", adv("front_domain", "www.google.com"))
        put("listen_host", adv("listen_host", "127.0.0.1"))
        put("socks5_enabled", adv("socks5_enabled", "true"))
        put("listen_port", profile.listenPort.toString())
        put("socks5_port", adv("socks5_port", "1080"))
        put("log_level", profile.logLevel.ifBlank { "INFO" }.uppercase())
        put("verify_ssl", adv("verify_ssl", "true"))
        put("lan_sharing", adv("lan_sharing", "true"))
        put("relay_timeout", adv("relay_timeout", "25"))
        put("tls_connect_timeout", adv("tls_connect_timeout", "15"))
        put("tcp_connect_timeout", adv("tcp_connect_timeout", "10"))
        put("max_response_body_bytes", adv("max_response_body_bytes", "209715200"))
        put("parallel_relay", adv("parallel_relay", "1"))
        put(
            "chunked_download_extensions",
            adv(
                "chunked_download_extensions",
                ".bin\n.zip\n.tar\n.gz\n.bz2\n.xz\n.7z\n.rar\n.exe\n.msi\n.dmg\n.deb\n.rpm\n.apk\n.iso\n.img\n.mp4\n.mkv\n.avi\n.mov\n.webm\n.mp3\n.flac\n.wav\n.aac\n.pdf\n.doc\n.docx\n.ppt\n.pptx\n.wasm"
            )
        )
        put("chunked_download_min_size", adv("chunked_download_min_size", "5242880"))
        put("chunked_download_chunk_size", adv("chunked_download_chunk_size", "524288"))
        put("chunked_download_max_parallel", adv("chunked_download_max_parallel", "8"))
        put("chunked_download_max_chunks", adv("chunked_download_max_chunks", "256"))
        put("block_hosts", adv("block_hosts", ""))
        put("bypass_hosts", adv("bypass_hosts", "localhost\n.local\n.lan\n.home.arpa"))
        put(
            "direct_google_exclude",
            adv(
                "direct_google_exclude",
                "gemini.google.com\naistudio.google.com\nnotebooklm.google.com\nlabs.google.com\nmeet.google.com\naccounts.google.com\nogs.google.com\nmail.google.com\ncalendar.google.com\ndrive.google.com\ndocs.google.com\nchat.google.com\nmaps.google.com\nplay.google.com\ntranslate.google.com\nassistant.google.com\nlens.google.com"
            )
        )
        put("direct_google_allow", adv("direct_google_allow", "www.google.com\nsafebrowsing.google.com"))
        put("youtube_via_relay", adv("youtube_via_relay", "false"))
        put("hosts", adv("hosts", ""))
    }
}

private fun parseAdvanced(json: String): Map<String, String> {
    return try {
        val type = object : TypeToken<Map<String, String>>() {}.type
        Gson().fromJson<Map<String, String>>(json, type) ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun parseDomains(json: String): List<String> {
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson<List<String>>(json, type)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    } catch (_: Exception) {
        json.lineSequence().map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }.toList()
    }
}
