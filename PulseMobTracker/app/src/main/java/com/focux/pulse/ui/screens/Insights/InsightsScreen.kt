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
    
    // Header State
    val currentWeekStart by viewModel.currentWeekStart.collectAsState()
    val weeklyFocusScore by viewModel.weeklyFocusScore.collectAsState()
    val deepWorkDuration by viewModel.deepWorkDuration.collectAsState()
    
    val today = java.time.LocalDate.now()
    val endOfWeek = currentWeekStart.plusDays(6)
    val canGoNext = endOfWeek.isBefore(today)
    
    // Date Range Formatter
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault())
    val dateRangeStr = "${currentWeekStart.format(formatter)}-${endOfWeek.format(formatter)}"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = PulseAppPaddingMedium, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            InsightsHeader(
                dateRange = dateRangeStr,
                focusScore = weeklyFocusScore,
                onPrevClick = { viewModel.prevWeek() },
                onNextClick = { viewModel.nextWeek() },
                canGoNext = canGoNext
            )
        }
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
             DeepWorkInsightCard(deepWorkDuration)
        }
    }
}


