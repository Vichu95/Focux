package com.focux.pulse.features.Summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.PulseAppColorSurface
import com.focux.pulse.ui.theme.Typography

@Composable
fun SummaryScreen() {
    // This is the "Container" that holds the "Blocks"
    // It injects the dummy data stub into the components.
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date & Focus Header (Moved from TopBar)
        item {
            DateFocusHeader()
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

@Composable
fun DateFocusHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp), // Slight extra spacing below header
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Date Nav
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                contentDescription = "Prev",
                tint = PulseAppColorSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "14 Dec",
                style = Typography.titleLarge,
                color = PulseAppColorSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next",
                tint = PulseAppColorSecondary
            )
        }
        
        // Right: Focus Score
        Box(
            modifier = Modifier
                .background(PulseAppColorSurface, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Focus: 72",
                style = Typography.labelSmall,
                color = PulseAppColorPrimary 
            )
        }
    }
}
