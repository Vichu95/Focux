package com.focux.pulse.ui.screens.Configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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

    // App Category ViewModel
    val categoryViewModel: AppCategoryViewModel = viewModel()
    val categoryState by categoryViewModel.uiState.collectAsState()

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
            onSearchQueryChanged = { categoryViewModel.setSearchQuery(it) },
            onCategoryChanged = { pkg, cat -> categoryViewModel.updateCategory(pkg, cat) }
        )
        
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
                Text(
                    text = "Database Management",
                    style = PulseAppFontSubHeader,
                    color = PulseAppColorPrimary
                )
                
                Text(
                    text = "Clear all raw data and reset processing indexes. Use this if you encounter data issues after reinstalling.",
                    style = PulseAppFontBody,
                    color = PulseAppColorSecondary
                )
                
                Button(
                    onClick = {
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
                            } catch (e: Exception) {
                                statusMessage = "✗ Error: ${e.message}"
                            }
                        }
                    },
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
                    onClick = {
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
                            } catch (e: Exception) {
                                statusMessage = "✗ Error: ${e.message}"
                            }
                        }
                    },
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
                        style = PulseAppFontBody,
                        color = if (statusMessage.startsWith("✓")) PulseAppColorPrimary else PulseAppColorDistracting
                    )
                }
            }
        }
    }
}

