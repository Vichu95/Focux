package com.focux.pulse.ui.screens.Timeline.components

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
import androidx.compose.ui.layout.onGloballyPositioned
import com.focux.pulse.R
import com.focux.pulse.utilities.ActivityType
import com.focux.pulse.utilities.TimelineEvent
import com.focux.pulse.ui.theme.*

@Composable
fun TimelineItem(event: TimelineEvent, isFirst: Boolean, isLast: Boolean, isEditMode: Boolean) {
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
        else -> AppTimelineItem(isFirst, isLast, event, isEditMode)
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
    primaryText: String, // Time or Duration (e.g. "06:00" or "45m")
    secondaryText: String // Title (e.g. "First Usage...", "Deep Work")
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppTimelineFactItemHeight)
            .padding(start = PulseAppIconSizeLarge, end = PulseAppPaddingMedium),
        horizontalArrangement = Arrangement.Start, // Changed to Start
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 15 / Icon Column: 20px width (matches App Item Line Column width)
        // This centers the Fact Icon on the timeline axis
        // Frame 15 / Icon Column: 20px wide
        // Replaced Box with Column to ensure dashed lines are interrupted by the icon (not drawn behind it)
        Column(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Upper Dashed Line
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f)
            )
            
            // Icon (Sun/Moon/Leaf)
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = secondaryText,
                modifier = if (iconHeight != null) {
                    Modifier.size(width = iconSize, height = iconHeight)
                } else {
                    Modifier.size(iconSize)
                }
            )

            // Lower Dashed Line
            DashedLineVertical(
                color = Color.White,
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f)
            )
        }

        // Gap between Line/Icon and Text
        Spacer(modifier = Modifier.width(16.dp)) 

        // Text Column: Title (Top), Time (Bottom)
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Secondary Text (Title) - Top
            Text(
                text = secondaryText,
                style = Typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = PulseAppColorPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(3.dp))
            
            // Primary Text (Time/Duration) - Bottom
            Text(
                text = primaryText,
                style = Typography.bodyLarge.copy(
                    fontSize = 12.sp,
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
fun AppTimelineItem(isFirst: Boolean, isLast: Boolean, event: TimelineEvent, isEditMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppTimelineAppItemHeight)
            .padding(start = PulseAppIconSizeLarge, end = PulseAppIconSizeLarge),
        horizontalArrangement = Arrangement.spacedBy(4.dp), // Reduced from PulseAppPaddingLarge (32dp) to match CSS gap 4px
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame 20: Solid line column (20px wide)
        SolidLineColumn(isFirst, isLast, packageName = event.app.iconName)

        // Frame 17: App Card (Flexible Width)
        AppTimelineCard(
            event = event,
            isEditMode = isEditMode,
            modifier = Modifier.weight(1f) // Fill remaining space
        )
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
fun SolidLineColumn(isFirst: Boolean, isLast: Boolean, packageName: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
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

        // Node (App Icon or Circle)
        if (packageName != null) {
            // Render App Icon
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier
                    .size(PulseAppTimelineNodeSize)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White),
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    val icon = com.focux.pulse.utilities.AppInfoHelper.getAppIcon(context, packageName)
                    imageView.setImageDrawable(icon)
                }
            )
        } else {
            // Fallback Circle
            Box(
                modifier = Modifier
                    .size(PulseAppTimelineNodeSize)
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
            )
        }

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
fun AppTimelineCard(event: TimelineEvent, isEditMode: Boolean, modifier: Modifier = Modifier) {
    var isSelecting by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(event.app.type ?: ActivityType.Neutral) }
    
    // Frame 17: Flexible Width Card (Dynamic Height)

    // Frame 17: Flexible Width Card (Dynamic Height)
    // CSS Requirement: No border on the card itself
    Box(
        modifier = modifier
            .heightIn(min = 56.dp) // CSS says Height 56px
            .background(
                Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium)
            )
            // Removed Border as per user request
    ) {
        // LAYER 1: Standard Card Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 12.dp), // CSS: padding 4px 12px
            verticalArrangement = Arrangement.spacedBy(4.dp) // CSS: gap 4px
        ) {
            // App Name (Frame 58)
            Text(
                text = event.app.name,
                style = Typography.bodyLarge.copy(
                    fontSize = 18.sp, // CSS: 18px
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.White
                )
            )

            // Row (Frame 59): Time Range + Conditional Pill
            // Fixed height 20dp to prevent time shift when Pill appears
            Row(
                modifier = Modifier.height(20.dp), // CSS Frame 59 height
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Time Range
                Text(
                    text = event.range,
                    style = Typography.labelSmall.copy(
                        fontSize = 12.sp, // CSS: 12px
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color.White
                    )
                )

                // Conditional Pill (Frame 16) - "To right of time"
                if (isEditMode) {
                    Spacer(modifier = Modifier.width(8.dp)) // Reduced gap
                    
                    Box(
                        modifier = Modifier
                            
                            .width(150.dp) // Adjusted to 150dp
                            .height(20.dp)
                            .border(
                                1.dp,
                                PulseAppColorPrimary,
                                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { isSelecting = true }
                            ,
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp) // Slight padding for arrow
                        ) {
                            Text(
                                text = selectedType.name,
                                style = Typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = PulseAppColorPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f) // Text takes available space, pushing arrow to end
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change Category",
                                tint = PulseAppColorPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // LAYER 2: Drawer in Popup for native dismiss behavior
                        if (isSelecting && isEditMode) {
                            
                            androidx.compose.ui.window.Popup(
                                popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                                    override fun calculatePosition(
                                        anchorBounds: androidx.compose.ui.unit.IntRect,
                                        windowSize: androidx.compose.ui.unit.IntSize,
                                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                        popupContentSize: androidx.compose.ui.unit.IntSize
                                    ): androidx.compose.ui.unit.IntOffset {
                                        // Position popup directly below the anchor, align left edge
                                        // Since popup width matches button width, this is "right under"
                                    val x = anchorBounds.left
                                    val y = anchorBounds.top // Start at top-left of the button (overlaying it)
                                    return androidx.compose.ui.unit.IntOffset(x, y)
                                }
                            },
                            onDismissRequest = { isSelecting = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(150.dp) // Match button width 150dp
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
                                                .height(30.dp) // Reasonable tap target
                                                .clickable {
                                                    selectedType = type
                                                    isSelecting = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = type.name,
                                                style = Typography.labelSmall.copy(
                                                    fontSize = 12.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    color = if (type == selectedType) PulseAppColorPrimary else Color.Gray
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


    }
}
