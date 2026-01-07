package com.focux.pulse.ui.screens.Insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focux.pulse.data.local.entities.*
import com.focux.pulse.utilities.*
import com.focux.pulse.ui.screens.Insights.components.*
import com.focux.pulse.ui.theme.PulseAppPaddingMedium

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = viewModel()) {
    val weeklyTrend by viewModel.weeklyTrend.collectAsState()
    val weeklyActivity by viewModel.weeklyActivity.collectAsState()
    val topApps by viewModel.topApps.collectAsState()
    val sessionLength by viewModel.sessionLength.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PulseAppPaddingMedium),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WeeklyTrendCard(weeklyTrend.ifEmpty { dummyWeeklyTrend })
        }
        item {
            WeeklyActivityCard(weeklyActivity ?: dummyWeeklyActivity)
        }
        item {
            RoutineCard(dummyRoutine)  // TODO: Calculate from session patterns
        }
        item {
            TopAppsCard(topApps.ifEmpty { dummyTopApps })
        }
        item {
            SessionLengthCard(sessionLength ?: dummySessionLength)
        }
        item {
             DeepWorkInsightCard()
        }
    }
}


