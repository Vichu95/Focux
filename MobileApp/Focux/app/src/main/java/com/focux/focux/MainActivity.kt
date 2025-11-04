package com.focux.focux

import android.Manifest
import android.app.AppOpsManager
import android.app.Notification
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.focux.focux.ui.theme.FocuxTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var onNotifPermissionResult: ((Boolean) -> Unit)? = null

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotifPermissionResult?.invoke(granted)
        onNotifPermissionResult = null
    }

    private fun hasNotifRuntimePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun areAppNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun openAppNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${GlobalTouchService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageStatsSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun fetchUsageStats() {
        if (!hasUsageStatsPermission()) {
            LogWriter.append(this, "USAGE_STATS: Permission not granted")
            return
        }
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(1)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        stats.forEach { stat ->
            val appName = stat.packageName
            val totalTime = TimeUnit.MILLISECONDS.toMinutes(stat.totalTimeInForeground)
            if (totalTime > 0) {
                LogWriter.append(this, "USAGE_STATS: $appName - ${totalTime}min")
            }
        }
    }

    private fun exportDatabase() {
        val dbFile = getDatabasePath("focux_database")
        if (!dbFile.exists()) {
            LogWriter.append(this, "EXPORT: Database file not found.")
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            dbFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export Database"))
    }

    private fun startTrackingViaShim() {
        startActivity(Intent(this, StarterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        LogWriter.append(this, "MAIN:STARTER_ACTIVITY_LAUNCHED")
    }

    private fun stopTrackingService() {
        stopService(Intent(this, EventListenerService::class.java))
        LogWriter.append(this, "SERVICE_STOP_REQUESTED")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocuxTheme {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        MainScreen(
                            onStart = { startTrackingViaShim() },
                            onStop = { stopTrackingService() },
                            onClear = { LogWriter.clear(this) },
                            readLog = { LogWriter.read(this) },
                            fetchUsage = { fetchUsageStats() },
                            onExport = { exportDatabase() },
                            openSettings = { openAppNotificationSettings() },
                            openAccessibilitySettings = { openAccessibilitySettings() },
                            openUsageStatsSettings = { openUsageStatsSettings() },
                            stateProvider = {
                                val perm = hasNotifRuntimePermission()
                                val enabled = areAppNotificationsEnabled()
                                val accessibility = isAccessibilityServiceEnabled()
                                val usageStats = hasUsageStatsPermission()
                                AppState(perm, enabled, accessibility, usageStats)
                            }
                        )
                    }
                }
            }
        }
    }
}

data class AppState(
    val notifPerm: Boolean,
    val notifEnabled: Boolean,
    val accessibilityEnabled: Boolean,
    val usageStatsEnabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    readLog: () -> String,
    fetchUsage: () -> Unit,
    onExport: () -> Unit,
    openSettings: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    openUsageStatsSettings: () -> Unit,
    stateProvider: () -> AppState,
) {
    var logText by remember { mutableStateOf("") }
    var appState by remember { mutableStateOf(stateProvider()) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun refreshStates() {
        appState = stateProvider()
    }

    LaunchedEffect(logText) {
        if (logText.isNotBlank()) {
            scope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Focux — Milestone 2", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Background logging + boot persistence via Foreground Service.")
            Text("Notification permission: " + if (appState.notifPerm) "GRANTED" else "NOT GRANTED")
            Text("App notifications toggle: " + if (appState.notifEnabled) "ENABLED" else "DISABLED")
            Text("Accessibility service: " + if (appState.accessibilityEnabled) "ENABLED" else "DISABLED")
            Text("Usage stats access: " + if (appState.usageStatsEnabled) "GRANTED" else "NOT GRANTED")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onStart(); refreshStates() }) { Text("Start Tracking") }
                OutlinedButton(onClick = { onStop(); refreshStates() }) { Text("Stop Tracking") }
            }

            if (!appState.notifPerm || !appState.notifEnabled) {
                OutlinedButton(onClick = openSettings) { Text("Open notification settings") }
            }

            if (!appState.accessibilityEnabled) {
                OutlinedButton(onClick = openAccessibilitySettings) { Text("Enable Touch Tracking") }
            }

            if (!appState.usageStatsEnabled) {
                OutlinedButton(onClick = openUsageStatsSettings) { Text("Enable Usage Stats") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { logText = readLog(); refreshStates() }) { Text("View Log") }
                OutlinedButton(onClick = { onClear(); logText = "" }) { Text("Clear Log") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { fetchUsage(); logText = readLog() }) { Text("Fetch Usage Stats") }
                OutlinedButton(onClick = { onExport() }) { Text("Export Log") }
            }

            Divider()
            Text("Log:")
            Text(
                text = if (logText.isBlank()) "(empty)" else logText,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            )
        }
    }
}
