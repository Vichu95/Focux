package com.focux.pulse.ui.screens.Timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.ui.screens.components.DaySelector // Fix: Updated package
import com.focux.pulse.ui.theme.*
import java.time.LocalDate

@Composable
fun TimelineHeader(
    date: LocalDate,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    canGoNext: Boolean,
    canGoPrev: Boolean,
    onFilterClick: () -> Unit
) {


    // Frame Date Filter
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppFocusScoreCardHeight), // Matching Summary Header Height (38dp)
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Date Selector (Frame Summary Date)
        DaySelector(
            date = date,
            onPrevClick = onPrevClick,
            onNextClick = onNextClick,
            canGoNext = canGoNext,
            canGoPrev = canGoPrev
        )

        // Right: Filter Button (Frame 22)
        androidx.compose.material3.Surface(
            color = PulseAppColorSurface, // #2B4555
            shape = androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium),
            modifier = Modifier
                .width(PulseAppTimelineFilterButtonWidth)
                .height(PulseAppTimelineFilterButtonHeight)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
                .clickable { onFilterClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Icon
                Icon(
                    painter = painterResource(id = com.focux.pulse.R.drawable.filter_icon),
                    contentDescription = "Filter",
                    tint = PulseAppColorPrimary, // #64B5F6
                    modifier = Modifier.size(PulseAppTimelineFilterIconSize)
                )
                Spacer(modifier = Modifier.width(PulseAppPaddingSmall))
                // Text
                Text(
                    text = "Filter",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 15.sp, // PulseAppFontSizeFocus is 15.sp
                        color = PulseAppColorSecondary
                    )
                )
            }
        }
    }
}
