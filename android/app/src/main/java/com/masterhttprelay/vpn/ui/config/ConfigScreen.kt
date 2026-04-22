package com.masterhttprelay.vpn.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.masterhttprelay.vpn.data.RustConfig
import androidx.compose.material3.ExperimentalMaterial3Api

@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    
    var scriptId by remember { mutableStateOf(config.scriptId) }
    var authKey by remember { mutableStateOf(config.authKey) }
    var googleIp by remember { mutableStateOf(config.googleIp) }
    var frontDomain by remember { mutableStateOf(config.frontDomain) }
    var listenPort by remember { mutableStateOf(config.listenPort.toString()) }
    var socks5Port by remember { mutableStateOf(config.socks5Port.toString()) }
    var logLevel by remember { mutableStateOf(config.logLevel) }
    var sniHosts by remember { mutableStateOf(config.sniHosts?.joinToString(", ") ?: "") }
    
    LaunchedEffect(config) {
        scriptId = config.scriptId
        authKey = config.authKey
        googleIp = config.googleIp
        frontDomain = config.frontDomain
        listenPort = config.listenPort.toString()
        socks5Port = config.socks5Port.toString()
        logLevel = config.logLevel
        sniHosts = config.sniHosts?.joinToString(", ") ?: ""
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Configuration",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = scriptId,
            onValueChange = { scriptId = it },
            label = { Text("Script ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = authKey,
            onValueChange = { authKey = it },
            label = { Text("Auth Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = googleIp,
            onValueChange = { googleIp = it },
            label = { Text("Google IP") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = frontDomain,
            onValueChange = { frontDomain = it },
            label = { Text("Front Domain") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = listenPort,
                onValueChange = { listenPort = it },
                label = { Text("HTTP Port") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            
            OutlinedTextField(
                value = socks5Port,
                onValueChange = { socks5Port = it },
                label = { Text("SOCKS5 Port") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        @OptIn(ExperimentalMaterial3Api::class)
        var expanded by remember { mutableStateOf(false) }
        val logLevels = listOf("debug", "info", "warn", "error")
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = logLevel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Log Level") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                logLevels.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level) },
                        onClick = {
                            logLevel = level
                            expanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = sniHosts,
            onValueChange = { sniHosts = it },
            label = { Text("SNI Hosts (comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val newConfig = RustConfig(
                    scriptId = scriptId,
                    authKey = authKey,
                    googleIp = googleIp,
                    frontDomain = frontDomain,
                    listenPort = listenPort.toIntOrNull() ?: 8085,
                    socks5Port = socks5Port.toIntOrNull() ?: 8086,
                    logLevel = logLevel,
                    sniHosts = if (sniHosts.isNotBlank()) {
                        sniHosts.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    } else null
                )
                viewModel.saveConfig(newConfig)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Configuration")
        }
        
        if (saveSuccess) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Configuration saved successfully!",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
