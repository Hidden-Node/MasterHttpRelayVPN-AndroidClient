package com.masterhttprelay.vpn.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
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
import com.masterhttprelay.vpn.ui.components.mdv.controls.MdvPrimaryActionButton
import com.masterhttprelay.vpn.ui.components.mdv.controls.MdvBackTopAppBar
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
    val options: List<String> = emptyList()
)

private val configFields = listOf(
    SettingField("Core", "MODE", "MODE", "Rust core mode", type = FieldType.OPTION, options = listOf("apps_script")),
    SettingField("Core", "SCRIPT_IDS", "SCRIPT_IDS", "Apps Script deployment IDs (comma-separated)"),
    SettingField("Core", "AUTH_KEY", "AUTH_KEY", "Shared secret used by your Apps Script"),
    SettingField("Network", "GOOGLE_IP", "GOOGLE_IP", "Google front IP"),
    SettingField("Network", "FRONT_DOMAIN", "FRONT_DOMAIN", "Front domain used in SNI"),
    SettingField("Network", "LISTEN_HOST", "LISTEN_HOST", "Local bind host (usually 127.0.0.1)"),
    SettingField("Network", "LISTEN_PORT", "LISTEN_PORT", "HTTP proxy port", keyboardType = KeyboardType.Number),
    SettingField("Network", "SOCKS5_PORT", "SOCKS5_PORT", "SOCKS5 proxy port", keyboardType = KeyboardType.Number),
    SettingField("Network", "VERIFY_SSL", "VERIFY_SSL", "Verify upstream TLS certificates", type = FieldType.BOOL),
    SettingField("Routing", "HOSTS", "HOSTS", "Direct hosts map as host=target,host2=target2"),
    SettingField("Routing", "SNI_HOSTS", "SNI_HOSTS", "SNI rotation pool (comma-separated)"),
    SettingField("Routing", "ENABLE_BATCHING", "ENABLE_BATCHING", "Enable batch relay requests", type = FieldType.BOOL),
    SettingField("Routing", "UPSTREAM_SOCKS5", "UPSTREAM_SOCKS5", "Optional upstream SOCKS5 (host:port)"),
    SettingField("Routing", "PARALLEL_RELAY", "PARALLEL_RELAY", "Parallel relay factor", keyboardType = KeyboardType.Number),
    SettingField(
        "Logging",
        "LOG_LEVEL",
        "LOG_LEVEL",
        "Rust core log level",
        type = FieldType.OPTION,
        options = listOf("debug", "info", "warn", "error")
    )
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

    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExportContent ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            writeTextToUri(context, uri, content)
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_toml_exported_msg)) }
        }
        pendingExportContent = null
    }

    val importTomlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = readTextFromUri(context, uri)
            val updated = viewModel.importTomlValues(text, fieldsState.toMap())
            fieldsState.clear()
            fieldsState.putAll(updated)
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_toml_imported_msg)) }
        }
    }

    val importResolversLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val selected = profile
        if (uri != null && selected != null) {
            val text = readTextFromUri(context, uri)
            viewModel.importResolvers(selected, text)
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_resolvers_imported_msg)) }
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
                        Spacer(modifier = Modifier.height(MdvSpace.S1))
                        Row(horizontalArrangement = Arrangement.spacedBy(MdvSpace.S2)) {
                            MdvPrimaryActionButton(
                                text = stringResource(R.string.action_import_toml),
                                onClick = {
                                    importTomlLauncher.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/plain",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                icon = Icons.Filled.UploadFile
                            )
                            MdvPrimaryActionButton(
                                text = stringResource(R.string.action_export_toml),
                                onClick = {
                                    pendingExportContent = viewModel.exportConfigToml(selected, fieldsState.toMap())
                                    exportLauncher.launch("${selected.name}_config.json")
                                },
                                icon = Icons.Filled.Download
                            )
                        }
                        Spacer(modifier = Modifier.height(MdvSpace.S1))
                        MdvPrimaryActionButton(
                            text = stringResource(R.string.action_import_resolvers),
                            onClick = { importResolversLauncher.launch(arrayOf("text/*")) },
                            icon = Icons.Filled.UploadFile
                        )
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
                                        onChange(option.substringBefore(" - ").trim())
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
                        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType)
                    )
                }
            }
        }
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    return runCatching {
        val stream = context.contentResolver.openInputStream(uri)
        stream?.bufferedReader()?.use { it.readText() } ?: ""
    }.getOrDefault("")
}

private fun writeTextToUri(context: Context, uri: Uri, content: String) {
    runCatching {
        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use {
            it.write(content)
        }
    }
}

private fun defaultValuesFor(profile: ProfileEntity): Map<String, String> {
    val advanced = parseAdvanced(profile.advancedJson)
    fun adv(key: String, fallback: String): String {
        return advanced[key]?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
    }

    return buildMap {
        put("MODE", adv("MODE", "apps_script"))
        put("SCRIPT_IDS", parseDomains(profile.domains).joinToString(", "))
        put("AUTH_KEY", profile.encryptionKey)
        put("GOOGLE_IP", adv("GOOGLE_IP", "216.239.38.120"))
        put("FRONT_DOMAIN", adv("FRONT_DOMAIN", "www.google.com"))
        put("LISTEN_HOST", adv("LISTEN_HOST", "127.0.0.1"))
        put("LISTEN_PORT", profile.listenPort.toString())
        put("SOCKS5_PORT", adv("SOCKS5_PORT", (profile.listenPort + 1).toString()))
        put("VERIFY_SSL", adv("VERIFY_SSL", "true"))
        put("HOSTS", adv("HOSTS", ""))
        put("SNI_HOSTS", adv("SNI_HOSTS", ""))
        put("ENABLE_BATCHING", adv("ENABLE_BATCHING", "false"))
        put("UPSTREAM_SOCKS5", adv("UPSTREAM_SOCKS5", ""))
        put("PARALLEL_RELAY", adv("PARALLEL_RELAY", "0"))
        put("LOG_LEVEL", profile.logLevel.lowercase())
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
        Gson().fromJson<List<String>>(json, type) ?: emptyList()
    } catch (_: Exception) {
        listOf(json.trim().removeSurrounding("\"")).filter { it.isNotBlank() }
    }
}
