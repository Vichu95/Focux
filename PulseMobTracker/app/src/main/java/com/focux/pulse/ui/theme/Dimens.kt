package com.focux.pulse.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Padding
val PulseAppPaddingSmall = 8.dp
val PulseAppPaddingMedium = 16.dp
val PulseAppPaddingLarge = 32.dp
val PulseAppGapMedium = 20.dp
val PulseAppPaddingRegular = 10.dp
val PulseAppGapSmall = 4.dp
val PulseAppGapTiny = 5.dp
val PulseAppDividerHeight = 97.dp
val PulseAppRoutineAppTextWidth = 110.dp

// Corner Radius
val PulseAppCornerRadiusMedium = 12.dp
val PulseAppCornerRadiusLarge = 16.dp

// Icon Sizes
val PulseAppIconSizeSmall = 24.dp
val PulseAppIconSizeMedium = 36.dp
val PulseAppIconSizeLarge = 48.dp

// Specific Elements
val PulseAppPaddingTiny = 6.dp

// Borders
val PulseAppBorderWidth = 1.dp
val PulseAppBorderWidthThick = 2.dp

// Card Dimensions
val PulseAppCardWidth = 380.dp
val PulseAppCardPadding = PulseAppPaddingMedium

// Text Sizes
val PulseAppFontSizeHeader = 24.sp
val PulseAppFontSizeDisplay = 36.sp
val PulseAppFontSizeLabel = 20.sp
val PulseAppFontSizeFocus = 15.sp
val PulseAppFontSizeSubHeader = 16.sp
val PulseAppFontSizeBody = 14.sp
val PulseAppFontSizeBigNumber = 36.sp
val PulseAppFontSizeSmall = 12.sp

// Bottom Bar
val PulseAppBottomBarHeight = 88.dp
val PulseAppBottomBarBottomPadding = 0.dp


//Summary Card
//Offline Streak Card
val PulseAppOfflineStreakCardHeight = 143.dp
//Device Access Card
val PulseAppDeviceAccessCardHeight = 171.dp
//First and Last App Card
val PulseAppFirstAndLastAppCardHeight = 171.dp
// Phone Activity Card
val PulseAppPhoneActivityCardHeight = 203.dp
// Focus Score Card
val PulseAppFocusScoreCardHeight = 38.dp

// Timeline Dimensions
val PulseAppTimelineCardWidth = 251.dp
val PulseAppTimelineCardHeight = 128.dp
val PulseAppTimelineAppItemHeight = 158.dp
val PulseAppTimelineFactItemHeight = 124.dp
val PulseAppTimelineAppTypeWidth = 227.dp
val PulseAppTimelineLineColumnWidth = 20.dp
val PulseAppTimelineLineColumnWidthDashed = 19.dp
val PulseAppTimelineNodeSize = 20.dp
val PulseAppTimelineFactContentWidth = 329.dp
val PulseAppTimelineFactIconSize = 48.dp
val PulseAppTimelineDeepWorkIconWidth = 26.dp
val PulseAppTimelineDeepWorkIconHeight = 21.dp

// Header & Navigation
val PulseAppGapHeader = 11.dp
val PulseAppIconSizeNavigation = 36.dp

// Timeline Specifics
val PulseAppTimelineDashIntervalOn = 10f
val PulseAppTimelineDashIntervalOff = 10f

// Weekly Trend Specifics
val PulseAppWeeklyTrendBarWidth = 22.dp
val PulseAppWeeklyTrendMinHeight = 6.dp
val PulseAppWeeklyTrendMaxHeight = 110.dp
val PulseAppWeeklyTrendTextThreshold = 50.dp



// Data Logging
val PulseAppDataLoggingFrequency = 15

/**
 * Jitter threshold in milliseconds.
 * Events within this threshold are considered "jitter" and merged.
 * Based on observed timestamp differences of ~2ms for jitter events.
 */
const val PULSE_JITTER_THRESHOLD_MS = 10L

/**
 * Launcher app package name.
 * This app is always running on home screen and should be ignored for UNLOCK_APP detection.
 */
const val PULSE_LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"

/**
 * Additional apps to ignore for screen session detection.
 * These apps won't trigger UNLOCK_APP when opened.
 * User can add custom packages here in the future.
 */
val PULSE_IGNORED_APPS: Set<String> = setOf(
    PULSE_LAUNCHER_PACKAGE,
    // Add more packages here as needed
)