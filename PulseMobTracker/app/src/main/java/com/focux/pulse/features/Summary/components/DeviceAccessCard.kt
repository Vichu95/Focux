package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focux.pulse.data_manager.DeviceAccessData
import com.focux.pulse.ui.theme.*

@Composable
fun DeviceAccessCard(data: DeviceAccessData) {
    // Frame Unlock Info (CSS Specs)
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppDeviceAccessCardHeight)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(PulseAppPaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Device Access",
            style = PulseAppFontHeader,
            color = PulseAppColorPrimary,
            modifier = Modifier.fillMaxWidth().align(Alignment.Start)
        )

        // Main Content Row (Centered vertically in remaining space)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly, // Space columns evenly
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unlocks Column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = data.unlocks.toString(),
                    style = PulseAppFontBigNumber,
                    color = PulseAppColorSecondary
                )
                Text(
                    text = "Unlocks",
                    style = Typography.bodyMedium.copy(fontSize = PulseAppFontSizeLabel),
                    color = PulseAppColorSecondary
                )
            }

            // Vertical Divider (Rectangle 4)
            // Height 97px in CSS, but here we can fill parent height or fixed. 
            // CSS has a specific height of 97px which matches the Frame 36 height.
            // We will use fillMaxHeight(0.8f) for a similar look or fixed height if strict.
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(97.dp)
                    .background(PulseAppColorSurface)
            )

            // Glances Column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = data.glances.toString(),
                    style = PulseAppFontBigNumber,
                    color = PulseAppColorSecondary
                )
                Text(
                    text = "Glances",
                    style = Typography.bodyMedium.copy(fontSize = PulseAppFontSizeLabel),
                    color = PulseAppColorSecondary
                )
            }
        }
    }
}

