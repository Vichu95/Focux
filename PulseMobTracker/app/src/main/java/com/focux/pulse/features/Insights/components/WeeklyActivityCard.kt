package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focux.pulse.data_manager.WeeklyActivityData
import com.focux.pulse.ui.theme.*

@Composable
fun WeeklyActivityCard(data: WeeklyActivityData) {
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(
                horizontal = PulseAppPaddingMedium,
                vertical = PulseAppPaddingSmall
            )
    ) {
        Text(
            text = "Weekly Activity",
            style = PulseAppFontLabel.copy(color = PulseAppColorPrimary)
        )
        
        Spacer(modifier = Modifier.height(PulseAppPaddingRegular)) // 10dp

        // Total Time
        Row(verticalAlignment = Alignment.CenterVertically) {
             Text(
                text = "Total: ${data.totalTime}",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = PulseAppFontSizeDisplay, // 36sp
                    color = PulseAppColorSecondary,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        // CSS implies 0px gap between Total and List container if we follow Frame heights strictly.
        // Frame 48 (111px) = Total (48px) + List (63px). 48+63=111. So 0 gap.
        
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            WeeklyActivityItem("Productive", data.productive)
            WeeklyActivityItem("Neutral", data.neutral)
            WeeklyActivityItem("Distracting", data.distracting)
        }
    }
}

@Composable
private fun WeeklyActivityItem(label: String, value: String) {
    Text(
        text = "• $label : $value",
        style = PulseAppFontBody, // 14sp
        color = PulseAppColorSecondary
    )
}

