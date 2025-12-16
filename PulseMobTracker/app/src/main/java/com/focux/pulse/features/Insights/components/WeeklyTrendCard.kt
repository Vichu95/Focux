package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.data.WeeklyTrendItem
import com.focux.pulse.ui.theme.CyberpunkPrimary
import com.focux.pulse.ui.theme.CyberpunkSecondaryText
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.cyberpunkCard

@Composable
fun WeeklyTrendCard(data: List<WeeklyTrendItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberpunkCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Weekly Activity Trend",
            style = Typography.titleLarge,
            color = CyberpunkPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bar Chart Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp), // Fixed height for chart
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { item ->
                TrendBar(item)
            }
        }
    }
}

@Composable
fun TrendBar(item: WeeklyTrendItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Bar
        val maxHours = 14f // Assumed max for scaling
        val heightFraction = (item.hours / maxHours).coerceIn(0.1f, 1f)
        
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(heightFraction)
                .background(
                    if (item.isSelected) CyberpunkPrimary else CyberpunkPrimary.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                )
        ) {
             // Text inside bar? Screenshot has text INSIDE or ON TOP depending on size. 
             // Screenshot has vertical text "2h 48m" inside the bar.
             // We'll skip complex vertical text for this first pass or try to rotate if easy, 
             // but cleaner to just have it visual for now.
             // Let's add simple hours if selected or just leave it visual.
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Day Label
        Text(
            text = item.day,
            style = Typography.labelSmall.copy(fontSize = 12.sp),
            color = CyberpunkSecondaryText
        )
    }
}
