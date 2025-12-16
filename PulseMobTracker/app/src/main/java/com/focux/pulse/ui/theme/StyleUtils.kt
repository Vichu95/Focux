package com.focux.pulse.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Helper for the common card style
fun Modifier.pulseAppCard(): Modifier = composed {
    this
        .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
        .border(PulseAppBorderWidth, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
}
