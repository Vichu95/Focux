package com.focux.pulse.features.Insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focux.pulse.data_manager.*
import com.focux.pulse.features.Insights.components.*
import com.focux.pulse.ui.theme.PulseAppPaddingMedium

@Composable
fun InsightsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PulseAppPaddingMedium),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WeeklyTrendCard(dummyWeeklyTrend)
        }
        item {
            WeeklyActivityCard(dummyWeeklyActivity)
        }
        item {
            RoutineCard(dummyRoutine)
        }
        item {
            TopAppsCard(dummyTopApps)
        }
        item {
            SessionLengthCard(dummySessionLength)
        }
        item {
             DeepWorkInsightCard()
        }
    }
}

