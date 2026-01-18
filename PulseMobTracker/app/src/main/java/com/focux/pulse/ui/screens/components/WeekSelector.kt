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
import androidx.compose.ui.text.style.TextAlign
import com.focux.pulse.ui.theme.*

@Composable
fun WeekSelector(
    dateRange: String,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    canGoNext: Boolean,
    canGoPrev: Boolean,
    modifier: Modifier = Modifier
) {
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
        
        Spacer(modifier = Modifier.width(PulseAppGapHeader))
        
        Text(
            text = dateRange,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = PulseAppFontSizeHeader,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                color = PulseAppColorSecondary,
                textAlign = TextAlign.Center
            )
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
