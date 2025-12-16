package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.data.DeviceAccessData
import com.focux.pulse.ui.theme.CyberpunkPrimary
import com.focux.pulse.ui.theme.CyberpunkSecondaryText
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.cyberpunkCard

@Composable
fun DeviceAccessCard(data: DeviceAccessData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberpunkCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Device Access",
            style = Typography.titleLarge,
            color = CyberpunkPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = data.unlocks.toString(),
                    style = Typography.titleLarge.copy(fontSize = 32.sp),
                    color = CyberpunkSecondaryText
                )
                Text(
                    text = "Unlocks",
                    style = Typography.labelSmall.copy(fontSize = 14.sp),
                    color = CyberpunkSecondaryText
                )
            }

            // Vertical Divider could be added here if needed, but screenshot implies spacing or subtle line
            // Adding a simple spacer or line if desired. For now, space around is sufficient.
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = data.glances.toString(),
                    style = Typography.titleLarge.copy(fontSize = 32.sp),
                    color = CyberpunkSecondaryText
                )
                Text(
                    text = "Glances",
                    style = Typography.labelSmall.copy(fontSize = 14.sp),
                    color = CyberpunkSecondaryText
                )
            }
        }
    }
}
