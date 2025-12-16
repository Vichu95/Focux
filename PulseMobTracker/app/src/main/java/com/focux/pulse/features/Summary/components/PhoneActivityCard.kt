package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.R
import com.focux.pulse.data.AppUsage
import com.focux.pulse.data.PhoneActivityData
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.pulseAppCard

@Composable
fun PhoneActivityCard(data: PhoneActivityData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pulseAppCard()
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Phone Activity",
            style = Typography.titleLarge,
            color = PulseAppColorPrimary
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Large Time + Legend
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.totalTime,
                style = Typography.titleLarge.copy(fontSize = 36.sp),
                color = PulseAppColorSecondary,
                modifier = Modifier.weight(1f)
            )

            Column(horizontalAlignment = Alignment.Start) {
                LegendItem("Productive", data.productiveTime)
                LegendItem("Neutral", data.neutralTime)
                LegendItem("Distracting", data.distractingTime)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Icons Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            data.topApps.forEach { app ->
                AppUsageItem(app)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, time: String) {
    Text(
        text = "• $label : $time",
        style = Typography.labelSmall,
        color = PulseAppColorSecondary
    )
}

@Composable
fun AppUsageItem(app: AppUsage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // NOTE: In a real app, map app.iconName to R.drawable dynamically or use a map.
        // For this stub, we hardcode to a few test icons or the generic one if unknown.
        val iconRes = when {
            app.iconName.contains("instagram") -> R.drawable.instagram_logo
            app.iconName.contains("gmail") -> R.drawable.gmail_logo
            app.iconName.contains("maps") -> R.drawable.maps_logo
            else -> R.drawable.temp_icon // Fallback
        }
        
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = app.name,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = app.duration,
            style = Typography.labelSmall,
            color = PulseAppColorSecondary
        )
    }
}
