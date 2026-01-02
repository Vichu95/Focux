package com.focux.pulse.features.Summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focux.pulse.data.dummyDeviceAccess
import com.focux.pulse.data.dummyFirstLastApps
import com.focux.pulse.data.dummyOfflineStreak
import com.focux.pulse.data.dummyPhoneActivity
import com.focux.pulse.features.Summary.components.DateFocusHeader
import com.focux.pulse.features.Summary.components.DeviceAccessCard
import com.focux.pulse.features.Summary.components.FirstLastAppsCard
import com.focux.pulse.features.Summary.components.OfflineStreakCard
import com.focux.pulse.features.Summary.components.PhoneActivityCard
import com.focux.pulse.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SummaryScreen() {
    // State for Date Navigation
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally // Center items including the header
    ) {
        // Date & Focus Header
        item {
            DateFocusHeader(
                date = selectedDate,
                onPrevClick = { selectedDate = selectedDate.minusDays(1) },
                onNextClick = { selectedDate = selectedDate.plusDays(1) }
            )
        }
        item {
            PhoneActivityCard(data = dummyPhoneActivity)
        }
        item {
            DeviceAccessCard(data = dummyDeviceAccess)
        }
        item {
            OfflineStreakCard(data = dummyOfflineStreak)
        }
        item {
            FirstLastAppsCard(data = dummyFirstLastApps)
        }
    }
}
