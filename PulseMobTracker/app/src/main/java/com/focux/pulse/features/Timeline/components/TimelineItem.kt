package com.focux.pulse.features.Timeline.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import com.focux.pulse.R
import com.focux.pulse.data.TimelineEvent
import com.focux.pulse.ui.theme.*

@Composable
fun TimelineItem(event: TimelineEvent, isFirst: Boolean, isLast: Boolean) {
    // Layout:
    // Left: Vertical Line (Dashed?) + Node Dot
    // Right: Content (Card or Icon+Text)

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    ) {
        // Left Column: The Line and Node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            // Upper Line
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(Color.Gray.copy(alpha = 0.5f)) // Dashed effect needs custom draw, simple line for alpha now
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // The Node
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.LightGray, androidx.compose.foundation.shape.CircleShape)
            )

            // Lower Line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(Color.Gray.copy(alpha = 0.5f))
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Right Column: Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 32.dp, start = 8.dp) // Spacing between items
        ) {
            when {
                event.isDeepWork -> DeepWorkItem(event)
                event.app.name == "Morning" -> MorningItem(event)
                event.app.name == "Night" -> NightItem(event)
                else -> AppTimelineCard(event)
            }
        }
    }
}

@Composable
fun AppTimelineCard(event: TimelineEvent) {
    // Frame 17 Spec: 251x128
    Column(
        modifier = Modifier
            .width(PulseAppTimelineCardWidth)
            .height(PulseAppTimelineCardHeight)
            .background(Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // App Name (Instagram)
        Text(
            text = event.app.name,
            style = PulseAppFontHeader, // 24sp
            color = PulseAppColorSecondary // White
        )

        // Time Range (14:02 - 14:16)
        Text(
            text = event.range, // e.g. "14:02 - 14:16"
            style = Typography.labelSmall.copy(fontSize = PulseAppFontSizeSmall), // 12sp
            color = PulseAppColorSecondary
        )

        Spacer(modifier = Modifier.weight(1f)) // Push pill to bottom if space remains, or just standard gap

        // Categorization Pill (Frame 16)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .border(PulseAppBorderWidth, PulseAppColorPrimary, androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
                //.padding(horizontal = 12.dp, vertical = 6.dp) // Auto-layout padding logic handled by alignment usually, but box content alignment is key
                .padding(horizontal = 12.dp), // Inner padding for text/icon
             contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (event.app.type.toString() == "Distracting") "Distracting" else "Productive",
                    style = Typography.labelSmall.copy(fontSize = PulseAppFontSizeSmall), // 12sp
                    color = PulseAppColorPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Dropdown Icon
                androidx.compose.material3.Icon(
                     imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                     contentDescription = "Edit",
                     tint = PulseAppColorPrimary,
                     modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DeepWorkItem(event: TimelineEvent) {
    // Just Icon and Text centered/aligned
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.deep_work_icon), // Ensure mapped to leaf/plant
                contentDescription = "Deep Work",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = event.deepWorkDuration ?: "45m",
                style = Typography.titleLarge,
                color = PulseAppColorPrimary
            )
        }
        Text(
            text = "Deep Work",
            style = Typography.labelSmall.copy(color = PulseAppColorPrimary),
        )
    }
}

@Composable
fun MorningItem(event: TimelineEvent) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(id = R.drawable.sun_icon),
            contentDescription = "Morning",
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = event.time,
            style = Typography.titleLarge.copy(color = PulseAppColorPrimary)
        )
    }
}

@Composable
fun NightItem(event: TimelineEvent) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(id = R.drawable.moon_icon),
            contentDescription = "Night",
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = event.time,
            style = Typography.titleLarge.copy(color = PulseAppColorPrimary)
        )
    }
}
