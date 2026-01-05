package com.focux.pulse.features.Timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focux.pulse.features.Summary.components.DateFocusHeader
import com.focux.pulse.features.Timeline.components.TimelineItem
import com.focux.pulse.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = viewModel()) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PulseAppPaddingMedium)
    ) {
        // Date Header
        item {
            DateFocusHeader(
                date = selectedDate,
                onPrevClick = { if (canGoPrev) selectedDate = selectedDate.minusDays(1) },
                onNextClick = { if (canGoNext) selectedDate = selectedDate.plusDays(1) },
                canGoNext = canGoNext,
                canGoPrev = canGoPrev
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

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

