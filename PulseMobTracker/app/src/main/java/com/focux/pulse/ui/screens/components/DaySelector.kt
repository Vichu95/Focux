package com.focux.pulse.ui.screens.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
fun DaySelector(
    date: LocalDate,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    canGoNext: Boolean,
    canGoPrev: Boolean,
    modifier: Modifier = Modifier
) {
    // Formatter: "14 Dec"
    val formatter = DateTimeFormatter.ofPattern("dd MMM")
    val dateText = date.format(formatter)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Prev",
            tint = if (canGoPrev) PulseAppColorSecondary else PulseAppColorSecondary.copy(alpha = 0.3f),
            modifier = Modifier
                .size(PulseAppIconSizeNavigation)
                .clip(CircleShape)
                .clickable(enabled = canGoPrev) { onPrevClick() }
        )
        // Using Spacer instead of padding to match DateFocusHeader exactly
        Spacer(modifier = Modifier.width(PulseAppGapHeader))
        
        Text(
            text = dateText,
            style = PulseAppFontHeader, // Matching Summary Screen Font
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
}
