package com.masterhttprelay.vpn.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.masterhttprelay.vpn.util.VpnStateManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val logs by VpnStateManager.logs.collectAsState()
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                actions = {
                    IconButton(onClick = { VpnStateManager.clearLogs() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "No logs yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(logs) { logEntry ->
                    LogEntryItem(logEntry)
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(logEntry: VpnStateManager.LogEntry) {
    val backgroundColor = when (logEntry.level) {
        VpnStateManager.LogLevel.ERROR -> Color(0xFFFFEBEE)
        VpnStateManager.LogLevel.WARN -> Color(0xFFFFF3E0)
        VpnStateManager.LogLevel.INFO -> Color(0xFFE3F2FD)
        VpnStateManager.LogLevel.DEBUG -> Color(0xFFF5F5F5)
    }
    
    val textColor = when (logEntry.level) {
        VpnStateManager.LogLevel.ERROR -> Color(0xFFC62828)
        VpnStateManager.LogLevel.WARN -> Color(0xFFEF6C00)
        VpnStateManager.LogLevel.INFO -> Color(0xFF1565C0)
        VpnStateManager.LogLevel.DEBUG -> Color(0xFF616161)
    }
    
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timestamp = dateFormat.format(Date(logEntry.timestamp))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = logEntry.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = logEntry.message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
