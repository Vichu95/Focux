package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.data.WeeklyActivityData
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.pulseAppCard

@Composable
fun WeeklyActivityCard(data: WeeklyActivityData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pulseAppCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Weekly Activity",
            style = Typography.titleLarge,
            color = PulseAppColorPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
             Text(
                text = "Total:",
                style = Typography.titleLarge,
                color = PulseAppColorSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = data.totalTime,
                style = Typography.titleLarge.copy(fontSize = 32.sp),
                color = PulseAppColorSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column {
            Text(text = "• Productive : ${data.productive}", style = Typography.labelSmall, color = PulseAppColorSecondary)
            Text(text = "• Neutral : ${data.neutral}", style = Typography.labelSmall, color = PulseAppColorSecondary)
            Text(text = "• Distracting : ${data.distracting}", style = Typography.labelSmall, color = PulseAppColorSecondary)
        }
    }
}
