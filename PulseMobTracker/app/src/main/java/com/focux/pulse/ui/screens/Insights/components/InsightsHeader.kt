package com.focux.pulse.ui.screens.Insights.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.R
import com.focux.pulse.ui.theme.*
import androidx.compose.material.icons.Icons
import com.focux.pulse.ui.screens.components.WeekSelector

@Composable
fun InsightsHeader(
    dateRange: String,
    focusScore: Int,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    canGoNext: Boolean,
    canGoPrev: Boolean = true
) {
    // Frame Week Date Focus Score
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppWeeklyHeaderHeight), // 38dp
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. Date Navigation (Left Side) ---
        // --- 1. Date Navigation (Left Side) ---
        WeekSelector(
            dateRange = dateRange,
            onPrevClick = onPrevClick,
            onNextClick = onNextClick,
            canGoNext = canGoNext,
            canGoPrev = canGoPrev
        )

        // --- 2. Focus Score Badge (Right Side) ---
        // Frame 22
        Surface(
            color = PulseAppColorSurface, // #2B4555
            shape = RoundedCornerShape(PulseAppCornerRadiusMedium), // 12dp
            modifier = Modifier
                .width(PulseAppFocusScoreButtonWidth) // 110dp
                .height(PulseAppFocusScoreButtonHeight) // 28dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Focus: $focusScore",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = PulseAppFontSizeFocus, // 15sp
                        fontWeight = FontWeight.Normal,
                        color = PulseAppColorSecondary, // White
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}
