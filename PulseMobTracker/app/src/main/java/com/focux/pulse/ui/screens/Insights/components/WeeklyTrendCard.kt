package com.focux.pulse.ui.screens.Insights.components

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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.utilities.WeeklyTrendItem
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
        val maxHours = data.maxOfOrNull { it.hours } ?: 1f
        val minHours = data.minOfOrNull { it.hours } ?: 0f
        val valueRange = (maxHours - minHours).coerceAtLeast(0.1f)
        
        // Visual Constants
        val minBarHeight = PulseAppWeeklyTrendMinHeight
        val maxBarHeight = PulseAppWeeklyTrendMaxHeight
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { item ->
                // Linear projection
                val fraction = ((item.hours - minHours) / valueRange).coerceIn(0f, 1f)
                val barHeight = minBarHeight + (maxBarHeight - minBarHeight) * fraction
                
                TrendBar(item, barHeight)
            }
        }
    }
}

@Composable
fun TrendBar(item: WeeklyTrendItem, height: Dp) {
    // Threshold for text inside/outside
    val isTextInside = height >= PulseAppWeeklyTrendTextThreshold
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // If Text Outside, place it here
        if (!isTextInside) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.durationText,
                    style = PulseAppFontFocus.copy(fontSize = 10.sp),
                    color = PulseAppColorPrimary,
                    modifier = Modifier.vertical()
                )
                Spacer(modifier = Modifier.height(6.dp)) // Gap between text and bar
            }
        }
    
        // Bar Box
        Box(
            modifier = Modifier
                .width(PulseAppWeeklyTrendBarWidth)
                .height(height)
                .background(
                    color = PulseAppColorPrimary,
                    shape = RoundedCornerShape(2.dp)
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // If Text Inside, place it here with bottom spacer
            if (isTextInside) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.durationText,
                        style = PulseAppFontFocus.copy(fontSize = 10.sp),
                        color = PulseAppColorSurface, 
                        modifier = Modifier.vertical()
                    )
                    Spacer(modifier = Modifier.height(6.dp)) // Bottom padding
                }
            }
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

private fun Modifier.vertical() = layout { measurable, constraints ->
    // Measure text with swapped constraints to allow full length
    val placeable = measurable.measure(constraints.copy(
        maxWidth = constraints.maxHeight,
        maxHeight = constraints.maxWidth,
        minWidth = 0,
        minHeight = 0
    ))
    
    // After -90 rotation: text width becomes height, text height becomes width
    val layoutWidth = placeable.height  // Rotated text height is new width
    val layoutHeight = placeable.width  // Rotated text width is new height
    
    layout(layoutWidth, layoutHeight) {
        // Center the placeable in the layout bounds, then rotate
        placeable.placeWithLayer(
            x = (layoutWidth - placeable.width) / 2,
            y = (layoutHeight - placeable.height) / 2
        ) {
            rotationZ = -90f
        }
    }
}

