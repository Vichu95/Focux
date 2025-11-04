package com.focux.focux

import android.content.Intent
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

    @Composable
    fun MainDashboard() {
        val db = remember { AppDatabase.getDatabase(this) }
        var dashboardStats by remember { mutableStateOf(DashboardStats()) }
        val scope = rememberCoroutineScope()

        fun refreshStats() {
            scope.launch {
                dashboardStats = calculateDashboardStats(db)
            }
        }

        LaunchedEffect(Unit) {
            refreshStats()
        }

        Scaffold(
            topBar = { DashboardTopBar() }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(8.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Control buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startTracking() }, modifier = Modifier.weight(1f)) { Text("Start", fontSize = 12.sp) }
                    Button(onClick = { stopTracking() }, modifier = Modifier.weight(1f)) { Text("Stop", fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { db.logEventDao().clear(); refreshStats() } }, modifier = Modifier.weight(1f)) { Text("Clear DB", fontSize = 12.sp) }
                    OutlinedButton(onClick = { exportDatabase() }, modifier = Modifier.weight(1f)) { Text("Export DB", fontSize = 12.sp) }
                }
                OutlinedButton(onClick = { openAccessibilitySettings() }) { Text("Accessibility Settings", fontSize = 12.sp) }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Screen Events (Today)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                StatRow("Unlocked:", dashboardStats.screenUnlocks.toString())
                StatRow("Locked:", dashboardStats.screenLocks.toString())

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text("App Usage (Today / Avg Daily)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                StatRow("YouTube:", "${dashboardStats.youtubeToday}min / ${dashboardStats.youtubeAvg.toInt()}min")
                StatRow("WhatsApp:", "${dashboardStats.whatsappToday}min / ${dashboardStats.whatsappAvg.toInt()}min")

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Recent Events", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(dashboardStats.recentEvents) { event ->
                        Text(event.toFormattedString(), fontSize = 10.sp, lineHeight = 12.sp)
                    }
                }

                Button(onClick = { refreshStats() }) { Text("Refresh Stats") }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DashboardTopBar() {
        TopAppBar(title = { Text("Focux Dashboard", fontWeight = FontWeight.Bold) })
    }

    private fun startTracking() {
        val intent = Intent(this, EventListenerService::class.java)
        startService(intent)
    }

    private fun stopTracking() {
        stopService(Intent(this, EventListenerService::class.java))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
            youtubeToday = dao.getUsageToday("com.google.android.youtube") ?: 0L,
            whatsappToday = dao.getUsageToday("com.whatsapp") ?: 0L,
            youtubeAvg = dao.getAverageUsage("com.google.android.youtube") ?: 0.0,
            whatsappAvg = dao.getAverageUsage("com.whatsapp") ?: 0.0,
            recentEvents = dao.getRecentTen()
        )
    }
}

data class DashboardStats(
    val screenLocks: Int = 0,
    val screenUnlocks: Int = 0,
    val youtubeToday: Long = 0,
    val whatsappToday: Long = 0,
    val youtubeAvg: Double = 0.0,
    val whatsappAvg: Double = 0.0,
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
