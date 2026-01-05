package com.focux.pulse.features.Summary.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focux.pulse.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DateFocusHeader(
    date: LocalDate,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    canGoNext: Boolean = true
) {
    // Formatter: "14 Dec"
    val formatter = DateTimeFormatter.ofPattern("dd MMM")
    val dateText = date.format(formatter)

    // Frame Date Focus Score (Width 380px)
    Row(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppFocusScoreCardHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Date Nav (Frame Summary Date)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, 
                contentDescription = "Prev",
                tint = PulseAppColorSecondary,
                modifier = Modifier
                    .size(PulseAppIconSizeNavigation)
                    .clip(CircleShape)
                    .clickable { onPrevClick() }
            )
            Spacer(modifier = Modifier.width(PulseAppGapHeader))
            Text(
                text = dateText,
                style = PulseAppFontHeader,
                color = PulseAppColorSecondary
            )
            Spacer(modifier = Modifier.width(PulseAppGapHeader))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next",
                tint = if (canGoNext) PulseAppColorSecondary else PulseAppColorSecondary.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(PulseAppIconSizeNavigation)
                    .clip(CircleShape)
                    .clickable(enabled = canGoNext) { onNextClick() }
            )
        }
        
        // Right: Focus Score (Frame 22)
        Box(
            modifier = Modifier
                .background(PulseAppColorSurface, RoundedCornerShape(PulseAppCornerRadiusMedium))
                .padding(horizontal = PulseAppPaddingSmall, vertical = PulseAppGapSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Focus: 72",
                style = PulseAppFontSubHeader,
                color = PulseAppColorSecondary
            )
        }
    }
}
