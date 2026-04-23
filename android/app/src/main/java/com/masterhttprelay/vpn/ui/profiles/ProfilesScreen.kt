package com.masterhttprelay.vpn.ui.profiles

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.masterhttprelay.vpn.R
import com.masterhttprelay.vpn.data.local.ProfileEntity
import com.masterhttprelay.vpn.ui.components.mdv.controls.MdvBackTopAppBar
import com.masterhttprelay.vpn.ui.theme.ConnectedGreen
import com.masterhttprelay.vpn.ui.theme.MdvColor
import com.masterhttprelay.vpn.ui.theme.MdvSpace
import kotlinx.coroutines.launch

private data class ImportedProfileDraft(
    val profile: ProfileEntity,
    val scriptIdsText: String
)

private val gson = Gson()

@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenSettings: (Long) -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var importedDraft by remember { mutableStateOf<ImportedProfileDraft?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = readTextFromUri(context, uri)
        val draft = parseProfileConfigJsonForImport(
            fileName = readDisplayName(context, uri) ?: context.getString(R.string.profiles_imported_profile_default),
            rawContent = text
        )
        if (draft == null) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.profiles_invalid_config_json_msg)) }
            return@rememberLauncherForActivityResult
        }
        importedDraft = draft
        editingProfile = null
        showEditor = true
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.profiles_config_json_imported_msg)) }
    }

    Scaffold(
        containerColor = MdvColor.Background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            MdvBackTopAppBar(
                title = stringResource(R.string.title_profiles),
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        editingProfile = null
                        importedDraft = null
                        showEditor = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profiles_add))
                    }
                }
            )
        }
    ) { padding ->
        if (showEditor) {
            ProfileEditorDialog(
                profile = editingProfile,
                importedDraft = importedDraft,
                onImportConfigJson = {
                    importConfigLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                    )
                },
                onSave = { profile ->
                    if (editingProfile != null) viewModel.updateProfile(profile) else viewModel.addProfile(profile)
                    showEditor = false
                    editingProfile = null
                    importedDraft = null
                },
                onDismiss = {
                    showEditor = false
                    editingProfile = null
                    importedDraft = null
                }
            )
        }

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MdvColor.OnSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.profiles_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MdvColor.OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(onClick = {
                        editingProfile = null
                        importedDraft = null
                        showEditor = true
                    }) {
                        Text(stringResource(R.string.profiles_create))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(MdvSpace.S4),
                verticalArrangement = Arrangement.spacedBy(MdvSpace.S2)
            ) {
                items(profiles) { profile ->
                    ProfileCard(
                        profile = profile,
                        onSelect = { viewModel.selectProfile(profile.id) },
                        onSettings = { onOpenSettings(profile.id) },
                        onEdit = {
                            editingProfile = profile
                            showEditor = true
                        },
                        onDelete = { viewModel.deleteProfile(profile) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileCard(
    profile: ProfileEntity,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isSelected) MdvColor.PrimaryContainer.copy(alpha = 0.16f) else MdvColor.SurfaceHigh
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profile.isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.profiles_selected),
                    tint = ConnectedGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = parseScriptIdsText(profile.domains),
                    style = MaterialTheme.typography.bodySmall,
                    color = MdvColor.OnSurfaceVariant,
                    maxLines = 2
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profiles_edit), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.profiles_settings), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.profiles_delete), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    profile: ProfileEntity?,
    importedDraft: ImportedProfileDraft?,
    onImportConfigJson: () -> Unit,
    onSave: (ProfileEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var scriptIdsText by remember { mutableStateOf(parseScriptIdsText(profile?.domains.orEmpty())) }
    var authKey by remember { mutableStateOf(profile?.encryptionKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.id) {
        if (profile != null) {
            name = profile.name
            scriptIdsText = parseScriptIdsText(profile.domains)
            authKey = profile.encryptionKey
        }
    }

    LaunchedEffect(importedDraft) {
        val importedProfile = importedDraft?.profile ?: return@LaunchedEffect
        if (name.isBlank()) name = importedProfile.name
        scriptIdsText = importedDraft.scriptIdsText
        authKey = importedProfile.encryptionKey
    }

    val hasScriptIds = parseScriptIdsLines(scriptIdsText).isNotEmpty()
    val hasAuthKey = authKey.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile != null) stringResource(R.string.profiles_dialog_edit_title) else stringResource(R.string.profiles_dialog_new_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profiles_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (profile == null) {
                    OutlinedButton(
                        onClick = onImportConfigJson,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_import_config_json))
                    }
                }

                OutlinedTextField(
                    value = scriptIdsText,
                    onValueChange = { scriptIdsText = it },
                    label = { Text(stringResource(R.string.profiles_script_ids_label)) },
                    supportingText = { Text(stringResource(R.string.profiles_script_ids_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedTextField(
                    value = authKey,
                    onValueChange = { authKey = it },
                    label = { Text(stringResource(R.string.profiles_encryption_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) stringResource(R.string.profiles_hide_sensitive) else stringResource(R.string.profiles_show_sensitive)
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val base = profile ?: importedDraft?.profile ?: ProfileEntity(name = "", domains = "")
                    val scriptIds = parseScriptIdsLines(scriptIdsText)
                    onSave(
                        base.copy(
                            name = name.trim().ifEmpty { "Profile" },
                            domains = gson.toJson(scriptIds),
                            encryptionKey = authKey.trim()
                        )
                    )
                },
                enabled = name.isNotBlank() && hasScriptIds && hasAuthKey
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
}

private fun readDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex < 0 || !cursor.moveToFirst()) return@use null
        cursor.getString(nameIndex)
    }?.substringBeforeLast(".")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun parseProfileConfigJsonForImport(fileName: String, rawContent: String): ImportedProfileDraft? {
    val root = runCatching { JsonParser.parseString(rawContent) }.getOrNull() ?: return null
    if (!root.isJsonObject) return null
    val obj = root.asJsonObject

    val authKey = obj.get("auth_key")?.asString?.trim().orEmpty()
    if (authKey.isBlank() || authKey == "CHANGE_ME_TO_A_STRONG_SECRET") return null

    val scriptIds = parseScriptIdsElement(obj.get("script_id"))
    if (scriptIds.isEmpty() || scriptIds.any { it == "YOUR_APPS_SCRIPT_DEPLOYMENT_ID" }) return null

    val advanced = JsonObject().apply {
        addProperty("mode", obj.get("mode")?.asString ?: "apps_script")
        addProperty("google_ip", obj.get("google_ip")?.asString ?: "216.239.38.120")
        addProperty("front_domain", obj.get("front_domain")?.asString ?: "www.google.com")
        addProperty("listen_host", obj.get("listen_host")?.asString ?: "127.0.0.1")
        addProperty("socks5_enabled", obj.get("socks5_enabled")?.asBoolean ?: true)
        addProperty("socks5_port", obj.get("socks5_port")?.asInt ?: 1080)
        addProperty("verify_ssl", obj.get("verify_ssl")?.asBoolean ?: true)
        addProperty("lan_sharing", obj.get("lan_sharing")?.asBoolean ?: true)
        addProperty("parallel_relay", obj.get("parallel_relay")?.asInt ?: 1)
        addProperty("block_hosts", jsonArrayToLines(obj.get("block_hosts")))
        addProperty("bypass_hosts", jsonArrayToLines(obj.get("bypass_hosts")))
        addProperty("direct_google_exclude", jsonArrayToLines(obj.get("direct_google_exclude")))
        addProperty("direct_google_allow", jsonArrayToLines(obj.get("direct_google_allow")))
        addProperty("hosts", jsonObjectToLines(obj.get("hosts")))
    }

    val importedProfile = ProfileEntity(
        name = fileName,
        domains = gson.toJson(scriptIds),
        encryptionMethod = 1,
        encryptionKey = authKey,
        protocolType = "SOCKS5",
        listenPort = obj.get("listen_port")?.asInt?.coerceIn(1, 65535) ?: 8085,
        packetDuplicationCount = 2,
        setupPacketDuplicationCount = 2,
        uploadCompression = 0,
        downloadCompression = 0,
        logLevel = obj.get("log_level")?.asString?.trim().takeUnless { it.isNullOrBlank() } ?: "INFO",
        advancedJson = gson.toJson(advanced)
    )

    return ImportedProfileDraft(
        profile = importedProfile,
        scriptIdsText = scriptIds.joinToString("\n")
    )
}

private fun parseScriptIdsElement(element: JsonElement?): List<String> {
    if (element == null || element.isJsonNull) return emptyList()
    return when {
        element.isJsonArray -> element.asJsonArray.mapNotNull { runCatching { it.asString.trim() }.getOrNull() }.filter { it.isNotEmpty() }
        element.isJsonPrimitive -> listOfNotNull(runCatching { element.asString.trim() }.getOrNull()?.takeIf { it.isNotEmpty() })
        else -> emptyList()
    }
}

private fun parseScriptIdsLines(raw: String): List<String> {
    return raw
        .lineSequence()
        .flatMap { it.split(',').asSequence() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
}

private fun parseScriptIdsText(domainsJson: String): String {
    return try {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        gson.fromJson<List<String>>(domainsJson, type)?.joinToString("\n") ?: ""
    } catch (_: Exception) {
        domainsJson
    }
}

private fun jsonArrayToLines(element: JsonElement?): String {
    if (element == null || !element.isJsonArray) return ""
    return element.asJsonArray.mapNotNull { runCatching { it.asString.trim() }.getOrNull() }.filter { it.isNotEmpty() }.joinToString("\n")
}

private fun jsonObjectToLines(element: JsonElement?): String {
    if (element == null || !element.isJsonObject) return ""
    return element.asJsonObject.entrySet().mapNotNull { (k, v) ->
        val value = runCatching { v.asString.trim() }.getOrNull().orEmpty()
        if (k.isBlank() || value.isBlank()) null else "$k=$value"
    }.joinToString("\n")
}
