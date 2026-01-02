package com.focux.pulse.features.Timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focux.pulse.R
import com.focux.pulse.data.ActivityType
import com.focux.pulse.data.TimelineEvent
import com.focux.pulse.ui.theme.*

@Composable
fun TimelineItem(event: TimelineEvent, isFirst: Boolean, isLast: Boolean) {
    when {
        event.app.name == "Morning" -> TimelineFactItem(
            isFirst = isFirst,
            isLast = isLast,
            iconRes = R.drawable.sun_icon,
            iconSize = PulseAppTimelineFactIconSize,
            primaryText = event.time,
            secondaryText = "First Usage of Phone"
        )
        event.app.name == "Night" -> TimelineFactItem(
            isFirst = isFirst,
            isLast = isLast,
            iconRes = R.drawable.moon_icon,
            iconSize = PulseAppTimelineFactIconSize,
            primaryText = event.time,
            secondaryText = "Last Usage of Phone"
        )
        event.isDeepWork -> TimelineFactItem(
            isFirst = isFirst,
            isLast = isLast,
            iconRes = R.drawable.deep_work_icon,
            iconSize = PulseAppTimelineDeepWorkIconWidth, // Will be overridden
            iconHeight = PulseAppTimelineDeepWorkIconHeight,
            primaryText = event.deepWorkDuration ?: "45m",
            secondaryText = "Deep Work"
        )
        else -> AppTimelineItem(isFirst, isLast, event)
    }
}

/**
 * Generic Timeline Fact Item for First Usage, Deep Work, Last Usage
 * Frame Timeline Wake Up/Deep Work/Sleep: 412x124, padding: 0px 16px 0px 48px
 */
@Composable
fun TimelineFactItem(
    isFirst: Boolean,
    isLast: Boolean,
    iconRes: Int,
    iconSize: Dp,
    iconHeight: Dp? = null,
    primaryText: String,
    secondaryText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppTimelineFactItemHeight)
            .padding(start = PulseAppIconSizeLarge, end = PulseAppPaddingMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 15: Dashed line column (19px wide)
        DashedLineColumn(isFirst, isLast)

        // Frame Deep Work Text / Frame 18: Content (329x124)
        Column(
            modifier = Modifier
                .width(PulseAppTimelineFactContentWidth)
                .fillMaxHeight()
                .padding(horizontal = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon + Primary Text Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = secondaryText,
                    modifier = if (iconHeight != null) {
                        Modifier.size(width = iconSize, height = iconHeight)
                    } else {
                        Modifier.size(iconSize)
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = primaryText,
                    style = Typography.bodyLarge.copy(
                        fontSize = PulseAppFontSizeLabel,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = PulseAppColorPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            // Secondary Text Label
            Text(
                text = secondaryText,
                style = Typography.bodyLarge.copy(
                    fontSize = PulseAppFontSizeSubHeader,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = PulseAppColorPrimary
                )
            )
        }
    }
}

/**
 * Timeline App Item (Instagram, Gmail, etc.)
 * Frame Timeline Application: 412x158, padding: 0px 48px, gap: 32px
 */
@Composable
fun AppTimelineItem(isFirst: Boolean, isLast: Boolean, event: TimelineEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppTimelineAppItemHeight)
            .padding(start = PulseAppIconSizeLarge, end = PulseAppIconSizeLarge),
        horizontalArrangement = Arrangement.spacedBy(PulseAppPaddingLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 20: Solid line column (20px wide)
        SolidLineColumn(isFirst, isLast)

        // Frame 17: App Card (251x128)
        AppTimelineCard(event)
    }
}

@Composable
fun DashedLineColumn(isFirst: Boolean, isLast: Boolean) {
    Column(
        modifier = Modifier
            .width(PulseAppTimelineLineColumnWidthDashed)
            .fillMaxHeight(),
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
}

@Composable
fun SolidLineColumn(isFirst: Boolean, isLast: Boolean) {
    Column(
        modifier = Modifier
            .width(PulseAppTimelineLineColumnWidth)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Upper solid line
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

        // Node (20x20)
        Box(
            modifier = Modifier
                .size(PulseAppTimelineNodeSize)
                .background(Color.White, androidx.compose.foundation.shape.CircleShape)
        )

        // Lower solid line
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f)
                    .background(PulseAppColorSecondary)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun DashedLineVertical(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(PulseAppTimelineDashIntervalOn, PulseAppTimelineDashIntervalOff), 0f)
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
    var isSelecting by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(event.app.type ?: ActivityType.Neutral) }

    // Frame 17: 251x128
    Box(
        modifier = Modifier
            .width(PulseAppTimelineCardWidth)
            .height(PulseAppTimelineCardHeight)
            .background(
                Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium)
            )
            .border(
                PulseAppBorderWidthThick,
                PulseAppColorPrimary,
                androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium)
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
    ) {
        // LAYER 1: Standard Card Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = PulseAppPaddingMedium, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(PulseAppPaddingSmall)
        ) {
            // App Name
            Text(
                text = event.app.name,
                style = Typography.bodyLarge.copy(
                    fontSize = PulseAppFontSizeHeader,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.White
                )
            )

            // Time Range
            Text(
                text = event.range,
                style = Typography.labelSmall.copy(
                    fontSize = PulseAppFontSizeSmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // Categorization Pill (Click to open drawer)
            Box(
                modifier = Modifier
                    .width(227.dp)
                    .height(32.dp)
                    .border(
                        PulseAppBorderWidth,
                        PulseAppColorPrimary,
                        androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium)
                    )
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
                    .clickable { isSelecting = true }
                    .padding(horizontal = 12.dp, vertical = PulseAppPaddingTiny),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = selectedType.name,
                        style = Typography.labelSmall.copy(
                            fontSize = PulseAppFontSizeSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = PulseAppColorPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Change Category",
                        tint = PulseAppColorPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // LAYER 2: Drawer in Popup for native dismiss behavior
        if (isSelecting) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val drawerHeightPx = with(density) { 82.dp.toPx().toInt() }
            val cardHeightPx = with(density) { PulseAppTimelineCardHeight.toPx().toInt() }
            val cardWidthPx = with(density) { PulseAppTimelineCardWidth.toPx().toInt() }
            
            androidx.compose.ui.window.Popup(
                popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize
                    ): androidx.compose.ui.unit.IntOffset {
                        // Position popup at bottom of anchor, centered horizontally
                        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                        val y = anchorBounds.bottom - popupContentSize.height
                        return androidx.compose.ui.unit.IntOffset(x, y)
                    }
                },
                onDismissRequest = { isSelecting = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(PulseAppTimelineAppTypeWidth) // 227dp - matches card content area
                        .height(82.dp)
                        .background(PulseAppColorBackground)
                        .border(
                            width = PulseAppBorderWidth,
                            color = PulseAppColorPrimary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium)
                        )
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium)),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ActivityType.values().forEach { type ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable {
                                    selectedType = type
                                    isSelecting = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.name,
                                style = Typography.labelSmall.copy(
                                    fontSize = (PulseAppFontSizeSmall.value + 2).sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = if (type == selectedType) PulseAppColorPrimary else Color.Gray
                                )
                            )
                        }
                        // Subtle separator
                        if (type != ActivityType.values().last()) {
                            Box(modifier = Modifier.fillMaxWidth(0.8f).height(1.dp).background(PulseAppColorPrimary.copy(alpha = 0.1f)))
                        }
                    }
                }
            }
        }
    }
}
