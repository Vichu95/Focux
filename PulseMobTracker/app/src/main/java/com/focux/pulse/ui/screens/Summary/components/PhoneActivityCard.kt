package com.focux.pulse.ui.screens.Summary.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focux.pulse.utilities.AppUsage
import com.focux.pulse.utilities.PhoneActivityData
import com.focux.pulse.ui.screens.components.AppIcon
import com.focux.pulse.ui.theme.*

@Composable
fun PhoneActivityCard(data: PhoneActivityData) {
    // Frame Phone Day Usage (CSS Specs)
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .wrapContentHeight() // Allow card to grow with the list
            .defaultMinSize(minHeight = PulseAppPhoneActivityCardHeight)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(PulseAppPaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title "Phone Activity"
        Text(
            text = "Phone Activity",
            style = PulseAppFontHeader,
            color = PulseAppColorPrimary,
            modifier = Modifier.fillMaxWidth().align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(10.dp))

        // Large Time + Breakdown Stats Row
        Row(
            modifier = Modifier.fillMaxWidth().height(83.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Timer "5h 12m"
            Text(
                text = data.totalTime,
                style = PulseAppFontBigNumber,
                color = PulseAppColorSecondary,
            )

            // Breakdown Column
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                LegendItem("Productive", data.productiveTime)
                LegendItem("Neutral", data.neutralTime)
                LegendItem("Distracting", data.distractingTime)
            }
        }

        // App Icons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            data.topApps.take(3).forEach { app ->
                AppUsageItem(app)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, time: String) {
    Text(
        text = "• $label : $time",
        style = PulseAppFontBody,
        color = PulseAppColorSecondary
    )
}

@Composable
fun AppUsageItem(app: AppUsage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Use real app icon from PackageManager
        AppIcon(
            packageName = app.iconName,
            size = PulseAppIconSizeMedium,
            contentDescription = app.name
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = app.duration,
            style = PulseAppFontSubHeader,
            color = PulseAppColorSecondary
        )
    }
}

