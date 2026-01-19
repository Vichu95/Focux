package com.focux.pulse.ui.screens.Insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    val routine by viewModel.routine.collectAsState()
    
    // Header State
    val currentWeekStart by viewModel.currentWeekStart.collectAsState()
    val weeklyFocusScore by viewModel.weeklyFocusScore.collectAsState()
    val deepWorkDuration by viewModel.deepWorkDuration.collectAsState()
    val earliestDate by viewModel.earliestDate.collectAsState()
    
    val today = java.time.LocalDate.now()
    val endOfWeek = currentWeekStart.plusDays(6)
    val canGoNext = endOfWeek.isBefore(today)
    
    // Calculate canGoPrev
    val canGoPrev = earliestDate?.let { earliest ->
        val earliestWeekStart = earliest.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        currentWeekStart.isAfter(earliestWeekStart)
    } ?: false
    
    // Date Range Formatter
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault())
    val dateRangeStr = "${currentWeekStart.format(formatter)}-${endOfWeek.format(formatter)}"

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fixed Header
        Box(modifier = Modifier.padding(horizontal = PulseAppPaddingMedium)) {
            InsightsHeader(
                dateRange = dateRangeStr,
                focusScore = weeklyFocusScore,
                onPrevClick = { viewModel.prevWeek() },
                onNextClick = { viewModel.nextWeek() },
                canGoNext = canGoNext,
                canGoPrev = canGoPrev
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(top = 12.dp, start = PulseAppPaddingMedium, end = PulseAppPaddingMedium, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WeeklyTrendCard(weeklyTrend.ifEmpty { dummyWeeklyTrend })
            }
            item {
                WeeklyActivityCard(weeklyActivity ?: dummyWeeklyActivity)
            }
            item {
                RoutineCard(routine)
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
}


