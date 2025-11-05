package com.focux.focux

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.focux.focux.db.AppDatabase
import com.focux.focux.db.LogEvent
import com.focux.focux.ui.theme.FocuxTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import java.util.Calendar

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocuxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainDashboard()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainDashboard() {
        val db = remember { AppDatabase.getDatabase(this) }
        var dashboardStats by remember { mutableStateOf(DashboardStats()) }
        var permissionsState by remember { mutableStateOf(PermissionsState()) }
        var isServiceRunning by remember { mutableStateOf(EventListenerService.isRunning) }

        val scope = rememberCoroutineScope()

        fun refreshState() {
            scope.launch {
                permissionsState = checkPermissions()
                isServiceRunning = EventListenerService.isRunning
                dashboardStats = calculateDashboardStats(db)
            }
        }

        LaunchedEffect(Unit, isServiceRunning) {
            refreshState()
        }

        Scaffold(
            topBar = { DashboardTopBar() }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(8.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Controls ---
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startTracking(); refreshState() }, enabled = !isServiceRunning, modifier = Modifier.weight(1f)) { Text("Start", fontSize = 12.sp) }
                    Button(onClick = { stopTracking(); refreshState() }, enabled = isServiceRunning, modifier = Modifier.weight(1f)) { Text("Stop", fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { db.logEventDao().clearAndReset(); refreshState() } }, modifier = Modifier.weight(1f)) { Text("Clear DB", fontSize = 12.sp) }
                    OutlinedButton(onClick = { exportDatabase() }, modifier = Modifier.weight(1f)) { Text("Export DB", fontSize = 12.sp) }
                }

                // --- Permissions ---
                if (!permissionsState.hasUsageStats || !permissionsState.isAccessibilityEnabled) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Required Permissions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (!permissionsState.isAccessibilityEnabled) {
                        OutlinedButton(onClick = { openAccessibilitySettings() }) { Text("Enable Accessibility Service", fontSize = 12.sp) }
                    }
                    if (!permissionsState.hasUsageStats) {
                        OutlinedButton(onClick = { openUsageStatsSettings() }) { Text("Enable Usage Stats", fontSize = 12.sp) }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // --- Dashboard Content ---
                if (isServiceRunning) {
                    Text("Screen Events (Today)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    StatRow("Unlocked:", dashboardStats.screenUnlocks.toString())
                    StatRow("Locked:", dashboardStats.screenLocks.toString())

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Recent Events", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(dashboardStats.recentEvents) {
                            Text(it.toFormattedString(), fontSize = 10.sp, lineHeight = 12.sp)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Tracking is stopped.", fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }
                
                Button(onClick = { refreshState() }) { Text("Refresh Stats") }
            }
        }
    }
    
    private fun startTracking() {
        startService(Intent(this, EventListenerService::class.java))
    }

    private fun stopTracking() {
        val intent = Intent(this, EventListenerService::class.java).apply { action = "STOP_SERVICE" }
        startService(intent)
    }
    
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openUsageStatsSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun exportDatabase() {
        val exportDir = File(cacheDir, "exported_data")
        if (exportDir.exists()) {
            exportDir.deleteRecursively()
        }
        exportDir.mkdirs()

        val dbFile = getDatabasePath("focux_database")
        if (!dbFile.exists()) return

        val tempFile = File(exportDir, dbFile.name)
        dbFile.copyTo(tempFile, overwrite = true)

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", tempFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export Database"))
    }

    private suspend fun calculateDashboardStats(db: AppDatabase): DashboardStats {
        val dao = db.logEventDao()
        return DashboardStats(
            screenLocks = dao.countScreenEventsToday("SCREEN_OFF"),
            screenUnlocks = dao.countScreenEventsToday("UNLOCKED"),
            recentEvents = dao.getRecentTen()
        )
    }

    private suspend fun manualFetchUsageStats(db: AppDatabase) {
        // This function is now obsolete.
        // Raw event logging will be handled by background services.
        // A daily summary will be calculated by a separate WorkManager job.
        return
    }

    private fun checkPermissions(): PermissionsState {
        return PermissionsState(
            hasUsageStats = hasUsageStatsPermission(),
            isAccessibilityEnabled = isAccessibilityServiceEnabled()
        )
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val cName = ComponentName(this, GlobalTouchService::class.java)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(cName.flattenToString()) == true
    }
}

data class PermissionsState(
    val hasUsageStats: Boolean = false,
    val isAccessibilityEnabled: Boolean = false
)

data class DashboardStats(
    val screenLocks: Int = 0,
    val screenUnlocks: Int = 0,
    val recentEvents: List<LogEvent> = emptyList()
)

@Composable
fun StatRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 12.sp)
    }
}

fun LogEvent.toFormattedString(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
    val time = sdf.format(Date(this.timestamp))
    return "$time | ${this.eventAction} | ${this.packageName ?: "-"}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar() {
    TopAppBar(title = { Text("Focux Dashboard", fontWeight = FontWeight.Bold) })
}
