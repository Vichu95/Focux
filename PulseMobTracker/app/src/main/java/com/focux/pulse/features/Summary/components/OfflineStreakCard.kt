package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.focux.pulse.R
import com.focux.pulse.data.OfflineStreakData
import com.focux.pulse.ui.theme.*

@Composable
fun OfflineStreakCard(data: OfflineStreakData) {
    // Frame Unused Time (CSS Specs)
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppOfflineStreakCardHeight)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium)) // 2dp Border
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
