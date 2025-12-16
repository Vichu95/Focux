package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.data.AppUsage
import com.focux.pulse.data.PhoneActivityData
import com.focux.pulse.ui.theme.*

@Composable
fun PhoneActivityCard(data: PhoneActivityData) {
    // Frame Phone Day Usage (CSS Specs)
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppPhoneActivityCardHeight)
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
        
        // This spacer pushes the main content to clear the header area slightly or we can just let spaceAround handle it.
        // CSS has gap: 10px in the main column.
        Spacer(modifier = Modifier.height(10.dp))

        // Large Time + Breakdown Stats Row
        // Frame 48 (Height 83px)
        Row(
            modifier = Modifier.fillMaxWidth().height(83.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Timer "5h 12m"
            Text(
                text = data.totalTime,
                style = PulseAppFontBigNumber, // 36sp
                color = PulseAppColorSecondary,
            )

            // Breakdown Column
            // Frame 47
            Column(
                horizontalAlignment = Alignment.Start, // Align text to right or start depending on pref, CSS aligns text items left within the block but block is right
                verticalArrangement = Arrangement.Center
            ) {
                LegendItem("Productive", data.productiveTime)
                LegendItem("Neutral", data.neutralTime)
                LegendItem("Distracting", data.distractingTime)
            }
        }

        Spacer(modifier = Modifier.weight(1f)) // Push apps to bottom or respect gap

        // App Icons Row
        // Frame 28
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, // Gap 51px in CSS implies spacing out
            verticalAlignment = Alignment.CenterVertically
        ) {
            // We expect exactly 3 apps for this design usually, or take top 3
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
        style = PulseAppFontBody, // 14sp
        color = PulseAppColorSecondary
    )
}

@Composable
fun AppUsageItem(app: AppUsage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val iconRes = when {
            app.iconName.contains("instagram") -> R.drawable.instagram_logo
            app.iconName.contains("gmail") -> R.drawable.gmail_logo
            app.iconName.contains("maps") -> R.drawable.maps_logo
            else -> R.drawable.temp_icon 
        }
        
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = app.name,
            modifier = Modifier.size(PulseAppIconSizeMedium) // 36dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = app.duration,
            style = PulseAppFontSubHeader, // 16sp
            color = PulseAppColorSecondary
        )
    }
}
