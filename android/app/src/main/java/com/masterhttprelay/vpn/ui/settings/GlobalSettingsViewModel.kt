package com.masterhttprelay.vpn.ui.settings

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masterhttprelay.vpn.bridge.PythonBridge
import com.masterhttprelay.vpn.util.GlobalSettings
import com.masterhttprelay.vpn.util.GlobalSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GlobalSettingsViewModel(app: Application) : AndroidViewModel(app) {
    data class AppEntry(
        val packageName: String,
        val label: String,
        val firstInstallTime: Long
    )

    val settings: StateFlow<GlobalSettings> = GlobalSettingsStore.observe(app.applicationContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalSettings())

    private val _installedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val installedApps: StateFlow<List<AppEntry>> = _installedApps

    init {
        loadInstalledApps()
    }

    fun save(settings: GlobalSettings) {
        viewModelScope.launch {
            GlobalSettingsStore.save(getApplication(), settings)
        }
    }

    suspend fun exportCaCertToDownloads(): Result<String> = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val caPath = PythonBridge.ensureCaCert(app)
            ?: return@withContext Result.failure(IllegalStateException("Failed to generate CA certificate"))
        val source = File(caPath)
        if (!source.exists() || source.length() <= 0L) {
            return@withContext Result.failure(IllegalStateException("CA certificate not found"))
        }

        val fileName = "masterhttprelayvpn-ca.crt"
        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = app.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to create Downloads entry"))
                resolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: return@withContext Result.failure(IllegalStateException("Failed to open Downloads output stream"))
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("$fileName saved to Downloads")
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val target = File(downloads, fileName)
                source.copyTo(target, overwrite = true)
                Result.success("$fileName saved to ${target.absolutePath}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val appList = withContext(Dispatchers.IO) {
                val packageManager = getApplication<Application>().packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val launcherPackages = packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.MATCH_ALL
                )
                    .asSequence()
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()

                packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
                    .asSequence()
                    .filter { app -> launcherPackages.contains(app.packageName) }
                    .filter { app -> app.packageName != getApplication<Application>().packageName }
                    .map { app: ApplicationInfo ->
                        val label = runCatching {
                            packageManager.getApplicationLabel(app).toString()
                        }.getOrDefault(app.packageName)
                        val installTime = runCatching {
                            packageManager.getPackageInfo(app.packageName, PackageManager.GET_META_DATA).firstInstallTime
                        }.getOrDefault(0L)
                        AppEntry(app.packageName, label, installTime)
                    }
                    .sortedWith(compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.packageName })
                    .toList()
            }
            _installedApps.value = appList
        }
    }
}
