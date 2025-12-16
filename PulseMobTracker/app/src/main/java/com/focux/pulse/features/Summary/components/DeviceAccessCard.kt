package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.data.DeviceAccessData
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.pulseAppCard

@Composable
fun DeviceAccessCard(data: DeviceAccessData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pulseAppCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Device Access",
            style = Typography.titleLarge,
            color = PulseAppColorPrimary
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
                    color = PulseAppColorSecondary
                )
                Text(
                    text = "Unlocks",
                    style = Typography.labelSmall.copy(fontSize = 14.sp),
                    color = PulseAppColorSecondary
                )
            }

            // Vertical Divider could be added here if needed, but screenshot implies spacing or subtle line
            // Adding a simple spacer or line if desired. For now, space around is sufficient.
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = data.glances.toString(),
                    style = Typography.titleLarge.copy(fontSize = 32.sp),
                    color = PulseAppColorSecondary
                )
                Text(
                    text = "Glances",
                    style = Typography.labelSmall.copy(fontSize = 14.sp),
                    color = PulseAppColorSecondary
                )
            }
        }
    }
}
