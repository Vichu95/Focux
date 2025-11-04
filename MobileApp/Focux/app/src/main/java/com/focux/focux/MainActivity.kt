package com.focux.focux

import android.Manifest
import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
                            openSettings = { openAppNotificationSettings() },
                            openAccessibilitySettings = { openAccessibilitySettings() },
                            stateProvider = {
                                val perm = hasNotifRuntimePermission()
                                val enabled = areAppNotificationsEnabled()
                                val accessibility = isAccessibilityServiceEnabled()
                                Triple(perm, enabled, accessibility)
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
    openAccessibilitySettings: () -> Unit,
    stateProvider: () -> Triple<Boolean, Boolean, Boolean>,
) {
    var logText by remember { mutableStateOf("") }
    var (perm, enabled, accessibility) = stateProvider()

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun refreshStates() {
        val (p, e, a) = stateProvider()
        perm = p
        enabled = e
        accessibility = a
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
            Text("Notification permission: " + if (perm) "GRANTED" else "NOT GRANTED")
            Text("App notifications toggle: " + if (enabled) "ENABLED" else "DISABLED")
            Text("Accessibility service: " + if (accessibility) "ENABLED" else "DISABLED")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onStart(); refreshStates() }) { Text("Start Tracking") }
                OutlinedButton(onClick = { onStop(); refreshStates() }) { Text("Stop Tracking") }
            }

            if (!perm || !enabled) {
                OutlinedButton(onClick = openSettings) { Text("Open notification settings") }
            }

            if (!accessibility) {
                OutlinedButton(onClick = openAccessibilitySettings) { Text("Enable Touch Tracking") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { logText = readLog(); refreshStates() }) { Text("View Log") }
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
