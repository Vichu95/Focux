package com.focux.pulse.ui.screens.Summary.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier // Fix: Added missing import
import com.focux.pulse.ui.screens.components.DaySelector // Fix: Updated package
import com.focux.pulse.ui.theme.*
import java.time.LocalDate

@Composable
fun DateFocusHeader(
    date: LocalDate,
    focusScore: Int,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    canGoNext: Boolean = true,
    canGoPrev: Boolean = true
) {


    // Frame Date Focus Score (Width 380px)
    Row(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppFocusScoreCardHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Date Nav (Frame Summary Date)
        DaySelector(
            date = date,
            onPrevClick = onPrevClick,
            onNextClick = onNextClick,
            canGoNext = canGoNext,
            canGoPrev = canGoPrev
        )
        
        // Right: Focus Score (Frame 22)
        Box(
            modifier = Modifier
                .width(PulseAppFocusScoreButtonWidth)
                .height(PulseAppFocusScoreButtonHeight)
                .background(PulseAppColorSurface, RoundedCornerShape(PulseAppCornerRadiusMedium))
                .padding(horizontal = PulseAppPaddingSmall, vertical = PulseAppGapSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Focus: $focusScore",
                style = PulseAppFontSubHeader,
                color = PulseAppColorSecondary
            )
        }
    }
}
