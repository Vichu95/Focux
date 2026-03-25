package com.focux.pulse.ui.screens.Timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onFilterClick: () -> Unit,
    isSortDescending: Boolean,
    onSortToggleClick: () -> Unit
) {
    // Frame Date Filter
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppWeeklyHeaderHeight) // 42dp
            .padding(top = PulseAppWeeklyHeaderPaddingTop), // 4dp
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

        // Right: Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sort Button
            androidx.compose.material3.Surface(
                color = PulseAppColorSurface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium),
                modifier = Modifier
                    .width(36.dp)
                    .height(PulseAppTimelineFilterButtonHeight)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
                    .clickable { onSortToggleClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSortDescending) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Toggle Sort",
                        tint = PulseAppColorPrimary,
                        modifier = Modifier.size(PulseAppTimelineFilterIconSize)
                    )
                }
            }
            
            // Button 1: Edit / Close
            androidx.compose.material3.Surface(
                color = PulseAppColorSurface, // #2B4555
                shape = androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium),
                modifier = Modifier
                    .width(36.dp) 
                    .height(PulseAppTimelineFilterButtonHeight)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
                    .clickable { onEditClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = if (isEditMode) com.focux.pulse.R.drawable.close_icon else com.focux.pulse.R.drawable.edit_icon),
                        contentDescription = if (isEditMode) "Close" else "Edit",
                        tint = PulseAppColorPrimary,
                        modifier = Modifier.size(PulseAppTimelineFilterIconSize)
                    )
                }
            }

            // Button 2: Filter / Save
            androidx.compose.material3.Surface(
                color = PulseAppColorSurface, // #2B4555
                shape = androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium),
                modifier = Modifier
                    .width(36.dp)
                    .height(PulseAppTimelineFilterButtonHeight)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(PulseAppCornerRadiusMedium))
                    .clickable { 
                        if (isEditMode) onSaveClick() else onFilterClick() 
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = if (isEditMode) com.focux.pulse.R.drawable.save_icon else com.focux.pulse.R.drawable.filter_icon),
                        contentDescription = if (isEditMode) "Save" else "Filter",
                        tint = PulseAppColorPrimary,
                        modifier = Modifier.size(PulseAppTimelineFilterIconSize)
                    )
                }
            }
        }
    }
}
