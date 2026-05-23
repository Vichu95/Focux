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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.launch
import com.focux.pulse.ui.screens.Configuration.ConfigurationScreen
import com.focux.pulse.ui.screens.Insights.InsightsScreen
import com.focux.pulse.ui.screens.Summary.SummaryScreen
import com.focux.pulse.ui.screens.Timeline.TimelineScreen
import com.focux.pulse.ui.screens.components.BottomNavBar
import com.focux.pulse.ui.screens.components.AdBanner
import com.focux.pulse.utilities.PulseAppDataLoggingFrequency
import com.google.android.gms.ads.MobileAds

import com.focux.pulse.ui.theme.PulseTheme
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.focux.pulse.utilities.ConfigInitializer.initializeDefaults(
                com.focux.pulse.data.local.PulseDatabase.getDatabase(applicationContext)
            )
        }

        enableEdgeToEdge()
        setContent {
            PulseTheme {
                var isSetupCompleted by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    val db = com.focux.pulse.data.local.PulseDatabase.getDatabase(applicationContext)
                    val completed = db.analyticsDao().getState("setup_completed")?.toBoolean() ?: false
                    isSetupCompleted = completed
                }

                var permissionsGranted by remember {
                    mutableStateOf(checkUsageStatsPermission())
                }

                if (isSetupCompleted == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PulseAppColorPrimary)
                    }
                } else if (isSetupCompleted == true) {
                    val openConfig = intent.getBooleanExtra("OPEN_CONFIG", false)
                    MainAppStructure(initialTab = if (openConfig) 3 else 0)
                } else {
                    if (!permissionsGranted) {
                        com.focux.pulse.ui.screens.onboarding.OnboardingScreen(
                            onFinish = {
                                if (checkUsageStatsPermission()) {
                                    permissionsGranted = true
                                }
                            }
                        )
                    } else {
                        com.focux.pulse.ui.screens.onboarding.OnboardingSetupFlow(
                            onFinish = {
                                initDataCollection()
                                lifecycleScope.launch {
                                    val db = com.focux.pulse.data.local.PulseDatabase.getDatabase(applicationContext)
                                    db.analyticsDao().updateState(com.focux.pulse.data.local.entities.SystemState("setup_completed", "true"))
                                    isSetupCompleted = true
                                }
                            }
                        )
                    }
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

        val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>(
            PulseAppDataLoggingFrequency.toLong(), java.util.concurrent.TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "DataCollectionWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<com.focux.pulse.data.workers.DataCollectionWorker>()
            .build()

        workManager.enqueueUniqueWork(
            "ImmediateDataSync",
            androidx.work.ExistingWorkPolicy.KEEP,
            oneTimeRequest
        )
    }
}

@Composable
fun MainAppStructure(initialTab: Int = 0) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialTab, pageCount = { 4 })
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Sync Pager -> Tab
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    // Sync Tab -> Pager (Click)
    // handled in BottomNavBar callback

    Scaffold(
        bottomBar = {
            Column {
                AdBanner()
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        selectedTab = index
                        coroutineScope.launch {
                            pagerState.scrollToPage(index)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        val contentModifier = Modifier.padding(innerPadding)

        // Wraps the screen content in a Box to apply the Scaffold padding
        androidx.compose.foundation.layout.Box(modifier = contentModifier) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> SummaryScreen()
                    1 -> TimelineScreen()
                    2 -> InsightsScreen()
                    3 -> ConfigurationScreen()
                }
            }
        }

    }
}