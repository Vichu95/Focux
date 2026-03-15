package com.focux.pulse.ui.screens.Summary.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focux.pulse.utilities.DeviceAccessData
import com.focux.pulse.ui.theme.*

@Composable
fun DeviceAccessCard(data: DeviceAccessData) {
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Device Access Meaning", color = PulseAppColorPrimary, style = PulseAppFontSubHeader) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("• Unlocks: Every time you turn your screen on and unlock your device to use an app.", color = PulseAppColorSecondary, style = PulseAppFontBody)
                    Text("• Glances: Turning your screen on without opening any app (e.g., just checking the time, or looking at a notification).", color = PulseAppColorSecondary, style = PulseAppFontBody)
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { showHelpDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
                ) { Text("Got it", color = PulseAppColorBackground) }
            },
            containerColor = PulseAppColorSurface
        )
    }

    // Frame Unlock Info (CSS Specs)
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppDeviceAccessCardHeight)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
            .clickable { showHelpDialog = true }
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

