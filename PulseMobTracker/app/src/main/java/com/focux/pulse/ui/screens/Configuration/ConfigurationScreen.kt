package com.focux.pulse.ui.screens.Configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.SystemState
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ConfigurationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    
    var showClearDialog by remember { mutableStateOf(false) }
    var showReprocessDialog by remember { mutableStateOf(false) }
    var showClearOldDialog by remember { mutableStateOf(false) }
    var weeksToKeep by remember { mutableStateOf("2") }
    
    var dbSizeBytes by remember { mutableLongStateOf(0L) }
    
    fun updateDbSize() {
        val file = context.getDatabasePath("pulse_user.db")
        dbSizeBytes = if (file.exists()) file.length() else 0L
    }

    LaunchedEffect(Unit) {
        updateDbSize()
    }
    
    val dbSizeString = remember(dbSizeBytes) {
        val kb = dbSizeBytes / 1024f
        if (kb > 1024f) {
            String.format(java.util.Locale.getDefault(), "%.2f MB", kb / 1024f)
        } else {
            String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
        }
    }

    // exportLauncher removed, we now use Intent.ACTION_SEND in the button click

    // App Category ViewModel
    val categoryViewModel: AppCategoryViewModel = viewModel()
    val categoryState by categoryViewModel.uiState.collectAsState()

    // Mindful Limits ViewModel
    val limitsViewModel: MindfulLimitsViewModel = viewModel()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PulseAppPaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── App Category Section ──
        AppCategorySection(
            state = categoryState,
            onToggleEditMode = { categoryViewModel.toggleEditMode() },
            onSave = { categoryViewModel.saveChanges() },
            onCancel = { categoryViewModel.cancelChanges() },
            onSearchQueryChanged = { categoryViewModel.setSearchQuery(it) },
            onToggleFilter = { categoryViewModel.toggleFilter(it) },
            onCategoryChanged = { pkg, cat -> categoryViewModel.updateCategory(pkg, cat) }
        )
        
        // ── Mindful Limits Section ──
        MindfulLimitsSection(viewModel = limitsViewModel)
        
        // ── System Permissions Section ──
        SystemPermissionsSection()
        
        // Database Reset Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
                .background(PulseAppColorSurface)
                .padding(PulseAppPaddingMedium)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Database Management",
                        style = PulseAppFontSubHeader,
                        color = PulseAppColorPrimary
                    )
                    Text(
                        text = dbSizeString,
                        style = PulseAppFontSubHeader.copy(fontSize = 14.sp),
                        color = PulseAppColorSecondary
                    )
                }
                
                Text(
                    text = "Export your data for backup or analysis, or reset the app if you encounter issues. Pulse guarantees 100% privacy—your data always belongs to you.",
                    style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall),
                    color = PulseAppColorSecondary
                )
                
                // Export Database Button
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                statusMessage = "Preparing database for export..."
                                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                                
                                // We copy to cache dir so FileProvider can share it securely
                                val sharedDir = File(context.cacheDir, "shared")
                                if (!sharedDir.exists()) sharedDir.mkdirs()
                                
                                val dbFile = context.getDatabasePath("pulse_user.db")
                                if (dbFile.exists()) {
                                    val exportFile = File(sharedDir, "pulse_backup_$timestamp.db")
                                    dbFile.copyTo(exportFile, overwrite = true)
                                    
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/x-sqlite3"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    
                                    context.startActivity(Intent.createChooser(intent, "Share Database Backup"))
                                    statusMessage = "✓ Ready to share!"
                                } else {
                                    statusMessage = "✗ Database file not found."
                                }
                            } catch (e: Exception) {
                                statusMessage = "✗ Export failed: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PulseAppColorPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Export Database",
                        color = PulseAppColorBackground
                    )
                }
                
                // Clear Old Data Button
                Button(
                    onClick = { showClearOldDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PulseAppColorDistracting.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Clear Old Data",
                        color = PulseAppColorBackground
                    )
                }
                
                // Clear Database Button
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PulseAppColorDistracting
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Clear Database & Reset Indexes",
                        color = PulseAppColorBackground
                    )
                }
                
                // Reprocess Data Button
                Button(
                    onClick = { showReprocessDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PulseAppColorSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Reprocess Data (Keep Raw Logs)",
                        color = PulseAppColorBackground
                    )
                }
                
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall),
                        color = if (statusMessage.startsWith("✓")) PulseAppColorPrimary else PulseAppColorDistracting
                    )
                }
            }
        }
    }
    
    // Clear Database Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text("Clear All Data?", style = PulseAppFontSubHeader)
            },
            text = {
                Text(
                    "This will completely erase all your saved habits, timelines, and raw tracking data. This action cannot be undone. Are you sure?",
                    style = PulseAppFontBody
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            try {
                                val workManager = androidx.work.WorkManager.getInstance(context)
                                
                                // 1. Stop background workers (Critical!)
                                workManager.cancelUniqueWork("DataCollectionWork")
                                workManager.cancelUniqueWork("ImmediateDataSync")
                                kotlinx.coroutines.delay(2000) // Increase delay to 2s to be safe
                                
                                val db = PulseDatabase.getDatabase(context)
                                
                                // 2. "Nuclear" Reset via MaintenanceDao
                                db.maintenanceDao().clearAllAndReset()
                                
                                // Reset memory state and SKIP history re-import
                                db.analyticsDao().updateState(SystemState("last_processed_app_id", "0"))
                                db.analyticsDao().updateState(SystemState("last_processed_screen_id", "0"))

                                // 3. Restart Data Collection
                                val restartRequest = androidx.work.PeriodicWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>(
                                    15, java.util.concurrent.TimeUnit.MINUTES
                                ).build()

                                workManager.enqueueUniquePeriodicWork(
                                    "DataCollectionWork",
                                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                                    restartRequest
                                )
                                
                                val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>()
                                    .build()
                                    
                                // Fix Race Condition: Use UniqueWork to ensure we don't double-queue
                                workManager.enqueueUniqueWork(
                                    "ImmediateDataSync",
                                    androidx.work.ExistingWorkPolicy.REPLACE,
                                    oneTimeRequest
                                )

                                statusMessage = "✓ Database cleared & restarted!"
                                kotlinx.coroutines.delay(500)
                                updateDbSize()
                            } catch (e: Exception) {
                                statusMessage = "✗ Error: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorDistracting)
                ) {
                    Text("Delete Everything", color = PulseAppColorBackground)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Reprocess Data Confirmation Dialog
    if (showReprocessDialog) {
        AlertDialog(
            onDismissRequest = { showReprocessDialog = false },
            title = {
                Text("Reprocess Data?", style = PulseAppFontSubHeader)
            },
            text = {
                Text(
                    "This will delete summarized statistics and rebuild them from raw tracking logs. This is useful for fixing data glitches. Your raw history stays intact.",
                    style = PulseAppFontBody
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReprocessDialog = false
                        scope.launch {
                            try {
                                val workManager = androidx.work.WorkManager.getInstance(context)
                                
                                // 1. Stop background workers
                                workManager.cancelUniqueWork("DataCollectionWork")
                                workManager.cancelUniqueWork("ImmediateDataSync")
                                kotlinx.coroutines.delay(1000)
                                
                                val db = PulseDatabase.getDatabase(context)
                                
                                // 2. Clear ONLY processed tables (Sessions, Stats)
                                db.analyticsDao().clearProcessedDataAndReset()

                                // 3. Restart Data Collection (This will now re-process EVERYTHING from Raw Data)
                                val restartRequest = androidx.work.PeriodicWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>(
                                    15, java.util.concurrent.TimeUnit.MINUTES
                                ).build()

                                workManager.enqueueUniquePeriodicWork(
                                    "DataCollectionWork",
                                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                                    restartRequest
                                )
                                
                                // Trigger immediate run to start crunching numbers
                                val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>()
                                    .build()
                                workManager.enqueueUniqueWork(
                                    "ImmediateDataSync",
                                    androidx.work.ExistingWorkPolicy.REPLACE,
                                    oneTimeRequest
                                )

                                statusMessage = "✓ Data cleared! Reprocessing started..."
                                kotlinx.coroutines.delay(500)
                                updateDbSize()
                            } catch (e: Exception) {
                                statusMessage = "✗ Error: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Reprocess Data")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReprocessDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Clear Old Data Dialog
    if (showClearOldDialog) {
        AlertDialog(
            onDismissRequest = { showClearOldDialog = false },
            title = {
                Text("Clear Old Data", style = PulseAppFontSubHeader)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the number of weeks of history you want to KEEP. Everything older will be permanently deleted.",
                        style = PulseAppFontBody
                    )
                    OutlinedTextField(
                        value = weeksToKeep,
                        onValueChange = { weeksToKeep = it },
                        label = { Text("Weeks to keep") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val weeks = weeksToKeep.toLongOrNull()
                        if (weeks == null || weeks < 0) {
                            statusMessage = "✗ Invalid number of weeks"
                            return@Button
                        }
                        
                        showClearOldDialog = false
                        scope.launch {
                            try {
                                statusMessage = "Clearing old data..."
                                val db = PulseDatabase.getDatabase(context)
                                
                                // Calculate thresholds
                                val nowMillis = System.currentTimeMillis()
                                val thresholdMillis = nowMillis - (weeks * 7L * 24L * 60L * 60L * 1000L)
                                
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                val thresholdDateString = dateFormat.format(java.util.Date(thresholdMillis))
                                
                                db.maintenanceDao().clearOldAppSessions(thresholdMillis)
                                db.maintenanceDao().clearOldRawData(thresholdMillis)
                                db.maintenanceDao().clearOldDailyStats(thresholdDateString)
                                
                                // Vacuum to reclaim space
                                db.openHelper.writableDatabase.execSQL("VACUUM")
                                
                                kotlinx.coroutines.delay(500)
                                updateDbSize()
                                statusMessage = "✓ Old data cleared successfully!"
                            } catch (e: Exception) {
                                statusMessage = "✗ Failed: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorDistracting)
                ) {
                    Text("Delete Old Data", color = PulseAppColorBackground)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearOldDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

