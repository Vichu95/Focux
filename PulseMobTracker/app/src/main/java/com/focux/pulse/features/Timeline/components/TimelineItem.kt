package com.focux.pulse.features.Timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.R
import com.focux.pulse.data.TimelineEvent
import com.focux.pulse.ui.theme.*

@Composable
fun TimelineItem(event: TimelineEvent, isFirst: Boolean, isLast: Boolean) {
    when {
        event.isDeepWork -> DeepWorkTimelineItem(isFirst, isLast, event)
        event.app.name == "Morning" -> WakeUpTimelineItem(isFirst, isLast, event)
        event.app.name == "Night" -> SleepTimelineItem(isFirst, isLast, event)
        else -> AppTimelineItem(isFirst, isLast, event)
    }
}

@Composable
fun AppTimelineItem(isFirst: Boolean, isLast: Boolean, event: TimelineEvent) {
    // Frame Timeline Application: 412x158, padding: 0px 48px, gap: 32px
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp)
            .padding(start = 48.dp, end = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 20: Line column (20px wide)
        Column(
            modifier = Modifier.width(20.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Upper solid line (Rectangle 2)
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .weight(1f)
                        .background(Color.White)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Ellipse 1: Node (20x20)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
            )

            // Lower solid line (Rectangle 3)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .weight(1f)
                        .background(Color.White)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Frame 17: App Card (251x128)
        AppTimelineCard(event)
    }
}

@Composable
fun WakeUpTimelineItem(isFirst: Boolean, isLast: Boolean, event: TimelineEvent) {
    // Frame Timeline Wake Up: 412x124, padding: 0px 16px 0px 48px, gap: 93px
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .padding(start = 48.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 15: Dashed line column (19px wide)
        Column(
            modifier = Modifier.width(19.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Line 1: Dashed line upper
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier.width(1.dp).weight(1f)
            )

            // Line 2: Dashed line lower
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier.width(1.dp).weight(1f)
            )
        }

        // Frame Deep Work Text: 329x124
        Row(
            modifier = Modifier.width(329.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Frame 18: Sun icon + time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 50.dp)
            ) {
                // Sun icon (48x49)
                Image(
                    painter = painterResource(id = R.drawable.sun_icon),
                    contentDescription = "Morning",
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // 06:00
                Text(
                    text = event.time,
                    style = Typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = PulseAppColorPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun SleepTimelineItem(isFirst: Boolean, isLast: Boolean, event: TimelineEvent) {
    // Frame Timeline Sleep: Same structure as Wake Up
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .padding(start = 48.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 15: Dashed line column (19px wide)
        Column(
            modifier = Modifier.width(19.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier.width(1.dp).weight(1f)
            )
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier.width(1.dp).weight(1f)
            )
        }

        // Content
        Row(
            modifier = Modifier.width(329.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 50.dp)
            ) {
                // Moon icon
                Image(
                    painter = painterResource(id = R.drawable.moon_icon),
                    contentDescription = "Night",
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // 22:00
                Text(
                    text = event.time,
                    style = Typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = PulseAppColorPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun DeepWorkTimelineItem(isFirst: Boolean, isLast: Boolean, event: TimelineEvent) {
    // Frame Timeline Deep Work: 412x124, padding: 0px 16px 0px 48px
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .padding(start = 48.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 15: Dashed line column (19px wide)
        Column(
            modifier = Modifier.width(19.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier.width(1.dp).weight(1f)
            )
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier.width(1.dp).weight(1f)
            )
        }

        // Frame Deep Work Text: 329x124, padding: 0px 46px, gap: 3px
        Column(
            modifier = Modifier.width(329.dp).fillMaxHeight().padding(horizontal = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Frame 18: Icon + Duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Leaf icon (26x21)
                Image(
                    painter = painterResource(id = R.drawable.deep_work_icon),
                    contentDescription = "Deep Work",
                    modifier = Modifier.size(width = 26.dp, height = 21.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // 45m
                Text(
                    text = event.deepWorkDuration ?: "45m",
                    style = Typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = PulseAppColorPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            // Deep Work label
            Text(
                text = "Deep Work",
                style = Typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = PulseAppColorPrimary
                )
            )
        }
    }
}

@Composable
fun DashedLineVertical(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 2f,
            pathEffect = pathEffect
        )
    }
}

@Composable
fun AppTimelineCard(event: TimelineEvent) {
    // Frame 17: 251x128, border: 2px solid #64B5F6, border-radius: 12px
    Column(
        modifier = Modifier
            .width(251.dp)
            .height(128.dp)
            .background(Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Instagram (24px, 700 weight)
        Text(
            text = event.app.name,
            style = Typography.bodyLarge.copy(
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.White
            )
        )

        // 14:02 - 14:16 (12px)
        Text(
            text = event.range,
            style = Typography.labelSmall.copy(
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.White
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        // Frame 16: Categorization Pill (227x32)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .border(PulseAppBorderWidth, PulseAppColorPrimary, androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (event.app.type.toString() == "Distracting") "Distracting" else "Productive",
                    style = Typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = PulseAppColorPrimary
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Edit",
                    tint = PulseAppColorPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
