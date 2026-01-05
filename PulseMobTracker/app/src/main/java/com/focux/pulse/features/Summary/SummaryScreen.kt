package com.focux.pulse.features.Summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focux.pulse.data_manager.*
import com.focux.pulse.features.Summary.components.DateFocusHeader
import com.focux.pulse.features.Summary.components.DeviceAccessCard
import com.focux.pulse.features.Summary.components.FirstLastAppsCard
import com.focux.pulse.features.Summary.components.OfflineStreakCard
import com.focux.pulse.features.Summary.components.PhoneActivityCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SummaryScreen(viewModel: SummaryViewModel = viewModel()) {
    // State for Date Navigation - cannot go beyond today
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    
    // Format for database query (YYYY-MM-DD)
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    // Load data when date changes
    LaunchedEffect(selectedDate) {
        viewModel.loadDataForDate(selectedDate.format(dateFormatter))
    }

    // Observe data from ViewModel
    val phoneActivity by viewModel.phoneActivity.collectAsState()
    val deviceAccess by viewModel.deviceAccess.collectAsState()
    val offlineStreak by viewModel.offlineStreak.collectAsState()
    val firstLastApps by viewModel.firstLastApps.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Date & Focus Header
        item {
            DateFocusHeader(
                date = selectedDate,
                onPrevClick = { selectedDate = selectedDate.minusDays(1) },
                onNextClick = { 
                    // Prevent navigation to future dates
                    if (selectedDate.isBefore(today)) {
                        selectedDate = selectedDate.plusDays(1) 
                    }
                },
                canGoNext = selectedDate.isBefore(today)
            )
        }
        item {
            PhoneActivityCard(data = phoneActivity ?: dummyPhoneActivity)
        }
        item {
            DeviceAccessCard(data = deviceAccess ?: dummyDeviceAccess)
        }
        item {
            OfflineStreakCard(data = offlineStreak ?: dummyOfflineStreak)
        }
        item {
            FirstLastAppsCard(data = firstLastApps ?: dummyFirstLastApps)
        }
    }
}

