package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.data.WeeklyActivityData
import com.focux.pulse.ui.theme.CyberpunkPrimary
import com.focux.pulse.ui.theme.CyberpunkSecondaryText
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.cyberpunkCard

@Composable
fun WeeklyActivityCard(data: WeeklyActivityData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberpunkCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Weekly Activity",
            style = Typography.titleLarge,
            color = CyberpunkPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
             Text(
                text = "Total:",
                style = Typography.titleLarge,
                color = CyberpunkSecondaryText,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = data.totalTime,
                style = Typography.titleLarge.copy(fontSize = 32.sp),
                color = CyberpunkSecondaryText
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column {
            Text(text = "• Productive : ${data.productive}", style = Typography.labelSmall, color = CyberpunkSecondaryText)
            Text(text = "• Neutral : ${data.neutral}", style = Typography.labelSmall, color = CyberpunkSecondaryText)
            Text(text = "• Distracting : ${data.distracting}", style = Typography.labelSmall, color = CyberpunkSecondaryText)
        }
    }
}
