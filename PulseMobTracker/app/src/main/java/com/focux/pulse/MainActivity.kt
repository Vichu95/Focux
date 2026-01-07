package com.focux.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.focux.pulse.ui.screens.Configuration.ConfigurationScreen
import com.focux.pulse.ui.screens.Insights.InsightsScreen
import com.focux.pulse.ui.screens.Summary.SummaryScreen
import com.focux.pulse.ui.screens.Timeline.TimelineScreen
import com.focux.pulse.ui.screens.components.BottomNavBar
import com.focux.pulse.utilities.PulseAppDataLoggingFrequency

import com.focux.pulse.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseTheme {
                var hasPermission by remember { mutableStateOf(checkUsageStatsPermission()) }
                
                // Re-check permission when app resumes (simple way to catch return from settings)
                // In a real app, use LifecycleEventObserver or request launcher
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            hasPermission = checkUsageStatsPermission()
                            if (hasPermission) {
                                initDataCollection()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (hasPermission) {
                    MainAppStructure()
                } else {
                    com.focux.pulse.ui.screens.onboarding.PermissionScreen()
                }
            }
        }
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun initDataCollection() {
        val workManager = androidx.work.WorkManager.getInstance(this)

        // 1. Periodic Work (Every 15 mins) - The heartbeat
        val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>(
            PulseAppDataLoggingFrequency.toLong(), java.util.concurrent.TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "DataCollectionWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        // 2. Immediate Work (App Open / Triggered) - Capture data right now
        val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>()
            .build()
            
        workManager.enqueueUniqueWork(
            "ImmediateDataSync",
            androidx.work.ExistingWorkPolicy.KEEP, // If one is already running/enqueued, don't spam
            oneTimeRequest
        )
    }
}

@Composable
fun MainAppStructure() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        
        val contentModifier = Modifier.padding(innerPadding)
        
        // Wraps the screen content in a Box to apply the Scaffold padding
        androidx.compose.foundation.layout.Box(modifier = contentModifier) {
            when (selectedTab) {
                0 -> SummaryScreen()
                1 -> TimelineScreen()
                2 -> InsightsScreen()
                3 -> ConfigurationScreen()
            }
        }

    }
}
