package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.R
import com.focux.pulse.data.OfflineStreakData
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.pulseAppCard

@Composable
fun OfflineStreakCard(data: OfflineStreakData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pulseAppCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Offline Streak",
            style = Typography.titleLarge,
            color = PulseAppColorPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.fire_icon), // Ensure this exists from our stub generator
                    contentDescription = "Streak",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = data.duration,
                    style = Typography.titleLarge.copy(fontSize = 32.sp),
                    color = PulseAppColorSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${data.startTime} - ${data.endTime}",
                style = Typography.labelSmall,
                color = PulseAppColorSecondary
            )
        }
    }
}
