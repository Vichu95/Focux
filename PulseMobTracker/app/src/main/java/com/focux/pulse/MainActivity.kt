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
import com.focux.pulse.features.Insights.InsightsScreen
import com.focux.pulse.features.Summary.SummaryScreen
import com.focux.pulse.features.Timeline.TimelineScreen
import com.focux.pulse.ui.components.BottomNavBar

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
                                scheduleDataCollection()
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
                    com.focux.pulse.ui.onboarding.PermissionScreen()
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

    private fun scheduleDataCollection() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.focux.pulse.workers.DataCollectionWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataCollectionWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
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
            }
        }

    }
}
