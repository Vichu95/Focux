package com.focux.focux

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import android.content.pm.PackageManager
import com.focux.focux.ui.theme.FocuxTheme
import kotlinx.coroutines.launch

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

    /** Try posting a tiny notification NOW; return true if it succeeds. */
    private fun tryPostProbeNotification(): Boolean {
        return runCatching {
            ForegroundNotification.createChannel(this)
            val n: Notification = NotificationCompat.Builder(this, ForegroundNotification.CHANNEL_ID)
                .setContentTitle("Focux probe")
                .setContentText("Testing notification permission")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify(9999, n)
            true
        }.onFailure {
            LogWriter.append(this, "PROBE_NOTIFY_FAILED:${it::class.java.simpleName}")
        }.getOrDefault(false)
    }

    private fun requestNotifPermissionIfNeeded(onDone: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifRuntimePermission()) {
            onNotifPermissionResult = { granted -> onDone(granted) }
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else onDone(true)
    }

    // inside MainActivity
    private fun startTrackingViaShim() {
        startActivity(Intent(this, StarterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        LogWriter.append(this, "MAIN:STARTER_ACTIVITY_LAUNCHED")
    }

    private fun startTrackingService() {
        // Step 1: runtime permission
        requestNotifPermissionIfNeeded { granted ->
            if (!granted) {
                LogWriter.append(this, "POST_NOTIFICATIONS_DENIED")
                openAppNotificationSettings()
                return@requestNotifPermissionIfNeeded
            }

            // Step 2: user toggle
            if (!areAppNotificationsEnabled()) {
                LogWriter.append(this, "NOTIF_TOGGLE_DISABLED")
                openAppNotificationSettings()
                return@requestNotifPermissionIfNeeded
            }

            // Step 3: probe-post a notification; if this fails, don’t start service
            if (!tryPostProbeNotification()) {
                openAppNotificationSettings()
                return@requestNotifPermissionIfNeeded
            }

            // Step 4: start the foreground service
            val intent = Intent(this, EventListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            LogWriter.append(this, "SERVICE_START_REQUESTED_OK")
        }
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
                            openSettings = { openAppNotificationSettings() },
                            stateProvider = {
                                val perm = hasNotifRuntimePermission()
                                val enabled = areAppNotificationsEnabled()
                                perm to enabled
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    readLog: () -> String,
    openSettings: () -> Unit,
    stateProvider: () -> Pair<Boolean, Boolean>,
) {
    var logText by remember { mutableStateOf("") }
    var perm by remember { mutableStateOf(stateProvider().first) }
    var enabled by remember { mutableStateOf(stateProvider().second) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun refreshState() {
        val p = stateProvider()
        perm = p.first
        enabled = p.second
    }

    // Auto-scroll to bottom when log text changes
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
            Text("Notification permission: " + if (perm) "GRANTED" else "NOT GRANTED")
            Text("App notifications toggle: " + if (enabled) "ENABLED" else "DISABLED")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onStart(); refreshState() }) { Text("Start Tracking") }
                OutlinedButton(onClick = { onStop(); refreshState() }) { Text("Stop Tracking") }
            }

            if (!perm || !enabled) {
                OutlinedButton(onClick = openSettings) { Text("Open notification settings") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { logText = readLog(); refreshState() }) { Text("View Log") }
                OutlinedButton(onClick = { onClear(); logText = "" }) { Text("Clear Log") }
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
