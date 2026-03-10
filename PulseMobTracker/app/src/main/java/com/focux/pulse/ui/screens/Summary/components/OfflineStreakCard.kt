package com.focux.pulse.ui.screens.Summary.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.focux.pulse.R
import com.focux.pulse.utilities.OfflineStreakData
import com.focux.pulse.ui.theme.*

@Composable
fun OfflineStreakCard(data: OfflineStreakData) {
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Longest Offline Gap", color = PulseAppColorPrimary, style = PulseAppFontSubHeader) },
            text = { 
                Text("This tracks your longest continuous period of not using your phone during the day. A longer offline streak indicates deep, uninterrupted focus or rest.", color = PulseAppColorSecondary, style = PulseAppFontBody)
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

    // Frame Unused Time (CSS Specs)
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppOfflineStreakCardHeight)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium)) // 2dp Border
            .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
            .clickable { showHelpDialog = true }
            .padding(PulseAppCardPadding), // 16px Padding
        horizontalAlignment = Alignment.Start
    ) {
        // Header
        Text(
            text = "Offline Streak",
            style = PulseAppFontHeader, // 24sp Bold Primary
            color = PulseAppColorPrimary
        )

        // Main Content Row (Centered in remaining space)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Fire Icon (48x48)
                Icon(
                    painter = painterResource(id = R.drawable.fire_icon),
                    contentDescription = "Streak",
                    tint = PulseAppColorPrimary,
                    modifier = Modifier.size(PulseAppIconSizeLarge)
                )
                Spacer(modifier = Modifier.width(PulseAppPaddingSmall)) // 8px Gap
                
                // Duration Text (36sp White)
                Text(
                    text = data.duration,
                    style = PulseAppFontBigNumber,
                    color = PulseAppColorSecondary
                )
            }
            // Sub Text (16sp) - CSS says 14:00 - 16:40
            // Using Box or simply Text below since it's a Column
            Text(
                text = "${data.startTime} - ${data.endTime}",
                style = PulseAppFontSubHeader, // 16sp
                color = PulseAppColorSecondary // White
            )
        }
    }
}

