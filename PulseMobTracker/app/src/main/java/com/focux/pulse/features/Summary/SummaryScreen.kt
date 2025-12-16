package com.focux.pulse.features.Summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focux.pulse.data.dummyDeviceAccess
import com.focux.pulse.data.dummyFirstLastApps
import com.focux.pulse.data.dummyOfflineStreak
import com.focux.pulse.data.dummyPhoneActivity
import com.focux.pulse.features.Summary.components.DeviceAccessCard
import com.focux.pulse.features.Summary.components.FirstLastAppsCard
import com.focux.pulse.features.Summary.components.OfflineStreakCard
import com.focux.pulse.features.Summary.components.PhoneActivityCard

@Composable
fun SummaryScreen() {
    // This is the "Container" that holds the "Blocks"
    // It injects the dummy data stub into the components.
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
