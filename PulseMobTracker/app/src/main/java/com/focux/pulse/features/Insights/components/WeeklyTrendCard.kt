package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.data.WeeklyTrendItem
import com.focux.pulse.ui.theme.*
import kotlin.math.max

@Composable
fun WeeklyTrendCard(data: List<WeeklyTrendItem>) {
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(16.dp)
    ) {
        Text(
            text = "Weekly Activity Trend",
            style = PulseAppFontLabel.copy(color = PulseAppColorPrimary)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bar Chart Area
        val maxHours = data.maxOfOrNull { it.hours }?.coerceAtLeast(0.1f) ?: 1f
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { item ->
                TrendBar(item, maxHours)
            }
        }
    }
}

@Composable
fun TrendBar(item: WeeklyTrendItem, maxHours: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Calculate Height
        val barMaxHeight = 150.dp
        // Largest is 95% of height.
        val fraction = (item.hours / maxHours) * 0.95f
        // Min height ensuring text readability (approx 15-20%)
        val minFraction = 0.2f 
        val actualFraction = max(fraction, minFraction)
        
        // Bar Box
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(barMaxHeight * actualFraction)
                .background(
                    color = if (item.isSelected) PulseAppColorPrimary else PulseAppColorPrimary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Rotated Text
            Text(
                text = item.durationText,
                style = PulseAppFontFocus.copy(fontSize = 10.sp),
                color = if (item.isSelected) Color.Black else PulseAppColorSecondary, 
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .rotate(-90f),
                maxLines = 1
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Day Label
        Text(
            text = item.day,
            style = PulseAppFontFocus.copy(fontSize = 12.sp),
            color = PulseAppColorSecondary
        )
    }
}
