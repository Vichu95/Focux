package com.focux.pulse.ui.screens.Timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focux.pulse.ui.screens.Summary.components.DateFocusHeader
import com.focux.pulse.ui.screens.Timeline.components.TimelineItem
import com.focux.pulse.ui.screens.Timeline.components.TimelineHeader
import com.focux.pulse.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(viewModel: TimelineViewModel = viewModel()) {
    // State for Date Navigation - Syncs with App Session
    val selectedDate by com.focux.pulse.ui.state.SessionDateManager.selectedDate.collectAsState()
    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Load data when date changes
    LaunchedEffect(selectedDate) {
        viewModel.loadDataForDate(selectedDate.format(dateFormatter))
    }

    val timelineEvents by viewModel.timelineEvents.collectAsState()
    val earliestDateStr by viewModel.earliestDate.collectAsState()
    
    val earliestDate = earliestDateStr?.let { 
        try { LocalDate.parse(it, dateFormatter) } catch (e: Exception) { null }
    }
    
    val canGoNext = selectedDate.isBefore(today)
    val canGoPrev = earliestDate == null || selectedDate.isAfter(earliestDate)
    
    // State for Filter Sheet
    var showFilterSheet by remember { mutableStateOf(false) }
    
    // Filter State
    val filterState by viewModel.filterState.collectAsState()
    val availableApps by viewModel.availableApps.collectAsState()
    val dayBounds by viewModel.dayBounds.collectAsState()
    
    if (showFilterSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            containerColor = PulseAppColorBackground
        ) {
            com.focux.pulse.ui.screens.Timeline.components.TimelineFilterSheet(
                onDismiss = { showFilterSheet = false },
                onApply = { timeRange, categories, apps, query, preset ->
                    viewModel.applyFilters(timeRange, categories, apps, query, preset)
                    showFilterSheet = false
                },
                onClear = {
                    // Do nothing here, sheet handles local UI reset.
                    // Apply button will commit these changes.
                },
                initialState = filterState, // We need to update generic Sheet to accept this
                availableApps = availableApps,
                availableRange = dayBounds
            )
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky Header - Outside LazyColumn
        Box(modifier = Modifier.padding(horizontal = PulseAppPaddingMedium)) {
            TimelineHeader(
                date = selectedDate,
                onPrevClick = { if (canGoPrev) com.focux.pulse.ui.state.SessionDateManager.setDate(selectedDate.minusDays(1)) },
                onNextClick = { if (canGoNext) com.focux.pulse.ui.state.SessionDateManager.setDate(selectedDate.plusDays(1)) },
                canGoNext = canGoNext,
                canGoPrev = canGoPrev,
                onFilterClick = { showFilterSheet = true }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PulseAppPaddingMedium, 
                end = PulseAppPaddingMedium, 
                bottom = PulseAppPaddingMedium,
                top = 0.dp
            )
        ) {
            if (timelineEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No activity for this day",
                            style = PulseAppFontBody,
                            color = PulseAppColorSecondary
                        )
                    }
                }
            } else {
                itemsIndexed(timelineEvents) { index, event ->
                    TimelineItem(
                        event = event,
                        isFirst = index == 0,
                        isLast = index == timelineEvents.lastIndex
                    )
                }
            }
        }
    }
}
